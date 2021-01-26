// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/bucket/bucketspace.h>
#include <memory>

namespace proton {

/**
 * Interface defining the communication needed with the owner of the
 * document sub db.
 */
class IDocumentSubDBOwner
{
public:
    virtual ~IDocumentSubDBOwner() {}
    virtual document::BucketSpace getBucketSpace() const = 0;
    virtual vespalib::string getName() const = 0;
    virtual uint32_t getDistributionKey() const = 0;
};

} // namespace proton
