// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/bucketdb/bucket_db_explorer.h>
#include <vespa/searchcore/proton/bucketdb/bucketdb.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("bucketdb_test");

using namespace document;
using namespace proton;
using namespace proton::bucketdb;
using namespace vespalib::slime;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using vespalib::Slime;

constexpr uint32_t MIN_NUM_BITS = 8u;
const GlobalId GID_1("111111111111");
const BucketId BUCKET_1(MIN_NUM_BITS, GID_1.convertToBucketId().getRawId());
const Timestamp TIME_1(1u);
const Timestamp TIME_2(2u);
const Timestamp TIME_3(3u);
constexpr uint32_t DOCSIZE_1(4096u);
constexpr uint32_t DOCSIZE_2(10000u);

typedef BucketInfo::ReadyState RS;
typedef SubDbType SDT;

void
assertDocCount(uint32_t ready,
               uint32_t notReady,
               uint32_t removed,
               const BucketState &state)
{
    EXPECT_EQUAL(ready, state.getReadyCount());
    EXPECT_EQUAL(notReady, state.getNotReadyCount());
    EXPECT_EQUAL(removed, state.getRemovedCount());
    BucketInfo info = state;
    EXPECT_EQUAL(ready + notReady, info.getDocumentCount());
    EXPECT_EQUAL(ready + notReady + removed, info.getEntryCount());
}

void
assertDocSizes(size_t ready,
               size_t notReady,
               size_t removed,
               const BucketState &state)
{
    EXPECT_EQUAL(ready, state.getReadyDocSizes());
    EXPECT_EQUAL(notReady, state.getNotReadyDocSizes());
    EXPECT_EQUAL(removed, state.getRemovedDocSizes());
    BucketInfo info = state;
    EXPECT_EQUAL(ready + notReady, info.getDocumentSize());
    EXPECT_EQUAL(ready + notReady + removed, info.getUsedSize());
}

void
assertReady(bool expReady,
            const BucketInfo &info)
{
    EXPECT_EQUAL(expReady, info.isReady());
}

struct Fixture
{
    BucketDB _db;
    Fixture()
        : _db()
    {}
    const BucketState &add(const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType) {
        return _db.add(GID_1, BUCKET_1, timestamp, docSize, subDbType);
    }
    const BucketState &add(const Timestamp &timestamp, SubDbType subDbType) {
        return add(timestamp, DOCSIZE_1, subDbType);
    }
    BucketState remove(const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType) {
        _db.remove(GID_1, BUCKET_1, timestamp, docSize, subDbType);
        return get();
    }
    BucketState remove(const Timestamp &timestamp, SubDbType subDbType) {
        return remove(timestamp, DOCSIZE_1, subDbType);
    }
    BucketState get() const {
        return _db.get(BUCKET_1);
    }
    BucketChecksum getChecksum(const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType) {
        BucketDB db;
        BucketChecksum retval = db.add(GID_1, BUCKET_1, timestamp, docSize, subDbType).getChecksum();
        // Must ensure empty bucket db before destruction.
        db.remove(GID_1, BUCKET_1, timestamp, docSize, subDbType);
        return retval;
    }
    BucketChecksum getChecksum(const Timestamp &timestamp, SubDbType subDbType) {
        return getChecksum(timestamp, DOCSIZE_1, subDbType);
    }
};

TEST_F("require that bucket db tracks doc counts per sub db type", Fixture)
{
    TEST_DO(assertDocCount(0, 0, 0, f.get()));
    TEST_DO(assertDocCount(1, 0, 0, f.add(TIME_1, SDT::READY)));
    TEST_DO(assertDocCount(1, 1, 0, f.add(TIME_2, SDT::NOTREADY)));
    TEST_DO(assertDocCount(1, 1, 1, f.add(TIME_3, SDT::REMOVED)));
    TEST_DO(assertDocCount(0, 1, 1, f.remove(TIME_1, SDT::READY)));
    TEST_DO(assertDocCount(0, 0, 1, f.remove(TIME_2, SDT::NOTREADY)));
    TEST_DO(assertDocCount(0, 0, 0, f.remove(TIME_3, SDT::REMOVED)));
}

