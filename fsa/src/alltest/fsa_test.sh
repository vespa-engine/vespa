#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_fsa_test_app > fsa_test.output 
diff fsa_test.output $SOURCE_DIRECTORY/fsa_test.out
