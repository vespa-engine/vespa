// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/server/document_scan_iterator.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace document;
using namespace proton;
using namespace search;

using vespalib::make_string;

typedef DocumentMetaStore::Result DMSResult;
typedef DocumentMetaStore::Timestamp Timestamp;
typedef std::set<uint32_t> LidSet;
typedef std::vector<uint32_t> LidVector;

struct Fixture
{
    DocumentMetaStore _metaStore;
    DocumentScanIterator _itr;
    Fixture()
        : _metaStore(std::make_shared<BucketDBOwner>()),
          _itr(_metaStore)
    {
        _metaStore.constructFreeList();
    }
    Fixture &add(const LidVector &lids) {
        for (auto lid : lids) {
            add(lid);
        }
        return *this;
    }
    Fixture &add(uint32_t lid) {
        DocumentId docId(make_string("id:test:test:n=%u:%u", 1, lid));
        const GlobalId &gid = docId.getGlobalId();
        DMSResult res = _metaStore.inspect(gid, 0u);
        ASSERT_EQUAL(lid, res._lid);
        uint32_t docSize = 1;
        _metaStore.put(gid, gid.convertToBucketId(), Timestamp(lid), docSize, lid, 0u);
        return *this;
    }
    LidSet scan(uint32_t count, uint32_t compactLidLimit, uint32_t maxDocsToScan = 10) {
        LidSet retval;
        for (uint32_t i = 0; i < count; ++i) {
            retval.insert(next(compactLidLimit, maxDocsToScan, false));
            EXPECT_TRUE(_itr.valid());
        }
        EXPECT_EQUAL(0u, next(compactLidLimit, maxDocsToScan, false));
        EXPECT_FALSE(_itr.valid());
        return retval;
    }
    uint32_t next(uint32_t compactLidLimit, uint32_t maxDocsToScan = 10, bool retry = false) {
        return _itr.next(compactLidLimit, maxDocsToScan, retry).lid;
    }
};

void
assertLidSet(const LidSet &exp, const LidSet &act)
{
    EXPECT_EQUAL(exp, act);
}

TEST_F("require that an empty document meta store don't return any thing", Fixture)
{
    assertLidSet({}, f.scan(0, 4));
}

TEST_F("require that only lids > lid limit are returned", Fixture)
{
    f.add({1,2,3,4,5,6,7,8});
    assertLidSet({5,6,7,8}, f.scan(4, 4));
}

TEST_F("require that max docs to scan (1) are taken into consideration", Fixture)
{
    f.add({1,2,3,4,5,6,7,8});
    assertLidSet({0,5,6,7,8}, f.scan(8, 4, 1));
}

TEST_F("require that max docs to scan (2) are taken into consideration", Fixture)
{
    f.add({1,2,3,4,5,6,7,8});
    // scan order is: 8, {2,4}, 7, {5,3}, {1,6} (5 scans total)
    assertLidSet({0,7,8}, f.scan(5, 6, 2));
}

TEST_F("require that we start scan at previous doc if retry is set", Fixture)
{
    f.add({1,2,3,4,5,6,7,8});
    uint32_t lid1 = f.next(4, 10, false);
    uint32_t lid2 = f.next(4, 10, true);
    EXPECT_EQUAL(lid1, lid2);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
