#!/usr/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This script converts an fbench summary report read from stdin to a
# single line containing only the numerical values written to
# stdout.

sed -n "s/.*: *\([0-9.][0-9.]*\).*/\1/p" | tr '\n' ' '
echo
