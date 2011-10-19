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
package com.arjuna.ats.jta.distributed;

import static org.junit.Assert.assertTrue;

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

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.byteman.rule.exception.ExecuteException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.jta.distributed.server.CompletionCounter;
import com.arjuna.ats.jta.distributed.server.DummyRemoteException;
import com.arjuna.ats.jta.distributed.server.IsolatableServersClassLoader;
import com.arjuna.ats.jta.distributed.server.LocalServer;
import com.arjuna.ats.jta.distributed.server.LookupProvider;
import com.arjuna.ats.jta.distributed.server.RemoteServer;

@RunWith(BMUnitRunner.class)
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

	@AfterClass
	public static void tearDown() throws Exception {
		for (int i = 0; i < localServers.length; i++) {
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(localServers[i].getClass().getClassLoader());
			localServers[i].shutdown();
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	/**
	 * The JCA XATerminator call wont allow intermediary calls to
	 * XATerminator::recover between TMSTARTSCAN and TMENDSCAN. This is fine for
	 * distributed JTA.
	 * 
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	@Test
	public void testSimultaneousRecover() throws XAException, DummyRemoteException {
		// Simulates different servers attempting to recover the XIDs from the
		// same server
		{
			RemoteServer server = lookupProvider.lookup(2000);
			server.propagateRecover(1000);
		}
		{
			RemoteServer server = lookupProvider.lookup(2000);
			server.propagateRecover(3000);
		}
	}

	@Test
	@BMScript("leaveorphan")
	public void testCreateOrphan() throws Exception {
		assertTrue(getLocalServer(3000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					int startingServer = 1000;
					LocalServer originalServer = getLocalServer(startingServer);
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
					performTransactionalWork(null, new LinkedList<Integer>(Arrays.asList(new Integer[] { 2000 })), remainingTimeout, currentXid, 1, false);
					transactionManager.resume(originalTransaction);
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
					originalTransaction.enlistResource(proxyXAResource);
					// Needs a second resource to make sure we dont get the one
					// phase optimization happening
					originalTransaction.enlistResource(new TestResource(null, originalServer.getNodeName(), false));
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (LinkageError t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (!phase2CommitAborted.isPhase2CommitAborted()) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		{

			LocalServer server = getLocalServer(2000);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
		{
			LocalServer server = getLocalServer(1000);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
	}

	@Test
	@BMScript("leaveorphan")
	public void testOnePhaseOrphan() throws Exception {
		assertTrue(getLocalServer(3000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					int startingServer = 1000;
					LocalServer originalServer = getLocalServer(startingServer);
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
					performTransactionalWork(null, new LinkedList<Integer>(Arrays.asList(new Integer[] { 2000 })), remainingTimeout, currentXid, 2, false);
					transactionManager.resume(originalTransaction);
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
					originalTransaction.enlistResource(proxyXAResource);
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (LinkageError t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (!phase2CommitAborted.isPhase2CommitAborted()) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		{

			LocalServer server = getLocalServer(2000);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
		{
			LocalServer server = getLocalServer(1000);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
	}

	@Test
	@BMScript("leave-subordinate-orphan")
	public void testOnePhaseSubordinateOrphan() throws Exception {
		assertTrue(getLocalServer(3000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					int startingServer = 1000;
					LocalServer originalServer = getLocalServer(startingServer);
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
					performTransactionalWork(null, new LinkedList<Integer>(Arrays.asList(new Integer[] { 2000 })), remainingTimeout, currentXid, 2, false);
					transactionManager.resume(originalTransaction);
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
					originalTransaction.enlistResource(proxyXAResource);
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (LinkageError t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (!phase2CommitAborted.isPhase2CommitAborted()) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getRollbackCount() == 0);
		getLocalServer(1000).doRecoveryManagerScan(true);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getRollbackCount() == 1);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getRollbackCount() == 2);

	}

	@Test
	@BMScript("fail2pc")
	public void testRecovery() throws Exception {
		tearDown();
		setup();
		assertTrue(getLocalServer(3000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer(3000).getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getRollbackCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 3000, 2000, 1000, 2000, 3000, 1000, 3000 }));
				try {
					doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true);
				} catch (InvalidTransactionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NotSupportedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SystemException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (RollbackException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (XAException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (HeuristicMixedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (HeuristicRollbackException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.setPhase2CommitAborted(true);
						phase2CommitAborted.notify();
					}
				}
			}
		});
		thread.start();
		synchronized (phase2CommitAborted) {
			if (!phase2CommitAborted.isPhase2CommitAborted()) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		getLocalServer(1000).doRecoveryManagerScan(false);

		assertTrue(getLocalServer(1000).getCompletionCounter().getCommitCount() == 4);
		assertTrue(getLocalServer(2000).getCompletionCounter().getCommitCount() == 4);
		assertTrue(getLocalServer(3000).getCompletionCounter().getCommitCount() == 3);
		assertTrue(getLocalServer(3000).getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer(2000).getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer(1000).getCompletionCounter().getRollbackCount() == 0);
	}

	@Test
	public void testOnePhaseCommit() throws NotSupportedException, SystemException, IllegalStateException, RollbackException, InvalidTransactionException,
			XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {
		int startingServer = 1000;
		LocalServer originalServer = getLocalServer(startingServer);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(0);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		transactionManager.suspend();
		performTransactionalWork(null, new LinkedList<Integer>(Arrays.asList(new Integer[] { 2000 })), remainingTimeout, currentXid, 1, false);
		transactionManager.resume(originalTransaction);
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
		originalTransaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(currentXid);
		transactionManager.commit();
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	@Test
	public void testUnPreparedRollback() throws NotSupportedException, SystemException, IllegalStateException, RollbackException, InvalidTransactionException,
			XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {
		int startingServer = 1000;
		LocalServer originalServer = getLocalServer(startingServer);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(0);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		transactionManager.suspend();
		performTransactionalWork(null, new LinkedList<Integer>(Arrays.asList(new Integer[] { 2000 })), remainingTimeout, currentXid, 1, false);
		transactionManager.resume(originalTransaction);
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
		originalTransaction.enlistResource(proxyXAResource);
		originalTransaction
				.registerSynchronization(originalServer.generateProxySynchronization(lookupProvider, originalServer.getNodeName(), 2000, currentXid));
		originalServer.removeRootTransaction(currentXid);
		transactionManager.rollback();
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	@Test
	public void testMigrateTransactionCommit() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {
		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 3000, 2000, 1000, 2000, 3000, 1000, 3000 }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true);
	}

	@Test
	public void testMigrateTransactionCommitDiamond() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {

		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 1000, 3000, 1000, 2000, 3000 }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true);
	}

	@Test
	public void testMigrateTransactionRollback() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {
		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 3000, 2000, 1000, 2000, 3000, 1000, 3000 }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, false);
	}

	@Test
	public void testMigrateTransactionRollbackDiamond() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {
		int startingTimeout = 0;
		List<Integer> nodesToFlowTo = new LinkedList<Integer>(Arrays.asList(new Integer[] { 1000, 2000, 1000, 3000, 1000, 2000, 3000 }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, false);
	}

	@Test
	public void testMigrateTransactionSubordinateTimeout() throws NotSupportedException, SystemException, IllegalStateException, RollbackException,
			InvalidTransactionException, XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException, InterruptedException {
		int rootTimeout = 10000;
		int subordinateTimeout = 1;

		// Start out at the first server
		CompletionCounter counter = new CompletionCounter() {
			private int commitCount = 0;
			private int rollbackCount = 0;

			@Override
			public void incrementCommit() {
				commitCount++;

			}

			@Override
			public void incrementRollback() {
				rollbackCount++;
			}

			@Override
			public int getCommitCount() {
				return commitCount;
			}

			@Override
			public int getRollbackCount() {
				return rollbackCount;
			}

			@Override
			public void resetCounters() {
				commitCount = 0;
				rollbackCount = 0;
			}
		};
		LocalServer originalServer = getLocalServer(1000);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(rootTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		Xid toMigrate = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		originalTransaction.enlistResource(new TestResource(counter, originalServer.getNodeName(), false));
		transactionManager.suspend();

		// Migrate a transaction
		LocalServer currentServer = getLocalServer(2000);
		ClassLoader parentsClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());
		currentServer.getAndResumeTransaction(subordinateTimeout, toMigrate);
		currentServer.getTransactionManager().getTransaction().enlistResource(new TestResource(counter, currentServer.getNodeName(), false));
		currentServer.getTransactionManager().suspend();
		Thread.currentThread().setContextClassLoader(parentsClassLoader);

		// Complete the transaction at the original server
		transactionManager.resume(originalTransaction);
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, originalServer.getNodeName(), 2000);
		originalTransaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(toMigrate);
		Thread.currentThread().sleep((subordinateTimeout + 1) * 1000);
		try {
			transactionManager.commit();
		} catch (RollbackException rbe) {
			// GOOD!
			assertTrue(counter.getRollbackCount() == 2);
		} finally {
			Thread.currentThread().setContextClassLoader(classLoader);
		}
	}

	private void doRecursiveTransactionalWork(int startingTimeout, List<Integer> nodesToFlowTo, boolean commit) throws NotSupportedException, SystemException,
			RollbackException, InvalidTransactionException, IllegalStateException, XAException, SecurityException, HeuristicMixedException,
			HeuristicRollbackException {

		// Start out at the first server
		CompletionCounter counter = new CompletionCounter() {
			private int commitCount = 0;
			private int rollbackCount = 0;

			@Override
			public void incrementCommit() {
				commitCount++;

			}

			@Override
			public void incrementRollback() {
				rollbackCount++;
			}

			@Override
			public int getCommitCount() {
				return commitCount;
			}

			@Override
			public int getRollbackCount() {
				return rollbackCount;
			}

			@Override
			public void resetCounters() {
				commitCount = 0;
				rollbackCount = 0;
			}
		};
		int totalNodeCount = nodesToFlowTo.size();
		int startingServer = nodesToFlowTo.get(0);
		LocalServer originalServer = getLocalServer(startingServer);
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
		performTransactionalWork(counter, nodesToFlowTo, remainingTimeout, currentXid, 1, true);
		transactionManager.resume(originalTransaction);
		originalServer.removeRootTransaction(currentXid);
		if (commit) {
			transactionManager.commit();
			assertTrue(counter.getCommitCount() == totalNodeCount);
		} else {
			transactionManager.rollback();
			assertTrue(counter.getRollbackCount() == totalNodeCount);
		}
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	private boolean performTransactionalWork(CompletionCounter counter, List<Integer> nodesToFlowTo, int remainingTimeout, Xid toMigrate,
			int numberOfResourcesToRegister, boolean addSynchronization) throws RollbackException, InvalidTransactionException, IllegalStateException,
			XAException, SystemException, NotSupportedException {
		Integer currentServerName = nodesToFlowTo.remove(0);
		LocalServer currentServer = getLocalServer(currentServerName);

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());

		boolean requiresProxyAtPreviousServer = !currentServer.getAndResumeTransaction(remainingTimeout, toMigrate);
		// Perform work on the migrated transaction
		TransactionManager transactionManager = currentServer.getTransactionManager();
		Transaction transaction = transactionManager.getTransaction();
		if (addSynchronization) {
			transaction.registerSynchronization(new TestSynchronization(currentServer.getNodeName()));
		}
		for (int i = 0; i < numberOfResourcesToRegister; i++) {
			transaction.enlistResource(new TestResource(counter, currentServer.getNodeName(), false));
		}

		if (!nodesToFlowTo.isEmpty()) {
			Integer nextServerNodeName = nodesToFlowTo.get(0);

			// FLOW THE TRANSACTION
			remainingTimeout = (int) (currentServer.getTimeLeftBeforeTransactionTimeout() / 1000);

			// SUSPEND THE TRANSACTION
			Xid currentXid = currentServer.getCurrentXid();
			transactionManager.suspend();
			boolean proxyRequired = performTransactionalWork(counter, nodesToFlowTo, remainingTimeout, currentXid, numberOfResourcesToRegister,
					addSynchronization);
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

	private class Phase2CommitAborted {
		private boolean phase2CommitAborted;

		public boolean isPhase2CommitAborted() {
			return phase2CommitAborted;
		}

		public void setPhase2CommitAborted(boolean phase2CommitAborted) {
			this.phase2CommitAborted = phase2CommitAborted;
		}
	}
}
