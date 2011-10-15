package com.arjuna.ats.jta.distributed;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
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

	public void initialise(Integer nodeName) throws CoreEnvironmentBeanException, IOException;

	public Integer getNodeName();

	public TransactionManager getTransactionManager() throws NotSupportedException, SystemException;

	public void doRecoveryManagerScan();

	public long getTimeLeftBeforeTransactionTimeout() throws RollbackException;

	public Xid getCurrentXid() throws SystemException;
	
	public void setOffline(boolean offline);

	public XAResource generateProxyXAResource(Integer localServerName, Integer remoteServerName);

	public Synchronization generateProxySynchronization(Integer localServerName, Integer remoteServerName, Xid toRegisterAgainst);

	public boolean importTransaction(int remainingTimeout, Xid toImport) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException;

}
