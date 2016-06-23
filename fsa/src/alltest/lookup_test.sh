#!/bin/bash
set -e
./fsa_lookup_test_app __testfsa__.__fsa__ < testinput.txt > lookup_test.output
diff lookup_test.output lookup_test.out
