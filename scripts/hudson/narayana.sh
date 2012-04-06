if [ -z "${WORKSPACE}" ]; then
  echo "UNSET WORKSPACE"
  exit -1;
fi

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

git pull origin master
if [ "$?" != "0" ]; then
	exit -1
fi

./build.sh clean install -DskipTests
if [ "$?" != "0" ]; then
	exit -1
fi

#START JBOSS
export JBOSS_HOME=${WORKSPACE}/jboss-as/build/target/jboss-as-7.1.2.Final-SNAPSHOT
$JBOSS_HOME/bin/standalone.sh --server-config=../../docs/examples/configs/standalone-xts.xml&
sleep 10 

#RUN XTS UNIT TESTS
cd ${WORKSPACE}
cd XTS
if [ "$?" != "0" ]; then
	exit -1
fi

ant -Dpublican=false -Dtesttype=tests-11 -Dsartype=sar-11 install
if [ "$?" != "0" ]; then
	exit -1
fi

cp xts-install/tests/*ear $JBOSS_HOME/standalone/deployments/
if [ "$?" != "0" ]; then
	exit -1
fi
sleep 10 

export MYTESTIP_1=localhost
cd xts-install/tests
ant -f run-tests.xml tests-11
if [ "$?" != "0" ]; then
	exit -1
fi

#RUN INTEROP11 TESTS
cd ${WORKSPACE}
cd XTS
cp xts-install/interop-tests/interop11.war $JBOSS_HOME/standalone/deployments/
if [ "$?" != "0" ]; then
	exit -1
fi
sleep 10 

cd xts-install/interop-tests
ant -f run-interop-tests.xml -Dserver.hostname=localhost wstx11-interop-tests
if [ "$?" != "0" ]; then
	exit -1
fi

#SHUTDOWN JBOSS
$JBOSS_HOME/bin/jboss-cli.sh --connect command=:shutdown
if [ "$?" != "0" ]; then
	exit -1
fi

#RUN XTS CRASH RECOVERY TESTS
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

#RUN QA TESTS
cd ${WORKSPACE}
cd qa
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
