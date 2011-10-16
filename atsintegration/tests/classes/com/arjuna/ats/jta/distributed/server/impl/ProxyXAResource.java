package com.arjuna.ats.jta.distributed.server.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.XAResourceWrapper;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.jta.distributed.server.DummyRemoteException;
import com.arjuna.ats.jta.distributed.server.LookupProvider;

public class ProxyXAResource implements XAResource, XAResourceWrapper {

	private int transactionTimeout;
	private Integer remoteServerName = -1;
	private File file;
	private Integer localServerName;
	private LookupProvider lookupProvider;
	private Xid xid;

	public ProxyXAResource(LookupProvider lookupProvider, Integer localServerName, Integer remoteServerName) {
		this.lookupProvider = lookupProvider;
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
	}

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

	@Override
	public void start(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_START   [" + xid + "]");
		this.xid = xid;
	}

	@Override
	public void end(Xid xid, int flags) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_END     [" + xid + "]");
		this.xid = null;
	}

	@Override
	public synchronized int prepare(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARE [" + xid + "]");

		try {
			File dir = new File(System.getProperty("user.dir") + "/tmp/ProxyXAResource/" + localServerName + "/");
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

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			int propagatePrepare = lookupProvider.lookup(remoteServerName).propagatePrepare(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARED");
			return propagatePrepare;
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}

	}

	@Override
	public synchronized void commit(Xid xid, boolean onePhase) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMIT  [" + xid + "]");

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			lookupProvider.lookup(remoteServerName).propagateCommit(xid, onePhase);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMITED");
		} catch (IllegalStateException e) {
			throw new XAException(XAException.XAER_INVAL);
		} catch (HeuristicMixedException e) {
			throw new XAException(XAException.XA_HEURMIX);
		} catch (HeuristicRollbackException e) {
			throw new XAException(XAException.XA_HEURRB);
		} catch (HeuristicCommitException e) {
			throw new XAException(XAException.XA_HEURCOM);
		} catch (SystemException e) {
			throw new XAException(XAException.XAER_PROTO);
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}

		if (file != null) {
			file.delete();
		}
	}

	@Override
	public synchronized void rollback(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACK[" + xid + "]");
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			lookupProvider.lookup(remoteServerName).propagateRollback(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_ROLLBACKED");
		} catch (IllegalStateException e) {
			throw new XAException(XAException.XAER_INVAL);
		} catch (HeuristicMixedException e) {
			throw new XAException(XAException.XA_HEURMIX);
		} catch (HeuristicCommitException e) {
			throw new XAException(XAException.XA_HEURCOM);
		} catch (HeuristicRollbackException e) {
			throw new XAException(XAException.XA_HEURRB);
		} catch (SystemException e) {
			throw new XAException(XAException.XAER_PROTO);
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}

		if (file != null) {
			file.delete();
		}
	}

	@Override
	public Xid[] recover(int flag) throws XAException {
		Xid[] recovered = null;
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			recovered = lookupProvider.lookup(remoteServerName).propagateRecover(localServerName, flag);
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}

		for (int i = 0; i < recovered.length; i++) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") recovered: " + recovered[i]);
		}

		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}

		if (recovered.length > 0) {
			return new Xid[] { xid };
		} else {
			return null;
		}
	}

	@Override
	public void forget(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGET  [" + xid + "]");
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			lookupProvider.lookup(remoteServerName).propagateForget(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGETED[" + xid + "]");
		} catch (DummyRemoteException ce) {
			throw new XAException(XAException.XA_RETRY);
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
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
	 * I don't think this is used by TM.
	 */
	@Override
	public XAResource getResource() {
		return null;
	}

	/**
	 * I don't think this is used by TM.
	 */
	@Override
	public String getProductName() {
		return null;
	}

	/**
	 * I don't think this is used by TM.
	 */
	@Override
	public String getProductVersion() {
		return null;
	}

	@Override
	public String getJndiName() {
		return "ProxyXAResource";
	}

	public Xid getXid() {
		return xid;
	}
}
