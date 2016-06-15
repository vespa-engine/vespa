#!/bin/bash
. ../../binref/env.sh

$BINREF/compilejava JavaServer.java
$BINREF/compilejava JavaClient.java
VESPA_LOG_LEVEL='all -spam' ./messagebus_test_error_test_app
