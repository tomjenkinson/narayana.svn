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
package com.arjuna.ats.jta.distributed.server.impl;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.TransactionTimeoutConfiguration;

import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
import com.arjuna.ats.internal.arjuna.utils.ManualProcessId;
import com.arjuna.ats.internal.jbossatx.jta.XAResourceRecordWrappingPluginImpl;
import com.arjuna.ats.internal.jta.recovery.arjunacore.RecoveryXids;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateTransaction;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateXidImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.XATerminatorImple;
import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import com.arjuna.ats.jbossatx.jta.TransactionManagerService;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.distributed.TestResourceRecovery;
import com.arjuna.ats.jta.distributed.server.CompletionCounter;
import com.arjuna.ats.jta.distributed.server.DummyRemoteException;
import com.arjuna.ats.jta.distributed.server.LocalServer;
import com.arjuna.ats.jta.distributed.server.LookupProvider;
import com.arjuna.ats.jta.distributed.server.RemoteServer;

public class ServerImpl implements LocalServer, RemoteServer {

	private int nodeName;
	private RecoveryManagerService recoveryManagerService;
	private TransactionManagerService transactionManagerService;
	private boolean offline;
	private LookupProvider lookupProvider;
	private Map<SubordinateXidImple, TransactionImple> transactions = new HashMap<SubordinateXidImple, TransactionImple>();
	private RecoveryManager _recoveryManager;
	private CompletionCounter counter;

