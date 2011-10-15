package com.arjuna.ats.jta.distributed.server.impl;

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import com.arjuna.ats.jta.distributed.server.DummyRemoteException;
import com.arjuna.ats.jta.distributed.server.LookupProvider;

public class ProxySynchronization implements Synchronization {

	private int localServerName;
	private int remoteServerName;
	private Xid toRegisterAgainst;
	private LookupProvider lookupProvider;

	public ProxySynchronization(LookupProvider lookupProvider, int localServerName, int remoteServerName, Xid toRegisterAgainst) {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
		this.toRegisterAgainst = toRegisterAgainst;
	}

	@Override
	public void beforeCompletion() {
		System.out.println("ProxySynchronization (" + localServerName + ":" + remoteServerName + ") beforeCompletion");
		try {
			lookupProvider.lookup(remoteServerName).propagateBeforeCompletion(toRegisterAgainst);
		} catch (XAException e) {
			e.printStackTrace();
		} catch (SystemException e) {
			e.printStackTrace();
		} catch (DummyRemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void afterCompletion(int status) {
		// These are not proxied but are handled during local commits
	}
}
