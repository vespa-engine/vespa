// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_identity.h"

namespace storage {

NodeIdentity::NodeIdentity(vespalib::stringref cluster_name_in,
                           const lib::NodeType& node_type_in,
                           uint16_t node_index_in)
    : _cluster_name(cluster_name_in),
      _node_type(node_type_in),
      _node_index(node_index_in)
{
}

}
