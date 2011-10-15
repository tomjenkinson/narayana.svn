package com.arjuna.ats.jta.distributed;

import java.net.ConnectException;
import java.util.List;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

public interface RemoteServer {

	public int propagatePrepare(Xid xid) throws XAException, ConnectException;

	public void propagateCommit(Xid xid, boolean onePhase) throws IllegalStateException, HeuristicMixedException, HeuristicRollbackException,
			HeuristicCommitException, SystemException, XAException, ConnectException;

	public void propagateRollback(Xid xid) throws IllegalStateException, HeuristicMixedException, HeuristicCommitException, HeuristicRollbackException,
			SystemException, XAException, ConnectException;

	public Xid[] propagateRecover(List<Integer> startScanned, int flag) throws XAException, ConnectException;

	public void propagateForget(Xid xid) throws XAException, ConnectException;

	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException, ConnectException;

}
