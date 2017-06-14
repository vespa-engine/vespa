#!/bin/bash
set -e
$VALGRIND ./persistence_providerstub_test_app
$VALGRIND ./persistence_providerproxy_test_app
$VALGRIND ./persistence_providerproxy_conformance_test_app
