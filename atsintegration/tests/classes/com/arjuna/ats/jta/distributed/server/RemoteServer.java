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
package com.arjuna.ats.jta.distributed.server;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

public interface RemoteServer {

	public void setOffline(boolean offline);

	public int propagatePrepare(Xid xid) throws XAException, DummyRemoteException;

	public void propagateCommit(Xid xid, boolean onePhase) throws XAException, DummyRemoteException;

	public void propagateRollback(Xid xid) throws XAException, DummyRemoteException;

	public Xid[] propagateRecover(Integer parentNodeName) throws XAException, DummyRemoteException;

	public void propagateForget(Xid xid) throws XAException, DummyRemoteException;

	public void propagateBeforeCompletion(Xid xid) throws XAException, SystemException, DummyRemoteException;

}
