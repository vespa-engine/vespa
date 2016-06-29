#!/bin/bash
set -e
./fsa_ngram_test_app > ngram_test.output
diff ngram_test.output ngram_test.out
