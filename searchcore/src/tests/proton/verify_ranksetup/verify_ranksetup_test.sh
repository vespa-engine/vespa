#!/bin/bash
set -e

export PWD=$(pwd)
$VALGRIND ./searchcore_verify_ranksetup_test_app
