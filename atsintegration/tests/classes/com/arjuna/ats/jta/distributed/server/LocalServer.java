package com.arjuna.ats.jta.distributed.server;

import java.io.IOException;

import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;

public interface LocalServer {

	public void initialise(LookupProvider lookupProvider, Integer nodeName) throws CoreEnvironmentBeanException, IOException, SecurityException,
			NoSuchFieldException, IllegalArgumentException, IllegalAccessException;

	public Integer getNodeName();

	public TransactionManager getTransactionManager() throws NotSupportedException, SystemException;

	public void doRecoveryManagerScan();

	public long getTimeLeftBeforeTransactionTimeout() throws RollbackException;

	public void storeRootTransaction() throws SystemException;

	public void removeRootTransaction(Xid toMigrate);

	public boolean getAndResumeTransaction(int remainingTimeout, Xid toImport) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException;

	public RemoteServer connectTo();

	public XAResource generateProxyXAResource(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName);

	public Synchronization generateProxySynchronization(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName, Xid toRegisterAgainst);

	public Xid extractXid(XAResource proxyXAResource);

	public Xid getCurrentXid() throws SystemException;

	public CompletionCounter getCompletionCounter();
}
