package com.arjuna.ats.jta.distributed.impl;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
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
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
import com.arjuna.ats.internal.jbossatx.jta.XAResourceRecordWrappingPluginImpl;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateTransaction;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import com.arjuna.ats.jbossatx.jta.TransactionManagerService;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.distributed.RemoteServer;
import com.arjuna.ats.jta.distributed.LocalServer;
import com.arjuna.ats.jta.distributed.TestResourceRecovery;

public class ServerImpl implements LocalServer, RemoteServer {

	private int nodeName;
	private RecoveryManagerService recoveryManagerService;
	private TransactionManagerService transactionManagerService;
	private boolean offline;

	public void initialise(Integer serverName) throws CoreEnvironmentBeanException, IOException {
		this.nodeName = serverName;

		RecoveryEnvironmentBean recoveryEnvironmentBean = com.arjuna.ats.arjuna.common.recoveryPropertyManager.getRecoveryEnvironmentBean();
		recoveryEnvironmentBean.setRecoveryBackoffPeriod(1);

		recoveryEnvironmentBean.setRecoveryInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setRecoveryPort(4712 + serverName);
		recoveryEnvironmentBean.setTransactionStatusManagerInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setTransactionStatusManagerPort(4713 + serverName);
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
		coreEnvironmentBean.setSocketProcessIdPort(4714 + serverName);
		coreEnvironmentBean.setNodeIdentifier(serverName);
		coreEnvironmentBean.setSocketProcessIdMaxPorts(1);

		CoordinatorEnvironmentBean coordinatorEnvironmentBean = com.arjuna.ats.arjuna.common.arjPropertyManager.getCoordinatorEnvironmentBean();
		coordinatorEnvironmentBean.setEnableStatistics(false);
		coordinatorEnvironmentBean.setDefaultTimeout(300);
		coordinatorEnvironmentBean.setTransactionStatusManagerEnable(false);
		coordinatorEnvironmentBean.setDefaultTimeout(0);

		ObjectStoreEnvironmentBean actionStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "default");
		actionStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/tmp/tx-object-store/" + serverName);

		ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "stateStore");
		stateStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/tmp/tx-object-store/" + serverName);

		ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator
				.getNamedInstance(com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "communicationStore");
		communicationStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/tmp/tx-object-store/" + serverName);

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
		xaRecoveryNodes.add(serverName);
		jTAEnvironmentBean.setXaRecoveryNodes(xaRecoveryNodes);

		List<String> xaResourceOrphanFilterClassNames = new ArrayList<String>();

		xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.JTATransactionLogXAResourceOrphanFilter");
		xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.JTANodeNameXAResourceOrphanFilter");
		// xaResourceOrphanFilterClassNames.add("com.arjuna.ats.internal.jta.recovery.arjunacore.ParentNodeNameXAResourceOrphanFilter");
		jTAEnvironmentBean.setXaResourceOrphanFilterClassNames(xaResourceOrphanFilterClassNames);
		jTAEnvironmentBean.setXAResourceRecordWrappingPlugin(new XAResourceRecordWrappingPluginImpl());

		recoveryManagerService = new RecoveryManagerService();
		recoveryManagerService.create();
		recoveryManagerService.addXAResourceRecovery(new ProxyXAResourceRecovery(serverName));
		recoveryManagerService.addXAResourceRecovery(new TestResourceRecovery(serverName));
		// recoveryManagerService.start();
		RecoveryManager.manager().initialize();

		transactionManagerService = new TransactionManagerService();
		TxControl txControl = new com.arjuna.ats.arjuna.coordinator.TxControl();
		transactionManagerService.setJbossXATerminator(new com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator());
		transactionManagerService
				.setTransactionSynchronizationRegistry(new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple());
		transactionManagerService.create();

	}

	@Override
	public void doRecoveryManagerScan() {
		RecoveryManager.manager().scan();
	}

	@Override
	public TransactionManager getTransactionManager() {
		return transactionManagerService.getTransactionManager();
	}

	@Override
	public boolean importTransaction(int remainingTimeout, Xid toResume) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException {
		boolean existed = true;
		SubordinateTransaction importTransaction = SubordinationManager.getTransactionImporter().getImportedTransaction(toResume);
		if (importTransaction == null) {
			importTransaction = SubordinationManager.getTransactionImporter().importTransaction(toResume, remainingTimeout);
			existed = false;
		}
		getTransactionManager().resume(importTransaction);
		return existed;
	}

	@Override
	public Integer getNodeName() {
		return nodeName;
	}

	@Override
	public long getTimeLeftBeforeTransactionTimeout() throws RollbackException {
		return ((TransactionTimeoutConfiguration) transactionManagerService.getTransactionManager()).getTimeLeftBeforeTransactionTimeout(false);
	}

	@Override
	public Xid getCurrentXid() throws SystemException {
		TransactionImple transaction = ((TransactionImple) transactionManagerService.getTransactionManager().getTransaction());
		return transaction.getTxId();
	}

	@Override
	public XAResource generateProxyXAResource(Integer localServerName, Integer remoteServerName) {
		return new ProxyXAResource(localServerName, remoteServerName);
	}

	@Override
	public Synchronization generateProxySynchronization(Integer localServerName, Integer remoteServerName, Xid toRegisterAgainst) {
		return new ProxySynchronization(localServerName, remoteServerName, toRegisterAgainst);
	}

	@Override
	public int propagatePrepare(Xid xid) throws XAException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		return SubordinationManager.getTransactionImporter().getImportedTransaction(xid).doPrepare();
	}

	@Override
	public void propagateCommit(Xid xid, boolean onePhase) throws IllegalStateException, HeuristicMixedException, HeuristicRollbackException,
			HeuristicCommitException, SystemException, XAException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		SubordinationManager.getTransactionImporter().getImportedTransaction(xid).doCommit();
	}

	@Override
	public void propagateRollback(Xid xid) throws IllegalStateException, HeuristicMixedException, HeuristicCommitException, HeuristicRollbackException,
			SystemException, XAException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		SubordinationManager.getTransactionImporter().getImportedTransaction(xid).doRollback();
	}

	@Override
	public Xid[] propagateRecover(List<Integer> recoveryScanStarted, int flag) throws XAException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		// Assumes that this thread is used by the recovery thread
		ProxyXAResource.RECOVERY_SCAN_STARTED.set(recoveryScanStarted);
		return SubordinationManager.getXATerminator().recover(flag);
	}

	@Override
	public void propagateForget(Xid xid) throws XAException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		SubordinationManager.getXATerminator().forget(xid);

	}

	@Override
	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException, ConnectException {
		if (offline) {
			throw new ConnectException("Connection refused to: " + nodeName);
		}
		SubordinateTransaction tx = SubordinationManager.getTransactionImporter().getImportedTransaction(xid);
		tx.doBeforeCompletion();
	}

	@Override
	public void setOffline(boolean offline) {
		this.offline = offline;
	}
}
