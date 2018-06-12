#!/usr/bin/perl
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use POSIX qw(strftime);

$srcdir = ".";

my $printmap = 0;
my $tagtype = "";

while ($opt = shift) {
    if ($opt =~ m/^-/) {
        if ($opt eq "-M") {
	    $printmap = 1;
	} elsif ($opt eq "-T") {
	    $tagtype = shift;
	} else {
	    print STDERR "ERROR: unknown option '$opt' for getversion\n";
	    print "error\n";
	    exit 1;
	}
    } else {
	$srcdir = $opt;
    }
}

if (!defined($srcdir)) {
    die "srcdir must be set";
}

# Read current major-minor release
sub read_head_version() {
    my $file = "$srcdir/VERSION";
    if (! -f $file) {
        die "Unable to locate version file";
    }
    open(my $fd, "< $file") ||
        die "Unable to open VERSION: $!";
    my $version = <$fd>;
    chomp($version);
    close($fd);

    return $version;
}

if ( ! -d $srcdir ) {
    print STDERR "ERROR: bad directory '$srcdir' for getversion\n";
    print "error\n";
    exit 1;
}

# assume HEAD if all else fails
my $mainver = read_head_version();

# date adding logic
# goal is to end with '$dateadd' set to a value
# starting with dot, date, new dot, wall-clock time
# vbuild/mbuild also has some logic for this:

$dateadd = $ENV{"VBUILD_VERSION_DATE"};
$buildtime = $ENV{"CVSBUILDTIME"};

if ($buildtime && $buildtime =~ m/^(\d+)-(\d+)-(\d+) (\d+):(\d+):(\d+)$/ ) {
    $dateadd = ".$1$2$3.$4$5$6";
} elsif ($dateadd) {
    1;
} else {
    $dateadd = (strftime ".%Y%m%d.%H%M%S", gmtime);
}

$tag = "HEAD";

if (defined $ENV{FACTORY_VESPA_VERSION}) { 
    $version = $ENV{FACTORY_VESPA_VERSION};
} elsif ($tagtype eq 'tag') {
    # only for exact tag, do not add date:
    $version = $mainver;
} else {
    $version = $mainver . $dateadd;
}

if ($printmap) {
    # other useful information

    chomp($ostype = `uname -s`);

    chomp($osver = `uname -r`);
    chomp($osarch = `uname -m`);

    $vtag_system_rev = $ostype . "-" . $osver;
    chomp ($who = `(whoami || logname) 2>/dev/null`);
    chomp ($where = `uname -n`);
    $where =~ s/\.yahoo\.com$//;

    $vtag_date = $dateadd;
    $vtag_date =~ s/^\.//;

    $mv = $version;
    foreach $m ( "major", "minor", "micro" ) {
	$mv =~ s/^\D+//;
	$cversion{$m} = "0";
	if ( $mv =~ s/^(\d+)// ) {
	    $cversion .= "." . $1;
	    $cversion{$m} = $1;
	}
    }
    $cversion =~ s/^\.//;
}

if ($printmap) {
    print "V_TAG            ${tag}\n";
    print "V_TAG_DATE       ${vtag_date}\n";
    print "V_TAG_PKG        ${version}\n";
    print "V_TAG_ARCH       ${osarch}\n";
    print "V_TAG_SYSTEM     ${ostype}\n";
    print "V_TAG_SYSTEM_REV ${vtag_system_rev}\n";
    print "V_TAG_BUILDER    ${who}\@${where}\n";
    print "V_TAG_COMPONENT  ${cversion}\n";
    exit;
}
    
print "$version\n";
exit;
