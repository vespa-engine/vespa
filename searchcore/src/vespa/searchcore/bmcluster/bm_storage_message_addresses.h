// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>

namespace storage::api { class StorageMessageAddress; }

namespace search::bmcluster {

/*
 * Class representing the storage message addresses for a set of nodes at
 * the given layer (service layer or distributor).
 */
class BmStorageMessageAddresses
{
    using StorageMessageAddress = storage::api::StorageMessageAddress;
    std::vector<std::unique_ptr<const StorageMessageAddress>> _addresses;
public:
    BmStorageMessageAddresses(uint32_t num_nodes, bool distributor);
    ~BmStorageMessageAddresses();
    const StorageMessageAddress &get_address(uint32_t node_idx) const { return *_addresses[node_idx]; }
    bool has_address(uint32_t node_idx) const { return node_idx < _addresses.size(); }

};

}
