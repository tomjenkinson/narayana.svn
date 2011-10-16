package com.arjuna.ats.jta.distributed.server;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

public interface RemoteServer {

	public int propagatePrepare(Xid xid) throws XAException, DummyRemoteException;

	public void propagateCommit(Xid xid, boolean onePhase) throws IllegalStateException, HeuristicMixedException, HeuristicRollbackException,
			HeuristicCommitException, SystemException, XAException, DummyRemoteException;

	public void propagateRollback(Xid xid) throws IllegalStateException, HeuristicMixedException, HeuristicCommitException, HeuristicRollbackException,
			SystemException, XAException, DummyRemoteException;

	public Xid[] propagateRecover(int formatId, byte[] gtrid, Integer serverNodeNameToRecoverFor, int flag) throws XAException, DummyRemoteException;

	public void propagateForget(Xid xid) throws XAException, DummyRemoteException;

	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException, DummyRemoteException;

}
