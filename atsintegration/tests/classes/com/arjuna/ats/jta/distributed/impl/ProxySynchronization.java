package com.arjuna.ats.jta.distributed.impl;

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import com.arjuna.ats.jta.distributed.SimpleIsolatedServers;

public class ProxySynchronization implements Synchronization {

	private int serverId;
	private int serverIdToProxyTo;
	private Xid toRegisterAgainst;

	public ProxySynchronization(int serverId, int serverIdToProxyTo, Xid toRegisterAgainst) {
		this.serverId = serverId;
		this.serverIdToProxyTo = serverIdToProxyTo;
		this.toRegisterAgainst = toRegisterAgainst;
	}

	@Override
	public void beforeCompletion() {
		System.out.println("ProxySynchronization (" + serverId + ":" + serverIdToProxyTo + ") beforeCompletion");
		int index = (serverIdToProxyTo / 1000) - 1;
		try {
			SimpleIsolatedServers.getServers()[index].propagateBeforeCompletion(toRegisterAgainst);
		} catch (XAException e) {
			e.printStackTrace();
		} catch (SystemException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void afterCompletion(int status) {
		// These are not proxied but are handled during local commits
	}
}
