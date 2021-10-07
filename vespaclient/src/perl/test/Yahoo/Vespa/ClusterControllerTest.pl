# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;
use Data::Dumper;

BEGIN { use_ok( 'Yahoo::Vespa::ClusterController' ); }
require_ok( 'Yahoo::Vespa::ClusterController' );

use TestUtils::OutputCapturer;
use Yahoo::Vespa::Mocks::ClusterControllerMock;
use Yahoo::Vespa::Mocks::VespaModelMock;

Yahoo::Vespa::ConsoleOutput::setVerbosity(0); # Squelch output when running test
detectClusterController();
Yahoo::Vespa::ConsoleOutput::setVerbosity(3);

my $cclist = Yahoo::Vespa::ClusterController::getClusterControllers();
is( scalar @$cclist, 1, "Cluster controllers detected" );
is( $$cclist[0]->host, 'testhost.yahoo.com', 'Host autodetected' );
is( $$cclist[0]->port, 19050, 'Port autodetected' );

is( join (' - ', Yahoo::Vespa::ClusterController::listContentClusters()),
    "music - books", 'Content clusters' );

my $state = getContentClusterState('music');

$Data::Dumper::Indent = 1;
# print Dumper($state);

is( $state->globalState, 'up', 'Generated state for music' );

is( $state->distributor->{'0'}->unit->state, 'up', 'Unit state for music' );
is( $state->distributor->{'1'}->unit->state, 'up', 'Unit state for music' );
is( $state->storage->{'0'}->unit->state, 'up', 'Unit state for music' );
is( $state->storage->{'1'}->unit->state, 'up', 'Unit state for music' );
is( $state->distributor->{'0'}->generated->state, 'down', 'Generated state' );
is( $state->distributor->{'1'}->generated->state, 'up', 'Generated state' );
is( $state->storage->{'0'}->generated->state, 'retired', 'Generated state' );
is( $state->storage->{'1'}->generated->state, 'up', 'Generated state' );
is( $state->distributor->{'0'}->user->state, 'down', 'User state' );
is( $state->distributor->{'1'}->user->state, 'up', 'User state' );
is( $state->storage->{'0'}->user->state, 'retired', 'User state' );
is( $state->storage->{'1'}->user->state, 'up', 'User state' );

is( $state->storage->{'1'}->unit->reason, 'Now reporting state U', 'Reason' );

done_testing();

exit(0);
