# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package TestUtils::OutputCapturer;

use Test::More;
use Yahoo::Vespa::ConsoleOutput;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
        getOutput
        isOutput
        matchesOutput
    );
}

Yahoo::Vespa::ConsoleOutput::setTerminalWidth(79);

our ($stdout, $stderr);
my $USE_COLORS = 1;

&openStreams();

END {
    &closeStreams();
}

return 1;

sub useColors {
    $USE_COLORS = $_[0];
    &closeStreams();
    &openStreams();
}

sub isOutput { # (stdout, stderr, test)
    my ($expected_cout, $expected_cerr, $test) = @_;
    my ($cout, $cerr) = &getOutput();
    &diff($expected_cout, $cout);
    ok ($cout eq $expected_cout, $test . " - stdout");
    &diff($expected_cerr, $cerr);
    ok ($cerr eq $expected_cerr, $test . " - stderr");
}

sub matchesOutput { # (stdout_pattern, stderr_pattern, test)
    my ($cout_pat, $cerr_pat, $test) = @_;
    my ($cout, $cerr) = &getOutput();
    if ($cout !~ $cout_pat) {
        diag("Output did not match standard out pattern:\n/$cout_pat/:\n$cout");
    }
    ok ($cout =~ $cout_pat, $test . " - stdout");
    if ($cerr !~ $cerr_pat) {
        diag("Stderr output did not match standard err pattern:\n"
           . "/$cerr_pat/:\n$cerr");
    }
    ok ($cerr =~ $cerr_pat, $test . " - stdout");
}

sub getOutput {
    my $cout = &getStdOut();
    my $cerr = &getStdErr();
    &closeStreams();
    &openStreams();
    return ($cout, $cerr);
}

sub openStreams {
    open ($stdout, ">/tmp/vespaclient.perltest.stdout.log")
            or die "Failed to create tmp file for stdout";
    open ($stderr, ">/tmp/vespaclient.perltest.stderr.log")
            or die "Failed to create tmp file for stdout";
    Yahoo::Vespa::ConsoleOutput::initialize($stdout, $stderr, $USE_COLORS);
}

sub closeStreams {
    close $stdout;
    close $stderr;
    system("rm /tmp/vespaclient.perltest.stdout.log");
    system("rm /tmp/vespaclient.perltest.stderr.log");
}

sub getStdOut {
    my $data = `cat /tmp/vespaclient.perltest.stdout.log`;
    if (!defined $data) { $data = ''; }
    return $data;
}

sub getStdErr {
    my $data = `cat /tmp/vespaclient.perltest.stderr.log`;
    if (!defined $data) { $data = ''; }
    return $data;
}

sub diff {
    my ($expected, $actual) = @_;
    if ($expected eq $actual) { return; }
    &writeToFile("/tmp/vespaclient.perltest.expected", $expected);
    &writeToFile("/tmp/vespaclient.perltest.actual", $actual);
    print "Output differs. Diff:\n";
    system("diff -u /tmp/vespaclient.perltest.expected "
                 . "/tmp/vespaclient.perltest.actual");
    system("rm -f /tmp/vespaclient.perltest.expected");
    system("rm -f /tmp/vespaclient.perltest.actual");
}

sub writeToFile {
    my ($file, $data) = @_;
    my $fh;
    open ($fh, ">$file") or die "Failed to open temp file for writing.";
    print $fh $data;
    close $fh;
}
