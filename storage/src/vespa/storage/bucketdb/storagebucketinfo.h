// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage::bucketdb {

struct StorageBucketInfo {
    api::BucketInfo info;

    StorageBucketInfo() noexcept : info() {}
    void print(std::ostream&, bool verbose, const std::string& indent) const;
    bool valid() const noexcept { return info.valid(); }
    void setBucketInfo(const api::BucketInfo& i) noexcept { info = i; }
    const api::BucketInfo& getBucketInfo() const noexcept { return info; }
    bool verifyLegal() const noexcept { return true; }
    uint32_t getMetaCount() const noexcept { return info.getMetaCount(); }
};

std::ostream& operator<<(std::ostream& out, const StorageBucketInfo& info);

}
