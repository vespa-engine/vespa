# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package Yahoo::Vespa::Bin::GetClusterState;

use strict;
use warnings;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::ClusterController;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::ContentNodeSelection;
use Yahoo::Vespa::Utils;
use Yahoo::Vespa::VespaModel;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
        getClusterState
    );
}

my %cluster_states;

return &init();

sub init {
    %cluster_states = ();
    return 1;
}

# Run the get node state tool
sub getClusterState { # (Command line arguments)
    my ($argsref) = @_;
    &handleCommandLine($argsref);
    detectClusterController();
    &showSettings();
    &showNodeStates();
}

# Parse command line arguments
sub handleCommandLine { # (Command line arguments)
    my ($args) = @_;
    my $description = <<EOS;
Get the cluster state of a given cluster.

EOS
    $description =~ s/(\S)\n(\S)/$1 $2/gs;
    chomp $description;

    setProgramDescription($description);
    Yahoo::Vespa::ContentNodeSelection::registerCommandLineArguments(
            NO_LOCALHOST_CONSTRAINT | CLUSTER_ONLY_LIMITATION);
    Yahoo::Vespa::VespaModel::registerCommandLineArguments();
    handleCommandLineArguments($args);
}

# Show what settings this tool is running with (if verbosity is high enough)
sub showSettings { # ()
    &Yahoo::Vespa::ClusterController::showSettings();
}

# Print all state we want to show for this request
sub showNodeStates { # ()
    
    Yahoo::Vespa::ContentNodeSelection::visit(\&showNodeStateForNode);
}

# Get the node state from cluster controller, unless already cached
sub getStateForNode { # (Type, Index, Cluster)
    my ($type, $index, $cluster) = @_;
    if (!exists $cluster_states{$cluster}) {
        my $state = getContentClusterState($cluster);
        $cluster_states{$cluster} = $state;
        if ($state->globalState eq "up") {
            printResult "\nCluster $cluster:\n";
        } else {
            printResult "\nCluster $cluster is " . COLOR_ERR
                    . $state->globalState . COLOR_RESET
                    . ". Too few nodes available.\n";
        }
    }
    return $cluster_states{$cluster}->$type->{$index};
}

# Print all states for a given node
sub showNodeStateForNode { # (Service, Index, NodeState, Model, ClusterName)
    my ($info) = @_;
    my ($cluster, $type, $index) = (
            $$info{'cluster'}, $$info{'type'}, $$info{'index'});
    my $nodestate = &getStateForNode($type, $index, $cluster);
    defined $nodestate or confess "No nodestate for $type $index $cluster";
    my $generated = $nodestate->generated;
    my $id = $cluster . "/";
    if (defined $nodestate->group) {
        $id .= $nodestate->group;
    }
    my $msg = "$cluster/$type/$index: ";
    if ($generated->state ne 'up') {
        $msg .= COLOR_ERR;
    }
    $msg .= $generated->state;
    if ($generated->state ne 'up') {
        $msg .= COLOR_RESET;
    }
    # TODO: Make the Cluster Controller always populate the reason for the
    # generated state.  Until then we'll avoid printing it to avoid confusion.
    # Use vespa-get-node-state to see the reasons on generated, user, and unit.
    #
    # if (length $generated->reason > 0) {
    #     $msg .= ': ' . $generated->reason;
    # }
    printResult $msg . "\n";
}

# ClusterState(Version: 7, Cluster state: Up, Distribution bits: 1) {
#   Group 0: mygroup. 1 node [0] {
#     All nodes in group up and available.
#   }
# }

# ClusterState(Version: 7, Cluster state: Up, Distribution bits: 1) {
#   Group 0: mygroup. 1 node [0] {
#     storage.0: Retired: foo
#   }
# }
