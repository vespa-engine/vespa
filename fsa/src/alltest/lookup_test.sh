#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_lookup_test_app __testfsa__.__fsa__ < $SOURCE_DIRECTORY/testinput.txt > lookup_test.output
diff lookup_test.output $SOURCE_DIRECTORY/lookup_test.out
