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
package com.arjuna.jta.distributed.example.server.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
import com.arjuna.ats.internal.jbossatx.jta.XAResourceRecordWrappingPluginImpl;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateXidImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.XATerminatorImple;
import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import com.arjuna.ats.jbossatx.jta.TransactionManagerService;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.xa.XATxConverter;
import com.arjuna.ats.jta.xa.XidImple;
import com.arjuna.jta.distributed.example.TestResourceRecovery;
import com.arjuna.jta.distributed.example.server.LocalServer;
import com.arjuna.jta.distributed.example.server.LookupProvider;
import com.arjuna.jta.distributed.example.server.RemoteServer;

/**
 * IMPORTANT: Although this example shows points at which the transport is
 * expected to persist data, it does not define concretely the mechanisms to do
 * so, nor should it be considered sufficient for reliably persisting this data.
 * For instance, we do not flush to disk.
 */
public class ServerImpl implements LocalServer, RemoteServer {

	private RecoveryManagerService recoveryManagerService;
	private TransactionManagerService transactionManagerService;
	private Map<SubordinateXidImple, TransactionImple> transactions = new HashMap<SubordinateXidImple, TransactionImple>();
	private String nodeName;
	private Map<SubordinateXidImple, File> subordinateOrphanDetection = new HashMap<SubordinateXidImple, File>();

	public void initialise(LookupProvider lookupProvider, String nodeName, int portOffset) throws CoreEnvironmentBeanException, IOException, SecurityException,
			NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		this.nodeName = nodeName;
		RecoveryEnvironmentBean recoveryEnvironmentBean = com.arjuna.ats.arjuna.common.recoveryPropertyManager.getRecoveryEnvironmentBean();
		recoveryEnvironmentBean.setRecoveryBackoffPeriod(1);

		recoveryEnvironmentBean.setRecoveryInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setRecoveryPort(4712 + portOffset);
		recoveryEnvironmentBean.setTransactionStatusManagerInetAddress(InetAddress.getByName("localhost"));
		recoveryEnvironmentBean.setTransactionStatusManagerPort(4713 + portOffset);
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
		coreEnvironmentBean.setSocketProcessIdPort(4714 + portOffset);
		coreEnvironmentBean.setNodeIdentifier(nodeName);
		coreEnvironmentBean.setSocketProcessIdMaxPorts(1);

		CoordinatorEnvironmentBean coordinatorEnvironmentBean = com.arjuna.ats.arjuna.common.arjPropertyManager.getCoordinatorEnvironmentBean();
		coordinatorEnvironmentBean.setEnableStatistics(false);
		coordinatorEnvironmentBean.setDefaultTimeout(300);
		coordinatorEnvironmentBean.setTransactionStatusManagerEnable(false);
		coordinatorEnvironmentBean.setDefaultTimeout(0);

