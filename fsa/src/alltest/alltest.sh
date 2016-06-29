#!/bin/bash
set -e

# first create the FSA
./fsa_fsa_create_test_app

# simple tests
./lookup_test.sh
./fsa_test.sh
./detector_test.sh

# advanced tests
./ngram_test.sh
./segmenter_test.sh
./vectorizer_test.sh

# manager test
./fsa_fsamanager_test_app . __testfsa__.__fsa__

# perf tests
./fsa_vectorizer_perf_test_app
./fsa_fsa_perf_test_app
