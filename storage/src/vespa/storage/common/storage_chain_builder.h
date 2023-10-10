// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_storage_chain_builder.h"

namespace storage {

/**
 * Class for building a storage chain.
 */
class StorageChainBuilder : public IStorageChainBuilder
{
protected:
    std::unique_ptr<StorageLink> _top;
public:
    StorageChainBuilder();
    ~StorageChainBuilder() override;
    void add(std::unique_ptr<StorageLink> link) override;
    std::unique_ptr<StorageLink> build() && override;
};

}
