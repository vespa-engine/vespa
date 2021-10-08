# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package Yahoo::Vespa::Mocks::VespaModelMock;

use strict;
use warnings;
use Yahoo::Vespa::VespaModel;

Yahoo::Vespa::VespaModel::setModelRetrievalFunction(\&getModelConfig);

our $defaultModelConfig = <<EOS;
hosts[0].name "testhost.yahoo.com"
hosts[0].services[0].name "container-clustercontroller"
hosts[0].services[0].type "container-clustercontroller"
hosts[0].services[0].configid "admin/cluster-controllers/0"
hosts[0].services[0].clustertype ""
hosts[0].services[0].clustername "cluster-controllers"
hosts[0].services[0].index 0
hosts[0].services[0].ports[0].number 19050
hosts[0].services[0].ports[0].tags "state external query http"
hosts[0].services[0].ports[1].number 19100
hosts[0].services[0].ports[1].tags "external http"
hosts[0].services[0].ports[2].number 19101
hosts[0].services[0].ports[2].tags "messaging rpc"
hosts[0].services[0].ports[3].number 19102
hosts[0].services[0].ports[3].tags "admin rpc"
hosts[0].services[1].name "distributor2"
hosts[0].services[1].type "distributor"
hosts[0].services[1].configid "music/distributor/0"
hosts[0].services[1].clustertype "content"
hosts[0].services[1].clustername "music"
hosts[0].services[1].index 0
hosts[0].services[1].ports[0].number 19131
hosts[0].services[1].ports[0].tags "messaging"
hosts[0].services[1].ports[1].number 19132
hosts[0].services[1].ports[1].tags "status rpc"
hosts[0].services[1].ports[2].number 19133
hosts[0].services[1].ports[2].tags "status http"
hosts[0].services[2].name "storagenode3"
hosts[0].services[2].type "storagenode"
hosts[0].services[2].configid "storage/storage/0"
hosts[0].services[2].clustertype "content"
hosts[0].services[2].clustername "books"
hosts[0].services[2].index 0
hosts[0].services[2].ports[0].number 19134
hosts[0].services[2].ports[0].tags "messaging"
hosts[0].services[2].ports[1].number 19135
hosts[0].services[2].ports[1].tags "status rpc"
hosts[0].services[2].ports[2].number 19136
hosts[0].services[2].ports[2].tags "status http"
hosts[1].name "other.host.yahoo.com"
hosts[1].services[0].name "distributor2"
hosts[1].services[0].type "distributor"
hosts[1].services[0].configid "music/distributor/1"
hosts[1].services[0].clustertype "content"
hosts[1].services[0].clustername "music"
hosts[1].services[0].index 1
hosts[1].services[0].ports[0].number 19131
hosts[1].services[0].ports[0].tags "messaging"
hosts[1].services[0].ports[1].number 19132
hosts[1].services[0].ports[1].tags "status rpc"
hosts[1].services[0].ports[2].number 19133
hosts[1].services[0].ports[2].tags "status http"
hosts[1].services[1].name "storagenode3"
hosts[1].services[1].type "storagenode"
hosts[1].services[1].configid "storage/storage/1"
hosts[1].services[1].clustertype "content"
hosts[1].services[1].clustername "books"
hosts[1].services[1].index 1
hosts[1].services[1].ports[0].number 19134
hosts[1].services[1].ports[0].tags "messaging"
hosts[1].services[1].ports[1].number 19135
hosts[1].services[1].ports[1].tags "status rpc"
hosts[1].services[1].ports[2].number 19136
hosts[1].services[1].ports[2].tags "status http"
hosts[1].services[2].name "storagenode2"
hosts[1].services[2].type "storagenode"
hosts[1].services[2].configid "storage/storage/0"
hosts[1].services[2].clustertype "content"
hosts[1].services[2].clustername "music"
hosts[1].services[2].index 0
hosts[1].services[2].ports[0].number 19134
hosts[1].services[2].ports[0].tags "messaging"
hosts[1].services[2].ports[1].number 19135
hosts[1].services[2].ports[1].tags "status rpc"
hosts[1].services[2].ports[2].number 19136
hosts[1].services[2].ports[2].tags "status http"

EOS

sub getModelConfig {
    my @output = split(/\n/, $defaultModelConfig);
    return @output;
}

1;
