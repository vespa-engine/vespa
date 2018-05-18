#!/usr/bin/env perl
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Various small functions used when bootstrapping the config system

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
    my $tmp = $ENV{'VESPA_HOSTNAME'};
    my $bin = $ENV{'VESPA_HOME'} . "/bin";
    if (!defined $tmp) {
        $tmp = `${bin}/vespa-detect-hostname || hostname -f || hostname || echo "localhost"`;
        chomp $tmp;
    }
    my $validate = "${bin}/vespa-validate-hostname";
    if (-f "${validate}") {
       system("${validate} $tmp");
       ( $? == 0 ) or die "Could not validate hostname\n";
    }
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

use strict;
use warnings;
use File::Copy;
use File::Temp;

my $myHostname = `vespa-print-default hostname`;
chomp $myHostname;
my $default_configproxy_port = "19090";
my $default_configserver_port = "19070";

my $base_cfg_dir = $VESPA_HOME . "/conf/vespa";

# Set this to 1 to look up values (see getValue) in config files instead
# of in environment variables
my $lookupInConfig = 0;


sub getValue {
    my ($varname, $prefix) = @_;
    if ($lookupInConfig) {
	return getConfigValue($varname, $prefix);
    } 
    else {
        return getEnvironmentValue($varname, $prefix);
    }
}

sub getConfigValue {
    my ($varname, $config) = @_;
    my $path = "$base_cfg_dir/$config.conf";
    if (open(CFG, "<$path")) {
        while (<CFG>) {
            chomp;
            if ( m{^(\w+)\s(.+)} ) {
		return $2 if $1 eq $varname;
            }
        }
        close(CFG);
    }
    return;
}

sub getEnvironmentValue {
    my ($varname, $prefix) = @_;
    my $value = $ENV{$prefix . "__" . $varname};
    if (defined $value && $value =~ m{^\s*(\S.*)\s*}) {
        return $1;
    }
    return $value;
}

sub getCCSVar {
    my ($varname, $default) = @_;
    my $value = getValue($varname, "cloudconfig_server");
    if (defined($value)) {
        return $value;
    }
    return $default;
}

sub getServicesVar {
    my ($varname, $default, $warn) = @_;
    # print "GET var '$varname'\n";
    my $cloud = getValue($varname, "services");
    my $vespa = getValue($varname, "vespa_base");
    my $plain = $ENV{$varname};
    if (defined($cloud) && defined($vespa)) {
        print STDERR "Found settings for both services.$varname and vespa_base.$varname, using settings from services\n";
    }
    if (defined($cloud)) {
        return $cloud;
    } elsif (defined($vespa)) {
        return $vespa;
    } elsif (defined($plain)) {
        return $plain;
    } elsif ($warn > 0) {
        print STDERR "No value found for 'services.$varname' or 'vespa_base.$varname'; using '$default'\n";
    }
    return $default;
}

sub getConfigServerPort {
    my $port = getServicesVar('port_configserver_rpc', $default_configserver_port, 0);
    return $port;
}

sub printConfigServerPort {
    my $port = getConfigServerPort();
    print "$port\n";
}

sub getConfigServers {
    my @ret;

    my $addr = $ENV{'VESPA_CONFIGSERVERS'};
    if (! defined($addr)) {
        $addr = getServicesVar('addr_configserver', $myHostname, 1);
    }
    my $port = getConfigServerPort();

    my $h;
    foreach $h (split(/,|\s+/, $addr)) {
        if ($h =~ m{(\S+:\d+)}) {
            push @ret, $1;
        } else {
            push @ret, "${h}:${port}";
        }
    }
    return @ret;
}

sub getZKPort {
    my $zk_client_port = getCCSVar('zookeeper_clientPort', 2181);
    return $zk_client_port;
}
sub getZKString {
    my $zk_client_port = getZKPort();
    my $out;
    my $addr;
    foreach $addr (getConfigServers()) {
        $addr =~ s{:\d+}{:$zk_client_port,};
        $out .= $addr;
    }
    chop($out);                 # last comma
    return $out;
}

sub printZKString {
    my $out = getZKString();
    print $out . "\n";
}

sub printAllConfigSourcesWithPort {
    my $cfport = getConfigServerPort();
    my $cpport = getServicesVar('port_configproxy_rpc',  $default_configproxy_port,  0);
    my $addr = "localhost";
    my $out = "tcp/${addr}:${cpport}";
    foreach $addr (getConfigServers()) {
        if ($addr =~ m{\/}) {
            if ($addr =~ m{\:}) {
                $out .= ",${addr}";
            } else {
                $out .= ",${addr}:${cfport}";
            }
        } else {
            if ($addr =~ m{\:}) {
                $out .= ",tcp/${addr}";
            } else {
                $out .= ",tcp/${addr}:${cfport}";
            }
        }
    }
    print $out . "\n";
}

sub printConfigSources {
    my $out;
    my $addr;
    foreach $addr (getConfigServers()) {
        $out .= "tcp/${addr},";
    }
    chop($out);                 # last comma
    print $out . "\n";
}

sub printConfigHttpSources {
    my $out;
    my $addr;
    foreach $addr (getConfigServers()) {
        my $host = "";
        my $port = 0;
        if ($addr =~ /(.*):(\d+)$/) {
            $host = $1;
            $port = $2;
        }
        $port++;                        # HTTP is rpc + 1
        $out .= "http://$host:$port ";
    }
    chop($out);                 # last space
    print $out . "\n";
}

# Perl trim function to remove whitespace from the start and end of the string
sub trim($) {
    my $string = shift;
    $string =~ s/^\s+//;
    $string =~ s/\s+$//;
    return $string;
}

sub getLastLine {
    my ($file) = @_;
    `grep -v \"\^\$\" $file | tail -n 1` # skip blank lines
}

sub usage {
    print "usage: ";
    print "vespa-config [-configsources | -confighttpsources | -zkstring | -configserverport | -zkclientport]\n";
}

if ( @ARGV == 0 ) {
    usage();
    exit 1;
}

if ( $ARGV[0] eq "-allconfigsources" ) {
    printAllConfigSourcesWithPort();
    exit 0;
}
if ( $ARGV[0] eq "-configsources" ) {
    printConfigSources();
    exit 0;
}
if ( $ARGV[0] eq "-confighttpsources" ) {
    $lookupInConfig = 1;
    printConfigHttpSources();
    exit 0;
}
if ( $ARGV[0] eq "-zkstring" ) {
    printZKString();
    exit 0;
}
if ( $ARGV[0] eq "-configserverport" ) {
    $lookupInConfig = 1;
    printConfigServerPort();
    exit 0;
}
if ( $ARGV[0] eq "-zkclientport" ) {
    my $zk_client_port = getZKPort();
    print "$zk_client_port\n";
    exit 0;
}

usage();
exit 1;
