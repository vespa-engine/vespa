#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
export VERSION=$1
if [ -z "$VERSION" ]
then
    echo "usage: $(basename $0) <version> [<builddir>]"
    exit 1
fi
echo VERSION=$VERSION

export BASEDIR=$2
if [ "$BASEDIR" ]
then
    export POM_FORMAT="$BASEDIR/%s-HEAD/%s/pom.xml"
else
    export POM_FORMAT="$(pwd)/../%s/pom.xml"
fi
echo POM_FORMAT=$POM_FORMAT
echo

echo "-------------------------------------------------------------------------------"
echo "Compiling toolkit."
echo "-------------------------------------------------------------------------------"
cd tools
mvn -q clean install -DskipTests
JAVA_TOOLS="java -cp $(pwd)/target/tools-jar-with-dependencies.jar"
echo
cd ..

DEPS_NEXT="com.yahoo.vespa:container-disc:jar:$VERSION:compile"
DEPS_PREV=""
BLACKLIST=$(cat dependency_blacklist)

PASS=0
rm -rf target
mkdir target
cd target
while [ "$DEPS_NEXT" ]
do
    PASS=$((PASS+1))
    mkdir -p "pass$PASS"
    cd "pass$PASS"

    DEPS_PREV=$DEPS_NEXT
    DEPS_NEXT=""

    echo "-------------------------------------------------------------------------------"
    echo "Deriving dependencies, pass $PASS."
    echo "-------------------------------------------------------------------------------"
    echo DEPENDENCIES=$DEPS_PREV
    echo "Building dependency tree.."
    $JAVA_TOOLS com.yahoo.container.dev.builder.PomFileGenerator $VERSION $DEPS_PREV > pom.xml
    mvn -q dependency:tree -DoutputFile=dependencies
    DEPS_NEXT="$($JAVA_TOOLS com.yahoo.container.dev.builder.DependencyResolver . $BLACKLIST)"

    echo "Resolving X-JDisc-Preinstall-Bundle instructions.."
    mvn -q dependency:unpack-dependencies             \
        -DexcludeTransitive=true                      \
        -Dmdep.unpack.includes="META-INF/MANIFEST.MF" \
        -Dmdep.useSubDirectoryPerArtifact=true        \
        -DoutputDirectory=.
    DEPS_NEXT="$DEPS_NEXT $($JAVA_TOOLS com.yahoo.container.dev.builder.PreinstalledBundleResolver . "$POM_FORMAT")"

    DEPS_NEXT=$(echo $DEPS_NEXT | sort | uniq)
    [ "$DEPS_NEXT" == "$DEPS_PREV" ] && DEPS_NEXT=""
    echo
    cd ..
done

echo "-------------------------------------------------------------------------------"
echo "Testing final pom.xml"
echo "-------------------------------------------------------------------------------"
cp pass$PASS/pom.xml .
mvn clean install -DskipTests || exit 1
cd ..
