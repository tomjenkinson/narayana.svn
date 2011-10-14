package com.arjuna.ats.jta.distributed;

import javax.transaction.Synchronization;

public class TestSynchronization implements Synchronization {
	private int serverId;

	public TestSynchronization(int serverId) {
		this.serverId = serverId;
	}

	@Override
	public void beforeCompletion() {
		System.out.println(" TestSynchronization (" + serverId + ")      beforeCompletion");
	}

	@Override
	public void afterCompletion(int status) {
		System.out.println(" TestSynchronization (" + serverId + ")      afterCompletion");
	}
}
