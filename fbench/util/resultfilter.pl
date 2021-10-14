#!/usr/bin/perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This script converts an fbench summary report read from stdin to a
# single line containing only the numerical values written to
# stdout.

while(<>) {
    chomp();
    if(/:\s*([-+]?[\d.]+)/) {
	print $1, " ";
    }
}
print "\n";
