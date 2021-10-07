# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Vespa model
#
# Make vespa model information available for tools. To for instance get an
# overview of where services are running.
#
# Possible improvements:
#
#   - Depending on config Rest API and config server might be better than
#     depending on vespa-get-config tool and config format.
#   - Support direct communication with config server if config proxy is not
#     running (unless vespa-get-config does that for us)
#   - Support specifying config server, to be able to run tool external from the
#     vespa system to talk to.
#   - Return a list of all matching sockets instead of first found.
#   - Be able to specify a set of port tags needed for a match.
#

package Yahoo::Vespa::VespaModel;

use strict;
use warnings;
use Yahoo::Vespa::ArgParser;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Utils;

my $RETRIEVE_MODEL_CONFIG; # Allow unit tests to switch source of config info
my $MODEL;
my $CONFIG_SERVER_HOST;
my $CONFIG_SERVER_PORT;
my $CONFIG_REQUEST_TIMEOUT;

&initialize();

return 1;

######################## Externally usable functions #######################

sub registerCommandLineArguments { # ()
    setOptionHeader("Config retrieval options:");
    setHostOption(
            ['config-server'],
            \$CONFIG_SERVER_HOST,
            'Host name of config server to query');
    setPortOption(
            ['config-server-port'],
            \$CONFIG_SERVER_PORT,
            'Port to connect to config server on');
    setFloatOption(
            ['config-request-timeout'],
            \$CONFIG_REQUEST_TIMEOUT,
            'Timeout of config request');
}

sub visitServices { # (Callback)
    my $model = &get();
    my ($callback) = @_;
    my @services = @{ &getServices($model); };
    foreach my $service (sort serviceOrder @services) {
        &$callback($service);
    }
}

sub getServices {
    my $model = &get();
    my @result;
    foreach my $hostindex (keys %{ $$model{'hosts'} }) {
        my $host = ${ $$model{'hosts'} }{ $hostindex };
        foreach my $serviceindex (keys %{ $$host{'services'} }) {
            my $service = ${ $$host{'services'} }{ $serviceindex };
            my %info = (
                'name' => $$service{'name'},
                'type' => $$service{'type'},
                'configid' => $$service{'configid'},
                'cluster' => $$service{'clustername'},
                'host' => $$host{'name'}
            );
            if (exists $$service{'index'}) {
                $info{'index'} = $$service{'index'};
            }
            push @result, \%info;
        }
    }
    return \@result;
}

# Get socket for given service matching given conditions (Given as a hash)
# Legal conditions:
#   type - Service type
#   tag - Port tag
#   index - Service index
#   clustername - Name of cluster.
# Example: getSocketForService( 'type' => 'distributor', 'index' => 3,
#                               'tag' => 'http', 'tag' => 'state' );
sub getSocketForService { # (Conditions) => [{host=>$,port=>$,index=>$}...]
    my $model = &get();
    my $conditions = \@_;
    printDebug "Looking at model to find socket for a service.\n";
    &validateConditions($conditions);
    my $hosts = $$model{'hosts'};
    if (!defined $hosts) { return; }
    my @results;
    foreach my $hostindex (keys %$hosts) {
        my $host = $$hosts{$hostindex};
        my $services = $$host{'services'};
        if (defined $services) {
            printSpam "Searching services on host $$host{'name'}\n";
            foreach my $serviceindex (keys %$services) {
                my $service = $$services{$serviceindex};
                my $type = $$service{'type'};
                my $cluster = $$service{'clustername'};
                if (!&serviceTypeMatchConditions($conditions, $type)) {
                    printSpam "Unwanted service '$type'.\n";
                    next;
                }
                if (!&indexMatchConditions($conditions, $$service{'index'})) {
                    printSpam "Unwanted index '$$service{'index'}'.\n";
                    next;
                }
                if (!&clusterNameMatchConditions($conditions, $cluster)) {
                    printSpam "Unwanted index '$$service{'index'}'.\n";
                    next;
                }
                my $ports = $$service{'ports'};
                if (defined $ports) {
                    my $resultcount = 0;
                    foreach my $portindex (keys %$ports) {
                        my $port = $$ports{$portindex};
                        my $tags = $$port{'tags'};
                        if (defined $tags) {
                            if (!&tagsMatchConditions($conditions, $tags)) {
                                next;
                            }
                        }
                        push @results, { 'host' => $$host{'name'},
                                         'port' => $$port{'number'},
                                         'index' => $$service{'index'} };
                        ++$resultcount;
                    }
                    if ($resultcount == 0) {
                        printSpam "No ports with acceptable tags found. "
                                . "Ignoring $type.$$service{'index'}\n";
                    }
                } else {
                    printSpam "No ports defined. "
                            . "Ignoring $type.$$service{'index'}\n";
                }
            }
        }
    }
    return \@results;
}

