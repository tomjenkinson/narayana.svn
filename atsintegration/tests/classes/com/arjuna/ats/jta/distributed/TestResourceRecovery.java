package com.arjuna.ats.jta.distributed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.xa.XAResource;

import org.jboss.tm.XAResourceRecovery;

import com.arjuna.ats.jta.distributed.server.CompletionCounter;

public class TestResourceRecovery implements XAResourceRecovery {

	private List<TestResource> resources = new ArrayList<TestResource>();

	public TestResourceRecovery(CompletionCounter counter, Integer nodeName) throws IOException {
		File file = new File(System.getProperty("user.dir") + "/tmp/TestResource/" + nodeName + "/");
		if (file.exists() && file.isDirectory()) {
			File[] listFiles = file.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File currentFile = listFiles[i];
				if (currentFile.getAbsolutePath().endsWith("_")) {
					resources.add(new TestResource(counter, nodeName, currentFile));
				}
			}
		}
	}

	@Override
	public XAResource[] getXAResources() {
		return resources.toArray(new XAResource[] {});
	}

}
