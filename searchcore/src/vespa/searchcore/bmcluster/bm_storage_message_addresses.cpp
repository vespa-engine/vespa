// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_storage_message_addresses.h"
#include <vespa/storageapi/messageapi/storagemessage.h>

using storage::api::StorageMessageAddress;
using storage::lib::NodeType;

namespace search::bmcluster {

namespace {

vespalib::string _Storage("storage");

}

BmStorageMessageAddresses::BmStorageMessageAddresses(uint32_t num_nodes, bool distributor)
    : _addresses(num_nodes)
{
    for (uint32_t node_idx = 0; node_idx < num_nodes; ++node_idx) {
        _addresses[node_idx] = std::make_unique<StorageMessageAddress>(&_Storage, distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE, node_idx);
    }
}

BmStorageMessageAddresses::~BmStorageMessageAddresses() = default;

}
