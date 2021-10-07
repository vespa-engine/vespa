# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;
use strict;
use warnings;

BEGIN { use_ok( 'Yahoo::Vespa::Bin::SetNodeState' ); }
require_ok( 'Yahoo::Vespa::Bin::SetNodeState' );

use TestUtils::VespaTest;
use Yahoo::Vespa::Mocks::ClusterControllerMock;
use Yahoo::Vespa::Mocks::VespaModelMock;

# Set which application is called on assertRun / assertRunMatches calls
setApplication( \&setNodeState ); 

&testSimple();
&testSyntaxPage();
&testHelp();
&testDownState();
&testDownFailure();
&testDefaultMaintenanceFails();
&testForcedMaintenanceSucceeds();

done_testing();

exit(0);

sub testSimple {
    my $stdout = <<EOS;
Set user state for books/storage/0 to 'up' with reason ''
Set user state for music/distributor/0 to 'up' with reason ''
EOS
    assertRun("Default - Min arguments", "up", 0, $stdout, "");
}

sub testSyntaxPage {
    my $stdout = <<EOS;
EOS
    my $pat = qr/^Set the user state of a node.*Usage:.*SetNodeState.*Arguments:.*Options:.*--help.*/s;
    assertRunMatches("Syntax page", "--help", 1, $pat, qr/^$/);
}

sub testHelp {
    my $stdout = <<EOS;
Set the user state of a node. This will set the generated state to the user
state if the user state is "better" than the generated state that would have
been created if the user state was up. For instance, a node that is currently
in initializing state can be forced into down state, while a node that is
currently down can not be forced into retired state, but can be forced into
maintenance state.

Usage: SetNodeStateTest.pl [Options] <Wanted State> [Description]

Arguments:
 Wanted State : User state to set. This must be one of up, down, maintenance or
                retired.
 Description  : Give a reason for why you are altering the user state, which
                will show up in various admin tools. (Use double quotes to give
                a reason with whitespace in it)

Options:
 -h --help                : Show this help page.
 -v                       : Create more verbose output.
 -s                       : Create less verbose output.
 --show-hidden            : Also show hidden undocumented debug options.

Options related to operation visibility:
 -n --no-wait             : Do not wait for node state changes to be visible in
                            the cluster before returning.

Node selection options. By default, nodes running locally will be selected:
 -c --cluster             : Cluster name. If unspecified,
                            and vespa is installed on current node, information
                            will be attempted auto-extracted
 -f --force               : Force execution
 -t --type                : Node type - can either be 'storage' or
                            'distributor'. If not specified, the operation will
                            use state for both types.
 -i --index               : Node index. If not specified,
                            all nodes found running on this host will be used.

Config retrieval options:
 --config-server          : Host name of config server to query
 --config-server-port     : Port to connect to config server on
 --config-request-timeout : Timeout of config request
EOS

    assertRun("Help text", "-h", 1, $stdout, "");
}

sub testDownState {
    my $stdout = <<EOS;
Set user state for books/storage/0 to 'down' with reason 'testing'
Set user state for music/distributor/0 to 'down' with reason 'testing'
EOS
    assertRun("Down state", "down testing", 0, $stdout, "");
}

sub testDownFailure {
    $Yahoo::Vespa::Mocks::ClusterControllerMock::forceInternalServerError = 1;

    my $stderr = <<EOS;
Failed to set node state for node books/storage/0: 500 Internal Server Error
(forced)
EOS

    assertRun("Down failure", "--nocolors down testing", 1, "", $stderr);

    $Yahoo::Vespa::Mocks::ClusterControllerMock::forceInternalServerError = 0;
}

sub testDefaultMaintenanceFails {
    my $stderr = <<EOS;
Setting the distributor to maintenance mode may have severe consequences for
feeding!
Please specify -t storage to only set the storage node to maintenance mode, or
-f to override this error.
EOS

    assertRun("Default maintenance fails", "--nocolors maintenance testing",
              1, "", $stderr);
}

sub testForcedMaintenanceSucceeds {
    my $stdout = <<EOS;
Set user state for books/storage/0 to 'maintenance' with reason 'testing'
Set user state for music/distributor/0 to 'maintenance' with reason 'testing'
EOS

    assertRun("Forced maintenance succeeds", "-f maintenance testing",
              0, $stdout, "");
}
