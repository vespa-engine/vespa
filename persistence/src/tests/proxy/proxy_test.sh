#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
$VALGRIND ./persistence_providerstub_test_app
$VALGRIND ./persistence_providerproxy_test_app
$VALGRIND ./persistence_providerproxy_conformance_test_app
