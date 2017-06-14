#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./fsa_segmenter_test_app < $SOURCE_DIRECTORY/testinput.txt > segmenter_test.output
diff segmenter_test.output $SOURCE_DIRECTORY/segmenter_test.out
