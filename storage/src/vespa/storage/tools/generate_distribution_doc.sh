#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
./generatedistributionbits -s -r 1 -b 32 --html > distbitreport.html
./generatedistributionbits -s -r 2 -b 32 --html >> distbitreport.html
./generatedistributionbits -s -r 2 -b 32 --highrange --html >> distbitreport.html
