# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;
use strict;
use warnings;

BEGIN { use_ok( 'Yahoo::Vespa::Bin::GetClusterState' ); }
require_ok( 'Yahoo::Vespa::Bin::GetClusterState' );

use TestUtils::VespaTest;
use Yahoo::Vespa::Mocks::ClusterControllerMock;
use Yahoo::Vespa::Mocks::VespaModelMock;

# Set which application is called on assertRun / assertRunMatches calls
setApplication( \&getClusterState );

useColors(0);

&testSimple();
&testSyntaxPage();
&testClusterDown();

done_testing();

exit(0);

sub testSimple {
    my $stdout = <<EOS;

Cluster books:
books/storage/0: down
books/storage/1: up

Cluster music:
music/distributor/0: down
music/distributor/1: up
music/storage/0: retired
EOS
    assertRun("Default - no arguments", "", 0, $stdout, "");
}

sub testClusterDown {
    Yahoo::Vespa::Mocks::ClusterControllerMock::setClusterDown();
    Yahoo::Vespa::ClusterController::init();
    Yahoo::Vespa::Bin::GetClusterState::init();
    my $stdout = <<EOS;

Cluster books:
books/storage/0: down
books/storage/1: up

Cluster music is down. Too few nodes available.
music/distributor/0: down
music/distributor/1: up
music/storage/0: retired
EOS
    assertRun("Music cluster down", "", 0, $stdout, "");
}

sub testSyntaxPage {
    my $stdout = <<EOS;
EOS
    my $pat = qr/^Get the cluster state of a given cluster.*Usage:.*GetClusterState.*Options.*--help.*/s;
    assertRunMatches("Syntax page", "--help", 1, $pat, qr/^$/);
}
