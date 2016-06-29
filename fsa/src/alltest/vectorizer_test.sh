#!/bin/bash
set -e
./fsa_vectorizer_test_app < testinput.txt > vectorizer_test.output
diff vectorizer_test.output vectorizer_test.out
