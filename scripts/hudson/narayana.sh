if [ -z "${WORKSPACE}" ]; then
  echo "UNSET WORKSPACE"
  exit -1;
fi

# FOR DEBUGGING SUBSEQUENT ISSUES
free -m

#Make sure no JBoss processes running
for i in `ps -eaf | grep java | grep "standalone*.xml" | grep -v grep | cut -c10-15`; do kill $i; done

#BUILD JBOSSTS
ant -Demma.enabled=false -Dpublican=false jbossall
if [ "$?" != "0" ]; then
	exit -1
fi

#BUILD JBOSS-AS
cd ${WORKSPACE}
rm -rf jboss-as
git clone git://github.com/jbosstm/jboss-as.git
if [ "$?" != "0" ]; then
	exit -1
fi

cd jboss-as
git checkout -t origin/4_16_BRANCH
if [ "$?" != "0" ]; then
	exit -1
fi

git remote add upstream git://github.com/jbossas/jboss-as.git
git pull --rebase --ff-only upstream master
if [ "$?" != "0" ]; then
	exit -1
fi

MAVEN_OPTS=-XX:MaxPermSize=256m ./build.sh clean install -DskipTests
if [ "$?" != "0" ]; then
	exit -1
fi

#START JBOSS
JBOSS_VERSION=`ls -1 ${WORKSPACE}/jboss-as/build/target | grep jboss-as`
export JBOSS_HOME=${WORKSPACE}/jboss-as/build/target/${JBOSS_VERSION}

cp ${JBOSS_HOME}/docs/examples/configs/standalone-xts.xml ${JBOSS_HOME}/standalone/configuration
$JBOSS_HOME/bin/standalone.sh --server-config=standalone-xts.xml&
sleep 10 

#RUN XTS UNIT TESTS
cd ${WORKSPACE}
cd XTS
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

ant -Dpublican=false -Dtesttype=tests-11 -Dsartype=sar-11 install
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

cp xts-install/tests/*ear $JBOSS_HOME/standalone/deployments/
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi
sleep 10 

export MYTESTIP_1=localhost
cd xts-install/tests
ant -f run-tests.xml tests-11
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

# Check output of Tests
ERRORS=$(cat reports/TEST-* | grep "<testsuite" | grep -v errors=\"0\")
FAILURES=$(cat reports/TEST-* | grep "<testsuite" | grep -v failures=\"0\")
if [ "$ERRORS" != "" -o "$FAILURES" != "" ]; then
	echo $FAILURES
	echo $ERRORS
	echo "Failure(s) and/or error(s) found in XTS unit and/or interop tests. See previous line"
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

#RUN INTEROP11 TESTS
cd ${WORKSPACE}
cd XTS
cp xts-install/interop-tests/interop11.war $JBOSS_HOME/standalone/deployments/
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi
sleep 10 

cd xts-install/interop-tests
mkdir reports
ant -f run-interop-tests.xml -Dserver.hostname=localhost wstx11-interop-tests
if [ "$?" != "0" ]; then
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

ERRORS=$(cat reports/Test-* | grep "<testsuite" | grep -v errors=\"0\")
FAILURES=$(cat reports/Test-* | grep "<testsuite" | grep -v failures=\"0\")
if [ "$ERRORS" != "" -o "$FAILURES" != "" ]; then
	echo $ERRORS
	echo $FAILURES
	echo "Failure(s) and/or error(s) found in XTS unit and/or interop tests. See previous line"
	$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
	exit -1
fi

#SHUTDOWN JBOSS
$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
if [ "$?" != "0" ]; then
	exit -1
fi

#REMOVE TEST WAR and EAR
rm -f $JBOSS_HOME/standalone/deployments/*war*
if [ "$?" != "0" ]; then
	exit -1
fi
rm -f $JBOSS_HOME/standalone/deployments/*ear*
if [ "$?" != "0" ]; then
	exit -1
fi

#RUN XTS CRASH RECOVERY TESTS
cd ${WORKSPACE}
cd XTS/sar/tests
ant 
if [ "$?" != "0" ]; then
	exit -1
fi

cd ${WORKSPACE}
cd XTS/sar/crash-recovery-tests
if [ "$?" != "0" ]; then
	exit -1
fi

mvn clean test
if [ "$?" != "0" ]; then
	exit -1
fi

java -cp target/classes/ com.arjuna.qa.simplifylogs.SimplifyLogs ./target/log/ ./target/log-simplified
if [ "$?" != "0" ]; then
	exit -1
fi

# Compile and run tx-bridge tests
cd ${WORKSPACE}/txbridge
if [ "$?" != "0" ]; then
	exit -1
fi

ant dist
if [ "$?" != "0" ]; then
	exit -1
fi

cd tests
if [ "$?" != "0" ]; then
	exit -1
fi

ant enable-recovery-listener -Djboss.home=$JBOSS_HOME
if [ "$?" != "0" ]; then
	exit -1
fi

ant test
if [ "$?" != "0" ]; then
	exit -1
fi

#RUN QA TESTS
cd ${WORKSPACE}/qa
if [ "$?" != "0" ]; then
	exit -1
fi

sed -i TaskImpl.properties -e "s#^COMMAND_LINE_0=.*#COMMAND_LINE_0=${JAVA_HOME}/bin/java#"
if [ "$?" != "0" ]; then
	exit -1
fi

ant -Ddriver.url=file:///home/hudson/dbdrivers get.drivers
if [ "$?" != "0" ]; then
	exit -1
fi

ant -f run-tests.xml ci-tests
if [ "$?" != "0" ]; then
	exit -1
fi
