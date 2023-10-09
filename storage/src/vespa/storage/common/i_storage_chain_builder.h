// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace storage {

class StorageLink;

/*
 * Interface class for building a storage chain.
 */
class IStorageChainBuilder
{
public:
    virtual ~IStorageChainBuilder() = default;
    virtual void add(std::unique_ptr<StorageLink> link) = 0;
    virtual std::unique_ptr<StorageLink> build() && = 0;
};

}
