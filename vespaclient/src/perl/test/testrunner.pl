#!/usr/bin/perl -w
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#
# Searches around in test dir to find test binaries and run them. Sadly these
# seem to return exit code 0 on some failures for unknown reasons. To counter
# that the testrunner grabs the output of the test and triggers test to fail if
# it finds unexpected data in the output.
#
# Unit tests should mostly not write as this will clutter report, but if they
# want to write some status they have to write it so it does not trigger
# failure here. Use printTest in VespaTest suite to prefix all test output to
# something we match here.
#

use strict;
use warnings;

$| = 1;
my @files = `find . -name \*Test.pl`;
chomp @files;

my $tempdir = `mktemp -d /tmp/mockup-vespahome-XXXXXX`;
chomp $tempdir;
$ENV{'VESPA_HOME'} = $tempdir . "/";
mkdir "${tempdir}/libexec";
mkdir "${tempdir}/libexec/vespa" or die "Cannot mkdir ${tempdir}/libexec/vespa\n";
`touch ${tempdir}/libexec/vespa/common-env.sh`;

my $pat;
if (exists $ENV{'TEST_SUBSET'}) {
    $pat = $ENV{'TEST_SUBSET'};
}

my $failure_pattern = qr/(?:Tests were run but no plan was declared and done_testing\(\) was not seen)/;
my $accepted_pattern = qr/^(?:\s*|\d+\.\.\d+|ok\s+\d+\s+-\s+.*|Test: .*|.*spam: .*)$/;

my $failures = 0;
foreach my $file (@files) {
    $file =~ /^(?:\.\/)?(.*)\.pl$/ or die "Strange file name '$file'.";
    my $test = $1;
    if (!defined $pat || $test =~ /$pat/) {
        print "\nRunning test suite $test.\n\n";
        my ($code, $result) = captureCommand("PERLLIB=../lib perl -w $file");
        my @data = split(/\n/, $result);
        if ($code != 0) {
            ++$failures;
            print "Test binary returned with non-null exitcode. Failure.\n";
        } elsif (&matchesFailurePattern(\@data)) {
            ++$failures;
        } elsif (&notMatchesSuccessPattern(\@data)) {
            ++$failures;
        }
    } else {
        # print "Skipping test suite '$test' not matching '$pat'.\n";
    }
}

if ($failures > 0) {
    print "\n\n$failures test suites failed.\n";
    exit(1);
} else {
    print "\n\nAll tests succeeded.\n";
}

`rm -rv ${tempdir}`;

exit(0);

sub matchesFailurePattern { # (LineArrayRef)
    my ($data) = @_;
    foreach my $line (@$data) {
        if ($line =~ $failure_pattern) {
            print "Line '$line' indicates failure. Failing test suite.\n";
            return 1;
        }
    }
    return 0;
}

sub notMatchesSuccessPattern { # (LineArrayRef)
    my ($data) = @_;
    foreach my $line (@$data) {
        if ($line !~ $accepted_pattern) {
            print "Suspicious line '$line'.\n";
            print "Failing test due to line suspected to indicate failure.\n"
                . "(Use printTest to print debug data during test to have it "
                . "not been marked suspected.\n";
            return 1;
        }
    }
    return 0;
}

# Run a given command, giving exitcode and output back, but let command write
# directly to stdout/stderr. (Useful for long running commands or commands that
# may stall, such that you can see where it got into trouble)
sub captureCommand { # (Cmd) -> (ExitCode, Output)
    my ($cmd) = @_;
    my ($fh, $line);
    my $data;
    open ($fh, "$cmd 2>&1 |") or die "Failed to run '$cmd'.";
    while ($line = <$fh>) {
        print $line;
        $data .= $line;
    }
    close $fh;
    my $exitcode = $?;
    return ($exitcode >> 8, $data);
}
