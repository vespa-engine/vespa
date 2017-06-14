#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_ngram_test_app > ngram_test.output
diff ngram_test.output $SOURCE_DIRECTORY/ngram_test.out
