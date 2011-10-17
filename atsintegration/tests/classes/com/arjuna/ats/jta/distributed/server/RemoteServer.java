package com.arjuna.ats.jta.distributed.server;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

public interface RemoteServer {

	public void setOffline(boolean offline);

	public int propagatePrepare(Xid xid) throws XAException, DummyRemoteException;

	public void propagateCommit(Xid xid) throws XAException, DummyRemoteException;

	public void propagateRollback(Xid xid) throws XAException, DummyRemoteException;

	public Xid[] propagateRecover(int formatId, byte[] gtrid, Integer serverNodeNameToRecoverFor, int flag) throws XAException, DummyRemoteException;

	public void propagateForget(Xid xid) throws XAException, DummyRemoteException;

	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException, DummyRemoteException;

}
