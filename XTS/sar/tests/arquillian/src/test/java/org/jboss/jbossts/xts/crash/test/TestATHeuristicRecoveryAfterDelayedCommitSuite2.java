package org.jboss.jbossts.xts.crash.test;

import org.jboss.jbossts.xts.crash.test.at.MultiServicePrepareAndCommit;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	MultiServicePrepareAndCommit.class,
	WatchRecovery.class,
	RenameTestLog.class
})
public class TestATHeuristicRecoveryAfterDelayedCommitSuite2 extends
		BaseCrashTest {
	@BeforeClass
	public static void setUp() throws Exception {
		deleteTestLog();
		copyBytemanScript("ATHeuristicRecoveryAfterDelayedCommit.txt");
		RenameTestLog.scriptName = "ATHeuristicRecoveryAfterDelayedCommit";
		WatchRecovery.wait_time = 8;
	}
}
