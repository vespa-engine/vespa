// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage::bucketdb {

struct StorageBucketInfo {
    api::BucketInfo info;

    StorageBucketInfo() : info() {}
    static bool mayContain(const StorageBucketInfo&) { return true; }
    void print(std::ostream&, bool verbose, const std::string& indent) const;
    bool valid() const { return info.valid(); }
    void setBucketInfo(const api::BucketInfo& i) { info = i; }
    const api::BucketInfo& getBucketInfo() const { return info; }
    void setEmptyWithMetaData() {
        info.setChecksum(1);
        info.setMetaCount(1);
        info.setDocumentCount(0);
        info.setTotalDocumentSize(0);
    }
    bool verifyLegal() const { return true; }
    uint32_t getMetaCount() const { return info.getMetaCount(); }
    void setChecksum(uint32_t crc) { info.setChecksum(crc); }
    bool operator == (const StorageBucketInfo & b) const;
    bool operator != (const StorageBucketInfo & b) const;
    bool operator < (const StorageBucketInfo & b) const;
};

std::ostream& operator<<(std::ostream& out, const StorageBucketInfo& info);

}
