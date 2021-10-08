# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package TestUtils::VespaTest;

use Test::More;
use TestUtils::OutputCapturer;
use Yahoo::Vespa::Utils;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
        isOutput
        matchesOutput
        setApplication
        assertRun
        assertRunMatches
        printTest
        useColors
        setLocalHost
    );
}

my $APPLICATION;

&initialize();

return 1;

sub initialize {
    Yahoo::Vespa::Utils::initializeUnitTest(
            'testhost.yahoo.com', \&mockedExitHandler);
}

sub setLocalHost {
    my ($host) = @_;
    Yahoo::Vespa::Utils::initializeUnitTest(
            $host, \&mockedExitHandler);
}

sub useColors {
    TestUtils::OutputCapturer::useColors(@_);
}

sub mockedExitHandler {
    my ($exitcode) = @_;
    die "Application exited with exitcode $exitcode.";
}

sub setApplication {
    my ($main_func) = @_;
    $APPLICATION = $main_func;
}

sub assertRun {
    my ($testname, $argstring,
        $expected_exitcode, $expected_stdout, $expected_stderr) = @_;
    my $exitcode = &run($argstring);
    is( $exitcode, $expected_exitcode, "$testname - exitcode" );
    # print OutputCapturer::getStdOut();
    isOutput($expected_stdout, $expected_stderr, $testname);
}

sub assertRunMatches {
    my ($testname, $argstring,
        $expected_exitcode, $expected_stdout, $expected_stderr) = @_;
    my $exitcode = &run($argstring);
    is( $exitcode, $expected_exitcode, "$testname - exitcode" );
    # print OutputCapturer::getStdOut();
    matchesOutput($expected_stdout, $expected_stderr, $testname);
}

sub run {
    my ($argstring) = @_;
    my @args = split(/\s+/, $argstring);
    eval {
        Yahoo::Vespa::ArgParser::initialize();
        &$APPLICATION(\@args);
    };
    my $exitcode = 0;
    if ($@) {
        if ($@ =~ /Application exited with exitcode (\d+)\./) {
            $exitcode = 1;
        } else {
            print "Unknown die signal '" . $@ . "'\n";
        }
    }
    return $exitcode;
}

sub printTest {
    print "Test: ", @_;
}
