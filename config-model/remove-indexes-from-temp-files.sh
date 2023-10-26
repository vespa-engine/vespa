#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

perl -pi -e 's{[[][0-9]*[]][.]}{[].}g;s{[[][0-9]*[]] }{[] };chomp;s/$/\n/' temp/*/*.cfg
