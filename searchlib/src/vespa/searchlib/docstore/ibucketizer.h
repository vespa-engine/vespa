// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/generation_guard.h>

#include <memory>

namespace search {

class IBucketizer {
public:
    using SP = std::shared_ptr<IBucketizer>;
    virtual ~IBucketizer() = default;
    virtual document::BucketId getBucketOf(const vespalib::GenerationGuard& guard, uint32_t lid) const = 0;
    virtual vespalib::GenerationGuard getGuard() const = 0;
};

class IBufferVisitor {
public:
    virtual ~IBufferVisitor() = default;
    virtual void visit(uint32_t lid, vespalib::ConstBufferRef buffer) = 0;
};

} // namespace search
