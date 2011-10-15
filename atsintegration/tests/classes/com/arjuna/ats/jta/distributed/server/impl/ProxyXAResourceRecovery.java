package com.arjuna.ats.jta.distributed.server.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.xa.XAResource;

import org.jboss.tm.XAResourceRecovery;

import com.arjuna.ats.jta.distributed.server.LookupProvider;

public class ProxyXAResourceRecovery implements XAResourceRecovery {

	private List<ProxyXAResource> resources = new ArrayList<ProxyXAResource>();

	public ProxyXAResourceRecovery(LookupProvider lookupProvider, int id) throws IOException {
		File file = new File(System.getProperty("user.dir") + "/tmp/ProxyXAResource/" + id + "/");
		if (file.exists() && file.isDirectory()) {
			File[] listFiles = file.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File currentFile = listFiles[i];
				resources.add(new ProxyXAResource(lookupProvider, id, currentFile));
			}
		}
	}

	@Override
	public XAResource[] getXAResources() {
		return resources.toArray(new XAResource[] {});
	}

}
