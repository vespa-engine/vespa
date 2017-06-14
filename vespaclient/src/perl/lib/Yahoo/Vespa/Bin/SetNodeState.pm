# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package Yahoo::Vespa::Bin::SetNodeState;

use strict;
use warnings;
use Yahoo::Vespa::ClusterController;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::ContentNodeSelection;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::Utils;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
        setNodeState
    );
}

our $wanted_state;
our $wanted_state_description;
our $nodes_attempted_set;
our $success;

return 1;

# Run the set node state tool
sub setNodeState { # (Command line arguments)
    my ($argsref) = @_;
    &handleCommandLine($argsref);
    detectClusterController();
    &showSettings();
    &execute();
}

# Parse command line arguments
sub handleCommandLine { # (Command line arguments)
    my ($args) = @_;
    my $description = <<EOS;
Set the user state of a node. This will set the generated state to the user
state if the user state is "better" than the generated state that would have
been created if the user state was up. For instance, a node that is currently
in initializing state can be forced into down state, while a node that is
currently down can not be forced into retired state, but can be forced into
maintenance state.
EOS
    $description =~ s/(\S)\n(\S)/$1 $2/gs;
    chomp $description;

    setProgramDescription($description);

    setArgument(\$wanted_state, "Wanted State",
                "User state to set. This must be one of "
              . "up, down, maintenance or retired.",
                OPTION_REQUIRED);
    setArgument(\$wanted_state_description, "Description",
                "Give a reason for why you are altering the user state, which "
              . "will show up in various admin tools. (Use double quotes to "
              . "give a reason with whitespace in it)");

    Yahoo::Vespa::ContentNodeSelection::registerCommandLineArguments();
    Yahoo::Vespa::VespaModel::registerCommandLineArguments();
    handleCommandLineArguments($args);

    if (!Yahoo::Vespa::ContentNodeSelection::validateCommandLineArguments(
             $wanted_state)) {
        exitApplication(1);
    }
}

# Show what settings this tool is running with (if verbosity is high enough)
sub showSettings { # ()
    Yahoo::Vespa::ClusterController::showSettings();
}

# Sets the node state
sub execute { # ()
    $success = 1;
    $nodes_attempted_set = 0;
    Yahoo::Vespa::ContentNodeSelection::visit(\&setNodeStateForNode);
    if ($nodes_attempted_set == 0) {
        printWarning("Attempted setting of user state for no nodes");
        exitApplication(1);
    }
    if (!$success) {
	exitApplication(1);
    }
}

sub setNodeStateForNode {
    my ($info) = @_;
    my ($cluster, $type, $index) = (
            $$info{'cluster'}, $$info{'type'}, $$info{'index'});
    $success &&= setNodeUserState($cluster, $type, $index, $wanted_state,
                                  $wanted_state_description);
    ++$nodes_attempted_set;
}
