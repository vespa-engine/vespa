#!/bin/bash

set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Usage: $0 <Vespa version> <Git reference>"
    exit 1
fi

readonly VESPA_RELEASE="$1"
readonly VESPA_REF="$2"

#QUERY_VERSION_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null https://oss.sonatype.org/content/repositories/releases/com/yahoo/vespa/parent/${VESPA_RELEASE}/)
#if [ "200" = $QUERY_VERSION_HTTP_CODE ]; then
#  echo "Version $VESPA_RELEASE is already promoted, exiting"
#  exit 0
#fi

export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))

VESPA_DIR=vespa-clean
git clone https://github.com/vespa-engine/vespa.git $VESPA_DIR
(
  cd $VESPA_DIR
  git checkout $VESPA_REF

  find . -name "pom.xml" -exec sed -i'' -e "s,<version>.*SNAPSHOT.*</version>,<version>$VESPA_RELEASE</version>," \
       -e "s,<vespaversion>.*project.version.*</vespaversion>,<vespaversion>$VESPA_RELEASE</vespaversion>," \
       -e "s,<test-framework.version>.*project.version.*</test-framework.version>,<test-framework.version>$VESPA_RELEASE</test-framework.version>," \
       {} \;
  ./bootstrap.sh
  mvn -B --no-snapshot-updates --threads 1C -DskipTests clean install

  # More magic needed there to settings.xml / keys
  #mvn -B --no-snapshot-updates --threads 1C -DskipTests deploy
)

