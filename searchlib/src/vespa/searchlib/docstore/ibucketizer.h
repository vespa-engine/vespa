// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/document/bucket/bucketid.h>

namespace search {

class IBucketizer
{
public:
    using SP = std::shared_ptr<IBucketizer>;
    virtual ~IBucketizer() = default;
    virtual document::BucketId getBucketOf(const vespalib::GenerationHandler::Guard & guard, uint32_t lid) const = 0;
    virtual vespalib::GenerationHandler::Guard getGuard() const = 0;
};

class IBufferVisitor {
public:
    virtual ~IBufferVisitor() = default;
    virtual void visit(uint32_t lid, vespalib::ConstBufferRef buffer) = 0;
};

}
