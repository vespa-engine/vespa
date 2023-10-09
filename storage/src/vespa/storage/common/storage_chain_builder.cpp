// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_chain_builder.h"
#include "storagelink.h"

namespace storage {

StorageChainBuilder::StorageChainBuilder()
    : _top()
{
}

StorageChainBuilder::~StorageChainBuilder() = default;

void
StorageChainBuilder::add(std::unique_ptr<StorageLink> link)
{
    if (_top) {
        _top->push_back(std::move(link));
    } else {
        _top = std::move(link);
    }
};

std::unique_ptr<StorageLink>
StorageChainBuilder::build() &&
{
    return std::move(_top);
}

}
