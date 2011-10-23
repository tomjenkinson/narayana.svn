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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.tm.XAResourceRecovery;

import com.arjuna.ats.jta.xa.XidImple;
import com.arjuna.jta.distributed.example.server.LookupProvider;

public class ProxyXAResourceRecovery implements XAResourceRecovery {

	private List<ProxyXAResource> resources = new ArrayList<ProxyXAResource>();

	public ProxyXAResourceRecovery(LookupProvider lookupProvider, Integer id) throws IOException {
		File directory = new File(System.getProperty("user.dir") + "/distributedjta-example/ProxyXAResource/" + id + "/");
		Map<String, Map<Xid, File>> savedData = new HashMap<String, Map<Xid, File>>();
		if (directory.exists() && directory.isDirectory()) {
			File[] listFiles = directory.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File file = listFiles[i];
				DataInputStream fis = new DataInputStream(new FileInputStream(file));
				int read = fis.readInt();
				byte[] nameB = new byte[read];
				fis.read(nameB, 0, read);
				String remoteServerName = new String(nameB);

				Map<Xid, File> map = savedData.get(remoteServerName);
				if (map == null) {
					map = new HashMap<Xid, File>();
					savedData.put(remoteServerName, map);
				}
				final int formatId = fis.readInt();
				int gtrid_length = fis.readInt();
				final byte[] gtrid = new byte[gtrid_length];
				fis.read(gtrid, 0, gtrid_length);

				int bqual_length = fis.readInt();
				final byte[] bqual = new byte[bqual_length];
				fis.read(bqual, 0, bqual_length);
				Xid xid = new XidImple(new Xid() {
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
				});
				map.put(xid, file);
			}
		}
		Iterator<String> iterator = savedData.keySet().iterator();
		while (iterator.hasNext()) {
			String remoteServerName = iterator.next();
			Map<Xid, File> map = savedData.get(remoteServerName);
			resources.add(new ProxyXAResource(lookupProvider, id, remoteServerName, map));
		}
	}

	@Override
	public XAResource[] getXAResources() {
		return resources.toArray(new XAResource[] {});
	}

}
