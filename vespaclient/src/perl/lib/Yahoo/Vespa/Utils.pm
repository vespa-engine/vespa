# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Some simple utilities to allow unit tests to mock behavior.
#

package Yahoo::Vespa::Utils;

use strict;
use warnings;
use Carp ();

BEGIN { # - Define exports from this module
    use base 'Exporter';
    our @EXPORT = qw(
        exitApplication
        getHostname
        confess
        assertNotUnitTest
        dumpStructure
    );
}

my $HOSTNAME;
my $EXIT_HANDLER;
my $IS_UNIT_TEST;

&initialize();

return 1;

########################## Default exported functions ########################

# Use this function to get hostname to allow unit test mocking for tests to be
# independent of computer they run on.
sub getHostname { # ()
    if (!defined $HOSTNAME) {
        &assertNotUnitTest();
        $HOSTNAME = `vespa-print-default hostname`;
        chomp $HOSTNAME;
    }
    return $HOSTNAME;
}

# Use instead of exit() to allow unit tests to mock the call to avoid aborting
sub exitApplication { #(ExitCode)
    if ($IS_UNIT_TEST && $EXIT_HANDLER == \&defaultExitHandler) {
        &confess("Exit handler not overridden in unit test");
    }
    &$EXIT_HANDLER(@_);
}

# Use instead of die to get backtrace when dieing
sub confess { # (Reason)
    Carp::confess(@_);
}

# Call for behavior that you want to ensure is not used in unit tests.
# Typically unit tests have to mock commands that for instance fetch host name
# or require that terminal is set etc. Unit tests use mocks for this. This
# command can be used in code, such that unit tests die if they reach the
# non-mocked code.
sub assertNotUnitTest { # ()
    if ($IS_UNIT_TEST) {
        confess "Unit tests should not reach here. Mock required. "
              . "Initialize mock";
    }
}

# Use to look at content of a perl struct.
sub dumpStructure { # (ObjTree) -> ReadableString
    my ($var) = @_;
    use Data::Dumper;
    local $Data::Dumper::Indent = 1;
    local $Data::Dumper::Sortkeys = 1;
    return Dumper($var);
}

################## Functions for unit tests to mock internals ################

sub initializeUnitTest { # (Hostname, ExitHandler)
    my ($host, $exitHandler) = @_;
    $IS_UNIT_TEST = 1;
    $HOSTNAME = $host;
    $EXIT_HANDLER = $exitHandler;
}

############## Utility functions - Not intended for external use #############

sub initialize { # ()
    $EXIT_HANDLER = \&defaultExitHandler;
}
sub defaultExitHandler { # ()
    my ($exitcode) = @_;
    exit($exitcode);
}