############## Utility functions - Not intended for external use #############

sub initialize { # ()
    $RETRIEVE_MODEL_CONFIG = \&retrieveModelConfigDefault;
}
sub setModelRetrievalFunction { # (Function)
    $RETRIEVE_MODEL_CONFIG = $_[0];
}
sub retrieveModelConfigDefault { # ()
    my $VESPA_HOME= $ENV{'VESPA_HOME'};
    my $cmd = ${VESPA_HOME} . '/bin/vespa-get-config -l -n cloud.config.model -i admin/model';

    if (defined $CONFIG_REQUEST_TIMEOUT) {
        $cmd .= " -w $CONFIG_REQUEST_TIMEOUT";
    }

    if (!defined $CONFIG_SERVER_HOST) {
        my $temp = `${VESPA_HOME}/bin/vespa-print-default configservers`;
        chomp($temp);
        $CONFIG_SERVER_HOST = $temp;
    }

    if (!defined $CONFIG_SERVER_PORT) {
        my $temp = `${VESPA_HOME}/bin/vespa-print-default configserver_rpc_port`;
        chomp($temp);
        $CONFIG_SERVER_PORT = $temp;
    }
    $cmd .= " -p $CONFIG_SERVER_PORT";

    my $errors = "";
    foreach my $cfshost (split(' ', $CONFIG_SERVER_HOST)) {
        my $hostcmd = $cmd . " -s $cfshost";

        printDebug "Fetching model config '$hostcmd'.\n";
        my @data = `$hostcmd 2>&1`;
        if ($? != 0 || join(' ', @data) =~ /^error/) {
            $errors .= "Failed to get model config from config command line tool:\n"
                 . "Command: $hostcmd\n"
                 . "Exit code: $?\n"
                 . "Output: " . join("\n", @data) . "\n";
        } else {
            return @data;
        }
    }
    printError $errors;
    exitApplication(1);
}
sub fetch { # ()
    my @data = &$RETRIEVE_MODEL_CONFIG();
    $MODEL = &parseConfig(@data);
    return $MODEL;
}
sub get { # ()
    if (!defined $MODEL) {
        return &fetch();
    }
    return $MODEL;
}
sub validateConditions { # (ConditionArrayRef)
    my ($condition) = @_;
    for (my $i=0, my $n=scalar @$condition; $i<$n; $i += 2) {
        if ($$condition[$i] !~ /^(type|tag|index|clustername)$/) {
            printError "Invalid socket for service condition "
                     . "'$$condition[$i]' given.\n";
            exitApplication(1);
        }
    }
}
sub tagsMatchConditions { # (Condition, TagList) -> Bool
    my ($condition, $taglist) = @_;
    my %tags = map { $_ => 1 } @$taglist;
    for (my $i=0, my $n=scalar @$condition; $i<$n; $i += 2) {
        if ($$condition[$i] eq 'tag' && !exists $tags{$$condition[$i + 1]}) {
            return 0;
        }
    }
    return 1;
}
sub serviceTypeMatchConditions { # (Condition, ServiceType) -> Bool
    my ($condition, $type) = @_;
    for (my $i=0, my $n=scalar @$condition; $i<$n; $i += 2) {
        if ($$condition[$i] eq 'type' && $$condition[$i + 1] ne $type) {
            return 0;
        }
    }
    return 1;
}
sub clusterNameMatchConditions { # (Condition, ClusterName) -> Bool
    my ($condition, $cluster) = @_;
    for (my $i=0, my $n=scalar @$condition; $i<$n; $i += 2) {
        if ($$condition[$i] eq 'clustername' && $$condition[$i + 1] ne $cluster)
        {
            return 0;
        }
    }
    return 1;
}
sub indexMatchConditions { # (Condition, Index) -> Bool
    my ($condition, $index) = @_;
    for (my $i=0, my $n=scalar @$condition; $i<$n; $i += 2) {
        if ($$condition[$i] eq 'index' && $$condition[$i + 1] ne $index) {
            return 0;
        }
    }
    return 1;
}
sub parseConfig { # ()
    my $model = {};
    printDebug "Parsing vespa model raw config to create object tree\n";
    my $autoLineBreak = enableAutomaticLineBreaks(0);
    foreach my $line (@_) {
        chomp $line;
        printSpam "Parsing line '$line'\n";
        if ($line =~ /^hosts\[(\d+)\]\.(([a-z]+).*)$/) {
            my ($hostindex, $tag, $rest) = ($1, $3, $2);
            my $host = &getHost($hostindex, $model);
            if ($tag eq 'services') {
                &parseService($host, $rest);
            } else {
                &parseValue($host, $rest);
            }
        }
    }
    enableAutomaticLineBreaks($autoLineBreak);
    return $model;
}
sub parseService { # (Host, Line)
    my ($host, $line) = @_;
    if ($line =~ /^services\[(\d+)\].(([a-z]+).*)$/) {
        my ($serviceindex, $tag, $rest) = ($1, $3, $2);
        my $service = &getService($serviceindex, $host);
        if ($tag eq 'ports') {
            &parsePort($service, $rest);
        } else {
            &parseValue($service, $rest);
        }
    }
}
sub parsePort { # (Service, Line)
    my ($service, $line) = @_;
    if ($line =~ /^ports\[(\d+)\].(([a-z]+).*)$/) {
        my ($portindex, $tag, $rest) = ($1, $3, $2);
        my $port = &getPort($portindex, $service);
        &parseValue($port, $rest);
    }
}
sub parseValue { # (Entity, Line)
    my ($entity, $line) = @_;
    $line =~ /^(\S+) (?:\"(.*)\"|(\d+))$/ or confess "Unexpected line '$line'.";
    my ($id, $string, $number) = ($1, $2, $3);
    if ($id eq 'tags' && defined $string) {
        my @tags = split(/\s+/, $string);
        $$entity{$id} = \@tags;
    } elsif (defined $string) {
        $$entity{$id} = $string;
    } else {
        defined $number or confess "Should not happen";
        $$entity{$id} = $number;
    }
}
sub getEntity { # (Type, Index, ParentEntity)
    my ($type, $index, $parent) = @_;
    if (!exists $$parent{$type}) {
        $$parent{$type} = {};
    }
    my $list = $$parent{$type};
    if (!exists $$list{$index}) {
        $$list{$index} = {};
    }
    return $$list{$index};
}
sub getHost { # (Index, Model)
    return &getEntity('hosts', $_[0], $_[1]);
}
sub getService { # (Index, Host)
    return &getEntity('services', $_[0], $_[1]);
}
sub getPort { # (Index, Service)
    return &getEntity('ports', $_[0], $_[1]);
}
sub serviceOrder {
    if ($a->{'cluster'} ne $b->{'cluster'}) {
        return $a->{'cluster'} cmp $b->{'cluster'};
    }
    if ($a->{'type'} ne $b->{'type'}) {
        return $a->{'type'} cmp $b->{'type'};
    }
    if ($a->{'index'} != $b->{'index'}) {
        return $a->{'index'} <=> $b->{'index'};
    }
    if ($a->{'host'} ne $b->{'host'}) {
        return $a->{'host'} cmp $b->{'host'};
    }
    if ($a->{'configid'} ne $b->{'configid'}) {
        return $a->{'configid'} cmp $b->{'configid'};
    }
    confess "Unsortable elements: " . dumpStructure($a) . "\n"
                                    . dumpStructure($b) . "\n";
}

