#!/bin/bash
set -e

. ../../binref/env.sh

$BINREF/compilejava JavaServer.java
$BINREF/compilejava JavaClient.java

(ulimit -c; ulimit -H -c; ulimit -c unlimited; ./messagebus_test_speed_test_app)
