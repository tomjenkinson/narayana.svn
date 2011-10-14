package com.arjuna.ats.jta.distributed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.xa.XAResource;

import org.jboss.tm.XAResourceRecovery;

public class TestResourceRecovery implements XAResourceRecovery {

	private List<TestResource> resources = new ArrayList<TestResource>();

	public TestResourceRecovery(int serverId) throws IOException {
		File file = new File(System.getProperty("user.dir") + "/tmp/TestResource/" + serverId + "/");
		if (file.exists() && file.isDirectory()) {
			File[] listFiles = file.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File currentFile = listFiles[i];
				if (currentFile.getAbsolutePath().endsWith("_")) {
					resources.add(new TestResource(serverId, currentFile));
				}
			}
		}
	}

	@Override
	public XAResource[] getXAResources() {
		return resources.toArray(new XAResource[] {});
	}

}
