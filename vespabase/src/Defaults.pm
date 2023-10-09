# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# utility functions for reading and setting environment defaults

package Yahoo::Vespa::Defaults;

use strict;
use warnings;
use Carp ();

BEGIN { # - Define exports from this module
    use base 'Exporter';
    our @EXPORT = qw(
        considerFallback
        readConfFile
    );
}

return 1;

########################## Default exported functions ########################

sub considerFallback {
    my ( $varname, $value ) = @_;
    my $prevVal = $ENV{$varname};
    if ( ! defined $value || $value eq '' ) {
        # print STDERR " (debug) skipping empty value for $varname\n";
        return;
    }
    if ( defined $prevVal && ! $prevVal eq '' ) {
        # print STDERR " (debug) $varname already is '$prevVal', skipping new value '$value'\n";
        return;
    }
    $ENV{$varname} = $value;
}

# Use this function to 
sub readConfFile {
    my $vHome = $ENV{'VESPA_HOME'};
    if ( $vHome eq "" || ! -d $vHome ) {
        die "Bad or missing VESPA_HOME environment variable $vHome\n";
    }
    my $deffile = $vHome . '/conf/vespa/default-env.txt' ;
    if ( -f $deffile ) {
        open(DEFFILE, $deffile) or die "Cannot open '$deffile'\n";
        while (<DEFFILE>) {
            chomp;
            my ( $action, $varname, $value ) = split(' ', $_, 3);
            if ( $varname !~ m{^\w+$} ) {
                # print STDERR "INVALID variable name '$varname' in $deffile, skipping\n";
                next;
            }
            if ( $action eq 'fallback' ) {
                considerFallback($varname, $value);
            }
        }
        close(DEFFILE);
    }
}
