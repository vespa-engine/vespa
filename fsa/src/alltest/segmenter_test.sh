#!/bin/bash
./fsa_segmenter_test_app < testinput.txt > segmenter_test.output
diff segmenter_test.output segmenter_test.out
