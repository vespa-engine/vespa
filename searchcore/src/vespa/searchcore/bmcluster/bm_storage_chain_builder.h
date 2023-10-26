// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/common/storage_chain_builder.h>

namespace search::bmcluster {

struct BmStorageLinkContext;

/*
 * Storage chain builder that inserts a BmStorageLink right below the
 * communication manager. This allows sending benchmark feed to chain.
 */
class BmStorageChainBuilder : public storage::StorageChainBuilder
{
    std::shared_ptr<BmStorageLinkContext> _context;
public:
    BmStorageChainBuilder();
    ~BmStorageChainBuilder() override;
    const std::shared_ptr<BmStorageLinkContext>& get_context() { return _context; }
    void add(std::unique_ptr<storage::StorageLink> link) override;
};

}

