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
package com.arjuna.jta.distributed.example.server;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/**
 * This interface is to simulate most remote calls to a server (except where
 * classloader separation is used in
 * <class>ExampelDistributedJTATestCase</class>
 * 
 * Most of the calls are fairly innocuous, however two need special explanation.
 * 
 * Firstly the before completion takes an XID, check out
 * <class>ProxySynchronization</class> for more details on that.
 * 
 * More interesting is the propagate recover call - see it's Javadoc for
 * details.
 */
public interface RemoteServer {

	/**
	 * Atypical for a recover call we need to pass over the node name of the
	 * caller. This will ensure that all Xids for the caller coordinated
	 * Subordinates are returned. Also this method should pass true to the
	 * XATerminator::recover method so that it will recover the inflight
	 * transactions for this node
	 * 
	 * @param callingServerNodeName
	 * @return
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public Xid[] recoverFor(Integer callingServerNodeName) throws XAException, DummyRemoteException;

	/**
	 * Relay the propagate completion.
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws SystemException
	 * @throws DummyRemoteException
	 */
	public void beforeCompletion(Xid xid) throws XAException, SystemException, DummyRemoteException;

	/**
	 * Relay a prepare to the remote side for a specific Xid.
	 * 
	 * @param xid
	 * @return
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public int prepare(Xid xid) throws XAException, DummyRemoteException;

	/**
	 * Relay the commit.
	 * 
	 * @param xid
	 * @param onePhase
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public void commit(Xid xid, boolean onePhase) throws XAException, DummyRemoteException;

	/**
	 * Relay the rollback
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public void rollback(Xid xid) throws XAException, DummyRemoteException;

	/**
	 * Relay the forget.
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public void forget(Xid xid) throws XAException, DummyRemoteException;

}
