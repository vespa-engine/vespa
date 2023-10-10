#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

# Run test
echo "Testing the FastS_URL class..."
$VALGRIND ./searchlib_url_test_app
if [ $? -eq 0 ]; then
    echo "SUCCESS: Test on FastS_URL passed!"
else
    echo "FAILURE: Test on FastS_URL failed!"
    exit 1
fi
exit 0
