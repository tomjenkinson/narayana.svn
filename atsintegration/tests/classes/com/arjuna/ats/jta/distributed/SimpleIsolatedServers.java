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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
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
			localServers[i].initialise(lookupProvider, String.valueOf((i + 1) * 1000), (i + 1) * 1000);
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

	private static void reboot(String serverName) throws Exception {
		int index = (Integer.valueOf(serverName) / 1000) - 1;
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(localServers[index].getClass().getClassLoader());
		localServers[index].shutdown();
		Thread.currentThread().setContextClassLoader(contextClassLoader);

		IsolatableServersClassLoader classLoader = new IsolatableServersClassLoader("com.arjuna.ats.jta.distributed.server", contextClassLoader);
		localServers[index] = (LocalServer) classLoader.loadClass("com.arjuna.ats.jta.distributed.server.impl.ServerImpl").newInstance();
		Thread.currentThread().setContextClassLoader(localServers[index].getClass().getClassLoader());
		localServers[index].initialise(lookupProvider, String.valueOf((index + 1) * 1000), (index + 1) * 1000);
		remoteServers[index] = localServers[index].connectTo();
		Thread.currentThread().setContextClassLoader(contextClassLoader);
	}

	/**
	 * Ensure that two servers can start up and call recover on the same server
	 * 
	 * The JCA XATerminator call wont allow intermediary calls to
	 * XATerminator::recover between TMSTARTSCAN and TMENDSCAN. This is fine for
	 * distributed JTA.
	 * 
	 * @throws XAException
	 * @throws IOException
	 * @throws DummyRemoteException
	 */
	@Test
	@BMScript("leave-subordinate-orphan")
	public void testSimultaneousRecover() throws Exception {
		System.out.println("testSimultaneousRecover");
		tearDown();
		setup();
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		{
			Thread thread = new Thread(new Runnable() {
				public void run() {
					int startingTimeout = 0;
					try {
						LocalServer originalServer = getLocalServer("1000");
						ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
						Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
						TransactionManager transactionManager = originalServer.getTransactionManager();
						transactionManager.setTransactionTimeout(startingTimeout);
						transactionManager.begin();
						Transaction originalTransaction = transactionManager.getTransaction();
						int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
						Xid currentXid = originalServer.getCurrentXid();
						originalServer.storeRootTransaction();
						XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
						transactionManager.suspend();
						performTransactionalWork(null, new LinkedList<String>(Arrays.asList(new String[] { "2000" })), remainingTimeout, currentXid, 2, false,
								false, 1);
						transactionManager.resume(originalTransaction);
						originalTransaction.enlistResource(proxyXAResource);
						originalServer.removeRootTransaction(currentXid);
						transactionManager.commit();
						Thread.currentThread().setContextClassLoader(classLoader);
					} catch (ExecuteException e) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					} catch (LinkageError t) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					} catch (Throwable t) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					}
				}
			}, "Orphan-creator");
			thread.start();
		}

		{
			Thread thread = new Thread(new Runnable() {
				public void run() {
					int startingTimeout = 0;
					try {
						LocalServer originalServer = getLocalServer("2000");
						ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
						Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
						TransactionManager transactionManager = originalServer.getTransactionManager();
						transactionManager.setTransactionTimeout(startingTimeout);
						transactionManager.begin();
						Transaction originalTransaction = transactionManager.getTransaction();
						int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
						Xid currentXid = originalServer.getCurrentXid();
						originalServer.storeRootTransaction();
						XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "1000");
						transactionManager.suspend();
						performTransactionalWork(null, new LinkedList<String>(Arrays.asList(new String[] { "1000" })), remainingTimeout, currentXid, 2, false,
								false, 1);
						transactionManager.resume(originalTransaction);
						originalTransaction.enlistResource(proxyXAResource);
						originalServer.removeRootTransaction(currentXid);
						transactionManager.commit();
						Thread.currentThread().setContextClassLoader(classLoader);
					} catch (ExecuteException e) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					} catch (LinkageError t) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					} catch (Throwable t) {
						System.err.println("Should be a thread death but cest la vie");
						synchronized (phase2CommitAborted) {
							phase2CommitAborted.incrementPhase2CommitAborted();
							phase2CommitAborted.notify();
						}
					}
				}
			}, "Orphan-creator");
			thread.start();
		}
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 2) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		getLocalServer("2000").doRecoveryManagerScan(true);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 2);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 1);

		tearDown();
		setup();
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		getLocalServer("1000").doRecoveryManagerScan(true);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 1);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 2);

	}

	/**
	 * Ensure that subordinate XA resource orphans created during 2PC can be
	 * recovered
	 */
	@Test
	@BMScript("leaveorphan")
	public void testTwoPhaseXAResourceOrphan() throws Exception {
		System.out.println("testTwoPhaseXAResourceOrphan");
		tearDown();
		setup();
		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					LocalServer originalServer = getLocalServer("1000");
					ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
					Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
					TransactionManager transactionManager = originalServer.getTransactionManager();
					transactionManager.setTransactionTimeout(startingTimeout);
					transactionManager.begin();
					Transaction originalTransaction = transactionManager.getTransaction();
					int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
					Xid currentXid = originalServer.getCurrentXid();
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
					originalServer.storeRootTransaction();
					transactionManager.suspend();
					performTransactionalWork(null, new LinkedList<String>(Arrays.asList(new String[] { "2000" })), remainingTimeout, currentXid, 1, false,
							false, 1);
					transactionManager.resume(originalTransaction);
					originalTransaction.enlistResource(proxyXAResource);
					// Needs a second resource to make sure we dont get the one
					// phase optimization happening
					originalTransaction.enlistResource(new TestResource(null, originalServer.getNodeName(), false));
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (Error t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 1) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		{

			LocalServer server = getLocalServer("2000");
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
		{
			LocalServer server = getLocalServer("1000");
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
	}

	/**
	 * Ensure that subordinate XA resource orphans created during 1PC (at root)
	 * can be recovered
	 */
	@Test
	@BMScript("leaveorphan")
	public void testOnePhaseXAResourceOrphan() throws Exception {
		System.out.println("testOnePhaseXAResourceOrphan");
		tearDown();
		setup();
		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					LocalServer originalServer = getLocalServer("1000");
					ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
					Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
					TransactionManager transactionManager = originalServer.getTransactionManager();
					transactionManager.setTransactionTimeout(startingTimeout);
					transactionManager.begin();
					Transaction originalTransaction = transactionManager.getTransaction();
					int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
					Xid currentXid = originalServer.getCurrentXid();
					originalServer.storeRootTransaction();
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
					transactionManager.suspend();
					performTransactionalWork(null, new LinkedList<String>(Arrays.asList(new String[] { "2000" })), remainingTimeout, currentXid, 2, false,
							false, 1);
					transactionManager.resume(originalTransaction);
					originalTransaction.enlistResource(proxyXAResource);
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (Error t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					t.printStackTrace();
//					synchronized (phase2CommitAborted) {
//						phase2CommitAborted.incrementPhase2CommitAborted();
//						phase2CommitAborted.notify();
//					}
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 1) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		{

			LocalServer server = getLocalServer("2000");
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
		{
			LocalServer server = getLocalServer("1000");
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 0);
			server.doRecoveryManagerScan(true);
			assertTrue(server.getCompletionCounter().getCommitCount() == 0);
			assertTrue(server.getCompletionCounter().getRollbackCount() == 1);
		}
	}

	/**
	 * Ensure that subordinate transaction orphans created during 1PC (at root)
	 * can be recovered
	 */
	@Test
	@BMScript("leave-subordinate-orphan")
	public void testOnePhaseSubordinateOrphan() throws Exception {
		System.out.println("testOnePhaseSubordinateOrphan");
		tearDown();
		setup();
		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					LocalServer originalServer = getLocalServer("1000");
					ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
					Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
					TransactionManager transactionManager = originalServer.getTransactionManager();
					transactionManager.setTransactionTimeout(startingTimeout);
					transactionManager.begin();
					Transaction originalTransaction = transactionManager.getTransaction();
					int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
					Xid currentXid = originalServer.getCurrentXid();
					originalServer.storeRootTransaction();
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
					transactionManager.suspend();
					performTransactionalWork(null, new LinkedList<String>(Arrays.asList(new String[] { "2000" })), remainingTimeout, currentXid, 2, false,
							false, 1);
					transactionManager.resume(originalTransaction);
					originalTransaction.enlistResource(proxyXAResource);
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (LinkageError t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 1) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		getLocalServer("1000").doRecoveryManagerScan(true);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 1);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 2);

	}

	/**
	 * Check that if transaction was in flight when a root crashed, when
	 * recovered it can terminate it.
	 * 
	 * recoverFor first greps the logs for any subordinates that are owned by
	 * "parentNodeName" then it greps the list of currently running transactions
	 * to see if any of them are owned by "parentNodeName" this is covered by
	 * testRecoverInflightTransaction basically what can happen is:
	 * 
	 * 1. TM1 starts tx 2. propagate to TM2 3. TM1 crashes 4. we need to
	 * rollback TM2 as it is now orphaned the detail being that as TM2 hasn't
	 * prepared we cant just grep the logs at TM2 as there wont be one
	 */
	@Test
	@BMScript("leaverunningorphan")
	public void testRecoverInflightTransaction() throws Exception {
		System.out.println("testRecoverInflightTransaction");
		tearDown();
		setup();
		final CompletionCounter counter = new CompletionCounter() {
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

		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				try {
					LocalServer originalServer = getLocalServer("1000");
					ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
					Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
					TransactionManager transactionManager = originalServer.getTransactionManager();
					transactionManager.setTransactionTimeout(startingTimeout);
					transactionManager.begin();
					Transaction originalTransaction = transactionManager.getTransaction();
					int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
					Xid currentXid = originalServer.getCurrentXid();
					originalServer.storeRootTransaction();
					XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
					transactionManager.suspend();
					performTransactionalWork(counter, new LinkedList<String>(Arrays.asList(new String[] { "2000" })), remainingTimeout, currentXid, 2, false,
							false, 1);
					transactionManager.resume(originalTransaction);
					originalTransaction.enlistResource(proxyXAResource);
					originalServer.removeRootTransaction(currentXid);
					transactionManager.commit();
					Thread.currentThread().setContextClassLoader(classLoader);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (LinkageError t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (Throwable t) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				}
			}
		}, "Orphan-creator");
		thread.start();
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 1) {
				phase2CommitAborted.wait();
			}
		}
		reboot("1000");
		assertTrue(counter.getCommitCount() == 0);
		assertTrue(counter.getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		getLocalServer("1000").doRecoveryManagerScan(true);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 1);
		assertTrue(counter.getCommitCount() == 0);
		assertTrue(counter.getRollbackCount() == 2);
	}

	/**
	 * Top down recovery of a prepared transaction
	 */
	@Test
	@BMScript("fail2pc")
	public void testRecovery() throws Exception {
		System.out.println("testRecovery");
		tearDown();
		setup();
		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("3000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		final Phase2CommitAborted phase2CommitAborted = new Phase2CommitAborted();
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int startingTimeout = 0;
				List<String> nodesToFlowTo = new LinkedList<String>(Arrays.asList(new String[] { "1000", "2000", "3000", "2000", "1000", "2000", "3000",
						"1000", "3000" }));
				try {
					doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true, false);
				} catch (ExecuteException e) {
					System.err.println("Should be a thread death but cest la vie");
					synchronized (phase2CommitAborted) {
						phase2CommitAborted.incrementPhase2CommitAborted();
						phase2CommitAborted.notify();
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		thread.start();
		synchronized (phase2CommitAborted) {
			if (phase2CommitAborted.getPhase2CommitAbortedCount() < 1) {
				phase2CommitAborted.wait();
			}
		}
		tearDown();
		setup();
		getLocalServer("1000").doRecoveryManagerScan(false);

		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 4);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 4);
		assertTrue(getLocalServer("3000").getCompletionCounter().getCommitCount() == 3);
		assertTrue(getLocalServer("3000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
	}

	@Test
	public void testOnePhaseCommit() throws Exception {
		System.out.println("testOnePhaseCommit");
		tearDown();
		setup();
		LocalServer originalServer = getLocalServer("1000");
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(0);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
		transactionManager.suspend();
		performTransactionalWork(getLocalServer("2000").getCompletionCounter(), new LinkedList<String>(Arrays.asList(new String[] { "2000" })),
				remainingTimeout, currentXid, 1, false, false, 1);
		transactionManager.resume(originalTransaction);
		originalTransaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(currentXid);
		transactionManager.commit();
		Thread.currentThread().setContextClassLoader(classLoader);

		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 1);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 1);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
	}

	@Test
	public void testUnPreparedRollback() throws Exception {
		System.out.println("testUnPreparedRollback");
		tearDown();
		setup();
		LocalServer originalServer = getLocalServer("1000");
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(0);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
		transactionManager.suspend();
		performTransactionalWork(getLocalServer("2000").getCompletionCounter(), new LinkedList<String>(Arrays.asList(new String[] { "2000" })),
				remainingTimeout, currentXid, 1, false, false, 1);
		transactionManager.resume(originalTransaction);
		originalTransaction.enlistResource(proxyXAResource);
		originalTransaction.registerSynchronization(originalServer.generateProxySynchronization(lookupProvider, originalServer.getNodeName(), "2000",
				currentXid));
		originalServer.removeRootTransaction(currentXid);
		transactionManager.rollback();
		Thread.currentThread().setContextClassLoader(classLoader);

		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 0);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 1);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 1);
	}

	@Test
	public void testMigrateTransactionRollbackOnlyCommit() throws Exception {
		System.out.println("testMigrateTransactionRollbackOnlyCommit");
		tearDown();
		setup();
		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(
				Arrays.asList(new String[] { "1000", "2000", "3000", "2000", "1000", "2000", "3000", "1000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true, true);
	}

	@Test
	public void testMigrateTransactionRollbackOnlyRollback() throws Exception {
		System.out.println("testMigrateTransactionRollbackOnlyRollback");
		tearDown();
		setup();
		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(
				Arrays.asList(new String[] { "1000", "2000", "3000", "2000", "1000", "2000", "3000", "1000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, false, true);
	}

	@Test
	public void testMigrateTransactionCommit() throws Exception {
		System.out.println("testMigrateTransactionCommit");
		tearDown();
		setup();
		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(
				Arrays.asList(new String[] { "1000", "2000", "3000", "2000", "1000", "2000", "3000", "1000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true, false);
	}

	@Test
	public void testMigrateTransactionCommitDiamond() throws Exception {
		System.out.println("testMigrateTransactionCommitDiamond");
		tearDown();
		setup();

		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(Arrays.asList(new String[] { "1000", "2000", "1000", "3000", "1000", "2000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, true, false);
	}

	@Test
	public void testMigrateTransactionRollback() throws Exception {
		System.out.println("testMigrateTransactionRollback");
		tearDown();
		setup();
		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(
				Arrays.asList(new String[] { "1000", "2000", "3000", "2000", "1000", "2000", "3000", "1000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, false, false);
	}

	@Test
	public void testMigrateTransactionRollbackDiamond() throws Exception {
		System.out.println("testMigrateTransactionRollbackDiamond");
		tearDown();
		setup();
		int startingTimeout = 0;
		List<String> nodesToFlowTo = new LinkedList<String>(Arrays.asList(new String[] { "1000", "2000", "1000", "3000", "1000", "2000", "3000" }));
		doRecursiveTransactionalWork(startingTimeout, nodesToFlowTo, false, false);
	}

	@Test
	public void testMigrateTransactionSubordinateTimeout() throws Exception {
		System.out.println("testMigrateTransactionSubordinateTimeout");
		tearDown();
		setup();
		int rootTimeout = 10000;
		int subordinateTimeout = 1;
		LocalServer originalServer = getLocalServer("1000");
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(rootTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		originalTransaction.enlistResource(new TestResource(originalServer.getCompletionCounter(), originalServer.getNodeName(), false));
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
		transactionManager.suspend();

		// Migrate a transaction
		LocalServer currentServer = getLocalServer("2000");
		ClassLoader parentsClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());
		currentServer.getAndResumeTransaction(subordinateTimeout, currentXid, 2000);
		currentServer.getTransactionManager().getTransaction()
				.enlistResource(new TestResource(currentServer.getCompletionCounter(), currentServer.getNodeName(), false));
		currentServer.getTransactionManager().suspend();
		Thread.currentThread().setContextClassLoader(parentsClassLoader);

		// Complete the transaction at the original server
		transactionManager.resume(originalTransaction);
		originalTransaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(currentXid);
		Thread.currentThread().sleep((subordinateTimeout + 1) * 1000);
		try {
			transactionManager.commit();
			fail("Did not rollback");
		} catch (RollbackException rbe) {
			// GOOD!
		} finally {
			Thread.currentThread().setContextClassLoader(classLoader);
		}
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 1);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 2);
	}
	
	
	@Test
	public void testTransactionReaperIsCleanedUp() throws Exception {
		System.out.println("testTransactionReaperIsCleanedUp");
		tearDown();
		setup();
		int rootTimeout = 5;
		LocalServer originalServer = getLocalServer("1000");
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(rootTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		originalTransaction.enlistResource(new TestResource(originalServer.getCompletionCounter(), originalServer.getNodeName(), false));
		XAResource proxyXAResource = originalServer.generateProxyXAResource(lookupProvider, "2000");
		int subordinateTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		transactionManager.suspend();

		// Migrate a transaction
		LocalServer currentServer = getLocalServer("2000");
		ClassLoader parentsClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());
		currentServer.getAndResumeTransaction(subordinateTimeout, currentXid, 2000);
		currentServer.getTransactionManager().getTransaction()
				.enlistResource(new TestResource(currentServer.getCompletionCounter(), currentServer.getNodeName(), false));
		currentServer.getTransactionManager().suspend();
		Thread.currentThread().setContextClassLoader(parentsClassLoader);

		// Complete the transaction at the original server
		transactionManager.resume(originalTransaction);
		originalTransaction.enlistResource(proxyXAResource);
		originalServer.removeRootTransaction(currentXid);
		transactionManager.commit();
		Thread.currentThread().setContextClassLoader(classLoader);
		assertTrue(getLocalServer("2000").getCompletionCounter().getCommitCount() == 1);
		assertTrue(getLocalServer("1000").getCompletionCounter().getCommitCount() == 2);
		assertTrue(getLocalServer("2000").getCompletionCounter().getRollbackCount() == 0);
		assertTrue(getLocalServer("1000").getCompletionCounter().getRollbackCount() == 0);
		
		Thread.currentThread().sleep((subordinateTimeout + 4) * 1000);
	}

	private void doRecursiveTransactionalWork(int startingTimeout, List<String> nodesToFlowTo, boolean commit, boolean rollbackOnlyOnLastNode) throws Exception {
		tearDown();
		setup();

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
		String startingServer = nodesToFlowTo.get(0);
		LocalServer originalServer = getLocalServer(startingServer);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(originalServer.getClass().getClassLoader());
		TransactionManager transactionManager = originalServer.getTransactionManager();
		transactionManager.setTransactionTimeout(startingTimeout);
		transactionManager.begin();
		Transaction transaction = transactionManager.getTransaction();
		int remainingTimeout = (int) (originalServer.getTimeLeftBeforeTransactionTimeout() / 1000);
		Xid currentXid = originalServer.getCurrentXid();
		originalServer.storeRootTransaction();
		transactionManager.suspend();
		DataReturnedFromRemoteServer dataReturnedFromRemoteServer = performTransactionalWork(counter, nodesToFlowTo, remainingTimeout, currentXid, 1, true,
				rollbackOnlyOnLastNode, 1);
		transactionManager.resume(transaction);
		originalServer.removeRootTransaction(currentXid);

		// Align the local state with the returning state of the
		// transaction
		// from the subordinate
		switch (dataReturnedFromRemoteServer.getTransactionState()) {
		case Status.STATUS_MARKED_ROLLBACK:
		case Status.STATUS_ROLLEDBACK:
		case Status.STATUS_ROLLING_BACK:
			switch (transaction.getStatus()) {
			case Status.STATUS_MARKED_ROLLBACK:
			case Status.STATUS_ROLLEDBACK:
			case Status.STATUS_ROLLING_BACK:
				transaction.setRollbackOnly();
			}
			break;
		default:
			break;
		}

		if (commit) {
			try {
				transactionManager.commit();
				assertTrue(counter.getCommitCount() == totalNodeCount);
			} catch (RollbackException e) {
				if (!rollbackOnlyOnLastNode) {
					assertTrue(counter.getRollbackCount() == totalNodeCount);
				}
			}
		} else {
			transactionManager.rollback();
			assertTrue(counter.getRollbackCount() == totalNodeCount);
		}
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	private DataReturnedFromRemoteServer performTransactionalWork(CompletionCounter counter, List<String> nodesToFlowTo, int remainingTimeout, Xid toMigrate,
			int numberOfResourcesToRegister, boolean addSynchronization, boolean rollbackOnlyOnLastNode, int nextAvailableSubordinateName)
			throws RollbackException, IllegalStateException, XAException, SystemException, NotSupportedException, IOException {
		String currentServerName = nodesToFlowTo.remove(0);
		LocalServer currentServer = getLocalServer(currentServerName);

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(currentServer.getClass().getClassLoader());

		boolean requiresProxyAtPreviousServer = !currentServer.getAndResumeTransaction(remainingTimeout, toMigrate, nextAvailableSubordinateName);
		if (requiresProxyAtPreviousServer) {
			nextAvailableSubordinateName++;
		}

		// Perform work on the migrated transaction
		{
			TransactionManager transactionManager = currentServer.getTransactionManager();
			Transaction transaction = transactionManager.getTransaction();
			if (addSynchronization) {
				transaction.registerSynchronization(new TestSynchronization(currentServer.getNodeName()));
			}
			for (int i = 0; i < numberOfResourcesToRegister; i++) {
				transaction.enlistResource(new TestResource(counter, currentServer.getNodeName(), false));
			}

			if (rollbackOnlyOnLastNode && nodesToFlowTo.isEmpty()) {
				transaction.setRollbackOnly();
			}
		}

		if (!nodesToFlowTo.isEmpty()) {

			TransactionManager transactionManager = currentServer.getTransactionManager();
			Transaction transaction = transactionManager.getTransaction();
			int status = transaction.getStatus();

			// Only propagate active transactions - this may be inactive through
			// user code (rollback/setRollbackOnly) or it may be inactive due to
			// the transaction reaper
			if (status == Status.STATUS_ACTIVE) {
				String nextServerNodeName = nodesToFlowTo.get(0);

				// FLOW THE TRANSACTION
				remainingTimeout = (int) (currentServer.getTimeLeftBeforeTransactionTimeout() / 1000);

				// STORE AND SUSPEND THE TRANSACTION
				Xid currentXid = currentServer.getCurrentXid();
				XAResource proxyXAResource = currentServer.generateProxyXAResource(lookupProvider, nodesToFlowTo.get(0));
				transactionManager.suspend();

				DataReturnedFromRemoteServer dataReturnedFromRemoteServer = performTransactionalWork(counter, nodesToFlowTo, remainingTimeout, currentXid,
						numberOfResourcesToRegister, addSynchronization, rollbackOnlyOnLastNode, nextAvailableSubordinateName);
				transactionManager.resume(transaction);

				// Create a proxy for the new server if necessary, this can
				// orphan
				// the remote server but XA recovery will handle that on the
				// remote
				// server
				// The alternative is to always create a proxy but this is a
				// performance drain and will result in multiple subordinate
				// transactions and performance issues
				if (dataReturnedFromRemoteServer.isProxyRequired()) {
					transaction.enlistResource(proxyXAResource);
					transaction.registerSynchronization(currentServer.generateProxySynchronization(lookupProvider, currentServer.getNodeName(),
							nextServerNodeName, toMigrate));
					nextAvailableSubordinateName = dataReturnedFromRemoteServer.getNextAvailableSubordinateName();
				} else {
					currentServer.cleanupProxyXAResource(proxyXAResource);
				}

				// Align the local state with the returning state of the
				// transaction
				// from the subordinate
				switch (dataReturnedFromRemoteServer.getTransactionState()) {
				case Status.STATUS_MARKED_ROLLBACK:
				case Status.STATUS_ROLLEDBACK:
				case Status.STATUS_ROLLING_BACK:
					switch (transaction.getStatus()) {
					case Status.STATUS_MARKED_ROLLBACK:
					case Status.STATUS_ROLLEDBACK:
					case Status.STATUS_ROLLING_BACK:
						transaction.setRollbackOnly();
					}
					break;
				default:
					break;
				}
			}
		}
		TransactionManager transactionManager = currentServer.getTransactionManager();
		int transactionState = transactionManager.getStatus();
		// SUSPEND THE TRANSACTION WHEN YOU ARE READY TO RETURN TO YOUR CALLER
		transactionManager.suspend();
		// Return to the previous caller back over the transport/classloader
		// boundary in this case
		Thread.currentThread().setContextClassLoader(classLoader);
		return new DataReturnedFromRemoteServer(requiresProxyAtPreviousServer, transactionState, nextAvailableSubordinateName);
	}

	private static LocalServer getLocalServer(String jndiName) {
		int index = (Integer.valueOf(jndiName) / 1000) - 1;
		return localServers[index];
	}

	private static class MyLookupProvider implements LookupProvider {

		@Override
		public RemoteServer lookup(String jndiName) {
			int index = (Integer.valueOf(jndiName) / 1000) - 1;
			return remoteServers[index];
		}

	}

	private class Phase2CommitAborted {
		private int phase2CommitAborted;

		public int getPhase2CommitAbortedCount() {
			return phase2CommitAborted;
		}

		public void incrementPhase2CommitAborted() {
			this.phase2CommitAborted++;
		}
	}

	/**
	 * This is the transactional data the transport needs to return from remote
	 * instances.
	 */
	private class DataReturnedFromRemoteServer {
		private boolean proxyRequired;

		private int transactionState;

		private Integer nextAvailableSubordinateName;

		public DataReturnedFromRemoteServer(boolean proxyRequired, int transactionState, Integer nextAvailableSubordinateName) {
			this.proxyRequired = proxyRequired;
			this.transactionState = transactionState;
			this.nextAvailableSubordinateName = nextAvailableSubordinateName;
		}

		public boolean isProxyRequired() {
			return proxyRequired;
		}

		public int getTransactionState() {
			return transactionState;
		}

		public Integer getNextAvailableSubordinateName() {
			return nextAvailableSubordinateName;
		}
	}
}
