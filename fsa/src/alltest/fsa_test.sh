#!/bin/bash
./fsa_fsa_test_app > fsa_test.output 
diff fsa_test.output fsa_test.out
