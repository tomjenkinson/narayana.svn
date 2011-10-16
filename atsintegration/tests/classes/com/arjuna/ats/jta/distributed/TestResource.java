/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors 
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors. 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */

package com.arjuna.ats.jta.distributed;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.common.Uid;

public class TestResource implements XAResource {
	private Xid xid;

	protected int timeout = 0;

	private boolean readonly = false;

	private File file;

	private int serverId;

	public TestResource(int serverId, boolean readonly) {
		this.serverId = serverId;
		this.readonly = readonly;
	}

	public TestResource(int serverId, File file) throws IOException {
		this.serverId = serverId;
		this.file = file;
		DataInputStream fis = new DataInputStream(new FileInputStream(file));
		final int formatId = fis.readInt();
		final int gtrid_length = fis.readInt();
		final byte[] gtrid = new byte[gtrid_length];
		fis.read(gtrid, 0, gtrid_length);
		final int bqual_length = fis.readInt();
		final byte[] bqual = new byte[bqual_length];
		fis.read(bqual, 0, bqual_length);
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
		};
	}

	public synchronized int prepare(Xid xid) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_PREPARE [" + xid + "]");

		if (readonly)
			return XA_RDONLY;
		else {
			File dir = new File(System.getProperty("user.dir") + "/tmp/TestResource/" + serverId + "/");
			dir.mkdirs();
			file = new File(dir, new Uid().fileStringForm() + "_");
			try {
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
			} catch (IOException e) {
				e.printStackTrace();
				throw new XAException(XAException.XAER_RMERR);
			}
			return XA_OK;
		}

		// throw new XAException();
	}

	public synchronized void commit(Xid id, boolean onePhase) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_COMMIT  [" + id + "]");
		// String absoluteFile = file.getAbsolutePath();
		// String newName = absoluteFile.substring(0, absoluteFile.length() -
		// 1);
		// File file2 = new File(newName);
		// file.renameTo(file2);
		if (file != null) {
			file.delete();
		}
		this.xid = null;
	}

	public synchronized void rollback(Xid xid) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_ROLLBACK[" + xid + "]");
		if (file != null) {
			file.delete();
		}
		this.xid = null;
	}

	public void end(Xid xid, int flags) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_END     [" + xid + "] Flags=" + flags);
	}

	public void forget(Xid xid) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_FORGET[" + xid + "]");
	}

	public int getTransactionTimeout() throws XAException {
		return (timeout);
	}

	public boolean isSameRM(XAResource xares) throws XAException {
		if (xares instanceof TestResource) {
			TestResource other = (TestResource) xares;
			if ((this.xid != null && other.xid != null)) {
				if (this.xid.getFormatId() == other.xid.getFormatId()) {
					if (Arrays.equals(this.xid.getGlobalTransactionId(), other.xid.getGlobalTransactionId())) {
						if (Arrays.equals(this.xid.getBranchQualifier(), other.xid.getBranchQualifier())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public Xid[] recover(int flag) throws XAException {
		if ((flag & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
			System.out.println("        TestResource (" + serverId + ")      RECOVER[XAResource.TMSTARTRSCAN]: " + serverId);
		}
		if ((flag & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN) {
			System.out.println("        TestResource (" + serverId + ")      RECOVER[XAResource.TMENDRSCAN]: " + serverId);
		}
		if (flag == XAResource.TMNOFLAGS) {
			System.out.println("        TestResource (" + serverId + ")      RECOVER[XAResource.TMENDRSCAN]: " + serverId);
		}
		if (xid == null) {
			return null;
		} else {
			return new Xid[] { xid };
		}
	}

	public boolean setTransactionTimeout(int seconds) throws XAException {
		timeout = seconds;
		return (true);
	}

	public void start(Xid xid, int flags) throws XAException {
		System.out.println("        TestResource (" + serverId + ")      XA_START   [" + xid + "] Flags=" + flags);
	}
}
