#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if test -x /usr/bin/setarch
then
    setarch $(arch) -R ${VALGRIND} ./searchlib_verify_feature_test_app
else
    ${VALGRIND} ./searchlib_verify_feature_test_app
fi