TEST_F("require that bucket db tracks doc sizes per sub db type", Fixture)
{
    constexpr size_t S = DOCSIZE_1;
    TEST_DO(assertDocSizes(0, 0, 0, f.get()));
    TEST_DO(assertDocSizes(S, 0, 0, f.add(TIME_1, DOCSIZE_1, SDT::READY)));
    TEST_DO(assertDocSizes(S, S, 0, f.add(TIME_2, DOCSIZE_1, SDT::NOTREADY)));
    TEST_DO(assertDocSizes(S, S, S, f.add(TIME_3, DOCSIZE_1, SDT::REMOVED)));
    TEST_DO(assertDocSizes(0, S, S, f.remove(TIME_1, DOCSIZE_1, SDT::READY)));
    TEST_DO(assertDocSizes(0, 0, S, f.remove(TIME_2, DOCSIZE_1, SDT::NOTREADY)));
    TEST_DO(assertDocSizes(0, 0, 0, f.remove(TIME_3, DOCSIZE_1, SDT::REMOVED)));
}

TEST_F("require that bucket checksum is a combination of sub db types", Fixture)
{
    BucketChecksum zero(0u);
    BucketChecksum ready = f.getChecksum(TIME_1, SDT::READY);
    BucketChecksum notReady = f.getChecksum(TIME_2, SDT::NOTREADY);

    EXPECT_EQUAL(zero, f.get().getChecksum());
    EXPECT_EQUAL(ready,            f.add(TIME_1, SDT::READY).getChecksum());
    EXPECT_EQUAL(BucketState::addChecksum(ready, notReady), f.add(TIME_2, SDT::NOTREADY).getChecksum());
    EXPECT_EQUAL(BucketState::addChecksum(ready, notReady), f.add(TIME_3, SDT::REMOVED).getChecksum());
    EXPECT_EQUAL(notReady, f.remove(TIME_1, SDT::READY).getChecksum());
    EXPECT_EQUAL(zero,     f.remove(TIME_2, SDT::NOTREADY).getChecksum());
    EXPECT_EQUAL(zero,     f.remove(TIME_3, SDT::REMOVED).getChecksum());
}

TEST_F("require that bucket is ready when not having docs in notready sub db", Fixture)
{
    assertReady(true, f.get());
    assertReady(true, f.add(TIME_1, SDT::READY));
    assertReady(false, f.add(TIME_2, SDT::NOTREADY));
    assertReady(false, f.add(TIME_3, SDT::REMOVED));
    assertReady(true, f.remove(TIME_2, SDT::NOTREADY));
    assertReady(true, f.remove(TIME_1, SDT::READY));
    assertReady(true, f.remove(TIME_3, SDT::REMOVED));
}

TEST_F("require that bucket can be cached", Fixture)
{
    f.add(TIME_1, SDT::READY);
    EXPECT_FALSE(f._db.isCachedBucket(BUCKET_1));
    f._db.cacheBucket(BUCKET_1);
    EXPECT_TRUE(f._db.isCachedBucket(BUCKET_1));

    TEST_DO(assertDocCount(1, 0, 0, f._db.cachedGet(BUCKET_1)));
    f.add(TIME_2, SDT::NOTREADY);
    TEST_DO(assertDocCount(1, 0, 0, f._db.cachedGet(BUCKET_1)));

    f._db.uncacheBucket();
    EXPECT_FALSE(f._db.isCachedBucket(BUCKET_1));
    TEST_DO(assertDocCount(1, 1, 0, f._db.cachedGet(BUCKET_1)));

    // Must ensure empty bucket db before destruction.
    f.remove(TIME_1, SDT::READY);
    f.remove(TIME_2, SDT::NOTREADY);
}

TEST_F("require that bucket checksum ignores document sizes", Fixture)
{
    auto state1 = f.add(TIME_1, DOCSIZE_1, SDT::READY);
    f.remove(TIME_1, DOCSIZE_1, SDT::READY);
    auto state2 = f.add(TIME_1, DOCSIZE_2, SDT::READY);
    f.remove(TIME_1, DOCSIZE_2, SDT::READY);
    EXPECT_NOT_EQUAL(state1.getReadyDocSizes(), state2.getReadyDocSizes());
    EXPECT_EQUAL(state1.getChecksum(), state2.getChecksum());
}

