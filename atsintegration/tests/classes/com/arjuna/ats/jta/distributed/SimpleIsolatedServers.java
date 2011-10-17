package com.arjuna.ats.jta.distributed;

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

import org.junit.BeforeClass;
import org.junit.Test;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.jta.distributed.server.IsolatableServersClassLoader;
import com.arjuna.ats.jta.distributed.server.LocalServer;
import com.arjuna.ats.jta.distributed.server.LookupProvider;
import com.arjuna.ats.jta.distributed.server.RemoteServer;

public class SimpleIsolatedServers {
	private static LookupProvider lookupProvider = new MyLookupProvider();
	private static LocalServer[] localServers = new LocalServer[3];
	private static RemoteServer[] remoteServers = new RemoteServer[3];

	@BeforeClass
	public static void setup() throws SecurityException, NoSuchMethodException, InstantiationException, IllegalAccessException, ClassNotFoundException,
			CoreEnvironmentBeanException, IOException, IllegalArgumentException, NoSuchFieldException {
		for (int i = 0; i < localServers.length; i++) {
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			IsolatableServersClassLoader classLoader = new IsolatableServersClassLoader("com.arjuna.ats.jta.distributed.server", contextClassLoader);
			localServers[i] = (LocalServer) classLoader.loadClass("com.arjuna.ats.jta.distributed.server.impl.ServerImpl").newInstance();
			Thread.currentThread().setContextClassLoader(localServers[i].getClass().getClassLoader());
			localServers[i].initialise(lookupProvider, (i + 1) * 1000);
			remoteServers[i] = localServers[i].connectTo();
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Test
	public void testRecovery() {
		// getLocalServer(3000).doRecoveryManagerScan();
		// getLocalServer(2000).doRecoveryManagerScan();
		getLocalServer(1000).doRecoveryManagerScan();
	}

	@Test
	public void testMigrateTransactionCommit() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {

		File file = new File(System.getProperty("user.dir") + "/tmp/");
		if (file.exists()) {
			file.delete();
		}
		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 3000, 2000, 1000, 2000, 3000, 1000, 3000 }));

		// Start out at the first server
		LocalServer originalServer = getLocalServer(1000);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(startingTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		transactionManager.suspend();
		performTransactionalWork(nodesToFlowTo, remainingTimeout, currentXid);
		transactionManager.resume(originalTransaction);
		originalServer.removeRootTransaction(currentXid);
		originalTransaction.commit();
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	@Test
	public void testMigrateTransactionSubordinateTimeout() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException, InterruptedException {

		File file = new File(System.getProperty("user.dir") + "/tmp/");
		if (file.exists()) {
			file.delete();
		}
		int rootTimeout = 10000;
		int subordinateTimeout = 1;

		// Start out at the first server
		LocalServer originalServer = getLocalServer(1000);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(rootTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		Xid toMigrate = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		Transaction transaction = transactionManager.getTransaction();
		transaction.enlistResource(new TestResource(originalServer.getNodeName(), false));
		transactionManager.suspend();

		// Migrate a transaction
		LocalServer currentServer = getLocalServer(2000);
		ClassLoader parentsClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());
		currentServer.getAndResumeTransaction(subordinateTimeout, toMigrate);
		currentServer.getTransactionManager().getTransaction().enlistResource(new TestResource(currentServer.getNodeName(), false));
		currentServer.getTransactionManager().suspend();
		Thread.currentThread().setContextClassLoader(parentsClassLoader);

		// Complete the transaction at the original server
		transactionManager.resume(transaction);
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
		transaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(toMigrate);
		Thread.currentThread().sleep((subordinateTimeout + 1) * 1000);
		try {
			originalTransaction.commit();
		} catch (RollbackException rbe) {
			// GOOD!
		} finally {
			Thread.currentThread().setContextClassLoader(classLoader);
		}
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
		transaction.enlistResource(new TestResource(currentServer.getNodeName(), false));

		if (!nodesToFlowTo.isEmpty()) {
			Integer nextServerNodeName = nodesToFlowTo.get(0);

			// FLOW THE TRANSACTION
			remainingTimeout = (int) (currentServer.getTimeLeftBeforeTransactionTimeout() / 1000);

			// SUSPEND THE TRANSACTION
			Xid currentXid = currentServer.getCurrentXid();
			transactionManager.suspend();
			boolean proxyRequired = performTransactionalWork(nodesToFlowTo, remainingTimeout, currentXid);
			transactionManager.resume(transaction);

			// Create a proxy for the new server if necessary, this can orphan
			// the remote server but XA recovery will handle that on the remote
			// server
			// The alternative is to always create a proxy but this is a
			// performance drain and will result in multiple subordinate
			// transactions and performance issues
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
