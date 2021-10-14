# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package Yahoo::Vespa::Bin::GetNodeState;

use strict;
use warnings;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::ClusterController;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::ContentNodeSelection;
use Yahoo::Vespa::Utils;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
        getNodeState
    );
}

our $resultdesc;
our %cluster_states;

return 1;

# Run the get node state tool
sub getNodeState { # (Command line arguments)
    my ($argsref) = @_;
    &handleCommandLine($argsref);
    detectClusterController();
    &showSettings();
    &showNodeStates();
}

# Parse command line arguments
sub handleCommandLine { # (Command line arguments)
    my ($args) = @_;
    $resultdesc = <<EOS;
Shows the various states of one or more nodes in a Vespa Storage cluster.
There exist three different type of node states. They are:

  Unit state      - The state of the node seen from the cluster controller.
  User state      - The state we want the node to be in. By default up. Can be
                    set by administrators or by cluster controller when it
                    detects nodes that are behaving badly.
  Generated state - The state of a given node in the current cluster state.
                    This is the state all the other nodes know about. This
                    state is a product of the other two states and cluster
                    controller logic to keep the cluster stable.
EOS
    $resultdesc =~ s/\s*\n(\S.)/ $1/gs;
    chomp $resultdesc;
    my $description = <<EOS;
Retrieve the state of one or more storage services from the fleet controller.
Will list the state of the locally running services, possibly restricted to
less by options.

$resultdesc

EOS
    $description =~ s/(\S)\n(\S)/$1 $2/gs;
    chomp $description;

    setProgramDescription($description);
    Yahoo::Vespa::ContentNodeSelection::registerCommandLineArguments();
    Yahoo::Vespa::VespaModel::registerCommandLineArguments();
    handleCommandLineArguments($args);
}

# Show what settings this tool is running with (if verbosity is high enough)
sub showSettings { # ()
    &Yahoo::Vespa::ClusterController::showSettings();
    &Yahoo::Vespa::ContentNodeSelection::showSettings();
}

# Print all state we want to show for this request
sub showNodeStates { # ()
    printInfo $resultdesc . "\n";
    Yahoo::Vespa::ContentNodeSelection::visit(\&showNodeStateForNode);
}

# Get the node state from cluster controller, unless already cached
sub getStateForNode { # (Type, Index, Cluster)
    my ($type, $index, $cluster) = @_;
    if (!exists $cluster_states{$cluster}) {
        $cluster_states{$cluster} = getContentClusterState($cluster);
    }
    return $cluster_states{$cluster}->$type->{$index};
}

# Print all states for a given node
sub showNodeStateForNode { # (Service, Index, NodeState, Model, ClusterName)
    my ($info) = @_;
    my ($cluster, $type, $index) = (
            $$info{'cluster'}, $$info{'type'}, $$info{'index'});
    printResult "\n$cluster/$type.$index:\n";
    my $nodestate = &getStateForNode($type, $index, $cluster);
    printState('Unit', $nodestate->unit);
    printState('Generated', $nodestate->generated);
    printState('User', $nodestate->user);
}

# Print the value of a single state type for a node
sub printState { # (State name, State)
    my ($name, $state) = @_;
    if (!defined $state) {
        printResult $name . ": UNKNOWN\n";
    } else {
        my $msg = $name . ": ";
        if ($state->state ne 'up') {
            $msg .= COLOR_ERR;
        }
        $msg .= $state->state;
        if ($state->state ne 'up') {
            $msg .= COLOR_RESET;
        }
        $msg .= ": " . $state->reason . "\n";
        printResult $msg;
    }
}
