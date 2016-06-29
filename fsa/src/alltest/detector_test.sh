#!/bin/bash
set -e
./fsa_detector_test_app < testinput.txt > detector_test.output
diff detector_test.output detector_test.out