TEST("require that bucket db can be explored")
{
    BucketDBOwner db;
    const BucketState & expectedState = db.takeGuard()->add(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SDT::READY);
    {
        BucketDBExplorer explorer(db.takeGuard());
        Slime expectSlime;
        vespalib::asciistream expectJson;
        expectJson <<
            "{"
            "  numBuckets: 1,"
            "  buckets: ["
            "    {"
            "      id: '0x2000000000000031',"
            "      checksum: '0x"
            << vespalib::hex << expectedState.getChecksum() <<
            "',"
            "      readyCount: 1,"
            "      notReadyCount: 0,"
            "      removedCount: 0,"
            "      readyDocSizes: 4096,"
            "      notReadyDocSizes: 0,"
            "      removedDocSizes: 0,"
            "      active: false"
            "    }"
            "  ]"
            "}";
        EXPECT_TRUE(JsonFormat::decode(expectJson.str(), expectSlime) > 0);
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        explorer.get_state(inserter, true);

        EXPECT_EQUAL(expectSlime, actualSlime);
    }

    // Must ensure empty bucket db before destruction.
    db.takeGuard()->remove(GID_1, BUCKET_1, TIME_1, DOCSIZE_1, SDT::READY);
}

BucketChecksum
verifyChecksumCompliance(ChecksumAggregator::ChecksumType type) {
    GlobalId gid1("a");
    GlobalId gid2("b");
    Timestamp t1(0);
    Timestamp t2(1);
    auto ckaggr = ChecksumAggregator::create(type, BucketChecksum(0));

    EXPECT_EQUAL(0u, ckaggr->getChecksum());
    ckaggr->addDoc(gid1, t1);
    BucketChecksum afterAdd = ckaggr->getChecksum();
    EXPECT_NOT_EQUAL(0u, afterAdd);                   // add Changes checksum
    ckaggr->removeDoc(gid1, t1);
    EXPECT_EQUAL(0u, ckaggr->getChecksum());          // add/remove are symmetrical
    ckaggr->addDoc(gid1, t2);
    EXPECT_NOT_EQUAL(afterAdd, ckaggr->getChecksum()); // timestamp changes checksum
    ckaggr->removeDoc(gid1, t2);
    EXPECT_EQUAL(0u, ckaggr->getChecksum());          // add/remove are symmetrical
    ckaggr->addDoc(gid2, t1);
    EXPECT_NOT_EQUAL(afterAdd, ckaggr->getChecksum()); // gid changes checksum
    ckaggr->removeDoc(gid2, t1);
    EXPECT_EQUAL(0u, ckaggr->getChecksum());          // add/remove are symmetrical

    {
        // Verify order does not matter, only current content. A,B == B,A
        ckaggr->addDoc(gid1, t1);
        BucketChecksum after1AddOfGid1 = ckaggr->getChecksum();
        ckaggr->addDoc(gid2, t2);
        BucketChecksum after2Add1 = ckaggr->getChecksum();
        ckaggr->removeDoc(gid2, t2);
        EXPECT_EQUAL(after1AddOfGid1, ckaggr->getChecksum());
        ckaggr->removeDoc(gid1, t1);
        EXPECT_EQUAL(0u, ckaggr->getChecksum());

        ckaggr->addDoc(gid2, t2);
        EXPECT_NOT_EQUAL(after1AddOfGid1, ckaggr->getChecksum());
        ckaggr->addDoc(gid1, t1);
        EXPECT_EQUAL(after2Add1, ckaggr->getChecksum());
        ckaggr->removeDoc(gid2, t2);
        EXPECT_EQUAL(after1AddOfGid1, ckaggr->getChecksum());
        ckaggr->removeDoc(gid1, t1);
        EXPECT_EQUAL(0u, ckaggr->getChecksum());          // add/remove are symmetrical
    }

    ckaggr->addDoc(gid1, t1); // Add something so we can verify it does not change between releases.

    return ckaggr->getChecksum();
}

TEST("test that legacy checksum complies") {
    BucketChecksum cksum = verifyChecksumCompliance(ChecksumAggregator::ChecksumType::LEGACY);
    EXPECT_EQUAL(0x61u, cksum);
}

TEST("test that xxhash64 checksum complies") {
    BucketChecksum cksum = verifyChecksumCompliance(ChecksumAggregator::ChecksumType::XXHASH64);
    EXPECT_EQUAL(0xc34cd5c3u, cksum);
}

TEST_MAIN() { TEST_RUN_ALL(); }
