#!/usr/bin/perl -w
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use strict;

# Simple wrapper for executing vdsdisktool-bin

my $args = &getArgs();
&run("vdsdisktool-bin $args");

exit(0);

sub isHelpRequest {
    foreach my $arg (@ARGV) {
        if ($arg eq '-h' || $arg eq '--help') {
            return 1;
        }
    }
    return 0;
}

sub getArgs {
    my @args;
    foreach my $arg (@ARGV) {
        $arg =~ s/([ \t\f])/\\$1/g;
        push @args, $arg;
    }
    return join(' ', @args);
}

sub isDebugRun {
    foreach my $arg (@ARGV) {
        if ($arg eq '--debug-perl-wrapper') {
            return 1;
        }
    }
    return 0;
}

sub run {
    my ($cmd) = @_;
    if (&isDebugRun()) {
        print "Debug: Would have executed '$cmd'.\n";
    } else {
        exec($cmd);
    }
}
