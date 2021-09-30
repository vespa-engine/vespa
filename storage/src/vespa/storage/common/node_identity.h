// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vdslib/state/nodetype.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage {

/**
 * Class that represents the identity of a storage or distributor node.
 */
class NodeIdentity {
private:
    vespalib::string _cluster_name;
    const lib::NodeType& _node_type;
    uint16_t _node_index;

public:
    NodeIdentity(vespalib::stringref cluster_name_in,
                 const lib::NodeType& node_type_in,
                 uint16_t node_index_in);
    const vespalib::string& cluster_name() const { return _cluster_name; }
    const lib::NodeType& node_type() const { return _node_type; }
    uint16_t node_index() const { return _node_index; }
};

}
