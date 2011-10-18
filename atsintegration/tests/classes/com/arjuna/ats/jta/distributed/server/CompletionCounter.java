package com.arjuna.ats.jta.distributed.server;

public interface CompletionCounter {
	public void incrementCommit();
	public void incrementRollback();
	int getCommitCount();
	int getRollbackCount();
	void resetCounters();
}
