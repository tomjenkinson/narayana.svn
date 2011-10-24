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

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import com.arjuna.jta.distributed.example.server.LookupProvider;

/**
 * Proxy the before completion call to the remote servers. Unusual for a
 * synchronization it must be created with a reference to an Xid in order to be
 * able to propate this information to a remote server.
 */
public class ProxySynchronization implements Synchronization {

	private String localServerName;
	private String remoteServerName;
	private Xid toRegisterAgainst;
	private LookupProvider lookupProvider;

	public ProxySynchronization(LookupProvider lookupProvider, String localServerName, String remoteServerName, Xid toRegisterAgainst) {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
		this.toRegisterAgainst = toRegisterAgainst;
	}

	@Override
	public void beforeCompletion() {
		System.out.println("ProxySynchronization (" + localServerName + ":" + remoteServerName + ") beforeCompletion");
		try {
			lookupProvider.lookup(remoteServerName).beforeCompletion(toRegisterAgainst);
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
