#!/usr/bin/env perl
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN perl environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

use File::Basename;
use File::Path;

sub findpath {
    my $myfullname = ${0};
    my($myname, $mypath) = fileparse($myfullname);

    return $mypath if ( $mypath && -d $mypath );
    $mypath=`pwd`;

    my $pwdfullname = $mypath . "/" . $myname;
    return $mypath if ( -f $pwdfullname );
    return 0;
}

# Returns the argument path if it seems to point to VESPA_HOME, 0 otherwise
sub is_vespa_home {
    my($VESPA_HOME) = shift;
    my $COMMON_ENV="libexec/vespa/common-env.sh";
    if ( $VESPA_HOME && -d $VESPA_HOME ) {
        my $common_env = $VESPA_HOME . "/" . $COMMON_ENV;
        return $VESPA_HOME if -f $common_env;
    }
    return 0;
}

# Returns the home of Vespa, or dies if it cannot
sub findhome {
    # Try the VESPA_HOME env variable
    return $ENV{'VESPA_HOME'} if is_vespa_home($ENV{'VESPA_HOME'});
    if ( $ENV{'VESPA_HOME'} ) { # was set, but not correctly
        die "FATAL: bad VESPA_HOME value '" . $ENV{'VESPA_HOME'} . "'\n";
    }

    # Try the ROOT env variable
    $ROOT = $ENV{'ROOT'};
    return $ROOT if is_vespa_home($ROOT);

    # Try the script location or current dir
    my $mypath = findpath();
    if ($mypath) {
        while ( $mypath =~ s|/[^/]*$|| ) {
            return $mypath if is_vespa_home($mypath);
        }
    }
    die "FATAL: Missing VESPA_HOME environment variable\n";
}

sub findhost {
    $ENV{'PATH'} = $ENV{'VESPA_HOME'} . '/bin:' . $ENV{'PATH'};
    my $tmp = $ENV{'VESPA_HOSTNAME'};
    if (!defined $tmp) {
        $tmp = `vespa-detect-hostname` or die "Could not detect hostname\n";
    }
    system("vespa-validate-hostname $tmp");
    ( $? == 0 ) or die "Could not validate hostname\n";
}

BEGIN {
    my $tmp = findhome();
    $ENV{'VESPA_HOME'} = $tmp;
    $tmp = findhost();
    $ENV{'VESPA_HOSTNAME'} = $tmp;
}
my $VESPA_HOME = $ENV{'VESPA_HOME'};

# END perl environment bootstrap section

use lib $ENV{'VESPA_HOME'} . '/lib/perl5/site_perl';
use lib $ENV{'VESPA_HOME'} . '/lib64/perl5/site_perl';
use Yahoo::Vespa::Defaults;
readConfFile();

use strict;
use warnings;

use Yahoo::Vespa::Bin::GetClusterState;

exit(getClusterState(\@ARGV));
