// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <memory>
#include <string>

namespace proton {

namespace matching { class SessionManager; }

/**
 * Interface defining the communication needed with the owner of the
 * document sub db.
 */
class IDocumentSubDBOwner
{
public:
    using SessionManager = matching::SessionManager;
    virtual ~IDocumentSubDBOwner() = default;
    virtual document::BucketSpace getBucketSpace() const = 0;
    virtual std::string getName() const = 0;
    virtual uint32_t getDistributionKey() const = 0;
    virtual SessionManager & session_manager() = 0;
};

} // namespace proton
