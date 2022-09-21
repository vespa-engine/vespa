#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

jar="target/jing.jar"
mainclass="com/thaiopensource/relaxng/util/Driver"

cmd="java -jar $jar target/generated-sources/trang/resources/schema/services.rng src/test/schema-test-files/services.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/services.rng src/test/schema-test-files/standalone-container.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/services.rng src/test/schema-test-files/services-hosted.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/services.rng src/test/schema-test-files/services-hosted-infrastructure.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/deployment.rng src/test/schema-test-files/deployment.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/deployment.rng src/test/schema-test-files/deployment-with-instances.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/validation-overrides.rng src/test/schema-test-files/validation-overrides.xml"
echo $cmd
$cmd

cmd="java -jar $jar target/generated-sources/trang/resources/schema/services.rng src/test/schema-test-files/services-bad-vespamalloc.xml"
echo $cmd
if $cmd; then
    echo 'invalid attribute not detected'
    exit 1
fi
