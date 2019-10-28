# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Handles Rest API requests to State Rest API in cluster controller, making
# wanted data programmatically available.
#
package Yahoo::Vespa::ClusterController;

use strict;
use warnings;
use Class::Struct;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::ClusterState;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Http;
use Yahoo::Vespa::Json;
use Yahoo::Vespa::Utils;
use Yahoo::Vespa::VespaModel;

BEGIN { # - Exports and aliases for the module
    use base 'Exporter';
    our $VERSION = '1.0';
    our @EXPORT = qw(
        detectClusterController
        getContentClusterState
        setNodeUserState
    ); # Exported unless specifically left out by user
        # Alias namespaces
    *VespaModel:: = *Yahoo::Vespa::VespaModel:: ;
    *Http:: = *Yahoo::Vespa::Http:: ;
    *Json:: = *Yahoo::Vespa::Json:: ;
}

struct( ClusterController => {
    index => '$', # Logical index of the cluster controller
    host => '$', # Host on which cluster controller runs
    port => '$' # Port where cluster controller is available
});

my %CACHED_CLUSTER_STATES;
my @CLUSTER_CONTROLLERS;

return &init();

########################## Default exported functions ########################

sub init {
    %CACHED_CLUSTER_STATES = ();
    @CLUSTER_CONTROLLERS = ();
    return 1;
}

sub detectClusterController { # ()
    if (scalar @CLUSTER_CONTROLLERS == 0) {
        use Yahoo::Vespa::VespaModel;
        printDebug "Attempting to auto-detect cluster controller location\n";
        my $sockets = VespaModel::getSocketForService(
                type => 'container-clustercontroller', tag => 'state');
        foreach my $sock (sort { $a->{'index'} <=> $b->{'index'} } @$sockets) {
            my $cc = new ClusterController;
            $cc->index($sock->{'index'});
            $cc->host($sock->{'host'});
            $cc->port($sock->{'port'});
            push @CLUSTER_CONTROLLERS, $cc;
        }
        if (scalar @$sockets == 0) {
            my $oldVal = enableAutomaticLineBreaks(0);
            printSpam dumpStructure(VespaModel::get());
            enableAutomaticLineBreaks($oldVal);
            printError "Failed to detect cluster controller to talk to. "
                     . "Resolve issue that failed automatic detection or "
                     . "provide cluster controller socket through command "
                     . "line options. (See --help)\n";
            exitApplication(1);
        }
        &showSettings();
        printSpam "Content of vespa model inspected to find cluster "
                . "controller:\n";
        my $oldVal = enableAutomaticLineBreaks(0);
        printSpam dumpStructure(VespaModel::get());
        enableAutomaticLineBreaks($oldVal);
    }
}
sub setNodeUserState { # (ClusterName, NodeType, Index, State, Reason, NoWait, SafeMode)
    my ($cluster, $service, $index, $state, $reason, $no_wait, $safe_mode) = @_;
    my @params = ();
    my @headers = (
        'Content-Type' => 'application/json'
    );
    $state =~ tr/A-Z/a-z/;
    $state =~ /(?:up|down|maintenance|retired)$/
            or confess "Invalid state '$state' attempted set.\n";
    if (!defined $reason) {
        $reason = "";
    }
    my $request = {
        "state" => {
            "user" => {
                "state" => $state,
                "reason" => $reason
            }
        }
    };
    if ($no_wait) {
        $request->{'response-wait'} = 'no-wait';
    }
    if ($safe_mode) {
        $request->{'condition'} = 'safe';
    }
    my $content = Json::encode($request);

    my $path = &getPathToNode($cluster, $service, $index);
    my %response = &requestCC('POST', $path, \@params, $content, \@headers);
    if (defined $response{'all'}) { printSpam $response{'all'}; }
    printDebug $response{'code'} . " " . $response{'status'} . "\n";
    printInfo exists($response{'content'}) ? $response{'content'} : '';
    if ($response{'code'} >= 200 && $response{'code'} < 300) {
        printResult "$response{'status'}\n";
        return 1
    } else {
        printWarning "Failed to set node state for node "
                   . "$cluster/$service/$index: "
                   . "$response{'code'} $response{'status'}\n";
        return 0
    }
}
sub getContentClusterState { # (ClusterName) -> ClusterState
    my ($cluster) = @_;
    if (!exists $CACHED_CLUSTER_STATES{$cluster}) {
        $CACHED_CLUSTER_STATES{$cluster} = &fetchContentClusterState($cluster);
    }
    return $CACHED_CLUSTER_STATES{$cluster};
}

######################## Externally usable functions #######################

sub getClusterControllers { # ()
    return \@CLUSTER_CONTROLLERS;
}
sub showSettings { # ()
    printDebug "Cluster controllers:\n";
    foreach my $cc (@CLUSTER_CONTROLLERS) {
        printDebug "  " . $cc->index . ": "
                . $cc->host . ":" . $cc->port . "\n";
    }
}

############## Utility functions - Not intended for external use #############

