#!/usr/bin/env perl
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# This script transforms a .def file into a .h and .cpp file for config
#
# TODO: Remove this script and use the java code directly. BTW, why
#       does this script have the same limitations as the old make-config.pl
#       in that the .def-file must reside in the root directory? The java
#       code supports reading the .def-file from any directory. This script
#       should have the same input parameters as the java code, and just
#       map them directly to the java system properties

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
        chomp $tmp;
    }
    system("vespa-validate-hostname $tmp");
    ( $? == 0 ) or die "Could not validate hostname\n";
    return $tmp;
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

require 5.006_001;
use strict;
use warnings;

use Cwd 'abs_path';

# Now this uses the new java codegen library. But the script still exist to
# map be able to call java the right way, setting the necessary properties

my ($root, $def) = @ARGV;

if (!defined $root || !defined $def) {
    print "Usage make-config.pl <source root dir> <def file>\n";
    exit(1);
}

#print "Root: $root\n"
#    . "Def: $def\n";

my $subdir = &getRelativePath($root, &getPath($def));

my $cmd = "java"
        . " -Dconfig.spec=$def"
        . " -Dconfig.dest=$root"
        . " -Dconfig.lang=cpp"
        . " -Dconfig.subdir=$subdir"
        . " -Dconfig.dumpTree=false"
        . " -Xms64m -Xmx64m"
        . " -jar $VESPA_HOME/lib/jars/configgen.jar";

print "Generating config: $cmd\n";
exec($cmd);

exit(0); # Will never be called due to exec above, but just to indicate end

sub getRelativePath {
    my ($from, $to) = @_;

    $from = abs_path($from);
    $to = abs_path($to);

    # Escape $from so it can contain regex special characters in path
    $from =~ s/([\+\*\(\)\{\}\.\?\[\]\$\&])/\\$1/g;

    $to =~ /^$from\/(.*)$/
            or die "The def file must be contained within the root";
    return $1;
}

sub getPath {
    my $file = $_[0];
    if ($file =~ /^(.*)\/[^\/]*$/) {
        return $1;
    } else {
        return ".";
    }
}
