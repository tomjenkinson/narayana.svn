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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.XAResourceWrapper;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.jta.distributed.example.server.DummyRemoteException;
import com.arjuna.jta.distributed.example.server.LookupProvider;

/**
 * I chose for this class to implement XAResourceWrapper so that I can provide a
 * name to the Transaction manager for it to store in its XID.
 */
public class ProxyXAResource implements XAResource, XAResourceWrapper {

	private int transactionTimeout;
	private Integer remoteServerName = -1;
	private File file;
	private Integer localServerName;
	private LookupProvider lookupProvider;
	private Xid xid;

	/**
	 * Create a new proxy to the remote server.
	 * 
	 * @param lookupProvider
	 * @param localServerName
	 * @param remoteServerName
	 */
	public ProxyXAResource(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName) {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
	}

	/**
	 * Used by recovery
	 * 
	 * @param lookupProvider
	 * @param localServerName
	 * @param file
	 * @throws IOException
	 */
	public ProxyXAResource(LookupProvider lookupProvider, Integer localServerName, File file) throws IOException {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.file = file;
		DataInputStream fis = new DataInputStream(new FileInputStream(file));
		this.remoteServerName = fis.readInt();
		final int formatId = fis.readInt();
		int gtrid_length = fis.readInt();
		final byte[] gtrid = new byte[gtrid_length];
		fis.read(gtrid, 0, gtrid_length);
		int bqual_length = fis.readInt();
		final byte[] bqual = new byte[bqual_length];
		fis.read(bqual, 0, bqual_length);
		this.xid = new Xid() {
			@Override
			public byte[] getBranchQualifier() {
				return bqual;
			}

			@Override
			public int getFormatId() {
				return formatId;
			}

			@Override
			public byte[] getGlobalTransactionId() {
				return gtrid;
			}
		};
	}

	/**
	 * Store the XID.
	 */
	@Override
	public void start(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_START   [" + xid + "]");
		this.xid = xid;
	}

	/**
	 * Reference the XID.
	 */
	@Override
	public void end(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_END     [" + xid + "]");
		this.xid = null;
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
			file = new File(dir, new Uid().fileStringForm());
			file.createNewFile();
			DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
			fos.writeInt(remoteServerName);
			fos.writeInt(xid.getFormatId());
			fos.writeInt(xid.getGlobalTransactionId().length);
			fos.write(xid.getGlobalTransactionId());
			fos.writeInt(xid.getBranchQualifier().length);
			fos.write(xid.getBranchQualifier());
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

	/**
	 * The remote side will not accept a one phase optimization.
	 */
	@Override
	public synchronized void commit(Xid xid, boolean onePhase) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMIT  [" + xid + "]");

		try {
			lookupProvider.lookup(remoteServerName).propagateCommit(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMITED");
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		}

		if (file != null) {
			file.delete();
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
		Xid[] recovered = null;
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}

		if (this.xid != null) {
			try {
				recovered = lookupProvider.lookup(remoteServerName).propagateRecover(xid.getFormatId(), xid.getGlobalTransactionId(), flag);
			} catch (DummyRemoteException ce) {
				throw new XAException(XAException.XA_RETRY);
			}
		}

		for (int i = 0; i < recovered.length; i++) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") recovered: " + recovered[i]);
		}

		Xid[] toReturn = null;
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
			toReturn = new Xid[] { xid };
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}
		return toReturn;
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

	public Xid getXid() {
		return xid;
	}
}
