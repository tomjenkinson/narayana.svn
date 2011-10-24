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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.jta.distributed.example.server.LookupProvider;

/**
 * The XA resource that the transport must provide inorder to proxy directives
 * from the root transaction coordinator.
 * 
 * One of the key features of the pattern is the Proxy. It will adapt the XID of
 * the subordinate (which the local transaction manager does not know about) to
 * an XID that is allocated by the local transaction manager for this spoof XA
 * resource.
 * 
 * Persistence points required by the transport: ProxyXAResource: 1. Before a
 * transaction is propagated the Xid must be recorded 2. When a transaction is
 * successfully prepared the previously recorded Xid must be deleted and the
 * real Xid recorded – otherwise the transaction could only be aborted with a
 * heuristic The transport must register a *single* proxy XA resource per
 * subordinate transaction – thereby eliminating diamonds in the users
 * transaction flow. In my example I do this by registering the proxy xa
 * resource after I have spoken to the remote server thereby allowing me to use
 * knowledge from the remote server to determine whether or not a proxy is
 * required. Do notice how the proxy xa resource is created before the
 * transaction is flowed though, and the XID of the transaction is persisted to
 * assist with recovery. Once the proxy xa resource is prepared, the file is
 * then replaced and persisted again with the XID of the actual proxy xa
 * resource. The first persist was just so that when recovering this proxy xa
 * resource can be recovered so that we know we talked to a remote server for
 * this transaction (and the remote server may have prepared the transaction,
 * even if the parent failed before it was able to).
 * 
 * Recovery The proxy xa resource is responsible for recording which
 * transactions are known of at the remote server and can therefore be recovered
 * by it without requiring the proxy invoke the remote server to ascertain this
 * list. When a transaction is propagated to a server the transport is
 * responsible for detecting that the server has not participated in the
 * transaction yet and if so it must assign it the next available subordinate
 * name and persist this information to help with recovery (see ServerImpl.java
 * and the test itself for how to determine the next available subordinate name
 * and a potential method of persisting this data). This is important when a
 * proxy xa resource is involved in recovery and invokes commit or rollback as
 * the transaction must be reloaded by the remote server before the
 * commit/rollback – if it was prepared - before we attempt to complete the
 * transaction.
 */
public class ProxyXAResource implements XAResource {

	private int transactionTimeout;
	private String remoteServerName = null;
	private Map<Xid, File> map;
	private String localServerName;
	private LookupProvider lookupProvider;
	private File file;
	private boolean recover;

	/**
	 * Create a new proxy to the remote server.
	 * 
	 * @param lookupProvider
	 * @param localServerName
	 * @param remoteServerName
	 */
	public ProxyXAResource(LookupProvider lookupProvider, String localServerName, String remoteServerName, File file) {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
		this.file = file;
		map = new HashMap<Xid, File>();
	}

	/**
	 * Used by recovery
	 * 
	 * @param lookupProvider
	 * @param localServerName
	 * @param map
	 * @param remoteServerName
	 * @param file
	 * @throws IOException
	 */
	public ProxyXAResource(LookupProvider lookupProvider, String localServerName, String remoteServerName, Map<Xid, File> map) throws IOException {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
		this.map = map;
		this.recover = true;
	}

	public void deleteTemporaryFile() {
		this.file.delete();
	}

	/**
	 * Store the XID.
	 */
	@Override
	public void start(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_START   [" + xid + "]");
	}

	/**
	 * Reference the XID.
	 */
	@Override
	public void end(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_END     [" + xid + "]");
	}

	/**
	 * Prepare the resource, save the XID locally first, the propagate the
	 * prepare. This ensures that in recovery we know the XID to ask a remote
	 * server about.
	 */
	@Override
	public synchronized int prepare(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARE [" + xid + "]");

		// Persist a proxy for the remote server this can mean we try to recover
		// a transaction at a remote server that did not get chance to
		// prepare but the alternative is to orphan a prepared server

		try {
			File dir = new File(System.getProperty("user.dir") + "/distributedjta-example/ProxyXAResource/" + localServerName + "/");
			dir.mkdirs();
			File file = new File(dir, new Uid().fileStringForm());
			file.createNewFile();
			DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));

			fos.writeInt(remoteServerName.length());
			fos.writeBytes(remoteServerName);
			fos.writeInt(xid.getFormatId());
			fos.writeInt(xid.getGlobalTransactionId().length);
			fos.write(xid.getGlobalTransactionId());
			fos.writeInt(xid.getBranchQualifier().length);
			fos.write(xid.getBranchQualifier());

			if (map.containsKey(xid)) {
				System.out.println(map.get(xid));
				map.remove(xid).delete();
			}
			if (this.file != null) {
				this.file.delete();
			}

			map.put(xid, file);
		} catch (IOException e) {
			e.printStackTrace();
			throw new XAException(XAException.XAER_RMERR);
		}

		int propagatePrepare = lookupProvider.lookup(remoteServerName).prepare(xid);
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARED");
		return propagatePrepare;
	}

	@Override
	public synchronized void commit(Xid xid, boolean onePhase) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMIT  [" + xid + "]");

		try {
			lookupProvider.lookup(remoteServerName).commit(xid, onePhase, recover);
		} catch (IOException e) {
			e.printStackTrace();
			throw new XAException(XAException.XA_RETRY);
		}

		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMITED");

		if (map.get(xid) != null) {
			map.get(xid).delete();
			map.remove(xid);
		}

		// THIS CAN ONLY HAPPEN IN 1PC OR ROLLBACK
		if (file != null) {
			file.delete();
		}
	}

	@Override
	public synchronized void rollback(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACK[" + xid + "]");

		try {
			lookupProvider.lookup(remoteServerName).rollback(xid, recover);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACKED");
		} catch (XAException e) {
			// We know the remote side must have done a JBTM-917
			if (e.errorCode == XAException.XAER_INVAL) {
				// We know that this means that the transaction is not known at
				// the remote side
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new XAException(XAException.XA_RETRY);
		}

		if (map.get(xid) != null) {
			map.get(xid).delete();
			map.remove(xid);
		}

		// THIS CAN ONLY HAPPEN IN 1PC OR ROLLBACK
		if (file != null) {
			file.delete();
		}
	}

	/**
	 * This will ensure that the remote server has loaded the subordinate
	 * transaction.
	 * 
	 * @return It returns the proxies view of the XID state, returning the
	 *         remote servers view of the XID would present an XID to the local
	 *         server that it knows nothing about and indeed potentially the
	 *         remote server does not have a corresponding record of the XID in
	 *         case of failure during prepare.
	 */
	@Override
	public Xid[] recover(int flag) throws XAException {
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}

		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}
		return map.keySet().toArray(new Xid[0]);
	}

	@Override
	public void forget(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGET  [" + xid + "]");
		lookupProvider.lookup(remoteServerName).forget(xid);
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGETED[" + xid + "]");
	}

	@Override
	public int getTransactionTimeout() throws XAException {
		return transactionTimeout;
	}

	@Override
	public boolean setTransactionTimeout(int seconds) throws XAException {
		this.transactionTimeout = seconds;
		return true;
	}

	@Override
	public boolean isSameRM(XAResource xares) throws XAException {
		boolean toReturn = false;
		if (xares instanceof ProxyXAResource) {
			if (((ProxyXAResource) xares).remoteServerName == remoteServerName) {
				toReturn = true;
			}
		}
		return toReturn;
	}
}