sub fetchContentClusterState { # (ClusterName) -> ClusterState
    my ($cluster) = @_;
    my @params = (
        'recursive' => 'true'
    );
    my %response = &getCC("/cluster/v2/$cluster/", \@params);
    if ($response{'code'} != 200) {
        printError "Failed to fetch cluster state of content cluster "
                 . "'$cluster':\n" . $response{'all'} . "\n";
        exitApplication(1);
    }
    my $json = Json::parse($response{'content'});
    my $result = new ClusterState;
    &fillInGlobalState($cluster, $result, $json);
    &fillInNodes($result, 'distributor',
                 &getJsonValue($json, ['service', 'distributor', 'node']));
    &fillInNodes($result, 'storage',
                 &getJsonValue($json, ['service', 'storage', 'node']));
    return $result;
}
sub fillInGlobalState { # (ClusterName, StateToFillIn, JsonToParse)
    my ($cluster, $state, $json) = @_;
    my $e = &getJsonValue($json, ['state', 'generated', 'state']);
    if (defined $e) {
        $state->globalState($e);
        if (!Yahoo::Vespa::ClusterState::legalState($state->globalState())) {
            printWarning "Illegal global cluster state $e found.\n";
        }
    } else {
        printDebug dumpStructure($json) . "\n";
        printWarning "Found no global cluster state\n";
    }
}
sub getPathToNode { # (ClusterName, NodeType, Index) 
    my ($cluster, $service, $index) = @_;
    return "/cluster/v2/$cluster/$service/$index";
}
sub listContentClusters { # () -> (ContentClusterName, ...)
    my %result = &getCC("/cluster/v2/");
    if ($result{'code'} != 200) {
        printError "Failed to fetch list of content clusters:\n"
                 . $result{'all'} . "\n";
        exitApplication(1);
    }
    my $json = Json::parse($result{'content'});
    return keys %{ $json->{'cluster'} };
}
sub fillInNodes { # (StateToFillIn, ServiceType, json)
    my ($state, $service, $json) = @_;
    foreach my $index (%{ $json }) {
        my $node = new Node;
        &parseNode($node, $json->{$index});
        $state->$service($index, $node);
    }
}
sub parseNode { # (StateToFillIn, JsonToParse)
    my ($node, $json) = @_;
    my $group = &getJsonValue($json, ['attributes', 'hierarchical-group']);
    if (defined $group && $group =~ /^[^\.]*\.(.*)$/) {
        $node->group($1);
    }
    parseState($node, $json, 'unit');
    parseState($node, $json, 'generated');
    parseState($node, $json, 'user');
    my $partitions = $json->{'partition'};
    if (defined $partitions) {
        foreach my $index (%{ $json->{'partition'} }) {
            my $partition = new Partition;
            parsePartition($partition, $json->{'partition'}->{$index});
            $node->partition($index, $partition);
        }
    }
}
sub parsePartition { # (StateToFillIn, JsonToParse)
    my ($partition, $json) = @_;
    my $buckets = &getJsonValue($json, ['metrics', 'bucket-count']);
    my $doccount = &getJsonValue($json, ['metrics', 'unique-document-count']);
    my $size = &getJsonValue($json, ['metrics', 'unique-document-total-size']);
    $partition->bucketcount($buckets);
    $partition->doccount($doccount);
    $partition->totaldocsize($size);
}
sub parseState { # (StateToFillIn, JsonToParse, StateType)
    my ($node, $json, $type) = @_;
    my $value = &getJsonValue($json, ['state', $type, 'state']);
    my $reason = &getJsonValue($json, ['state', $type, 'reason']);
    if (defined $value) {
        my $state = new State;
        $state->state($value);
        $state->reason($reason);
        $state->source($type);
        $node->$type($state);
    }
}
sub getJsonValue { # (json, [ keys ])
    my ($json, $keys) = @_;
    foreach my $key (@$keys) {
        if (!defined $json) { return; }
        $json = $json->{$key};
    }
    return $json;
}
sub getCC { # (Path, Params, Headers) -> Response
    my ($path, $params, $headers) = @_;
    return requestCC('GET', $path, $params, undef, $headers);
}
sub requestCC { # (Type, Path, Params, Content, Headers) -> Response
    my ($type, $path, $params, $content, $headers) = @_;
    my %response;
    foreach my $cc (@CLUSTER_CONTROLLERS) {
        %response = Http::request($type, $cc->host, $cc->port, $path,
                                  $params, $content, $headers);
        if ($response{'code'} == 200) {
            return %response;
        } elsif ($response{'code'} == 307) {
            my %headers = $response{'headers'};
            my $masterlocation = $headers{'Location'};
            if (defined $masterlocation) {
                if ($masterlocation =~ /http:\/\/([^\/:]+):(\d+)\//) {
                    my ($host, $port) = ($1, $2);
                    return Http::request($type, $host, $port, $path,
                                      $params, $content, $headers);
                } else {
                    printError("Unhandled relocaiton URI '$masterlocation'.");
                    exitApplication(1);
                }
            }
        }
    }
    return %response;
}
