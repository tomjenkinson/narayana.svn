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
package com.arjuna.ats.jta.distributed.server.impl;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.XAResourceWrapper;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.jta.distributed.server.CompletionCounter;
import com.arjuna.ats.jta.distributed.server.DummyRemoteException;
import com.arjuna.ats.jta.distributed.server.LookupProvider;

/**
 * I chose for this class to implement XAResourceWrapper so that I can provide a
 * name to the Transaction manager for it to store in its XID.
 */
public class ProxyXAResource implements XAResource, XAResourceWrapper {

	private int transactionTimeout;
	private Integer remoteServerName = -1;
	private Map<Xid, File> map;
	private Integer localServerName;
	private LookupProvider lookupProvider;
	private CompletionCounter completionCounter;
	private File file;

	/**
	 * Create a new proxy to the remote server.
	 * 
	 * @param lookupProvider
	 * @param localServerName
	 * @param remoteServerName
	 */
	public ProxyXAResource(CompletionCounter completionCounter, LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName, File file) {
		this.completionCounter = completionCounter;
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
	public ProxyXAResource(CompletionCounter completionCounter, LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName,
			Map<Xid, File> map) throws IOException {
		this.completionCounter = completionCounter;
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
		this.map = map;
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
			File dir = new File(System.getProperty("user.dir") + "/distributedjta/ProxyXAResource/" + localServerName + "/");
			dir.mkdirs();
			File file = new File(dir, new Uid().fileStringForm());
			file.createNewFile();
			DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
			fos.writeInt(remoteServerName);
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

		try {
			int propagatePrepare = lookupProvider.lookup(remoteServerName).propagatePrepare(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARED");
			return propagatePrepare;
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		}
	}

	@Override
	public synchronized void commit(Xid xid, boolean onePhase) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMIT  [" + xid + "]");

		try {
			lookupProvider.lookup(remoteServerName).propagateCommit(xid, onePhase);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMITED");
		} catch (DummyRemoteException ce) {
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
		if (completionCounter != null) {
			completionCounter.incrementCommit();
		}
	}

	@Override
	public synchronized void rollback(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACK[" + xid + "]");

		try {
			lookupProvider.lookup(remoteServerName).propagateRollback(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACKED");
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} catch (XAException e) {
			// We know the remote side must have done a JBTM-917
			if (e.errorCode == XAException.XAER_INVAL) {
				// We know that this means that the transaction is not known at
				// the remote side
				e.printStackTrace();
			}
		}

		if (map.get(xid) != null) {
			map.get(xid).delete();
			map.remove(xid);
		}

		// THIS CAN ONLY HAPPEN IN 1PC OR ROLLBACK
		if (file != null) {
			file.delete();
		}
		if (completionCounter != null) {
			completionCounter.incrementRollback();
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

		List<Xid> toReturn = new ArrayList<Xid>();
		Xid[] recovered = null;
		try {
			recovered = lookupProvider.lookup(remoteServerName).propagateRecover(localServerName);
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		}

		List<Xid> arrayList = new ArrayList<Xid>();
		arrayList.addAll(map.keySet());
		if (recovered != null) {
			for (int i = 0; i < recovered.length; i++) {
				System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") recovered: " + recovered[i]);
				Iterator<Xid> iterator = map.keySet().iterator();
				while (iterator.hasNext()) {
					Xid next = iterator.next();
					if (Arrays.equals(next.getGlobalTransactionId(), recovered[i].getGlobalTransactionId())) {
						toReturn.add(next);
					} else if (!iterator.hasNext()) {
						toReturn.add(recovered[i]);
					}
					arrayList.remove(next);
				}
				System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") added: " + toReturn.get(toReturn.size() - 1));
			}
		}

		// We now know the remote server didn't know about these Xids
		List<Xid> knownNoneKnownXids = new ArrayList<Xid>();
		knownNoneKnownXids.addAll(arrayList);
		Iterator<Xid> iterator = knownNoneKnownXids.iterator();
		while (iterator.hasNext()) {
			Xid next = iterator.next();
			map.remove(next).delete();
		}
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}
		return toReturn.toArray(new Xid[0]);
	}

	@Override
	public void forget(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGET  [" + xid + "]");
		try {
			lookupProvider.lookup(remoteServerName).propagateForget(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGETED[" + xid + "]");
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		}
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

	/**
	 * Not used by the TM.
	 */
	@Override
	public XAResource getResource() {
		return null;
	}

	/**
	 * Not used by the TM.
	 */
	@Override
	public String getProductName() {
		return null;
	}

	/**
	 * Not used by the TM.
	 */
	@Override
	public String getProductVersion() {
		return null;
	}

	/**
	 * This allows the proxy to contain meaningful information in the XID in
	 * case of failure to recover.
	 */
	@Override
	public String getJndiName() {
		return "ProxyXAResource: " + localServerName + " " + remoteServerName;
	}
}
