// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("frozenbucketsmap_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/frozenbuckets.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace proton;
using document::BucketId;

class RWTask : public vespalib::Executor::Task {
public:
    RWTask(FrozenBucketsMap & m, BucketId b, size_t count) : _b(b), _m(m), _count(count) {}
protected:
    const BucketId     _b;
    FrozenBucketsMap & _m;
    const size_t       _count;
};

class Reader : public RWTask {
public:
    Reader(FrozenBucketsMap & m, BucketId b, size_t count) :
        RWTask(m, b, count),
        numContended(0)
    {}
    ~Reader() {
        LOG(info, "NumContended = %ld", numContended);
    }
    void run() override {
        for (size_t i(0); i < _count; i++) {
            _m.freezeBucket(_b);
            if (_m.thawBucket(_b)) {
                numContended++;
            }
        }
    }
    size_t numContended;
};

class Writer : public RWTask {
public:
    Writer(FrozenBucketsMap & m, BucketId b, size_t count) :
            RWTask(m, b, count),
            numFailed(0),
            numSucces(0)
    {}
    ~Writer() {
        EXPECT_EQUAL(_count, numSucces + numFailed);
        LOG(info, "NumSuccess = %ld, NumFailed = %ld", numSucces, numFailed);
    }
    void run() override {
        for (size_t i(0); i < _count; i++) {
            IFrozenBucketHandler::ExclusiveBucketGuard::UP guard = _m.acquireExclusiveBucket(_b);
            if (guard) {
                numSucces++;
            } else {
                numFailed++;
            }
        }
    }
    size_t numFailed;
    size_t numSucces;
};

TEST("Race reader and writer on FrozenBucketsMap") {
    FrozenBucketsMap m;
    BucketId a(8, 6);
    constexpr size_t NUM_READERS = 3;
    constexpr size_t NUM_WRITERS = 1;
    constexpr size_t READER_COUNT = 1000000;
    constexpr size_t WRITER_COUNT = 1000000;
    vespalib::ThreadStackExecutor executor(NUM_READERS+NUM_WRITERS, 0x10000);
    for (size_t i(0); i < NUM_READERS; i++) {
        EXPECT_FALSE(bool(executor.execute(std::make_unique<Reader>(m, a, READER_COUNT))));
    }
    for (size_t i(0); i < NUM_WRITERS; i++) {
        EXPECT_FALSE(bool(executor.execute(std::make_unique<Writer>(m, a, WRITER_COUNT))));
    }
    executor.sync();
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
