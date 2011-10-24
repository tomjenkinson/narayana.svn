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

import java.io.IOException;

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
	 * Relay the propagate completion.
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws SystemException
	 * @throws DummyRemoteException
	 */
	public void beforeCompletion(Xid xid) throws XAException, SystemException;

	/**
	 * Relay a prepare to the remote side for a specific Xid.
	 * 
	 * @param xid
	 * @return
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public int prepare(Xid xid) throws XAException;

	/**
	 * Relay the commit.
	 * 
	 * If this call is coming from a recover scan on a ProxyXAResource, then
	 * pass the recover flag in so the remote server knows it needs to recover
	 * the transaction.
	 * 
	 * @param xid
	 * @param onePhase
	 * @throws XAException
	 * @throws IOException 
	 * @throws DummyRemoteException
	 */
	public void commit(Xid xid, boolean onePhase, boolean recover) throws XAException, IOException;

	/**
	 * Relay the rollback.
	 * 
	 * If this call is coming from a recover scan on a ProxyXAResource, then
	 * pass the recover flag in so the remote server knows it needs to recover
	 * the transaction.
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws IOException 
	 * @throws DummyRemoteException
	 */
	public void rollback(Xid xid, boolean recover) throws XAException, IOException;

	/**
	 * Relay the forget.
	 * 
	 * @param xid
	 * @throws XAException
	 * @throws DummyRemoteException
	 */
	public void forget(Xid xid) throws XAException;

}
