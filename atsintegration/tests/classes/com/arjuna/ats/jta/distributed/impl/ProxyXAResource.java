package com.arjuna.ats.jta.distributed.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.XAResourceWrapper;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.jta.distributed.SimpleIsolatedServers;

public class ProxyXAResource implements XAResource, XAResourceWrapper {

	public static final ThreadLocal<List<Integer>> RECOVERY_SCAN_STARTED = new ThreadLocal<List<Integer>>();

	private int transactionTimeout;
	private Xid xid;
	private Integer remoteServerName = -1;
	private File file;
	private Integer localServerName;

	public ProxyXAResource(Integer localServerName, Integer remoteServerName) {
		this.localServerName = localServerName;
		this.remoteServerName = remoteServerName;
	}

	public ProxyXAResource(Integer localServerName, File file) throws IOException {
		this.localServerName = localServerName;
		this.file = file;
		DataInputStream fis = new DataInputStream(new FileInputStream(file));
		final int formatId = fis.readInt();
		final int gtrid_length = fis.readInt();
		final byte[] gtrid = new byte[gtrid_length];
		fis.read(gtrid, 0, gtrid_length);
		final int bqual_length = fis.readInt();
		final byte[] bqual = new byte[bqual_length];
		fis.read(bqual, 0, bqual_length);
		int remoteServerName = fis.readInt();
		this.remoteServerName = remoteServerName;
		this.xid = new Xid() {
			@Override
			public byte[] getGlobalTransactionId() {
				return gtrid;
			}

			@Override
			public int getFormatId() {
				return formatId;
			}

			@Override
			public byte[] getBranchQualifier() {
				return bqual;
			}

			@Override
			public boolean equals(Object object) {
				Xid xid = (Xid) object;
				if (xid == null)
					return false;

				if (xid == this)
					return true;
				else {

					if (xid.getFormatId() == formatId) {
						byte[] gtx = xid.getGlobalTransactionId();
						byte[] bql = xid.getBranchQualifier();
						final int bqlength = (bql == null ? 0 : bql.length);

						if ((gtrid.length == gtx.length) && (bqual.length == bqlength)) {
							int i;

							for (i = 0; i < gtrid.length; i++) {
								if (gtrid[i] != gtx[i])
									return false;
							}

							for (i = 0; i < bqual.length; i++) {
								if (bqual[i] != bql[i])
									return false;
							}

							return true;
						}
					}
				}

				return false;
			}
		};
	}

	public Xid getXid() {
		return xid;
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

			final int formatId = xid.getFormatId();
			final byte[] gtrid = xid.getGlobalTransactionId();
			final int gtrid_length = gtrid.length;
			final byte[] bqual = xid.getBranchQualifier();
			final int bqual_length = bqual.length;

			DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
			fos.writeInt(formatId);
			fos.writeInt(gtrid_length);
			fos.write(gtrid, 0, gtrid_length);
			fos.writeInt(bqual_length);
			fos.write(bqual, 0, bqual_length);
			fos.writeInt(remoteServerName);
		} catch (IOException e) {
			e.printStackTrace();
			throw new XAException(XAException.XAER_RMERR);
		}

		try {
			int propagatePrepare = SimpleIsolatedServers.lookup(remoteServerName).propagatePrepare(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_PREPARED");
			return propagatePrepare;
		} catch (ConnectException ce) {
			throw new XAException(XAException.XA_RETRY);
		}
	}

	@Override
	public synchronized void commit(Xid xid, boolean onePhase) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_COMMIT  [" + xid + "]");

		try {
			SimpleIsolatedServers.lookup(remoteServerName).propagateCommit(xid, onePhase);
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
		} catch (ConnectException ce) {
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
			SimpleIsolatedServers.lookup(remoteServerName).propagateRollback(xid);
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
		} catch (ConnectException ce) {
			throw new XAException(XAException.XA_RETRY);
		}

		if (file != null) {
			file.delete();
		}
	}

	@Override
	public Xid[] recover(int flag) throws XAException {
		List<Integer> startScanned = RECOVERY_SCAN_STARTED.get();
		if (startScanned == null) {
			startScanned = new ArrayList<Integer>();
			RECOVERY_SCAN_STARTED.set(startScanned);
		}

		int tocheck = (flag & XAResource.TMSTARTRSCAN);
		if (tocheck == XAResource.TMSTARTRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);

			if (!startScanned.contains(remoteServerName)) {
				startScanned.add(remoteServerName);

				// Make sure that the remote server has recovered all
				// transactions
				try {
					SimpleIsolatedServers.lookup(remoteServerName).propagateRecover(startScanned, flag);
				} catch (ConnectException ce) {
					throw new XAException(XAException.XA_RETRY);
				} finally {
					startScanned.remove((Integer) remoteServerName);
				}
			}

			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMSTARTRSCAN]: "
					+ remoteServerName);
		}
		tocheck = (flag & XAResource.TMENDRSCAN);
		if (tocheck == XAResource.TMENDRSCAN) {
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVER [XAResource.TMENDRSCAN]: "
					+ remoteServerName);

			if (!startScanned.contains(remoteServerName)) {
				try {
					SimpleIsolatedServers.lookup(remoteServerName).propagateRecover(startScanned, flag);
				} catch (ConnectException ce) {
					throw new XAException(XAException.XA_RETRY);
				} finally {
					startScanned.remove((Integer) remoteServerName);
				}
			}

			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_RECOVERD[XAResource.TMENDRSCAN]: "
					+ remoteServerName);
		}

		return new Xid[] { xid };
	}

	@Override
	public void forget(Xid xid) throws XAException {
		System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGET  [" + xid + "]");
		try {
			SimpleIsolatedServers.lookup(remoteServerName).propagateForget(xid);
			System.out.println("     ProxyXAResource (" + localServerName + ":" + remoteServerName + ") XA_FORGETED[" + xid + "]");
		} catch (ConnectException ce) {
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
		return xares.equals(this);
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
}
