if [ -z "${WORKSPACE}" ]; then
  echo "UNSET WORKSPACE"
  exit -1;
fi

#BUILD JBOSSTS
ant -Demma.enabled=false -Dpublican=false jbossall
if [ "$?" != "0" ]; then
	exit -1
fi

#RUN QA TESTS
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
