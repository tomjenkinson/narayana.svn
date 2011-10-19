/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.arjuna.jta.distributed.example;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.TransactionTimeoutConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.jta.distributed.example.server.IsolatableServersClassLoader;
import com.arjuna.jta.distributed.example.server.LocalServer;
import com.arjuna.jta.distributed.example.server.LookupProvider;
import com.arjuna.jta.distributed.example.server.RemoteServer;

public class ExampleDistributedJTATestCase {
	private static LookupProvider lookupProvider = new MyLookupProvider();
	private static LocalServer[] localServers = new LocalServer[3];
	private static RemoteServer[] remoteServers = new RemoteServer[3];

	@BeforeClass
	public static void setup() throws SecurityException, NoSuchMethodException, InstantiationException, IllegalAccessException, ClassNotFoundException,
			CoreEnvironmentBeanException, IOException, IllegalArgumentException, NoSuchFieldException {
		for (int i = 0; i < localServers.length; i++) {
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			IsolatableServersClassLoader classLoader = new IsolatableServersClassLoader("com.arjuna.jta.distributed.example.server", contextClassLoader);
			localServers[i] = (LocalServer) classLoader.loadClass("com.arjuna.jta.distributed.example.server.impl.ServerImpl").newInstance();
			Thread.currentThread().setContextClassLoader(localServers[i].getClass().getClassLoader());
			localServers[i].initialise(lookupProvider, (i + 1) * 1000);
			remoteServers[i] = localServers[i].connectTo();
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Test
	public void testMigrateTransaction() throws NotSupportedException, SystemException, IllegalStateException, RollbackException, InvalidTransactionException,
			XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {

		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 3000, 2000, 1000, 2000, 3000, 1000, 3000 }));

		// Start out at the first server
		int totalNodeCount = nodesToFlowTo.size();
		int startingServer = nodesToFlowTo.remove(0);
		LocalServer originalServer = getLocalServer(startingServer);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(startingTimeout);
		transactionManager.begin();

		Transaction originalTransaction = transactionManager.getTransaction();
		originalTransaction.registerSynchronization(new TestSynchronization(originalServer.getNodeName()));
		originalTransaction.enlistResource(new TestResource(originalServer.getNodeName()));

		if (!nodesToFlowTo.isEmpty()) {
			Integer nextServerNodeName = nodesToFlowTo.get(0);

			// FLOW THE TRANSACTION
			int remainingTimeout = (int) (((TransactionTimeoutConfiguration) transactionManager).getTimeLeftBeforeTransactionTimeout(false) / 1000);

			// SUSPEND THE TRANSACTION
			Xid currentXid = originalServer.getCurrentXid();
			originalServer.storeRootTransaction();
			transactionManager.suspend();
			boolean proxyRequired = performTransactionalWork(nodesToFlowTo, remainingTimeout, currentXid);
			transactionManager.resume(originalTransaction);

			// Create a proxy for the new server if necessary, this can orphan
			// the remote server but XA recovery will handle that on the remote
			// server
			// The alternative is to always create a proxy but this is a
			// performance drain and will result in multiple subordinate
			// transactions and performance issues
			if (proxyRequired) {
				XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), nextServerNodeName);
				originalTransaction.enlistResource(proxyXAResource);
				originalTransaction.registerSynchronization(originalServer.generateProxySynchronization(lookupProvider, originalServer.getNodeName(),
						nextServerNodeName, currentXid));
			}
			originalServer.removeRootTransaction(currentXid);
		}
		transactionManager.commit();
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	private boolean performTransactionalWork(List<Integer> nodesToFlowTo, int remainingTimeout, Xid toMigrate) throws RollbackException,
			InvalidTransactionException, IllegalStateException, XAException, SystemException, NotSupportedException {
		Integer currentServerName = nodesToFlowTo.remove(0);
		LocalServer currentServer = getLocalServer(currentServerName);

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());

		boolean requiresProxyAtPreviousServer = !currentServer.getAndResumeTransaction(remainingTimeout, toMigrate);
		// Perform work on the migrated transaction
		TransactionManager transactionManager = currentServer.getTransactionManager();
		Transaction transaction = transactionManager.getTransaction();
		transaction.registerSynchronization(new TestSynchronization(currentServer.getNodeName()));
		transaction.enlistResource(new TestResource(currentServer.getNodeName()));

		if (!nodesToFlowTo.isEmpty()) {
			Integer nextServerNodeName = nodesToFlowTo.get(0);

			// FLOW THE TRANSACTION
			remainingTimeout = (int) (((TransactionTimeoutConfiguration) transactionManager).getTimeLeftBeforeTransactionTimeout(false) / 1000);

			// SUSPEND THE TRANSACTION
			Xid currentXid = currentServer.getCurrentXid();
			transactionManager.suspend();
			boolean proxyRequired = performTransactionalWork(nodesToFlowTo, remainingTimeout, currentXid);
			transactionManager.resume(transaction);

			if (proxyRequired) {
				XAResource proxyXAResource = currentServer.generateProxyXAResource(lookupProvider, currentServer.getNodeName(), nextServerNodeName);
				transaction.enlistResource(proxyXAResource);
				transaction.registerSynchronization(currentServer.generateProxySynchronization(lookupProvider, currentServer.getNodeName(), nextServerNodeName,
						toMigrate));
			}
		}

		// SUSPEND THE TRANSACTION WHEN YOU ARE READY TO RETURN TO YOUR CALLER
		transactionManager.suspend();

		Thread.currentThread().setContextClassLoader(classLoader);
		return requiresProxyAtPreviousServer;
	}

	private static LocalServer getLocalServer(Integer jndiName) {
		int index = (jndiName / 1000) - 1;
		return localServers[index];
	}

	private static class MyLookupProvider implements LookupProvider {

		@Override
		public RemoteServer lookup(Integer jndiName) {
			int index = (jndiName / 1000) - 1;
			return remoteServers[index];
		}

	}
}
