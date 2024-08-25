// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/server/document_scan_iterator.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace document;
using namespace proton;
using namespace search;

using vespalib::make_string;

using DMSResult = DocumentMetaStore::Result;
using Timestamp = DocumentMetaStore::Timestamp;
using LidSet = std::set<uint32_t>;
using LidVector = std::vector<uint32_t>;

namespace document_scan_iterator_test {

struct Fixture
{
    DocumentMetaStore _metaStore;
    std::unique_ptr<DocumentScanIterator> _itr;
    Fixture()
        : _metaStore(std::make_shared<bucketdb::BucketDBOwner>()),
          _itr()
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
        EXPECT_EQ(lid, res._lid);
        uint32_t docSize = 1;
        _metaStore.put(gid, gid.convertToBucketId(), Timestamp(lid), docSize, lid, 0u);
        return *this;
    }
    LidSet scan(uint32_t count, uint32_t compactLidLimit) {
        if (!_itr) {
            _itr = std::make_unique<DocumentScanIterator>(_metaStore);
        }
        LidSet retval;
        for (uint32_t i = 0; i < count; ++i) {
            uint32_t lid = next(compactLidLimit);
            retval.insert(lid);
            EXPECT_TRUE(_itr->valid() || lid <= compactLidLimit);
        }
        EXPECT_EQ(0u, next(compactLidLimit));
        EXPECT_FALSE(_itr->valid());
        return retval;
    }
    uint32_t next(uint32_t compactLidLimit) {
        if (!_itr) {
            _itr = std::make_unique<DocumentScanIterator>(_metaStore);
        }
        return _itr->next(compactLidLimit).lid;
    }
};

class DocumentScanIteratorTest : public ::testing::Test, public Fixture
{
protected:
    DocumentScanIteratorTest();
    ~DocumentScanIteratorTest() override;
};

DocumentScanIteratorTest::DocumentScanIteratorTest()
    : ::testing::Test(),
      Fixture()
{
}

DocumentScanIteratorTest::~DocumentScanIteratorTest() = default;

void
assertLidSet(const LidSet &exp, const LidSet &act)
{
    EXPECT_EQ(exp, act);
}

TEST_F(DocumentScanIteratorTest, require_that_an_empty_document_meta_store_doesnt_return_any_thing)
{
    assertLidSet({}, scan(0, 4));
}

TEST_F(DocumentScanIteratorTest, require_that_only_lids_gt_lid_limit_are_returned)
{
    add({1,2,3,4,5,6,7,8});
    assertLidSet({5,6,7,8}, scan(4, 4));
}

}
