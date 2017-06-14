#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_fsa_test_app > fsa_test.output 
diff fsa_test.output $SOURCE_DIRECTORY/fsa_test.out
