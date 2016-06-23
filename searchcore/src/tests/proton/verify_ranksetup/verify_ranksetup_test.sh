#!/bin/bash
set -e

export PWD=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
$VALGRIND ./searchcore_verify_ranksetup_test_app
