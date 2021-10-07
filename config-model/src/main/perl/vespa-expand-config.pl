#!/usr/bin/env perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#============================================================================
# @version  $Id: expand-config.pl,v 1.1 2006-07-26 15:52:43 gv Exp $
# @project  Vespa Admin
# @author   Gj�ran Voldengen
# @date     created 2005-04-15
# 
# Create a vespa config file from an application package config file
# that might contain "file=<filename>" statements. The output stream
# consists of the original file contents, with the expanded and escaped 
# contents of the files given in file= statements.
#=============================================================================

use strict;

$| = 1;

# Check for correct number of command line args
if ( int(@ARGV) != 0 ) {
    die ("\nUsage: cat infile | $0 > outfile\n\n");
}


#============================================================================
# Global Constants
#============================================================================

# "Reserved keywords" to recognize in input file
my $FILE = "file";


#============================================================================
# Subroutines
#============================================================================

#---------------------------------------------------------------------------
# Expand the contents of the input file into a one-line string,
# escaping special chars.
#---------------------------------------------------------------------------
sub expandFile {
    my ($filename) = @_;

    my $config = "";

    # Read the complete input file into a single string
    open (INFILE, "$filename") || die "Cannot open $filename\n";
    while (<INFILE>) {
	$config .= $_;
    }
    
    $config =~ s{\\}{\\\\}g;
    $config =~ s{\"}{\\\"}g; #" emacs gets confused..
    $config =~ s{\n}{\\n}g;    

    return $config;
}


#============================================================================
# Main program
#============================================================================
my $file = "";

while (<STDIN>) {

    # Comment lines are allowed, and must be preserved, along with all
    # lines that don't contain "file="
    unless (m/^\#/) {
	# Allow several files on one line
	while (m{$FILE \s* = \s* ([^\s\"]+) }x) {   #"
 	    $file = $1;
	    $file =~ s{^\s+}{};
	    $file =~ s{\s+ $ }{}x;
	    $_ = $` . expandFile($file) . $';
	}
    }
    print STDOUT ($_);
}



############################### File end ################################