		ObjectStoreEnvironmentBean actionStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "default");
		actionStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta-example/tx-object-store/" + nodeName);

		ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator.getNamedInstance(
				com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "stateStore");
		stateStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta-example/tx-object-store/" + nodeName);

		ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean = com.arjuna.common.internal.util.propertyservice.BeanPopulator
				.getNamedInstance(com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.class, "communicationStore");
		communicationStoreObjectStoreEnvironmentBean.setObjectStoreDir(System.getProperty("user.dir") + "/distributedjta-example/tx-object-store/" + nodeName);

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
		List<String> xaRecoveryNodes = new ArrayList<String>();
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
		recoveryManagerService.addXAResourceRecovery(new ProxyXAResourceRecovery(lookupProvider, nodeName));
		recoveryManagerService.addXAResourceRecovery(new TestResourceRecovery(nodeName));

		recoveryManagerService.start();

		transactionManagerService = new TransactionManagerService();
		TxControl txControl = new com.arjuna.ats.arjuna.coordinator.TxControl();
		transactionManagerService.setJbossXATerminator(new com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator());
		transactionManagerService
				.setTransactionSynchronizationRegistry(new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple());
		transactionManagerService.create();
	}

	@Override
	public TransactionManager getTransactionManager() {
		return transactionManagerService.getTransactionManager();
	}

	/**
	 * If this returns the root transaction, it must not be committed!
	 * 
	 * e.g. A transaction flowed 1,2,1 **must not** be committed at the third
	 * stage of the flow!!!
	 * 
	 * NOTE: CMT would not allow you do this anyway
	 * 
	 * @throws IOException
	 */
	@Override
	public boolean getAndResumeTransaction(int remainingTimeout, Xid toResume, Integer nextAvailableSubordinateName) throws XAException, IllegalStateException,
			SystemException, IOException {
		boolean existed = true;
		Transaction transaction = transactions.get(new SubordinateXidImple(toResume));
		if (transaction == null) {
			transaction = SubordinationManager.getTransactionImporter().getImportedTransaction(toResume);
			if (transaction == null) {

				File dir = new File(System.getProperty("user.dir") + "/distributedjta-example/SubordinateNameXANodeNameMap/" + TxControl.getXANodeName());
				dir.mkdirs();
				File file = new File(dir, new Uid().fileStringForm());
				file.createNewFile();
				DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
				byte[] nodeName = TxControl.getXANodeName().getBytes();
				fos.writeInt(nodeName.length);
				fos.write(nodeName);
				fos.writeInt(nextAvailableSubordinateName);
				fos.writeInt(toResume.getGlobalTransactionId().length);
				fos.write(toResume.getGlobalTransactionId());

				subordinateOrphanDetection.put(new SubordinateXidImple(toResume), file);

				XidImple toImport = new XidImple(toResume);
				XATxConverter.setSubordinateNodeName(toImport.getXID(), nextAvailableSubordinateName);

				transaction = SubordinationManager.getTransactionImporter().importTransaction(toImport, remainingTimeout);
				existed = false;
			}
		}
		transactionManagerService.getTransactionManager().resume(transaction);
		return existed;
	}

	@Override
	public String getNodeName() {
		return TxControl.getXANodeName();
	}

	/**
	 * If a subordinate returns the root transaction in a call to
	 * getAndResumeTransaction, it must not be committed
	 * 
	 * e.g. A transaction flowed 1,2,1 **must not** be committed at the third
	 * stage of the flow!!!
	 */
	@Override
	public void storeRootTransaction(Transaction transaction) throws SystemException {
		TransactionImple transactionI = ((TransactionImple) transaction);
		Xid txId = transactionI.getTxId();
		transactions.put(new SubordinateXidImple(txId), transactionI);
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
	public ProxyXAResource generateProxyXAResource(LookupProvider lookupProvider, String remoteServerName) throws IOException, SystemException {
		// Persist a proxy for the remote server this can mean we try to recover
		// transactions at a remote server that did not get chance to
		// prepare but the alternative is to orphan a prepared server

		Xid currentXid = getCurrentXid();
		File dir = new File(System.getProperty("user.dir") + "/distributedjta-example/ProxyXAResource/" + TxControl.getXANodeName());
		dir.mkdirs();
		File file = new File(dir, new Uid().fileStringForm());
		file.createNewFile();
		DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
		fos.writeInt(remoteServerName.length());
		fos.writeBytes(remoteServerName);
		fos.writeInt(currentXid.getFormatId());
		fos.writeInt(currentXid.getGlobalTransactionId().length);
		fos.write(currentXid.getGlobalTransactionId());
		fos.writeInt(currentXid.getBranchQualifier().length);
		fos.write(currentXid.getBranchQualifier());

		return new ProxyXAResource(lookupProvider, nodeName, remoteServerName, file);
	}

	@Override
	public void cleanupProxyXAResource(XAResource proxyXAResource) {
		((ProxyXAResource) proxyXAResource).deleteTemporaryFile();
	}

	@Override
	public Synchronization generateProxySynchronization(LookupProvider lookupProvider, String remoteServerName, Xid toRegisterAgainst) {
		return new ProxySynchronization(lookupProvider, nodeName, remoteServerName, toRegisterAgainst);
	}

	@Override
	public RemoteServer connectTo() {
		return this;
	}

	@Override
	public int prepare(Xid xid) throws XAException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			return SubordinationManager.getXATerminator().prepare(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public void commit(Xid xid, boolean onePhase, boolean recover) throws XAException, IOException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		if (recover) {
			recover(xid);
		}
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().commit(xid, onePhase);
			subordinateOrphanDetection.remove(new SubordinateXidImple(xid)).delete();
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	@Override
	public void rollback(Xid xid, boolean recover) throws XAException, IOException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		if (recover) {
			recover(xid);
		}
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().rollback(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);

			subordinateOrphanDetection.remove(new SubordinateXidImple(xid)).delete();
		}
	}

	public void recover(Xid toRecover) throws XAException, IOException {
		// Work out what the subordinate name would be for these transaction
		// for this server
		XidImple recoverable = null;

		// Look at the list of subordinate name to XA node name map
		File directory = new File(System.getProperty("user.dir") + "/distributedjta-example/SubordinateNameXANodeNameMap/" + nodeName + "/");
		if (directory.exists() && directory.isDirectory()) {
			File[] listFiles = directory.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File file = listFiles[i];
				DataInputStream fis = new DataInputStream(new FileInputStream(file));
				int nodeNameLength = fis.readInt();
				final byte[] nodeNameBytes = new byte[nodeNameLength];
				fis.read(nodeNameBytes, 0, nodeNameLength);
				String nodeName = new String(nodeNameBytes);

				// Is the node name this servers node name
				if (nodeName.equals(TxControl.getXANodeName())) {
					// Read in the subordinate name for the encapsulated
					// transaction
					Integer subordinateNodeName = fis.readInt();
					int gtridLength = fis.readInt();
					byte[] gtrid = new byte[gtridLength];
					fis.read(gtrid, 0, gtridLength);

					// Check if the transaction in the list the client is
					// requesting
					byte[] requestedGtrid = toRecover.getGlobalTransactionId();
					if (Arrays.equals(gtrid, requestedGtrid)) {
						// Set the subordinate name
						recoverable = new XidImple(toRecover);
						XATxConverter.setSubordinateNodeName(recoverable.getXID(), subordinateNodeName);
						subordinateOrphanDetection.put(new SubordinateXidImple(recoverable), file);
					}
				}
			}

		}

		if (recoverable != null) {
			((XATerminatorImple) SubordinationManager.getXATerminator()).doRecover(recoverable);
		}
	}

	@Override
	public void forget(Xid xid) throws XAException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			SubordinationManager.getXATerminator().forget(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
			subordinateOrphanDetection.remove(new SubordinateXidImple(xid)).delete();
		}

	}

	@Override
	public void beforeCompletion(Xid xid) throws XAException, SystemException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			((XATerminatorImple) SubordinationManager.getXATerminator()).beforeCompletion(xid);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}
}
