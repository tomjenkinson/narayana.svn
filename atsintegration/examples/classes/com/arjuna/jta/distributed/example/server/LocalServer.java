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

import java.io.File;
import java.io.IOException;

import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;

public interface LocalServer {

	public void initialise(LookupProvider lookupProvider, Integer nodeName) throws CoreEnvironmentBeanException, IOException, SecurityException,
			NoSuchFieldException, IllegalArgumentException, IllegalAccessException;

	public Integer getNodeName();

	public TransactionManager getTransactionManager() throws NotSupportedException, SystemException;

	public void storeRootTransaction() throws SystemException;

	public void removeRootTransaction(Xid toMigrate);

	public boolean getAndResumeTransaction(int remainingTimeout, Xid toImport) throws XAException, InvalidTransactionException, IllegalStateException,
			SystemException;

	public RemoteServer connectTo();

	public XAResource generateProxyXAResource(LookupProvider lookupProvider, Integer remoteServerName) throws IOException, SystemException;

	public void cleanupProxyXAResource(XAResource proxyXAResource);

	public Synchronization generateProxySynchronization(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName, Xid toRegisterAgainst);

	public Xid getCurrentXid() throws SystemException;
}