	public void initialise(LookupProvider lookupProvider, Integer nodeName) throws CoreEnvironmentBeanException, IOException, SecurityException,
			NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		this.lookupProvider = lookupProvider;
		this.nodeName = nodeName;
		this.counter = new CompletionCounter() {
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

		RecoveryEnvironmentBean recoveryEnvironmentBean = com.arjuna.ats.arjuna.common.recoveryPropertyManager.getRecoveryEnvironmentBean();
		recoveryEnvironmentBean.setRecoveryBackoffPeriod(1);

		recoveryEnvironmentBean.setRecoveryInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setRecoveryPort(4712 + nodeName);
		recoveryEnvironmentBean.setTransactionStatusManagerInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setTransactionStatusManagerPort(4713 + nodeName);
		List<String> recoveryModuleClassNames = new ArrayList<String>();

		recoveryModuleClassNames.add("com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule");
		recoveryModuleClassNames.add("com.arjuna.ats.internal.txoj.recovery.TORecoveryModule");
		recoveryModuleClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule");
		recoveryEnvironmentBean.setRecoveryModuleClassNames(recoveryModuleClassNames);
		List<String> expiryScannerClassNames = new ArrayList<String>();
		expiryScannerClassNames.add("com.arjuna.ats.internal.arjuna.recovery.ExpiredTransactionStatusManagerScanner");
		recoveryEnvironmentBean.setExpiryScannerClassNames(expiryScannerClassNames);
		recoveryEnvironmentBean.setRecoveryActivators(null);

		CoreEnvironmentBean coreEnvironmentBean = com.arjuna.ats.arjuna.common.arjPropertyManager.getCoreEnvironmentBean();
		// coreEnvironmentBean.setSocketProcessIdPort(4714 + nodeName);
		coreEnvironmentBean.setNodeIdentifier(nodeName);
		// coreEnvironmentBean.setSocketProcessIdMaxPorts(1);
		coreEnvironmentBean.setProcessImplementationClassName(ManualProcessId.class.getName());
		coreEnvironmentBean.setPid(coreEnvironmentBean.getNodeIdentifier());

		CoordinatorEnvironmentBean coordinatorEnvironmentBean = com.arjuna.ats.arjuna.common.arjPropertyManager.getCoordinatorEnvironmentBean();
		coordinatorEnvironmentBean.setEnableStatistics(false);
		coordinatorEnvironmentBean.setDefaultTimeout(300);
		coordinatorEnvironmentBean.setTransactionStatusManagerEnable(false);
		coordinatorEnvironmentBean.setDefaultTimeout(0);

		ObjectStoreEnvironmentBean actionStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "default");
		actionStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta/tx-object-store/" + nodeName);

		ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "stateStore");
		stateStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta/tx-object-store/" + nodeName);

		ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator
				.getNamedInstance(com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "communicationStore");
		communicationStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta/tx-object-store/" + nodeName);

		ObjStoreBrowser objStoreBrowser = new ObjStoreBrowser();
		Map<String, String> types = new HashMap<String, String>();
		types.put("StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction", "com.arjuna.ats.internal.jta.tools.osb.mbean.jta.JTAActionBean");
		objStoreBrowser.setTypes(types);

		JTAEnvironmentBean jTAEnvironmentBean = com.arjuna.ats.jta.common.jtaPropertyManager.getJTAEnvironmentBean();
		jTAEnvironmentBean.setLastResourceOptimisationInterface(org.jboss.tm.LastResource.class);
		jTAEnvironmentBean.setTransactionManagerClassName("com.arjuna.ats.jbossatx.jta.TransactionManagerDelegate");
		jTAEnvironmentBean.setUserTransactionClassName("com.arjuna.ats.internal.jta.transaction.arjunacore.UserTransactionImple");
		jTAEnvironmentBean
				.setTransactionSynchronizationRegistryClassName("com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple");
		List<Integer> xaRecoveryNodes = new ArrayList<Integer>();
		xaRecoveryNodes.add(nodeName);
		jTAEnvironmentBean.setXaRecoveryNodes(xaRecoveryNodes);

		List<String> xaResourceOrphanFilterClassNames = new ArrayList<String>();

		xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.JTATransactionLogXAResourceOrphanFilter");
		xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.JTANodeNameXAResourceOrphanFilter");
		xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.SubordinateJTAXAResourceOrphanFilter");
		jTAEnvironmentBean.setXaResourceOrphanFilterClassNames(xaResourceOrphanFilterClassNames);
		jTAEnvironmentBean.setXAResourceRecordWrappingPlugin(new XAResourceRecordWrappingPluginImpl());

		recoveryManagerService = new RecoveryManagerService();
		recoveryManagerService.create();
		recoveryManagerService.addXAResourceRecovery(new ProxyXAResourceRecovery(counter, lookupProvider, nodeName));
		recoveryManagerService.addXAResourceRecovery(new TestResourceRecovery(counter, nodeName));

		// recoveryManagerService.start();
		_recoveryManager = RecoveryManager.manager();
		RecoveryManager.manager().initialize();

		transactionManagerService = new TransactionManagerService();
		TxControl txControl = new com.arjuna.ats.arjuna.coordinator.TxControl();
		transactionManagerService.setJbossXATerminator(new com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator());
		transactionManagerService
				.setTransactionSynchronizationRegistry(new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple());
		transactionManagerService.create();
	}

	@Override
	public void shutdown() throws Exception {
		recoveryManagerService.stop();
		TransactionReaper.transactionReaper().terminate(false);
	}

	@Override
	public void doRecoveryManagerScan(boolean hackSafetyInterval) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader serversClassLoader = this.getClass().getClassLoader();
		Thread.currentThread().setContextClassLoader(serversClassLoader);
		int originalSafetyInterval = -1;

		if (hackSafetyInterval) {
			try {
				Field safetyIntervalMillis = RecoveryXids.class.getDeclaredField("safetyIntervalMillis");
				safetyIntervalMillis.setAccessible(true);
				originalSafetyInterval = (Integer) safetyIntervalMillis.get(null);
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				safetyIntervalMillis.set(null, 0);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		_recoveryManager.scan();

		if (hackSafetyInterval) {
			try {
				Field safetyIntervalMillis = RecoveryXids.class.getDeclaredField("safetyIntervalMillis");
				safetyIntervalMillis.setAccessible(true);
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				safetyIntervalMillis.set(null, originalSafetyInterval);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	@Override
	public TransactionManager getTransactionManager() {
		return transactionManagerService.getTransactionManager();
	}

	@Override
	public boolean getAndResumeTransaction(int remainingTimeout, Xid toResume) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException {
		boolean existed = true;
		Transaction transaction = transactions.get(new SubordinateXidImple(toResume));
		if (transaction == null) {
			transaction = SubordinationManager.getTransactionImporter().getImportedTransaction(toResume);
			if (transaction == null) {
				transaction = SubordinationManager.getTransactionImporter().importTransaction(toResume, remainingTimeout);
				existed = false;
			}
		}
		transactionManagerService.getTransactionManager().resume(transaction);
		return existed;
	}

	@Override
	public Integer getNodeName() {
		return nodeName;
	}

	@Override
	public long getTimeLeftBeforeTransactionTimeout() throws RollbackException {
		return ((TransactionTimeoutConfiguration) transactionManagerService.getTransactionManager()).getTimeLeftBeforeTransactionTimeout(true);
	}

	@Override
	public void storeRootTransaction() throws SystemException {
		TransactionImple transaction = ((TransactionImple) transactionManagerService.getTransactionManager().getTransaction());
		Xid txId = transaction.getTxId();
		transactions.put(new SubordinateXidImple(txId), transaction);
	}

	@Override
	public Xid getCurrentXid() throws SystemException {
		TransactionImple transaction = ((TransactionImple) transactionManagerService.getTransactionManager().getTransaction());
		return transaction.getTxId();
	}

	@Override
	public void removeRootTransaction(Xid toMigrate) {
		transactions.remove(new SubordinateXidImple(toMigrate));
	}

	@Override
	public CompletionCounter getCompletionCounter() {
		return counter;
	}

	@Override
	public ProxyXAResource generateProxyXAResource(LookupProvider lookupProvider, Integer remoteServerName) throws SystemException, IOException {

		// Persist a proxy for the remote server this can mean we try to recover
		// transactions at a remote server that did not get chance to
		// prepare but the alternative is to orphan a prepared server

		Xid currentXid = getCurrentXid();
		File dir = new File(System.getProperty("user.dir") + "/distributedjta/ProxyXAResource/" + getNodeName());
		dir.mkdirs();
		File file = new File(dir, new Uid().fileStringForm());
		file.createNewFile();
		DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
		fos.writeInt(remoteServerName);
		fos.writeInt(currentXid.getFormatId());
		fos.writeInt(currentXid.getGlobalTransactionId().length);
		fos.write(currentXid.getGlobalTransactionId());
		fos.writeInt(currentXid.getBranchQualifier().length);
		fos.write(currentXid.getBranchQualifier());

		return new ProxyXAResource(counter, lookupProvider, getNodeName(), remoteServerName, file);
	}

	@Override
	public void cleanupProxyXAResource(XAResource proxyXAResource) {
		((ProxyXAResource) proxyXAResource).deleteTemporaryFile();
	}

	@Override
	public Synchronization generateProxySynchronization(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName, Xid toRegisterAgainst) {
		return new ProxySynchronization(lookupProvider, localServerName, remoteServerName, toRegisterAgainst);
	}

	@Override
	public void setOffline(boolean offline) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader serversClassLoader = this.getClass().getClassLoader();
		Thread.currentThread().setContextClassLoader(serversClassLoader);
		this.offline = offline;
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	@Override
	public RemoteServer connectTo() {
		return this;
	}

	@Override
	public int prepare(Xid xid) throws XAException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			return SubordinationManager.getXATerminator().prepare(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public void commit(Xid xid, boolean onePhase) throws XAException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().commit(xid, onePhase);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public void rollback(Xid xid) throws XAException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().rollback(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public Xid[] recoverFor(Integer parentNodeName) throws XAException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			Xid[] recovered = ((XATerminatorImple) SubordinationManager.getXATerminator()).doRecover(parentNodeName, true);
			return recovered;
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public void forget(Xid xid) throws XAException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().forget(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}

	}

	@Override
	public void beforeCompletion(Xid xid) throws XAException, SystemException, DummyRemoteException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			((XATerminatorImple) SubordinationManager.getXATerminator()).beforeCompletion(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}
}
