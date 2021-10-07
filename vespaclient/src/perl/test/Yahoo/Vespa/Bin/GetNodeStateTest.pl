# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;
use strict;
use warnings;

BEGIN { use_ok( 'Yahoo::Vespa::Bin::GetNodeState' ); }
require_ok( 'Yahoo::Vespa::Bin::GetNodeState' );

use TestUtils::VespaTest;
use Yahoo::Vespa::Mocks::ClusterControllerMock;
use Yahoo::Vespa::Mocks::VespaModelMock;

useColors(0);

# Set which application is called on assertRun / assertRunMatches calls
setApplication( \&getNodeState );

&testSimple();
&testSyntaxPage();
&testRetired();

done_testing();

exit(0);

sub testSimple {
    my $stdout = <<EOS;
Shows the various states of one or more nodes in a Vespa Storage cluster. There
exist three different type of node states. They are:

  Unit state      - The state of the node seen from the cluster controller.
  User state      - The state we want the node to be in. By default up. Can be
                    set by administrators or by cluster controller when it
                    detects nodes that are behaving badly.
  Generated state - The state of a given node in the current cluster state.
                    This is the state all the other nodes know about. This
                    state is a product of the other two states and cluster
                    controller logic to keep the cluster stable.

books/storage.0:
Unit: down: Not in slobrok
Generated: down: Not seen
User: down: default

music/distributor.0:
Unit: up: Now reporting state U
Generated: down: Setting it down
User: down: Setting it down
EOS
    assertRun("Default - no arguments", "", 0, $stdout, "");
}

sub testRetired {
    setLocalHost("other.host.yahoo.com");
    my $stdout = <<EOS;

music/storage.0:
Unit: up: Now reporting state U
Generated: retired: Stop using
User: retired: Stop using
EOS
    assertRun("Other node", "-c music -t storage -i 0 -s", 0, $stdout, "");
}

sub testSyntaxPage {
    my $stdout = <<EOS;
EOS
    my $pat = qr/^Retrieve the state of one or more.*Usage:.*GetNodeState.*Options.*--help.*/s;
    assertRunMatches("Syntax page", "--help", 1, $pat, qr/^$/);
}
