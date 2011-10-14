package com.arjuna.ats.jta.distributed;

import java.io.IOException;
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

public interface Server {

	public void initialise(int nodeName) throws CoreEnvironmentBeanException, IOException;

	public int getNodeName();

	public TransactionManager getTransactionManager() throws NotSupportedException, SystemException;

	public boolean importTransaction(int remainingTimeout, Xid toMigrate) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException;

	public void propagateCommit(Xid xid, boolean onePhase) throws IllegalStateException, HeuristicMixedException, HeuristicRollbackException,
			HeuristicCommitException, SystemException, XAException;

	public int propagatePrepare(Xid xid) throws XAException;

	public void propagateRollback(Xid xid) throws IllegalStateException, HeuristicMixedException, HeuristicCommitException, HeuristicRollbackException,
			SystemException, XAException;

	public void propagateForget(Xid xid) throws XAException;

	public void doRecoveryManagerScan();

	public Xid[] propagateRecover(List<Integer> startScanned, int flag) throws XAException;

	public long getTimeLeftBeforeTransactionTimeout() throws RollbackException;

	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException;

	public Xid getCurrentXid() throws SystemException;

	public XAResource generateProxyXAResource(int currentNodeName, int nextNodeName);

	public Synchronization generateProxySynchronization(int serverId, int serverIdToProxyTo, Xid toRegisterAgainst);
}
