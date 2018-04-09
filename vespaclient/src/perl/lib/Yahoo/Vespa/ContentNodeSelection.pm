# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# This module implements a way to select a subset of nodes from a Vespa
# application.
#

package Yahoo::Vespa::ContentNodeSelection;

use strict;
use warnings;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Utils;
use Yahoo::Vespa::VespaModel;

BEGIN { # - Declare exports and dependency aliases for module
    use base 'Exporter';
    our @EXPORT = qw(
        NO_LOCALHOST_CONSTRAINT
        CLUSTER_ONLY_LIMITATION
    );
        # Package aliases
    *VespaModel:: = *Yahoo::Vespa::VespaModel:: ;
}

my $CLUSTER;
my $NODE_TYPE;
my $INDEX;
my $FORCE = 0;
our $LOCALHOST;

use constant NO_LOCALHOST_CONSTRAINT => 1;
use constant CLUSTER_ONLY_LIMITATION => 2;

return 1;

######################## Externally usable functions #######################

sub registerCommandLineArguments { # (Flags)
    my ($flags) = @_;
    if (!defined $flags) { $flags = 0; }
    if (($flags & NO_LOCALHOST_CONSTRAINT) == 0) {
        $LOCALHOST = getHostname();
    } else {
        $LOCALHOST = undef;
    }
    if (($flags & CLUSTER_ONLY_LIMITATION) == 0) {
        setOptionHeader("Node selection options. By default, nodes running "
                      . "locally will be selected:");
    }
    setStringOption(
            ['c', 'cluster'],
            \$CLUSTER,
            'Cluster name of cluster to query. '
          . 'If unspecified, and vespa is installed on current node, '
          . 'information will be attempted auto-extracted');
    setFlagOption(
        ['f', 'force'],
        \$FORCE,
        'Force the execution of a dangerous command.');
    if (($flags & CLUSTER_ONLY_LIMITATION) == 0) {
        setStringOption(
                ['t', 'type'],
                \$NODE_TYPE,
                'Node type to query. This can either be \'storage\' or '
              . '\'distributor\'. If not specified, the operation will show '
              . 'state for all types.');
        setIntegerOption(
                ['i', 'index'],
                \$INDEX,
                'The node index to show state for. If not specified, all nodes '
              . 'found running on this host will be shown.');
    }
}
sub visit { # (Callback)
    my ($callback) = @_;
    printDebug "Visiting selected services: "
            . "Cluster " . (defined $CLUSTER ? $CLUSTER : 'undef')
            . " node type " . (defined $NODE_TYPE ? $NODE_TYPE : 'undef')
            . " index " . (defined $INDEX ? $INDEX : 'undef')
            . " localhost only ? " . ($LOCALHOST ? "true" : "false") . "\n";
    VespaModel::visitServices(sub {
        my ($info) = @_;
        $$info{'type'} = &convertType($$info{'type'});
        if (!&validType($$info{'type'})) { return; }
        if (defined $CLUSTER && $CLUSTER ne $$info{'cluster'}) { return; }
        if (defined $NODE_TYPE && $NODE_TYPE ne $$info{'type'}) { return; }
        if (defined $INDEX && $INDEX ne $$info{'index'}) { return; }
        if (!defined $INDEX && defined $LOCALHOST
            && $LOCALHOST ne $$info{'host'})
        {
            return;
        }
       # printResult "Ok $$info{'cluster'} $$info{'type'} $$info{'index'}\n";
        &$callback($info);
    });
}
sub showSettings { # ()
    printDebug "Visiting selected services: "
            . "Cluster " . (defined $CLUSTER ? $CLUSTER : 'undef')
            . " node type " . (defined $NODE_TYPE ? $NODE_TYPE : 'undef')
            . " index " . (defined $INDEX ? $INDEX : 'undef')
            . " localhost only ? " . ($LOCALHOST ? "true" : "false") . "\n";
}

sub validateCommandLineArguments { # (WantedState)
    my ($wanted_state) = @_;

    if (defined $NODE_TYPE) {
        if ($NODE_TYPE !~ /^(distributor|storage)$/) {
            printWarning "Invalid value '$NODE_TYPE' given for node type.\n";
            return 0;
        }
    }

    if (!$FORCE &&
        (!defined $NODE_TYPE || $NODE_TYPE eq "distributor") &&
        $wanted_state eq "maintenance") {
        printWarning "Setting the distributor to maintenance mode may have "
            . "severe consequences for feeding!\n"
            . "Please specify -t storage to only set the storage node to "
            . "maintenance mode, or -f to override this error.\n";
        return 0;
    }

    printDebug "Command line arguments validates ok\n";
    return 1;
}

sub hasClusterSelection {
    return defined $CLUSTER;
}

############## Utility functions - Not intended for external use #############

sub validType { # (ServiceType) -> Bool
    my ($type) = @_;
    return $type =~ /^(?:distributor|storage)$/;
}
sub convertType { # (ServiceType) -> Bool
    my ($type) = @_;
    if ($type eq 'storagenode') { return 'storage'; }
    return $type;
}

