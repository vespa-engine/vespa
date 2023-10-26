// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/documentid.h>
#include <vespa/searchlib/docstore/compacter.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_set.h>
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

BucketId
createBucketId(size_t i) {
    constexpr size_t USED_BITS=5;
    vespalib::asciistream os;
    os << "id:a:b:n=" << userId(i) << ":" << i;
    document::DocumentId docId(os.str());
    BucketId b = docId.getGlobalId().convertToBucketId();
    EXPECT_EQUAL(userId(i), docId.getGlobalId().getLocationSpecificBits());
    b.setUsedBits(USED_BITS);
    return b;
}
void
add(StoreByBucket & sbb, size_t i) {
    BucketId b = createBucketId(i);
    vespalib::string s = createPayload(b);
    sbb.add(b, i%10, i, {s.c_str(), s.size()});
}

class VerifyBucketOrder : public StoreByBucket::IWrite {
public:
    VerifyBucketOrder() : _lastLid(0), _lastBucketId(0), _uniqueUser(), _uniqueBucket(){ }
    ~VerifyBucketOrder() override;
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, vespalib::ConstBufferRef data) override {
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
        EXPECT_EQUAL(0, memcmp(data.data(), createPayload(bucketId).c_str(), data.size()));
    }

private:
    uint32_t _lastLid;
    BucketId _lastBucketId;
    vespalib::hash_set<uint32_t> _uniqueUser;
    vespalib::hash_set<uint64_t> _uniqueBucket;

};

VerifyBucketOrder::~VerifyBucketOrder() = default;

struct StoreIndex : public StoreByBucket::StoreIndex {
    ~StoreIndex() override;
    void store(const StoreByBucket::Index &index) override {
        _where.push_back(index);
    }
    std::vector<StoreByBucket::Index> _where;
};
StoreIndex::~StoreIndex() = default;

struct Iterator : public StoreByBucket::IndexIterator {
    explicit Iterator(const std::vector<StoreByBucket::Index> & where) : _where(where), _current(0) {}

    bool has_next() noexcept override {
        return _current < _where.size();
    }

    StoreByBucket::Index next() noexcept override {
        return _where[_current++];
    }

    const std::vector<StoreByBucket::Index> & _where;
    uint32_t _current;
};

TEST("require that StoreByBucket gives bucket by bucket and ordered within")
{
    std::mutex backing_lock;
    vespalib::MemoryDataStore backing(vespalib::alloc::Alloc::alloc(256), &backing_lock);
    vespalib::ThreadStackExecutor executor(8);
    StoreIndex storeIndex;
    StoreByBucket sbb(storeIndex, backing, executor, CompressionConfig::LZ4);
    for (size_t i(1); i <= 500u; i++) {
        add(sbb, i);
    }
    for (size_t i(1000); i > 500u; i--) {
        add(sbb, i);
    }
    sbb.close();
    std::sort(storeIndex._where.begin(), storeIndex._where.end());
    EXPECT_EQUAL(1000u, storeIndex._where.size());
    VerifyBucketOrder vbo;
    Iterator all(storeIndex._where);
    sbb.drain(vbo, all);
}

constexpr uint32_t NUM_PARTS = 3;

void
verifyIter(BucketIndexStore &store, uint32_t partId, uint32_t expected_count) {
    auto iter = store.createIterator(partId);
    uint32_t count(0);
    while (iter->has_next()) {
        StoreByBucket::Index idx = iter->next();
        EXPECT_EQUAL(store.toPartitionId(idx._bucketId), partId);
        count++;
    }
    EXPECT_EQUAL(expected_count, count);
}

TEST("test that iterators cover the whole corpus and maps to correct partid") {

    BucketIndexStore bucketIndexStore(32, NUM_PARTS);
    for (size_t i(1); i <= 500u; i++) {
        bucketIndexStore.store(StoreByBucket::Index(createBucketId(i), 1, 2, i));
    }
    bucketIndexStore.prepareForIterate();
    EXPECT_EQUAL(500u, bucketIndexStore.getLidCount());
    EXPECT_EQUAL(32u, bucketIndexStore.getBucketCount());
    constexpr uint32_t COUNT_0 = 175, COUNT_1 = 155, COUNT_2 = 170;
    verifyIter(bucketIndexStore, 0, COUNT_0);
    verifyIter(bucketIndexStore, 1, COUNT_1);
    verifyIter(bucketIndexStore, 2, COUNT_2);
    EXPECT_EQUAL(500u, COUNT_0 + COUNT_1 + COUNT_2);
}

TEST_MAIN() { TEST_RUN_ALL(); }
