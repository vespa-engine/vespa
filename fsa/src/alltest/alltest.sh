#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

# first create the FSA
./fsa_fsa_create_test_app

# simple tests
$SOURCE_DIRECTORY/lookup_test.sh
$SOURCE_DIRECTORY/fsa_test.sh
$SOURCE_DIRECTORY/detector_test.sh

# advanced tests
$SOURCE_DIRECTORY/ngram_test.sh
$SOURCE_DIRECTORY/segmenter_test.sh
$SOURCE_DIRECTORY/vectorizer_test.sh

# manager test
./fsa_fsamanager_test_app . __testfsa__.__fsa__

# perf tests
./fsa_vectorizer_perf_test_app
./fsa_fsa_perf_test_app
