package com.arjuna.ats.jta.distributed;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

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

public class SimpleIsolatedServers {
	private static Server[] servers = new Server[3];

	@BeforeClass
	public static void setup() throws SecurityException, NoSuchMethodException, InstantiationException, IllegalAccessException, ClassNotFoundException,
			CoreEnvironmentBeanException, IOException {

		// Get the Server interface loaded, only way I found to do this was
		// instantiate one
		Server server = (Server) java.lang.reflect.Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { Server.class },
				new InvocationHandler() {

					@Override
					public Object invoke(Object arg0, Method arg1, Object[] arg2) throws Throwable {
						// TODO Auto-generated method stub
						return null;
					}
				});

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		for (int i = 0; i < getServers().length; i++) {
			IsolatableServersClassLoader classLoader = new IsolatableServersClassLoader(contextClassLoader);
			getServers()[i] = (Server) classLoader.loadClass("com.arjuna.ats.jta.distributed.impl.ServerImpl").newInstance();
			getServers()[i].initialise((i + 1) * 1000);
		}
	}

	@Test
	public void testRecovery() {
		getServers()[0].doRecoveryManagerScan();
	}

	@Test
	public void testMigrateTransaction() throws NotSupportedException, SystemException, IllegalStateException, RollbackException, InvalidTransactionException,
			XAException, SecurityException, HeuristicMixedException, HeuristicRollbackException {

		File file = new File(System.getProperty("user.dir") + "/tmp/");
		if (file.exists()) {
			file.delete();
		}
		int startingTimeout = 0;

		// Start out at the first server
		Server originalServer = getServers()[0];
		TransactionManager transactionManager = getServers()[0].getTransactionManager();
		transactionManager.setTransactionTimeout(startingTimeout);
		transactionManager.begin();
		Transaction originalTransaction = transactionManager.getTransaction();
		originalTransaction.registerSynchronization(new TestSynchronization(originalServer.getNodeName()));
		originalTransaction.enlistResource(new TestResource(originalServer.getNodeName(), false));
		Xid toMigrate = originalServer.getCurrentXid();

		// Loop through the rest of the servers passing the transaction up and
		// down
		Transaction suspendedTransaction = originalServer.getTransactionManager().suspend();
		boolean proxyRequired = recursivelyFlowTransaction(0, 1, toMigrate);
		originalServer.getTransactionManager().resume(suspendedTransaction);
		if (proxyRequired) {
			XAResource proxyXAResource = originalServer.generateProxyXAResource(originalServer.getNodeName(), getServers()[1].getNodeName());
			originalTransaction.enlistResource(proxyXAResource);
			originalTransaction.registerSynchronization(originalServer.generateProxySynchronization(originalServer.getNodeName(),
					getServers()[1].getNodeName(), toMigrate));
		}

		Transaction transaction = transactionManager.getTransaction();
		transaction.commit();
	}

	private boolean recursivelyFlowTransaction(int previousServerIndex, int currentServerIndex, Xid toMigrate) throws RollbackException,
			InvalidTransactionException, IllegalStateException, XAException, SystemException, NotSupportedException {

		Server previousServer = getServers()[previousServerIndex];
		Server currentServer = getServers()[currentServerIndex];

		// Migrate the transaction to the next server
		int remainingTimeout = (int) (previousServer.getTimeLeftBeforeTransactionTimeout() / 1000);

		boolean requiresProxyAtPreviousServer = !currentServer.importTransaction(remainingTimeout, toMigrate);
		// Perform work on the migrated transaction
		TransactionManager transactionManager = currentServer.getTransactionManager();
		Transaction transaction = transactionManager.getTransaction();
		transaction.registerSynchronization(new TestSynchronization(currentServer.getNodeName()));
		transaction.enlistResource(new TestResource(currentServer.getNodeName(), false));

		int nextNextServerIndex = -1;
		if (currentServerIndex > previousServerIndex && currentServerIndex + 1 != getServers().length) {
			// Ascending
			nextNextServerIndex = currentServerIndex + 1;
		} else {
			// Descending
			nextNextServerIndex = currentServerIndex - 1;
		}

		// THE WORKHORSE OF FLOWING A TRANSACTION
		// SUSPEND THE TRANSACTION
		Transaction suspendedTransaction = currentServer.getTransactionManager().suspend();
		boolean proxyRequired = false;
		if (nextNextServerIndex != -1) {
			// FLOW THE TRANSACTION
			proxyRequired = recursivelyFlowTransaction(currentServerIndex, nextNextServerIndex, toMigrate);
		}
		// RESUME THE TRANSACTION IN CASE THERE IS MORE WORK
		currentServer.getTransactionManager().resume(suspendedTransaction);
		// Create a proxy for the new server if necessary
		if (proxyRequired) {
			XAResource proxyXAResource = currentServer.generateProxyXAResource(currentServer.getNodeName(), getServers()[nextNextServerIndex].getNodeName());
			suspendedTransaction.enlistResource(proxyXAResource);
			suspendedTransaction.registerSynchronization(currentServer.generateProxySynchronization(currentServer.getNodeName(),
					getServers()[nextNextServerIndex].getNodeName(), toMigrate));
		}
		// SUSPEND THE TRANSACTION WHEN YOU ARE READY TO RETURN TO YOUR CALLER
		suspendedTransaction = currentServer.getTransactionManager().suspend();
		return requiresProxyAtPreviousServer;
	}

	public static Server[] getServers() {
		return servers;
	}
}
