#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_detector_test_app < $SOURCE_DIRECTORY/testinput.txt > detector_test.output
diff detector_test.output $SOURCE_DIRECTORY/detector_test.out
