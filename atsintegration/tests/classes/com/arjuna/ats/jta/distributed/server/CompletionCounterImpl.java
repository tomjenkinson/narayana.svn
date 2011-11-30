package com.arjuna.ats.jta.distributed.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class CompletionCounterImpl implements CompletionCounter {

	private static CompletionCounter instance;

	private Map<String, Integer> commitCounter = new HashMap<String, Integer>();
	private Map<String, Integer> rollbackCounter = new HashMap<String, Integer>();

	public static CompletionCounter getCompletionCounter() {
		if (instance == null) {
			instance = new CompletionCounterImpl();
		}
		return instance;
	}

	@Override
	public void incrementCommit(String nodeName) {
		Integer integer = commitCounter.get(nodeName);
		if (integer == null) {
			integer = new Integer(1);
		} else {
			integer = new Integer(integer.intValue() + 1);
		}
		commitCounter.put(nodeName, integer);

	}

	@Override
	public void incrementRollback(String nodeName) {
		Integer integer = rollbackCounter.get(nodeName);
		if (integer == null) {
			integer = new Integer(1);
		} else {
			integer = new Integer(integer.intValue() + 1);
		}
		rollbackCounter.put(nodeName, integer);
	}

	@Override
	public int getCommitCount(String nodeName) {
		Integer integer = commitCounter.get(nodeName);
		if (integer == null) {
			integer = new Integer(0);
		}
		return integer;
	}

	@Override
	public int getRollbackCount(String nodeName) {
		Integer integer = rollbackCounter.get(nodeName);
		if (integer == null) {
			integer = new Integer(0);
		}
		return integer;
	}

	@Override
	public int getTotalCommitCount() {
		Integer toReturn = 0;
		Iterator<Integer> iterator = commitCounter.values().iterator();
		while (iterator.hasNext()) {
			toReturn += iterator.next();
		}
		return toReturn;
	}

	@Override
	public int getTotalRollbackCount() {
		Integer toReturn = 0;
		Iterator<Integer> iterator = rollbackCounter.values().iterator();
		while (iterator.hasNext()) {
			toReturn += iterator.next();
		}
		return toReturn;
	}

	@Override
	public void reset() {
		commitCounter.clear();
		rollbackCounter.clear();
	}
}
