// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/buckets/bucketinfo.h>
#include <memory>

namespace storage {

class MessageTracker;

struct Types {
    using BucketInfo = api::BucketInfo;
    using MessageTrackerUP = std::unique_ptr<MessageTracker>;
protected:
    ~Types() = default; // Noone should refer to objects as Types objects
};

} // storage

