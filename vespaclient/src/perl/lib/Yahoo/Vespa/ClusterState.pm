# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Defines structs to represent a cluster state
#
package Yahoo::Vespa::ClusterState;

use strict;
use warnings;
use Class::Struct;

struct( ClusterState => {
    globalState => '$', # A state primitive
    distributor => '%', # Index to Node map
    storage => '%'      # Index to Node map
});

struct( Node => {
    group => '$',         # Hierarchical group node belongs to
    unit => 'State',
    generated => 'State',
    user => 'State',
    partition => '%'
});

struct( Partition => {
    generated => 'State',
    bucketcount => '$',
    doccount => '$',
    totaldocsize => '$'
});

struct( State => {
    state => '$',     # A state primitive
    reason => '$',    # Textual reason for it to be set.
    timestamp => '$', # Timestamp of the time it got set.
    source => '$'     # What type of state is it (unit/generated/user)
});

return 1;

sub legalState { # (State) -> Bool
    my ($state) = @_;
    return ($state =~ /^(up|down|maintenance|retired|stopping|initializing)$/);
}

