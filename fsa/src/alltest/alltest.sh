#!/bin/bash
set -e
set -x
./detector_test.sh
./fsa_test.sh
./fsa_fsa_create_test_app
./fsa_fsa_perf_test_app
./fsa_fsamanager_test_app . __testfsa__.__fsa__ 
./lookup_test.sh
./ngram_test.sh
./segmenter_test.sh
./vectorizer_test.sh
./fsa_vectorizer_perf_test_app
