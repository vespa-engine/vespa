// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace document { class BucketSpace; }

namespace proton {

class DocumentDBConfig;

/*
 * Interface class defining reconfigure method for a document db.
 */
class IDocumentDBConfigOwner
{
public:
    virtual ~IDocumentDBConfigOwner() = default;
    virtual document::BucketSpace getBucketSpace() const = 0;
    virtual void reconfigure(std::shared_ptr<DocumentDBConfig> config) = 0;
};

} // namespace proton
