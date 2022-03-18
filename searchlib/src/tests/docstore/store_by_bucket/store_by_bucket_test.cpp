// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/documentid.h>
#include <vespa/searchlib/docstore/storebybucket.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("store_by_bucket_test");

using namespace search::docstore;
using document::BucketId;
using vespalib::compression::CompressionConfig;

vespalib::string
createPayload(BucketId b) {
    constexpr const char * BUF = "Buffer for testing Bucket drain order.";
    vespalib::asciistream os;
    os << BUF << " " << b;
    return os.str();
}
uint32_t userId(size_t i) { return i%100; }

void
add(StoreByBucket & sbb, size_t i) {
    constexpr size_t USED_BITS=5;
    vespalib::asciistream os;
    os << "id:a:b:n=" << userId(i) << ":" << i;
    document::DocumentId docId(os.str());
    BucketId b = docId.getGlobalId().convertToBucketId();
    EXPECT_EQUAL(userId(i), docId.getGlobalId().getLocationSpecificBits());
    b.setUsedBits(USED_BITS);
    vespalib::string s = createPayload(b);
    sbb.add(b, i%10, i, s.c_str(), s.size());
}

class VerifyBucketOrder : public StoreByBucket::IWrite {
public:
    VerifyBucketOrder() : _lastLid(0), _lastBucketId(0), _uniqueUser(), _uniqueBucket() { }
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override {
        (void) chunkId;
        EXPECT_LESS_EQUAL(_lastBucketId.toKey(), bucketId.toKey());
        if (_lastBucketId != bucketId) {
            EXPECT_TRUE(_uniqueBucket.find(bucketId.getRawId()) == _uniqueBucket.end());
            _uniqueBucket.insert(bucketId.getRawId());
        }
        if (userId(_lastLid) != userId(lid)) {
            EXPECT_TRUE(_uniqueUser.find(userId(lid)) == _uniqueUser.end());
            _uniqueUser.insert(userId(lid));
        }
        _lastLid = lid;
        _lastBucketId = bucketId;
        EXPECT_EQUAL(0, memcmp(buffer, createPayload(bucketId).c_str(), sz));
    }
private:
    uint32_t _lastLid;
    BucketId _lastBucketId;
    vespalib::hash_set<uint32_t> _uniqueUser;
    vespalib::hash_set<uint64_t> _uniqueBucket;
};

TEST("require that StoreByBucket gives bucket by bucket and ordered within")
{
    std::mutex backing_lock;
    vespalib::MemoryDataStore backing(vespalib::alloc::Alloc::alloc(256), &backing_lock);
    vespalib::ThreadStackExecutor executor(8, 128_Ki);
    StoreByBucket sbb(backing, executor, CompressionConfig::LZ4);
    for (size_t i(1); i <=500; i++) {
        add(sbb, i);
    }
    for (size_t i(1000); i > 500; i--) {
        add(sbb, i);
    }
    EXPECT_EQUAL(32u, sbb.getBucketCount());
    EXPECT_EQUAL(1000u, sbb.getLidCount());
    VerifyBucketOrder vbo;
    sbb.drain(vbo);
}

TEST_MAIN() { TEST_RUN_ALL(); }
