#!/bin/bash
set -e

. ../../binref/env.sh

$BINREF/compilejava JavaServer.java

$VALGRIND ./messagebus_test_trace_test_app
