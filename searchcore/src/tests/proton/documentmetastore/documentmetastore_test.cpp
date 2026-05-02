// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/checksumaggregators.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_listener.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/server/itlssyncer.h>
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <filesystem>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("documentmetastore_test");

using namespace document;
using proton::bucketdb::BucketState;
using proton::bucketdb::IBucketCreateListener;
using proton::bucketdb::LegacyChecksumAggregator;
using proton::bucketdb::XXH64ChecksumAggregator;
using search::AttributeFileSaveTarget;
using search::AttributeGuard;
using search::AttributeVector;
using search::CommitParam;
using search::DocumentMetadata;
using search::GrowStrategy;
using search::LidUsageStats;
using search::QueryTermSimple;
using search::TuneFileAttributes;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::common::sortspec::MissingPolicy;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldMatchData;
using search::index::DummyFileHeaderContext;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleResult;
using searchcorespi::IFlushTarget;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using vespalib::Generation;
using vespalib::GenerationHandler;
using vespalib::GenerationHolder;
using vespalib::HwInfo;
using namespace std::literals;

namespace proton {

namespace {

static constexpr uint32_t numBucketBits = UINT32_C(20);
static constexpr uint64_t timestampBias = UINT64_C(2000000000000);

}

class DummyTlsSyncer : public ITlsSyncer {
public:
    ~DummyTlsSyncer() override = default;

    void sync() override { }
};

struct BoolVector : public std::vector<bool> {
    BoolVector() : std::vector<bool>() {}
    explicit BoolVector(size_t sz) : std::vector<bool>(sz) {}
    BoolVector &T() { push_back(true); return *this; }
    BoolVector &F() { push_back(false); return *this; }

    uint32_t
    countTrue() const
    {
        uint32_t res(0);
        for (uint32_t i = 0; i < size(); ++i)
            if ((*this)[i])
                ++res;
        return res;
    }
};

using PutRes = DocumentMetaStore::Result;
using Result = DocumentMetaStore::Result;

std::shared_ptr<bucketdb::BucketDBOwner>
createBucketDB()
{
    return std::make_shared<bucketdb::BucketDBOwner>();
}

void
assertPut(const BucketId &bucketId,
          uint64_t timestamp,
          uint32_t lid,
          const DocumentId& docId,
          DocumentMetaStore &dms)
{
    auto gid = docId.getGlobalId();
    Result inspect = dms.inspect(gid, 0u);
    uint32_t docSize = 1;
    PutRes putRes = dms.put(docId, bucketId, timestamp, docSize, inspect.getLid(), 0u);
    dms.commit();
    EXPECT_TRUE(putRes.ok());
    EXPECT_EQ(lid, putRes.getLid());
}

bool
compare(const GlobalId &lhs, const GlobalId &rhs)
{
    EXPECT_EQ(lhs.toString(), rhs.toString());
    return (lhs.toString() == rhs.toString());
}

void
assertGid(const GlobalId &exp, uint32_t lid, const DocumentMetaStore &dms)
{
    GlobalId act;
    EXPECT_TRUE(dms.getGid(lid, act));
    EXPECT_TRUE(compare(exp, act));
}

void
assertGid(const GlobalId &exp,
          uint32_t lid,
          const DocumentMetaStore &dms,
          const BucketId &expBucketId,
          const Timestamp &expTimestamp)
{
    GlobalId act;
    BucketId bucketId;
    Timestamp timestamp(1);
    EXPECT_TRUE(dms.getGid(lid, act));
    EXPECT_TRUE(compare(exp, act));
    DocumentMetadata meta = dms.getMetadata(act);
    EXPECT_TRUE(meta.valid());
    bucketId = meta.bucketId;
    timestamp = meta.timestamp;
    EXPECT_EQ(expBucketId.getRawId(), bucketId.getRawId());
    EXPECT_EQ(expBucketId.getId(), bucketId.getId());
    EXPECT_EQ(expTimestamp, timestamp);
}

void
assertLid(uint32_t exp, const GlobalId &gid, const DocumentMetaStore &dms)
{
    uint32_t act;
    EXPECT_TRUE(dms.getLid(gid, act));
    EXPECT_EQ(exp, act);
}

void
assertMetadata(const DocumentMetadata &exp, const DocumentMetadata &act)
{
    SCOPED_TRACE(std::format("exp.lid={}", exp.lid));
    EXPECT_EQ(exp.lid, act.lid);
    EXPECT_EQ(exp.timestamp, act.timestamp);
    EXPECT_EQ(exp.bucketId, act.bucketId);
    EXPECT_EQ(exp.gid, act.gid);
    EXPECT_EQ(exp.removed, act.removed);
    EXPECT_EQ(exp.docid, act.docid);
}

void
assertActiveLids(const BoolVector &exp, const search::BitVector &act)
{
    // lid 0 is reserved
    EXPECT_EQ(exp.size() + 1, act.size());
    for (size_t i = 0; i < exp.size(); ++i) {
        EXPECT_EQ(exp[i], act.testBit(i + 1));
    }
}

void
assertWhiteList(const SimpleResult &exp, Blueprint::UP whiteListBlueprint, bool strict, uint32_t docIdLimit)
{
    MatchDataLayout mdl;
    MatchData::UP md = mdl.createMatchData();
    whiteListBlueprint->basic_plan(strict, docIdLimit);
    whiteListBlueprint->fetchPostings(search::queryeval::ExecuteInfo::FULL);

    SearchIterator::UP sb = whiteListBlueprint->createSearch(*md);
    SimpleResult act;
    act.search(*sb, docIdLimit);
    EXPECT_EQ(exp, act);
}

void
assertSearchResult(const SimpleResult &exp, const DocumentMetaStore &dms,
                   const std::string &term, const QueryTermSimple::Type &termType,
                   bool strict, uint32_t docIdLimit = 100)
{
    std::unique_ptr<SearchContext> sc =
            dms.getSearch(std::make_unique<QueryTermSimple>(term, termType), SearchContextParams());
    TermFieldMatchData tfmd;
    SearchIterator::UP sb = sc->createIterator(&tfmd, strict);
    SimpleResult act;
    act.search(*sb, docIdLimit);
    EXPECT_EQ(exp, act);
}

void
assertBucketInfo(uint32_t expDocCount,
                 uint32_t expMetaCount,
                 const BucketInfo &act)
{
    EXPECT_EQ(expDocCount, act.getDocumentCount());
    EXPECT_EQ(expMetaCount, act.getEntryCount());
}

DocumentId docid1("id::test::111111111111");
DocumentId docid2("id::test::222222222222");
DocumentId docid3("id::test::333333333333");
DocumentId docid4("id::test::444444444444");
DocumentId docid5("id::test::555555555555");
const GlobalId& gid1 = docid1.getGlobalId();
const GlobalId& gid2 = docid2.getGlobalId();
const GlobalId& gid3 = docid3.getGlobalId();
const GlobalId& gid4 = docid4.getGlobalId();
const GlobalId& gid5 = docid5.getGlobalId();
const uint32_t minNumBits = 8u;
BucketId bucketId1(minNumBits,
                   gid1.convertToBucketId().getRawId());
BucketId bucketId2(minNumBits,
                   gid2.convertToBucketId().getRawId());
BucketId bucketId3(minNumBits,
                   gid3.convertToBucketId().getRawId());
BucketId bucketId4(minNumBits,
                   gid4.convertToBucketId().getRawId());
BucketId bucketId5(minNumBits,
                   gid5.convertToBucketId().getRawId());
Timestamp time1(1u);
Timestamp time2(2u);
Timestamp time3(42u);
Timestamp time4(82u);
Timestamp time5(141u);
uint32_t docSize1 = 1;
uint32_t docSize4 = 1;
uint32_t docSize5 = 1;

uint32_t
addDoc(DocumentMetaStore &dms, const DocumentId &docid, const BucketId &bid, Timestamp timestamp, uint32_t docSize = 1)
{
    auto& gid = docid.getGlobalId();
    Result inspect = dms.inspect(gid, 0u);
    PutRes putRes;
    EXPECT_TRUE((putRes = dms.put(docid, bid, timestamp, docSize, inspect.getLid(), 0u)).ok());
    dms.commit();
    return putRes.getLid();
}

uint32_t
addDoc(DocumentMetaStore &dms, const DocumentId& docid, Timestamp timestamp)
{
    auto& gid = docid.getGlobalId();
    BucketId bid(minNumBits, gid.convertToBucketId().getRawId());
    return addDoc(dms, docid, bid, timestamp);
}

void
putDoc(DocumentMetaStore &dms, const DocumentId& docid, uint32_t lid, Timestamp timestamp = Timestamp())
{
    auto& gid = docid.getGlobalId();
    BucketId bid(minNumBits, gid.convertToBucketId().getRawId());
    uint32_t docSize = 1;
    EXPECT_TRUE(dms.put(docid, bid, timestamp, docSize, lid, 0u).ok());
    dms.commit();
}

TEST(DocumentMetaStoreTest, control_metadata_sizeof) {
    EXPECT_EQ(32u, sizeof(RawDocumentMetadata));
    EXPECT_EQ(40u + sizeof(std::string), sizeof(search::DocumentMetadata));
}
 TEST(DocumentMetaStoreTest, removed_documents_are_bucketized_to_bucket_0)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    EXPECT_EQ(1u, dms.getNumDocs());
    EXPECT_EQ(0u, dms.getNumUsedLids());

    auto guard(dms.getGuard());
    EXPECT_EQ(BucketId(), dms.getBucketOf(guard, 1));
    assertPut(bucketId1, time1, 1, docid1, dms);
    EXPECT_EQ(bucketId1, dms.getBucketOf(guard, 1));
    assertPut(bucketId2, time2, 2, docid2, dms);
    EXPECT_EQ(bucketId2, dms.getBucketOf(guard, 2));
    EXPECT_TRUE(dms.remove(1, 0u));
    dms.commit();
    EXPECT_EQ(BucketId(), dms.getBucketOf(guard, 1));
    EXPECT_EQ(bucketId2, dms.getBucketOf(guard, 2));
}

TEST(DocumentMetaStoreTest, gids_can_be_inserted_and_retrieved)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    // put()
    EXPECT_EQ(1u, dms.getNumDocs());
    EXPECT_EQ(0u, dms.getNumUsedLids());
    assertPut(bucketId1, time1, 1, docid1, dms);
    EXPECT_EQ(2u, dms.getNumDocs());
    EXPECT_EQ(1u, dms.getNumUsedLids());
    assertPut(bucketId2, time2, 2, docid2, dms);
    EXPECT_EQ(3u, dms.getNumDocs());
    EXPECT_EQ(2u, dms.getNumUsedLids());
    // gid1 already inserted
    assertPut(bucketId1, time1, 1, docid1, dms);
    // gid2 already inserted
    assertPut(bucketId2, time2, 2, docid2, dms);


    // getGid()
    GlobalId gid;
    assertGid(gid1, 1, dms);
    assertGid(gid2, 2, dms);
    EXPECT_TRUE(!dms.getGid(3, gid));

    // getLid()
    uint32_t lid = 0;
    assertLid(1, gid1, dms);
    assertLid(2, gid2, dms);
    EXPECT_TRUE(!dms.getLid(gid3, lid));
}

TEST(DocumentMetaStore, gids_can_be_cleared)
{
    DocumentMetaStore dms(createBucketDB());
    GlobalId gid;
    uint32_t lid = 0u;
    dms.constructFreeList();
    addDoc(dms, docid1, bucketId1, time1);
    assertGid(gid1, 1, dms);
    assertLid(1, gid1, dms);
    EXPECT_EQ(1u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.remove(1, 0u));
    dms.commit();
    EXPECT_EQ(0u, dms.getNumUsedLids());
    EXPECT_TRUE(!dms.getGid(1, gid));
    EXPECT_TRUE(!dms.getLid(gid1, lid));
    dms.removes_complete({ 1 });
    // reuse lid
    addDoc(dms, docid2, bucketId2, time2);
    assertGid(gid2, 1, dms);
    assertLid(1, gid2, dms);
    EXPECT_EQ(1u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.remove(1, 0u));
    dms.commit();
    EXPECT_EQ(0u, dms.getNumUsedLids());
    EXPECT_TRUE(!dms.getGid(1, gid));
    EXPECT_TRUE(!dms.getLid(gid2, lid));
    dms.removes_complete({ 1 });
    EXPECT_TRUE(!dms.remove(1, 0u)); // not used
    EXPECT_TRUE(!dms.remove(2, 0u)); // outside range
}

TEST(DocumentMetaStoreTest, full_docids_can_be_inserted_and_retrieved)
{
    DocumentMetaStore dms(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms.constructFreeList();
    uint32_t lid1 = 1;
    uint32_t lid2 = 2;
    // put()
    assertPut(bucketId1, time1, lid1, docid1, dms);
    assertPut(bucketId2, time2, lid2, docid2, dms);

    // get_docid_string()
    EXPECT_EQ(docid1.toString(), dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));

    EXPECT_EQ(docid1.toString(), dms.get_document_id_string_view(lid1));
    EXPECT_EQ(docid2.toString(), dms.get_document_id_string_view(lid2));

    // already inserted
    assertPut(bucketId1, time1, lid1, docid1, dms);
    assertPut(bucketId2, time2, lid2, docid2, dms);

    // get_docid_string()
    EXPECT_EQ(docid1.toString(), dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));

    EXPECT_EQ(docid1.toString(), dms.get_document_id_string_view(lid1));
    EXPECT_EQ(docid2.toString(), dms.get_document_id_string_view(lid2));
}

TEST(DocumentMetaStoreTest, full_docids_are_removed)
{
    DocumentMetaStore dms(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms.constructFreeList();

    // Add document and get gid
    uint32_t lid1 = addDoc(dms, docid1, bucketId1, time1);
    EXPECT_EQ(1u, lid1);
    EXPECT_EQ(docid1.toString(), dms.get_docid_string(gid1));
    EXPECT_EQ(docid1.toString(), dms.get_document_id_string_view(lid1));
    // Remove and check that full string is also removed
    EXPECT_TRUE(dms.remove(lid1, 0u));
    dms.commit();
    EXPECT_EQ("", dms.get_docid_string(gid1));
    EXPECT_EQ("", dms.get_document_id_string_view(lid1));
    dms.removes_complete({ lid1 });

    // With reused lid
    uint32_t lid2 = addDoc(dms, docid2, bucketId2, time2);
    EXPECT_EQ(1u, lid2);
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
    EXPECT_EQ(docid2.toString(), dms.get_document_id_string_view(lid2));
    EXPECT_TRUE(dms.remove(lid2, 0u));
    dms.commit();
    EXPECT_EQ("", dms.get_docid_string(gid2));
    EXPECT_EQ("", dms.get_document_id_string_view(lid2));
    dms.removes_complete({ lid2 });
}

TEST(DocumentMetaStore, generation_handling_is_working)
{
    auto dms = std::make_shared<DocumentMetaStore>(createBucketDB());
    dms->constructFreeList();
    const GenerationHandler & gh = dms->getGenerationHandler();
    EXPECT_EQ(Generation(1u), gh.getCurrentGeneration());
    addDoc(*dms, docid1, bucketId1, time1);
    EXPECT_EQ(Generation(2u), gh.getCurrentGeneration());
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    {
        AttributeGuard g1(dms);
        EXPECT_EQ(1u, gh.getGenerationRefCount());
        {
            AttributeGuard g2(dms);
            EXPECT_EQ(2u, gh.getGenerationRefCount());
        }
        EXPECT_EQ(1u, gh.getGenerationRefCount());
    }
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    dms->remove(1, 0u);
    dms->removes_complete({ 1 });
    EXPECT_EQ(Generation(3u), gh.getCurrentGeneration());
}

TEST(DocumentMetaStore, generation_handling_is_working_for_full_document_ids)
{
    auto dms = std::make_shared<DocumentMetaStore>(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms->constructFreeList();

    assertPut(bucketId1, time1, 1u, docid1, *dms);
    EXPECT_EQ(docid1.toString(), dms->get_docid_string(gid1));
    vespalib::MemoryUsage memory_usage;
    {
        AttributeGuard g1(dms);
        auto str_view = dms->get_docid_string(gid1);
        EXPECT_EQ(docid1.toString(), str_view);
        dms->remove(1u, 0u);
        EXPECT_EQ("", dms->get_docid_string(gid1));
        dms->incGeneration();
        memory_usage = dms->get_docid_memory_usage();
        dms->reclaim_unused_memory(); // Nothing to reclaim yet
        EXPECT_EQ(docid1.toString(), str_view); // Check that str_view still contains the docid
        EXPECT_EQ(memory_usage, dms->get_docid_memory_usage());
    }
    dms->reclaim_unused_memory(); // Reclaim now
    EXPECT_NE(memory_usage, dms->get_docid_memory_usage());
    dms->removes_complete({ 1 });
}

TEST(DocumentMetaStoreTest, lid_and_gid_space_is_reused)
{
    auto dms = std::make_shared<DocumentMetaStore>(createBucketDB());
    dms->constructFreeList();
    EXPECT_EQ(1u, dms->getNumDocs());
    EXPECT_EQ(0u, dms->getNumUsedLids());
    assertPut(bucketId1, time1, 1, docid1, *dms); // -> gen 1
    EXPECT_EQ(2u, dms->getNumDocs());
    EXPECT_EQ(1u, dms->getNumUsedLids());
    assertPut(bucketId2, time2, 2, docid2, *dms); // -> gen 2
    EXPECT_EQ(3u, dms->getNumDocs());
    EXPECT_EQ(2u, dms->getNumUsedLids());
    dms->remove(2, 0u); // -> gen 3
    dms->removes_complete({ 2 }); // -> gen 4
    EXPECT_EQ(3u, dms->getNumDocs());
    EXPECT_EQ(1u, dms->getNumUsedLids());
    // -> gen 5 (reuse of lid 2)
    assertPut(bucketId3, time3, 2, docid3, *dms);
    EXPECT_EQ(3u, dms->getNumDocs());
    EXPECT_EQ(2u, dms->getNumUsedLids()); // reuse
    assertGid(gid3, 2, *dms);
    {
        AttributeGuard g1(dms); // guard on gen 5
        dms->remove(2, 0u);
        dms->removes_complete({ 2 });
        EXPECT_EQ(3u, dms->getNumDocs());
        EXPECT_EQ(1u, dms->getNumUsedLids()); // lid 2 free but guarded
        assertPut(bucketId4, time4, 3, docid4, *dms);
        EXPECT_EQ(4u, dms->getNumDocs()); // generation guarded, new lid
        EXPECT_EQ(2u, dms->getNumUsedLids());
        assertGid(gid4, 3, *dms);
    }
    assertPut(bucketId5, time5, 4, docid5, *dms);
    EXPECT_EQ(5u, dms->getNumDocs()); // reuse blocked by previous guard. released at end of put()
    EXPECT_EQ(3u, dms->getNumUsedLids());
    assertGid(gid5, 4, *dms);
    assertPut(bucketId2, time2, 2, docid2, *dms); // reuse of lid 2
    EXPECT_EQ(5u, dms->getNumDocs());
    EXPECT_EQ(4u, dms->getNumUsedLids());
    assertGid(gid2, 2, *dms);
}

DocumentId
createDocId(uint32_t lid)
{
    DocumentId docId(vespalib::make_string("id:ns:testdoc::%u", lid));
    return docId;
}

DocumentId
createDocId(uint32_t userId, uint32_t lid)
{
    DocumentId docId(vespalib::make_string("id:ns:testdoc:n=%u:%u", userId, lid));
    return docId;
}

TEST(DocumentMetaStoreTest, can_store_bucket_id_and_timestamp)
{
    DocumentMetaStore dms(createBucketDB());
    uint32_t numLids = 1000;

    dms.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        uint32_t addLid = addDoc(dms, docid, bucketId, Timestamp(lid + timestampBias));
        EXPECT_EQ(lid, addLid);
    }
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        assertGid(gid, lid, dms, bucketId,
                  Timestamp(lid + timestampBias));
        assertLid(lid, gid, dms);
    }
}

TEST(DocumentMetaStoreTest, can_store_full_document_id)
{
    DocumentMetaStore dms(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    uint32_t numLids = 1000;

    dms.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        uint32_t addLid = addDoc(dms, docid, bucketId, Timestamp(lid + timestampBias));
        EXPECT_EQ(lid, addLid);
    }
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        EXPECT_EQ(docid.toString(), dms.get_docid_string(gid));
    }
}

TEST(DocumentMetaStoreTest, gids_can_be_saved_and_loaded)
{
    DocumentMetaStore dms1(createBucketDB());
    uint32_t numLids = 1000;
    std::vector<uint32_t> removeLids;
    removeLids.push_back(10);
    removeLids.push_back(20);
    removeLids.push_back(100);
    removeLids.push_back(500);
    dms1.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        uint32_t addLid = addDoc(dms1, docid, bucketId, Timestamp(lid + timestampBias));
        EXPECT_EQ(lid, addLid);
    }
    for (uint32_t lid : removeLids) {
        dms1.remove(lid, 0u);
        dms1.removes_complete({ lid });
    }
    uint64_t expSaveBytesSize = DocumentMetaStore::minHeaderLen +
                                (1000 - 4) * DocumentMetaStore::entrySize;
    EXPECT_EQ(expSaveBytesSize, dms1.getEstimatedSaveByteSize());
    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    EXPECT_TRUE(dms1.save(saveTarget, "documentmetastore2"));

    DocumentMetaStore dms2(createBucketDB(), "documentmetastore2");
    EXPECT_TRUE(dms2.load());
    dms2.constructFreeList();
    EXPECT_EQ(numLids + 1, dms2.getNumDocs());
    EXPECT_EQ(numLids - 4, dms2.getNumUsedLids()); // 4 removed
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        const auto& gid = docid.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        if (std::count(removeLids.begin(), removeLids.end(), lid) == 0) {
            assertGid(gid, lid, dms2, bucketId,
                      Timestamp(lid + timestampBias));
            assertLid(lid, gid, dms2);
        } else {
            LOG(info, "Lid %u was removed before saving", lid);
            uint32_t myLid;
            GlobalId myGid;
            EXPECT_TRUE(!dms2.getGid(lid, myGid));
            EXPECT_TRUE(!dms2.getLid(gid, myLid));
        }
    }
    // check we can re-use from free list after load
    for (size_t i = 0; i < removeLids.size(); ++i) {
        LOG(info, "Re-use remove lid %u", removeLids[i]);
        const auto docid = createDocId(removeLids[i]);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(numBucketBits,
                          gid.convertToBucketId().getRawId());
        // re-use removeLid[i]
        uint32_t addLid = addDoc(dms2, docid, bucketId, Timestamp(43u + i));
        EXPECT_EQ(removeLids[i], addLid);
        EXPECT_EQ(numLids + 1, dms2.getNumDocs());
        EXPECT_EQ(numLids - (3 - i), dms2.getNumUsedLids());
    }
    std::filesystem::remove(std::filesystem::path("documentmetastore2.dat"));
}

TEST(DocumentMetaStoreTest, bucket_used_bits_are_lbounded_at_load_time)
{
    DocumentMetaStore dms1(createBucketDB());
    dms1.constructFreeList();

    constexpr uint32_t lid = 1;
    const auto docid = createDocId(lid);
    auto& gid = docid.getGlobalId();
    BucketId bucketId(gid.convertToBucketId());
    bucketId.setUsedBits(storage::spi::BucketLimits::MinUsedBits - 1);
    uint32_t added_lid = addDoc(dms1, docid, bucketId, Timestamp(1000));
    ASSERT_EQ(added_lid, lid);

    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    ASSERT_TRUE(dms1.save(saveTarget, "documentmetastore2"));

    DocumentMetaStore dms2(createBucketDB(), "documentmetastore2");
    ASSERT_TRUE(dms2.load());
    ASSERT_EQ(dms2.getNumDocs(), 2); // Incl. zero LID

    BucketId expected_bucket(storage::spi::BucketLimits::MinUsedBits, gid.convertToBucketId().getRawId());
    assertGid(gid, lid, dms2, expected_bucket, Timestamp(1000));
    std::filesystem::remove(std::filesystem::path("documentmetastore2.dat"));
}

TEST(DocumentMetaStore, stats_are_updated)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    size_t perGidUsed = sizeof(uint32_t) + GlobalId::LENGTH;
    EXPECT_EQ(1u, dms.getStatus().getNumDocs());
    EXPECT_EQ(1u, dms.getStatus().getNumValues());
    uint64_t lastAllocated = dms.getStatus().getAllocated();
    uint64_t lastUsed = dms.getStatus().getUsed();
    EXPECT_GT(lastAllocated, perGidUsed);
    EXPECT_GT(lastUsed, perGidUsed);

    std::this_thread::sleep_for(6000ms);
    addDoc(dms, docid1, bucketId1, time1);
    EXPECT_EQ(2u, dms.getStatus().getNumDocs());
    EXPECT_EQ(2u, dms.getStatus().getNumValues());
    EXPECT_GE(dms.getStatus().getAllocated(), lastAllocated);
    EXPECT_GE(dms.getStatus().getAllocated(), lastUsed);
    EXPECT_GT(dms.getStatus().getUsed(), lastUsed);
    EXPECT_GT(dms.getStatus().getUsed(), 2 * perGidUsed);
    lastAllocated = dms.getStatus().getAllocated();
    lastUsed = dms.getStatus().getUsed();

    addDoc(dms, docid2, bucketId2, time2);
    dms.commit(CommitParam::UpdateStats::FORCE);
    EXPECT_EQ(3u, dms.getStatus().getNumDocs());
    EXPECT_EQ(3u, dms.getStatus().getNumValues());
    EXPECT_GE(dms.getStatus().getAllocated(), lastAllocated);
    EXPECT_GE(dms.getStatus().getAllocated(), lastUsed);
    EXPECT_GT(dms.getStatus().getUsed(), lastUsed);
    EXPECT_GT(dms.getStatus().getUsed(), 3 * perGidUsed);
    LOG(info,
        "stats after 2 gids added: allocated %d, used is %d > %d (3 * %d)",
        static_cast<int>(dms.getStatus().getAllocated()),
        static_cast<int>(dms.getStatus().getUsed()),
        static_cast<int>(3 * perGidUsed),
        static_cast<int>(perGidUsed));
}

TEST(DocumentMetaStoreTest, can_put_and_remove_before_free_list_construct)
{
    DocumentMetaStore dms(createBucketDB());
    EXPECT_TRUE(dms.put(docid4, bucketId4, time4, docSize4, 4, 0u).ok());
    dms.commit();
    assertLid(4, gid4, dms);
    assertGid(gid4, 4, dms);
    EXPECT_EQ(1u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
    EXPECT_TRUE(dms.put(docid1, bucketId1, time1, docSize1, 1, 0u).ok());
    dms.commit();
    // already there, nothing changes
    EXPECT_TRUE(dms.put(docid1, bucketId1, time1, docSize1, 1, 0u).ok());
    dms.commit();
    assertLid(1, gid1, dms);
    assertGid(gid1, 1, dms);
    EXPECT_EQ(2u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
    // gid1 already there with lid 1
    EXPECT_THROW(dms.put(docid1, bucketId1, time1, docSize1, 2, 0u).ok(),
                 vespalib::IllegalStateException);
    EXPECT_THROW(dms.put(docid5, bucketId5, time5, docSize5, 1, 0u).ok(),
                 vespalib::IllegalStateException);
    assertLid(1, gid1, dms);
    assertGid(gid1, 1, dms);
    EXPECT_EQ(2u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
    EXPECT_TRUE(dms.remove(4, 0u)); // -> goes to free list. cleared and re-applied in constructFreeList().
    dms.commit();
    uint32_t lid;
    GlobalId gid;
    EXPECT_TRUE(!dms.getLid(gid4, lid));
    EXPECT_TRUE(!dms.getGid(4, gid));
    EXPECT_EQ(1u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
    dms.constructFreeList();
    EXPECT_EQ(1u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
    assertPut(bucketId2, time2, 2, docid2, dms);
    assertPut(bucketId3, time3, 3, docid3, dms);
    EXPECT_EQ(3u, dms.getNumUsedLids());
    EXPECT_EQ(5u, dms.getNumDocs());
}

void
requireThatBasicBucketInfoWorks()
{
    DocumentMetaStore dms(createBucketDB());
    using Elem = std::pair<BucketId, GlobalId>;
    using Map = std::map<Elem, Timestamp>;
    Map m;
    uint32_t numLids = 2000;
    dms.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        Timestamp timestamp(UINT64_C(123456789) * lid);
        Timestamp oldTimestamp;
        BucketId bucketId(minNumBits, gid.convertToBucketId().getRawId());
        uint32_t addLid = addDoc(dms, docid, bucketId, timestamp);
        EXPECT_EQ(lid, addLid);
        m[std::make_pair(bucketId, gid)] = timestamp;
    }
    for (uint32_t lid = 2; lid <= numLids; lid += 7) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        Timestamp timestamp(UINT64_C(14735) * lid);
        Timestamp oldTimestamp;
        BucketId bucketId(minNumBits, gid.convertToBucketId().getRawId());
        uint32_t addLid = addDoc(dms, docid, bucketId, timestamp);
        EXPECT_EQ(lid, addLid);
        m[std::make_pair(bucketId, gid)] = timestamp;
    }
    for (uint32_t lid = 3; lid <= numLids; lid += 5) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(minNumBits, gid.convertToBucketId().getRawId());
        EXPECT_TRUE(dms.remove(lid, 0u));
        dms.removes_complete({ lid });
        m.erase(std::make_pair(bucketId, gid));
    }
    assert(!m.empty());
    BucketState cksum;
    BucketId prevBucket = m.begin()->first.first;
    uint32_t cnt = 0u;
    uint32_t maxcnt = 0u;
    bucketdb::Guard bucketDB = dms.getBucketDB().takeGuard();
    for (const auto & e : m) {
        if (e.first.first == prevBucket) {
            cksum.add(e.first.second, e.second, 1, SubDbType::READY);
            ++cnt;
        } else {
            BucketInfo bi = bucketDB->get(prevBucket);
            EXPECT_EQ(cnt, bi.getDocumentCount());
            EXPECT_EQ(cksum.getChecksum(), bi.getChecksum());
            prevBucket = e.first.first;
            cksum = BucketState();
            cksum.add(e.first.second, e.second, 1, SubDbType::READY);
            maxcnt = std::max(maxcnt, cnt);
            cnt = 1u;
        }
    }
    maxcnt = std::max(maxcnt, cnt);
    BucketInfo bi = bucketDB->get(prevBucket);
    EXPECT_EQ(cnt, bi.getDocumentCount());
    EXPECT_EQ(cksum.getChecksum(), bi.getChecksum());
    LOG(info, "Largest bucket: %u elements", maxcnt);
}

TEST(DocumentMetaStoreTest, basic_bucket_info_works)
{
    BucketState::setChecksumType(BucketState::ChecksumType::LEGACY);
    requireThatBasicBucketInfoWorks();
    BucketState::setChecksumType(BucketState::ChecksumType::XXHASH64);
    requireThatBasicBucketInfoWorks();
}

TEST(DocumentMetaStoreTest, can_retrieve_list_of_lids_from_bucket_id)
{
    using LidVector = std::vector<uint32_t>;
    using Map = std::map<BucketId, LidVector>;
    DocumentMetaStore dms(createBucketDB());
    const uint32_t bucketBits = 2; // -> 4 buckets
    uint32_t numLids = 1000;
    Map m;

    dms.constructFreeList();
    // insert global ids
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        const auto docid = createDocId(lid);
        auto& gid = docid.getGlobalId();
        BucketId bucketId(bucketBits,
                          gid.convertToBucketId().getRawId());
        uint32_t addLid = addDoc(dms, docid, bucketId, Timestamp(0));
        EXPECT_EQ(lid, addLid);
        m[bucketId].push_back(lid);
    }

    // Verify that bucket id x has y lids
    EXPECT_EQ(4u, m.size());
    for (const auto & e : m) {
        const BucketId &bucketId = e.first;
        const LidVector &expLids = e.second;
        LOG(info, "Verify that bucket id '%s' has %zu lids",
            bucketId.toString().c_str(), expLids.size());
        LidVector actLids;
        dms.getLids(bucketId, actLids);
        EXPECT_EQ(expLids.size(), actLids.size());
        for (uint32_t lid : expLids) {
            EXPECT_TRUE(std::find(actLids.begin(), actLids.end(), lid) != actLids.end());
        }
    }

    // Remove and verify empty buckets
    for (const auto & e : m) {
        const BucketId &bucketId = e.first;
        const LidVector &expLids = e.second;
        for (uint32_t lid : expLids) {
            EXPECT_TRUE(dms.remove(lid, 0u));
            dms.removes_complete({ lid });
        }
        LOG(info, "Verify that bucket id '%s' has 0 lids", bucketId.toString().c_str());
        LidVector actLids;
        dms.getLids(bucketId, actLids);
        EXPECT_TRUE(actLids.empty());
    }
}

struct Comparator {
    bool operator() (const DocumentMetadata &lhs, const DocumentMetadata &rhs) const {
        return lhs.lid < rhs.lid;
    }
};

struct UserDocFixture {
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    DocumentMetaStore dms;
    std::vector<DocumentId> docids;
    BucketId bid1;
    BucketId bid2;
    BucketId bid3;
    bucketdb::BucketDBHandler _bucketDBHandler;
    UserDocFixture();
    explicit UserDocFixture(bool store_docid);
    ~UserDocFixture();
    void addDocumentId(const DocumentId& docid, uint32_t expLid, uint32_t timestampConst = 100) {
        uint32_t actLid = addDoc(dms, docid, Timestamp(expLid + timestampConst));
        EXPECT_EQ(expLid, actLid);
    }
    void addDocumentIds(size_t numGids=7) __attribute__((noinline));
};

UserDocFixture::UserDocFixture()
    : UserDocFixture(false)
{
}

UserDocFixture::UserDocFixture(bool store_docid)
    : _bucketDB(createBucketDB()),
      dms(_bucketDB, DocumentMetaStore::getFixedName(), search::GrowStrategy(), store_docid, SubDbType::READY),
      docids(), bid1(), bid2(), bid3(),
      _bucketDBHandler(*_bucketDB)
{
    _bucketDBHandler.addDocumentMetaStore(&dms, 0);
    docids.push_back(createDocId(10, 1));
    docids.push_back(createDocId(10, 2));
    docids.push_back(createDocId(20, 3));
    docids.push_back(createDocId(10, 4));
    docids.push_back(createDocId(10, 5));
    docids.push_back(createDocId(20, 6));
    docids.push_back(createDocId(20, 7));
    docids.push_back(createDocId(30, 8)); // extra
    docids.push_back(createDocId(10, 9)); // extra
    // 3 users -> 3 buckets
    bid1 = BucketId(minNumBits, docids[0].getGlobalId().convertToBucketId().getRawId());
    bid2 = BucketId(minNumBits, docids[2].getGlobalId().convertToBucketId().getRawId());
    bid3 = BucketId(minNumBits, docids[7].getGlobalId().convertToBucketId().getRawId());
    EXPECT_EQ(store_docid, dms.can_populate_document_metadata_docid());
}
UserDocFixture::~UserDocFixture() = default;

void
UserDocFixture::addDocumentIds(size_t numGids) {
    for (size_t i = 0; i < numGids; ++i) {
        uint32_t expLid = i + 1;
        addDocumentId(docids[i], expLid);
    }
}

TEST(DocumentMetaStoreTest, can_retrieve_list_of_metadata_from_bucket_id)
{
    UserDocFixture f(true);
    { // empty bucket
        DocumentMetadata::Vector result;
        f.dms.getMetadata(f.bid1, result, false);
        EXPECT_EQ(0u, result.size());
    }
    f.dms.constructFreeList();
    f.addDocumentIds();
    { // verify bucket 1
        DocumentMetadata::Vector result;
        f.dms.getMetadata(f.bid1, result, true);
        std::sort(result.begin(), result.end(), Comparator());
        EXPECT_EQ(4u, result.size());
        assertMetadata(DocumentMetadata(1, Timestamp(101), f.bid1, f.docids[0].getGlobalId(), false,
                                        f.docids[0].toString()), result[0]);
        assertMetadata(DocumentMetadata(2, Timestamp(102), f.bid1, f.docids[1].getGlobalId(), false,
                                        f.docids[1].toString()), result[1]);
        assertMetadata(DocumentMetadata(4, Timestamp(104), f.bid1, f.docids[3].getGlobalId(), false,
                                        f.docids[3].toString()), result[2]);
        assertMetadata(DocumentMetadata(5, Timestamp(105), f.bid1, f.docids[4].getGlobalId(), false,
                                        f.docids[4].toString()), result[3]);
    }
    { // verify bucket 2
        DocumentMetadata::Vector result;
        f.dms.getMetadata(f.bid2, result, true);
        std::sort(result.begin(), result.end(), Comparator());
        EXPECT_EQ(3u, result.size());
        assertMetadata(DocumentMetadata(3, Timestamp(103), f.bid2, f.docids[2].getGlobalId(), false,
                                        f.docids[2].toString()), result[0]);
        assertMetadata(DocumentMetadata(6, Timestamp(106), f.bid2, f.docids[5].getGlobalId(), false,
                                        f.docids[5].toString()), result[1]);
        assertMetadata(DocumentMetadata(7, Timestamp(107), f.bid2, f.docids[6].getGlobalId(), false,
                                        f.docids[6].toString()), result[2]);
    }
}

TEST(DocumentMetaStoreTest, bucket_state_can_be_updated)
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_EQ(1u, f.dms.getActiveLids().size()); // lid 0 is reserved

    f.addDocumentIds();
    assertActiveLids(BoolVector().F().F().F().F().F().F().F(), f.dms.getActiveLids());
    EXPECT_EQ(0u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.dms.setBucketState(f.bid1, true);
    assertActiveLids(BoolVector().T().T().F().T().T().F().F(), f.dms.getActiveLids());
    EXPECT_EQ(4u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.dms.setBucketState(f.bid2, true);
    assertActiveLids(BoolVector().T().T().T().T().T().T().T(), f.dms.getActiveLids());
    EXPECT_EQ(7u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.addDocumentId(createDocId(30, 8), 8);
    f.addDocumentId(createDocId(10, 9), 9); // bid1 is active so added document should be active as well
    assertActiveLids(BoolVector().T().T().T().T().T().T().T().F().T(), f.dms.getActiveLids());
    EXPECT_EQ(8u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());

    f.dms.setBucketState(f.bid1, false);
    assertActiveLids(BoolVector().F().F().T().F().F().T().T().F().F(), f.dms.getActiveLids());
    EXPECT_EQ(3u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());

    f.dms.setBucketState(f.bid2, false);
    assertActiveLids(BoolVector().F().F().F().F().F().F().F().F().F(), f.dms.getActiveLids());
    EXPECT_EQ(0u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());
}


TEST(DocumentMetaStoreTest, removed_lids_are_cleared_as_active)
{
    UserDocFixture f;
    f.dms.constructFreeList();
    f.addDocumentIds(2);
    f.dms.setBucketState(f.bid1, true);
    assertActiveLids(BoolVector().T().T(), f.dms.getActiveLids());
    EXPECT_EQ(2u, f.dms.getNumActiveLids());
    f.dms.remove(2, 0u);
    f.dms.removes_complete({ 2 });
    assertActiveLids(BoolVector().T().F(), f.dms.getActiveLids());
    EXPECT_EQ(1u, f.dms.getNumActiveLids());
    f.addDocumentId(f.docids[2], 2); // from bid2
    assertActiveLids(BoolVector().T().F(), f.dms.getActiveLids());
    EXPECT_EQ(1u, f.dms.getNumActiveLids());
    f.dms.remove(2, 0u);
    f.dms.removes_complete({ 2 });
    f.addDocumentId(f.docids[3], 2); // from bid1
    assertActiveLids(BoolVector().T().T(), f.dms.getActiveLids());
    EXPECT_EQ(2u, f.dms.getNumActiveLids());
}

TEST(DocumentMetaStoreTest, whitelist_blueprint_is_created)
{
    UserDocFixture f;
    f.dms.constructFreeList();
    f.addDocumentIds();

    f.dms.setBucketState(f.bid1, true);
    assertWhiteList(SimpleResult().addHit(1).addHit(2).addHit(4).addHit(5), f.dms.createWhiteListBlueprint(),
                    true, f.dms.getCommittedDocIdLimit());

    f.dms.setBucketState(f.bid2, true);
    assertWhiteList(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(4).addHit(5).addHit(6).addHit(7), f.dms.createWhiteListBlueprint(),
                    true,  f.dms.getCommittedDocIdLimit());
}

TEST(DocumentMetaStoreTest, document_and_meta_entry_count_is_updated)
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f.addDocumentIds();
    EXPECT_EQ(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQ(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQ(3u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQ(3u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f.dms.remove(3, 0u); // from bid2
    f.dms.removes_complete({ 3 });
    EXPECT_EQ(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQ(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQ(2u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQ(2u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
}

TEST(DocumentMetaStoreTest, empty_buckets_are_removed)
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.addDocumentIds(3);
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(3, 0u); // from bid2
    f.dms.removes_complete({ 3 });
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f._bucketDBHandler.handleDeleteBucket(f.bid2);
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(1, 0u); // from bid1
    f.dms.removes_complete({ 1 });
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(2, 0u); // from bid1
    f.dms.removes_complete({ 2 });
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_EQ(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    f._bucketDBHandler.handleDeleteBucket(f.bid1);
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
}

struct GlobalIdEntry {
    uint32_t lid;
    DocumentId docid;
    GlobalId gid;
    BucketId bid1;
    BucketId bid2;
    BucketId bid3;
    explicit GlobalIdEntry(uint32_t lid_) :
        lid(lid_),
        docid(createDocId(lid_)),
        gid(docid.getGlobalId()),
        bid1(1, gid.convertToBucketId().getRawId()),
        bid2(2, gid.convertToBucketId().getRawId()),
        bid3(3, gid.convertToBucketId().getRawId())
    {}
};

using GlobalIdVector = std::vector<GlobalIdEntry>;

struct MyBucketCreateListener : public IBucketCreateListener {
    std::vector<document::BucketId> _buckets;

    MyBucketCreateListener();
    ~MyBucketCreateListener() override;
    void notifyCreateBucket(const bucketdb::Guard & guard, const document::BucketId &bucket) override;
};

MyBucketCreateListener::MyBucketCreateListener() = default;

MyBucketCreateListener::~MyBucketCreateListener() = default;

void
MyBucketCreateListener::notifyCreateBucket(const bucketdb::Guard &, const document::BucketId &bucket)
{
    _buckets.emplace_back(bucket);
}

struct SplitAndJoinEmptyFixture {
    DocumentMetaStore dms;
    BucketId          bid10;
    BucketId          bid11;
    BucketId          bid20; // contained in bid10
    BucketId          bid21; // contained in bid11
    BucketId          bid22; // contained in bid10
    BucketId          bid23; // contained in bid11
    BucketId          bid30; // contained in bid10 and bid20
    BucketId          bid32; // contained in bid10 and bid22
    BucketId          bid34; // contained in bid10 and bid20
    BucketId          bid36; // contained in bid10 and bid22
    bucketdb::BucketDBHandler _bucketDBHandler;
    MyBucketCreateListener    _bucketCreateListener;

    SplitAndJoinEmptyFixture();
    ~SplitAndJoinEmptyFixture();

    BucketInfo getInfo(const BucketId &bid) const {
        return dms.getBucketDB().takeGuard()->get(bid);
    }

    void assertNotifyCreateBuckets(const std::vector<document::BucketId> & expBuckets) {
        EXPECT_EQ(expBuckets, _bucketCreateListener._buckets);
    }
    void assertBucketDBIntegrity() {
        ASSERT_TRUE(dms.getBucketDB().takeGuard()->validateIntegrity());
    }
};

SplitAndJoinEmptyFixture::SplitAndJoinEmptyFixture()
    : dms(createBucketDB()),
      bid10(1, 0), bid11(1, 1),
      bid20(2, 0), bid21(2, 1), bid22(2, 2), bid23(2, 3),
      bid30(3, 0), bid32(3, 2), bid34(3, 4), bid36(3, 6),
      _bucketDBHandler(dms.getBucketDB()),
      _bucketCreateListener()
{
    _bucketDBHandler.addDocumentMetaStore(&dms, 0);
    _bucketDBHandler.getBucketCreateNotifier().addListener(&_bucketCreateListener);
}
SplitAndJoinEmptyFixture::~SplitAndJoinEmptyFixture()
{
    _bucketDBHandler.getBucketCreateNotifier().removeListener(&_bucketCreateListener);
}


struct SplitAndJoinFixture : public SplitAndJoinEmptyFixture {
    using BucketMap = std::map<BucketId, GlobalIdVector>;
    GlobalIdVector    gids;
    BucketMap         bid1s;
    BucketMap         bid2s;
    BucketMap         bid3s;
    const GlobalIdVector *bid10Gids;
    const GlobalIdVector *bid11Gids;
    const GlobalIdVector *bid21Gids;
    const GlobalIdVector *bid23Gids;
    const GlobalIdVector *bid30Gids;
    const GlobalIdVector *bid32Gids;
    SplitAndJoinFixture();
    ~SplitAndJoinFixture();
    void insertGids1() {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            EXPECT_TRUE(dms.put(gids[i].docid, gids[i].bid1, Timestamp(0),
                                docSize,
                                gids[i].lid, 0u).ok());
        }
        dms.commit();
    }
    void insertGids2() {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            EXPECT_TRUE(dms.put(gids[i].docid, gids[i].bid2, Timestamp(0),
                                docSize,
                                gids[i].lid, 0u).ok());
        }
        dms.commit();
    }

    void
    insertGids1Mostly(const BucketId &alt)
    {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            const GlobalIdEntry &g(gids[i]);
            BucketId b(g.bid3 == alt ? g.bid2 : g.bid1);
            EXPECT_TRUE(dms.put(g.docid, b, Timestamp(0), docSize, g.lid, 0u).ok());
        }
        dms.commit();
    }

    void
    insertGids2Mostly(const BucketId &alt)
    {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            const GlobalIdEntry &g(gids[i]);
            BucketId b(g.bid3 == alt ? g.bid1 : g.bid2);
            EXPECT_TRUE(dms.put(g.docid, b, Timestamp(0), docSize, g.lid, 0u).ok());
        }
        dms.commit();
    }
};

SplitAndJoinFixture::SplitAndJoinFixture()
    : SplitAndJoinEmptyFixture(),
      gids(),
      bid1s(), bid2s(), bid3s(),
      bid10Gids(), bid11Gids(), bid21Gids(), bid23Gids(),
      bid30Gids(), bid32Gids()
{
    for (uint32_t i = 1; i <= 31; ++i) {
        gids.push_back(GlobalIdEntry(i));
        bid1s[gids.back().bid1].push_back(gids.back());
        bid2s[gids.back().bid2].push_back(gids.back());
        bid3s[gids.back().bid3].push_back(gids.back());
    }
    assert(2u == bid1s.size());
    assert(4u == bid2s.size());
    assert(8u == bid3s.size());
    bid10Gids = &bid1s[bid10];
    bid11Gids = &bid1s[bid11];
    bid21Gids = &bid2s[bid21];
    bid23Gids = &bid2s[bid23];
    bid30Gids = &bid3s[bid30];
    bid32Gids = &bid3s[bid32];
}
SplitAndJoinFixture::~SplitAndJoinFixture() {}

BoolVector
getBoolVector(const GlobalIdVector &gids, size_t sz)
{
    BoolVector retval(sz);
    for (size_t i = 0; i < gids.size(); ++i) {
        uint32_t lid(gids[i].lid);
        assert(lid <= sz && lid > 0u);
        retval[lid - 1] = true;
    }
    return retval;
}


BoolVector
getBoolVectorFiltered(const GlobalIdVector &gids, size_t sz,
                      const BucketId &skip)
{
    BoolVector retval(sz);
    for (size_t i = 0; i < gids.size(); ++i) {
        const GlobalIdEntry &g(gids[i]);
        uint32_t lid(g.lid);
        assert(lid <= sz && lid > 0u);
        if (g.bid3 == skip) {
            continue;
        }
        retval[lid - 1] = true;
    }
    return retval;
}

TEST(DocumentMetaStoreTest, bucket_info_is_correct_after_split)
{
    SplitAndJoinFixture f;
    f.insertGids1();
    BucketInfo bi10 = f.getInfo(f.bid10);
    BucketInfo bi11 = f.getInfo(f.bid11);
    LOG(info, "%s: %s", f.bid10.toString().c_str(), bi10.toString().c_str());
    LOG(info, "%s: %s", f.bid11.toString().c_str(), bi11.toString().c_str());
    assertBucketInfo(f.bid10Gids->size(), f.bid10Gids->size(), bi10);
    assertBucketInfo(f.bid11Gids->size(), f.bid11Gids->size(), bi11);
    EXPECT_NE(bi10.getEntryCount(), bi11.getEntryCount());
    EXPECT_EQ(31u, bi10.getEntryCount() + bi11.getEntryCount());

    f._bucketDBHandler.handleSplit(10, f.bid11, f.bid21, f.bid23);

    BucketInfo nbi10 = f.getInfo(f.bid10);
    BucketInfo nbi11 = f.getInfo(f.bid11);
    BucketInfo bi21 = f.getInfo(f.bid21);
    BucketInfo bi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid10.toString().c_str(), nbi10.toString().c_str());
    LOG(info, "%s: %s", f.bid11.toString().c_str(), nbi11.toString().c_str());
    LOG(info, "%s: %s", f.bid21.toString().c_str(), bi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), bi23.toString().c_str());
    assertBucketInfo(f.bid10Gids->size(),
                     f.bid10Gids->size(),
                     nbi10);
    assertBucketInfo(0u, 0u, nbi11);
    assertBucketInfo(f.bid21Gids->size(),
                     f.bid21Gids->size(),
                     bi21);
    assertBucketInfo(f.bid23Gids->size(),
                     f.bid23Gids->size(),
                     bi23);
    EXPECT_EQ(bi11.getEntryCount(),
              bi21.getEntryCount() + bi23.getEntryCount());
    EXPECT_EQ(bi11.getDocumentCount(),
              bi21.getDocumentCount() + bi23.getDocumentCount());
    f.assertNotifyCreateBuckets({ f.bid21, f.bid23 });
}

TEST(DocumentMetaStoreTest, active_state_is_preserved_after_split)
{
    { // non-active bucket
        SplitAndJoinFixture f;
        f.insertGids1();
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // active bucket
        SplitAndJoinFixture f;
        f.insertGids1();
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // non-active source, active overlapping target1
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid30);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
        f.dms.setBucketState(f.bid20, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        assertActiveLids(getBoolVector(*f.bid30Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid30Gids->size(), f.dms.getNumActiveLids());
        f.assertBucketDBIntegrity();
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        f.assertBucketDBIntegrity();
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // non-active source, active overlapping target2
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid32);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
        f.dms.setBucketState(f.bid22, true);
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid32Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid32Gids->size(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // active source, non-active overlapping target1
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid30);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid30));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQ(filtered.countTrue(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // active source, non-active overlapping target2
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid32);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid32));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQ(filtered.countTrue(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
}

TEST(DocumentMetaStoreTest, active_state_is_preserved_after_empty_split)
{
    { // non-active bucket
        SplitAndJoinEmptyFixture f;
        f._bucketDBHandler.handleCreateBucket(f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
    }
    { // active bucket
        SplitAndJoinEmptyFixture f;
        f._bucketDBHandler.handleCreateBucket(f.bid10);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
    }
}

TEST(DocumentMetaStoreTest, bucket_info_is_correct_after_join)
{
    SplitAndJoinFixture f;
    f.insertGids2();
    BucketInfo bi21 = f.getInfo(f.bid21);
    BucketInfo bi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid21.toString().c_str(), bi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), bi23.toString().c_str());
    assertBucketInfo(f.bid21Gids->size(), f.bid21Gids->size(), bi21);
    assertBucketInfo(f.bid23Gids->size(), f.bid23Gids->size(), bi23);
    EXPECT_NE(bi21.getEntryCount(), bi23.getEntryCount());
    EXPECT_EQ(f.bid11Gids->size(), bi21.getEntryCount() + bi23.getEntryCount());

    f._bucketDBHandler.handleJoin(10, f.bid21, f.bid23, f.bid11);
    BucketInfo bi11 = f.getInfo(f.bid11);
    BucketInfo nbi21 = f.getInfo(f.bid21);
    BucketInfo nbi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid11.toString().c_str(), bi11.toString().c_str());
    LOG(info, "%s: %s", f.bid21.toString().c_str(), nbi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), nbi23.toString().c_str());
    assertBucketInfo(f.bid11Gids->size(),
                     f.bid11Gids->size(), bi11);
    assertBucketInfo(0u, 0u, nbi21);
    assertBucketInfo(0u, 0u, nbi23);
    EXPECT_EQ(bi21.getEntryCount() + bi23.getEntryCount(),
              bi11.getEntryCount());
    EXPECT_EQ(bi21.getDocumentCount() +
              bi23.getDocumentCount(),
              bi11.getDocumentCount());
    f.assertNotifyCreateBuckets({ f.bid11 });
}

TEST(DocumentMetaStoreTest, active_state_is_preserved_after_join)
{
    { // non-active buckets
        SplitAndJoinFixture f;
        f.insertGids2();
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // active buckets
        SplitAndJoinFixture f;
        f.insertGids2();
        f.dms.setBucketState(f.bid20, true);
        f.dms.setBucketState(f.bid22, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // 1 active bucket
        SplitAndJoinFixture f;
        f.insertGids2();
        f.dms.setBucketState(f.bid20, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // 1 active bucket
        SplitAndJoinFixture f;
        f.insertGids2();
        f.dms.setBucketState(f.bid22, true);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // non-active buckets, active target
        SplitAndJoinFixture f;
        f.insertGids2Mostly(f.bid30);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid30Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid30Gids->size(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // non-active buckets, active target
        SplitAndJoinFixture f;
        f.insertGids2Mostly(f.bid32);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid32Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid32Gids->size(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQ(0u, f.dms.getNumActiveLids());
    }
    { // active buckets, non-active target
        SplitAndJoinFixture f;
        f.insertGids2Mostly(f.bid30);
        f.dms.setBucketState(f.bid20, true);
        f.dms.setBucketState(f.bid22, true);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid30));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQ(filtered.countTrue(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // active buckets, non-active target
        SplitAndJoinFixture f;
        f.insertGids2Mostly(f.bid32);
        f.dms.setBucketState(f.bid20, true);
        f.dms.setBucketState(f.bid22, true);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid32));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQ(filtered.countTrue(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
}

TEST(DocumentMetaStoreTest, active_state_is_preserved_after_empty_join)
{
    { // non-active buckets
        SplitAndJoinEmptyFixture f;
        f._bucketDBHandler.handleCreateBucket(f.bid20);
        f._bucketDBHandler.handleCreateBucket(f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
    }
    { // active buckets
        SplitAndJoinEmptyFixture f;
        f._bucketDBHandler.handleCreateBucket(f.bid20);
        f._bucketDBHandler.handleCreateBucket(f.bid22);
        f.dms.setBucketState(f.bid20, true);
        f.dms.setBucketState(f.bid22, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
    }
    { // 1 active bucket
        SplitAndJoinEmptyFixture f;
        f._bucketDBHandler.handleCreateBucket(f.bid20);
        f._bucketDBHandler.handleCreateBucket(f.bid22);
        f.dms.setBucketState(f.bid20, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
    }
}

TEST(DocumentMetaStoreTest, overlapping_bucket_active_state_works)
{
    SplitAndJoinFixture f;
    f.insertGids1Mostly(f.bid30);
    assertActiveLids(BoolVector(31), f.dms.getActiveLids());
    EXPECT_EQ(0u, f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid10, true);
    BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid30));
    assertActiveLids(filtered, f.dms.getActiveLids());
    EXPECT_EQ(filtered.countTrue(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid20, true);
    assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                     f.dms.getActiveLids());
    EXPECT_EQ(f.bid10Gids->size(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid10, false);
    assertActiveLids(getBoolVector(*f.bid30Gids, 31),
                     f.dms.getActiveLids());
    EXPECT_EQ(f.bid30Gids->size(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid20, false);
    assertActiveLids(BoolVector(31), f.dms.getActiveLids());
    EXPECT_EQ(0u, f.dms.getNumActiveLids());
}

struct RemovedFixture {
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    DocumentMetaStore dms;
    bucketdb::BucketDBHandler _bucketDBHandler;

    RemovedFixture();
    ~RemovedFixture();

    BucketInfo
    getInfo(const BucketId &bid) const
    {
        return dms.getBucketDB().takeGuard()->get(bid);
    }
};

RemovedFixture::RemovedFixture()
    : _bucketDB(createBucketDB()),
      dms(_bucketDB,
          DocumentMetaStore::getFixedName(),
          search::GrowStrategy(),
          SubDbType::REMOVED),
      _bucketDBHandler(dms.getBucketDB())
{
    _bucketDBHandler.addDocumentMetaStore(&dms, 0);
}
RemovedFixture::~RemovedFixture() = default;

TEST(DocumentMetaStoreTest, remove_changed_bucket_works)
{
    RemovedFixture f;
    GlobalIdEntry g(1);
    f.dms.constructFreeList();
    f._bucketDBHandler.handleCreateBucket(g.bid1);
    uint32_t addLid1 = addDoc(f.dms, g.docid, g.bid1, Timestamp(0));
    EXPECT_EQ(1u, addLid1);
    uint32_t addLid2 = addDoc(f.dms, g.docid, g.bid2, Timestamp(0));
    EXPECT_TRUE(1u == addLid2);
    EXPECT_TRUE(f.dms.remove(1u, 0u));
    f.dms.removes_complete({ 1u });
}

TEST(DocumentMetaStoreTest, get_lid_usage_stats_works)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();

    LidUsageStats s = dms.getLidUsageStats();
    EXPECT_EQ(1u, s.getLidLimit());
    EXPECT_EQ(0u, s.getUsedLids());
    EXPECT_EQ(1u, s.getLowestFreeLid());
    EXPECT_EQ(0u, s.getHighestUsedLid());

    putDoc(dms, createDocId(1), 1);

    s = dms.getLidUsageStats();
    EXPECT_EQ(2u, s.getLidLimit());
    EXPECT_EQ(1u, s.getUsedLids());
    EXPECT_EQ(2u, s.getLowestFreeLid());
    EXPECT_EQ(1u, s.getHighestUsedLid());

    putDoc(dms, createDocId(2), 2);

    s = dms.getLidUsageStats();
    EXPECT_EQ(3u, s.getLidLimit());
    EXPECT_EQ(2u, s.getUsedLids());
    EXPECT_EQ(3u, s.getLowestFreeLid());
    EXPECT_EQ(2u, s.getHighestUsedLid());


    putDoc(dms, createDocId(3), 3);

    s = dms.getLidUsageStats();
    EXPECT_EQ(4u, s.getLidLimit());
    EXPECT_EQ(3u, s.getUsedLids());
    EXPECT_EQ(4u, s.getLowestFreeLid());
    EXPECT_EQ(3u, s.getHighestUsedLid());

    dms.remove(1, 0u);
    dms.removes_complete({ 1 });

    s = dms.getLidUsageStats();
    EXPECT_EQ(4u, s.getLidLimit());
    EXPECT_EQ(2u, s.getUsedLids());
    EXPECT_EQ(1u, s.getLowestFreeLid());
    EXPECT_EQ(3u, s.getHighestUsedLid());

    dms.remove(3, 0u);
    dms.removes_complete({ 3 });

    s = dms.getLidUsageStats();
    EXPECT_EQ(4u, s.getLidLimit());
    EXPECT_EQ(1u, s.getUsedLids());
    EXPECT_EQ(1u, s.getLowestFreeLid());
    EXPECT_EQ(2u, s.getHighestUsedLid());

    dms.remove(2, 0u);
    dms.removes_complete({ 2 });

    s = dms.getLidUsageStats();
    EXPECT_EQ(4u, s.getLidLimit());
    EXPECT_EQ(0u, s.getUsedLids());
    EXPECT_EQ(1u, s.getLowestFreeLid());
    EXPECT_EQ(0u, s.getHighestUsedLid());
}

void
assertLidBloat(uint32_t expBloat, uint32_t lidLimit, uint32_t usedLids)
{
    LidUsageStats stats(lidLimit, usedLids, 0, 0);
    EXPECT_EQ(expBloat, stats.getLidBloat());
}

TEST(LidUsageStatsTest, lid_bloat_is_calculated)
{
    assertLidBloat(4, 10, 5);
    assertLidBloat(0, 1, 0);
    assertLidBloat(0, 1, 1);
}

TEST(DocumentMetaStoreTest, move_works)
{
    DocumentMetaStore dms(createBucketDB());
    GlobalId gid;
    uint32_t lid = 0u;
    dms.constructFreeList();

    EXPECT_EQ(1u, dms.getNumDocs());
    EXPECT_EQ(0u, dms.getNumUsedLids());
    assertPut(bucketId1, time1, 1u, docid1, dms);
    EXPECT_EQ(2u, dms.getNumDocs());
    EXPECT_EQ(1u, dms.getNumUsedLids());
    assertPut(bucketId2, time2, 2u, docid2, dms);
    EXPECT_EQ(3u, dms.getNumDocs());
    EXPECT_EQ(2u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_TRUE(dms.getLid(gid2, lid));
    EXPECT_EQ(gid1, gid);
    EXPECT_EQ(2u, lid);
    EXPECT_TRUE(dms.remove(1, 0u));
    dms.commit();
    EXPECT_FALSE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGidEvenIfMoved(1u, gid));
    EXPECT_TRUE(dms.getGid(2u, gid));
    dms.removes_complete({ 1u });
    EXPECT_FALSE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGidEvenIfMoved(1u, gid));
    EXPECT_TRUE(dms.getGid(2u, gid));
    EXPECT_EQ(1u, dms.getNumUsedLids());
    dms.move(2u, 1u, 0u);
    dms.commit();
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGid(2u, gid));
    EXPECT_TRUE(dms.getGidEvenIfMoved(2u, gid));
    dms.removes_complete({ 2u });
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGid(2u, gid));
    EXPECT_TRUE(dms.getGidEvenIfMoved(2u, gid));
    EXPECT_TRUE(dms.getLid(gid2, lid));
    EXPECT_EQ(gid2, gid);
    EXPECT_EQ(1u, lid);
}

TEST(DocumentMetaStoreTest, getting_full_document_id_works_after_move)
{
    DocumentMetaStore dms(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms.constructFreeList();

    assertPut(bucketId1, time1, 1u, docid1, dms);
    assertPut(bucketId2, time2, 2u, docid2, dms);
    EXPECT_EQ(docid1.toString(), dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
    EXPECT_TRUE(dms.remove(1u, 0u));
    dms.commit();
    EXPECT_EQ("", dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
    dms.removes_complete({ 1u });
    EXPECT_EQ("", dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
    dms.move(2u, 1u, 0u);
    dms.commit();
    EXPECT_EQ("", dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
    dms.removes_complete({ 2u });
    // Make sure that the lid of gid2 is 1, i.e., that the lid was moved
    uint32_t lid = 0;
    EXPECT_TRUE(dms.getLid(gid2, lid));
    EXPECT_EQ(1u, lid);
    EXPECT_EQ("", dms.get_docid_string(gid1));
    EXPECT_EQ(docid2.toString(), dms.get_docid_string(gid2));
}

void
assertLidSpace(uint32_t numDocs,
               uint32_t committedDocIdLimit,
               uint32_t numUsedLids,
               bool wantShrinkLidSpace,
               bool canShrinkLidSpace,
               const DocumentMetaStore &dms)
{
    EXPECT_EQ(numDocs, dms.getNumDocs());
    EXPECT_EQ(committedDocIdLimit, dms.getCommittedDocIdLimit());
    EXPECT_EQ(numUsedLids, dms.getNumUsedLids());
    EXPECT_EQ(wantShrinkLidSpace, dms.wantShrinkLidSpace());
    EXPECT_EQ(canShrinkLidSpace, dms.canShrinkLidSpace());
}

void
populate(uint32_t endLid, DocumentMetaStore &dms)
{
    for (uint32_t lid = 1; lid < endLid; ++lid) {
        const auto docid = createDocId(lid);
        putDoc(dms, docid, lid, Timestamp(10000 + lid));
    }
    assertLidSpace(endLid, endLid, endLid - 1, false, false, dms);
}

void
remove(uint32_t startLid, uint32_t shrinkTarget, DocumentMetaStore &dms)
{
    for (uint32_t lid = startLid; lid >= shrinkTarget; --lid) {
        dms.remove(lid, 0u);
        dms.commit();
        dms.removes_complete({ lid });
    }
}

TEST(DocumentMetaStoreTest, shrink_works)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();

    populate(10, dms);

    uint32_t shrinkTarget = 5;
    remove(9, shrinkTarget, dms);
    assertLidSpace(10, 10, shrinkTarget - 1, false, false, dms);

    dms.compactLidSpace(shrinkTarget);
    assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, dms);

    dms.holdUnblockShrinkLidSpace();
    assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, true, dms);

    dms.shrinkLidSpace();
    assertLidSpace(shrinkTarget, shrinkTarget, shrinkTarget - 1, false, false, dms);
}

TEST(DocumentMetaStoreTest, shrink_via_flush_target_works)
{
    auto dms = std::make_shared<DocumentMetaStore>(createBucketDB());
    dms->constructFreeList();
    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    DummyTlsSyncer dummyTlsSyncer;
    HwInfo hwInfo;
    using Type = IFlushTarget::Type;
    using Component = IFlushTarget::Component;
    IFlushTarget::SP ft(std::make_shared<ShrinkLidSpaceFlushTarget>
                        ("documentmetastore.shrink", Type::GC, Component::ATTRIBUTE, 0, IFlushTarget::Time(), dms));
    populate(10, *dms);

    uint32_t shrinkTarget = 5;
    remove(9, shrinkTarget, *dms);
    assertLidSpace(10, 10, shrinkTarget - 1, false, false, *dms);
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());

    dms->compactLidSpace(shrinkTarget);
    assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, *dms);
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());
    auto g = std::make_shared<AttributeGuard>(dms);

    dms->holdUnblockShrinkLidSpace();
    assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, *dms);
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());

    g.reset();
    dms->reclaim_unused_memory();
    assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, true, *dms);
    EXPECT_TRUE(ft->getApproxMemoryGain().getBefore() >
                ft->getApproxMemoryGain().getAfter());

    vespalib::ThreadStackExecutor exec(1);
    vespalib::Executor::Task::UP task = ft->initFlush(11, std::make_shared<search::FlushToken>());
    exec.execute(std::move(task));
    exec.sync();
    exec.shutdown();
    assertLidSpace(shrinkTarget, shrinkTarget, shrinkTarget - 1, false, false, *dms);
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());
}

namespace {

void
addLid(DocumentMetaStore &dms, uint32_t lid, uint32_t docSize = 1)
{
    const auto docid = createDocId(lid);
    auto& gid = docid.getGlobalId();
    BucketId bucketId(gid.convertToBucketId());
    bucketId.setUsedBits(numBucketBits);
    uint32_t addedLid = addDoc(dms, docid, bucketId, Timestamp(lid + timestampBias), docSize);
    EXPECT_EQ(lid, addedLid);
}

void
assertSize(DocumentMetaStore &dms, uint32_t lid, uint32_t expSize)
{
    EXPECT_TRUE(dms.validLid(lid));
    const auto &metadata = dms.getRawMetadata(lid);
    EXPECT_EQ(expSize, metadata.getDocSize());
}

void
removeLid(DocumentMetaStore &dms, uint32_t lid)
{
    dms.remove(lid, 0u);
    dms.removes_complete({ lid });
}

void
assertCompact(DocumentMetaStore &dms, uint32_t docIdLimit,
              uint32_t committedDocIdLimit,
              uint32_t compactTarget, uint32_t numUsedLids)
{
    assertLidSpace(docIdLimit, committedDocIdLimit, numUsedLids, false, false, dms);
    dms.compactLidSpace(compactTarget);
    assertLidSpace(docIdLimit, compactTarget, numUsedLids, true, false, dms);
    dms.holdUnblockShrinkLidSpace();
    assertLidSpace(docIdLimit, compactTarget, numUsedLids, true, true, dms);
}

void
assertShrink(DocumentMetaStore &dms, uint32_t shrinkTarget, uint32_t numUsedLids)
{
    dms.shrinkLidSpace();
    assertLidSpace(shrinkTarget, shrinkTarget, numUsedLids, false, false, dms);
}

}

TEST(DocumentMetaStoreTest, second_shrink_works_after_compact_and_inactive_insert)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    addLid(dms, 1);
    addLid(dms, 2);
    addLid(dms, 3);
    removeLid(dms, 2);
    removeLid(dms, 3);
    assertLidSpace(4, 4, 1, false, false, dms);
    assertCompact(dms, 4, 4, 2, 1);
    addLid(dms, 2);
    assertShrink(dms, 3, 2);
    removeLid(dms, 2);
    assertCompact(dms, 3, 3, 2, 1);
    assertShrink(dms, 2, 1);
}

TEST(DocumentMetaStoreTest, document_sizes_are_saved)
{
    DocumentMetaStore dms1(createBucketDB());
    dms1.constructFreeList();
    addLid(dms1, 1, 100);
    addLid(dms1, 2, 10000);
    addLid(dms1, 3, 100000000);
    assertSize(dms1, 1, 100);
    assertSize(dms1, 2, 10000);
    assertSize(dms1, 3, (1u << 24) - 1);

    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    EXPECT_TRUE(dms1.save(saveTarget, "documentmetastore3"));
    dms1.setTrackDocumentSizes(false);
    EXPECT_TRUE(dms1.save(saveTarget, "documentmetastore4"));

    DocumentMetaStore dms3(createBucketDB(), "documentmetastore3");
    EXPECT_TRUE(dms3.load());
    dms3.constructFreeList();
    assertSize(dms3, 1, 100);
    assertSize(dms3, 2, 10000);
    assertSize(dms3, 3, (1u << 24) - 1);

    DocumentMetaStore dms4(createBucketDB(), "documentmetastore4");
    EXPECT_TRUE(dms4.load());
    dms4.constructFreeList();
    assertSize(dms4, 1, 1);
    assertSize(dms4, 2, 1);
    assertSize(dms4, 3, 1);
    std::filesystem::remove(std::filesystem::path("documentmetastore3.dat"));
    std::filesystem::remove(std::filesystem::path("documentmetastore4.dat"));
}

TEST(DocumentMetaStoreTest, full_document_ids_are_saved)
{
    DocumentMetaStore dms1(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms1.constructFreeList();
    addLid(dms1, 1);
    addLid(dms1, 2);
    addLid(dms1, 3);

    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    EXPECT_TRUE(dms1.save(saveTarget, "documentmetastore5"));

    DocumentMetaStore dms_loaded_docids(createBucketDB(), "documentmetastore5", search::GrowStrategy(), true, SubDbType::READY);
    EXPECT_TRUE(dms_loaded_docids.load());
    dms_loaded_docids.constructFreeList();

    auto d1 = createDocId(1);
    EXPECT_EQ(d1.toString(), dms_loaded_docids.get_docid_string(d1.getGlobalId()));
    auto d2 = createDocId(2);
    EXPECT_EQ(d2.toString(), dms_loaded_docids.get_docid_string(d2.getGlobalId()));
    auto d3 = createDocId(3);
    EXPECT_EQ(d3.toString(), dms_loaded_docids.get_docid_string(d3.getGlobalId()));

    std::filesystem::remove(std::filesystem::path("documentmetastore5.dat"));
    std::filesystem::remove(std::filesystem::path("documentmetastore5.docids.dat"));
}

namespace {

void
assertLidGidFound(uint32_t lid, DocumentMetaStore &dms)
{
    const auto docid = createDocId(lid);
    auto& gid = docid.getGlobalId();
    assertLid(lid, gid, dms);
    assertGid(gid, lid, dms);
    EXPECT_TRUE(dms.validLid(lid));
}

void
assertLidGidNotFound(uint32_t lid, DocumentMetaStore &dms)
{
    const auto docid = createDocId(lid);
    auto& gid = docid.getGlobalId();
    uint32_t resultLid;
    GlobalId resultGid;
    EXPECT_FALSE(dms.getLid(gid, resultLid));
    EXPECT_FALSE(dms.getGid(lid, resultGid));
    EXPECT_FALSE(dms.validLid(lid));
}

}

TEST(DocumentMetaStoreTest, multiple_lids_can_be_removed_with_removeBatch)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    addLid(dms, 1);
    addLid(dms, 2);
    addLid(dms, 3);
    addLid(dms, 4);

    assertLidGidFound(1, dms);
    assertLidGidFound(2, dms);
    assertLidGidFound(3, dms);
    assertLidGidFound(4, dms);

    dms.removeBatch({1, 3}, 5);
    dms.commit();
    assertLidGidNotFound(1, dms);
    assertLidGidFound(2, dms);
    assertLidGidNotFound(3, dms);
    assertLidGidFound(4, dms);
    dms.removes_complete({1, 3});
}

TEST(DocumentMetaStoreTest, removeBatch_removes_full_document_ids)
{
    DocumentMetaStore dms(createBucketDB(), "[documentmetastore]", search::GrowStrategy(), true, SubDbType::READY);
    dms.constructFreeList();
    addLid(dms, 1);
    addLid(dms, 2);
    addLid(dms, 3);
    addLid(dms, 4);

    dms.removeBatch({1, 3}, 5);
    dms.commit();

    DocumentId id1, id2, id3, id4;
    id1 = createDocId(1);
    id2 = createDocId(2);
    id3 = createDocId(3);
    id4 = createDocId(4);

    EXPECT_EQ("", dms.get_docid_string(id1.getGlobalId()));
    EXPECT_EQ(id2.toString(), dms.get_docid_string(id2.getGlobalId()));
    EXPECT_EQ("", dms.get_docid_string(id3.getGlobalId()));
    EXPECT_EQ(id4.toString(), dms.get_docid_string(id4.getGlobalId()));
    dms.removes_complete({1, 3});
}

TEST(DocumentMetaStoreTest, serialize_for_sort)
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    addLid(dms, 1);
    addLid(dms, 2);
    assertLidGidFound(1, dms);
    assertLidGidFound(2, dms);

    constexpr size_t SZ = document::GlobalId::LENGTH;
    EXPECT_EQ(12u, SZ);
    EXPECT_EQ(SZ, dms.getFixedWidth());
    uint8_t asc_dest[SZ];
    auto asc_writer = dms.make_sort_blob_writer(true, nullptr, MissingPolicy::DEFAULT, std::string_view());
    EXPECT_EQ(0, asc_writer->write(3, asc_dest, sizeof(asc_dest)));
    EXPECT_EQ(-1, asc_writer->write(1, asc_dest, sizeof(asc_dest) - 1));
    document::GlobalId gid;

    EXPECT_EQ(SZ, asc_writer->write(1, asc_dest, sizeof(asc_dest)));
    EXPECT_TRUE(dms.getGid(1, gid));
    EXPECT_EQ(0, memcmp(asc_dest, gid.get(), SZ));

    EXPECT_EQ(SZ, asc_writer->write(2, asc_dest, sizeof(asc_dest)));
    EXPECT_TRUE(dms.getGid(2, gid));
    EXPECT_EQ(0, memcmp(asc_dest, gid.get(), SZ));

    uint8_t desc_dest[SZ];
    auto desc_writer = dms.make_sort_blob_writer(false, nullptr, MissingPolicy::DEFAULT, std::string_view());
    EXPECT_EQ(SZ, desc_writer->write(2, desc_dest, sizeof(desc_dest)));
    for (size_t i(0); i < SZ; i++) {
        EXPECT_EQ(0xff - asc_dest[i], desc_dest[i]);
    }
}

class MockOperationListener : public documentmetastore::OperationListener {
public:
    size_t remove_batch_cnt;
    size_t remove_cnt;

    MockOperationListener() noexcept
        : remove_batch_cnt(0),
          remove_cnt(0)
    {
    }
    void notify_remove_batch() override { ++remove_batch_cnt; }
    void notify_remove() override { ++remove_cnt; }
};

TEST(DocumentMetaStoreTest, call_to_remove_batch_is_notified)
{
    DocumentMetaStore dms(createBucketDB());
    auto listener = std::make_shared<MockOperationListener>();
    dms.set_operation_listener(listener);
    dms.constructFreeList();
    addLid(dms, 1);

    dms.removeBatch({1}, 5);
    EXPECT_EQ(1, listener->remove_batch_cnt);
}

TEST(DocumentMetaStoreTest, call_to_remove_is_notified)
{
    DocumentMetaStore dms(createBucketDB());
    auto listener = std::make_shared<MockOperationListener>();
    dms.set_operation_listener(listener);
    dms.constructFreeList();
    addLid(dms, 1);

    dms.remove(1, 0u);
    dms.commit();
    EXPECT_EQ(1, listener->remove_cnt);
}

namespace {

void try_compact_document_meta_store(DocumentMetaStore &dms)
{
    dms.reclaim_unused_memory();
    dms.commit(CommitParam::UpdateStats::FORCE);
}

}

TEST(DocumentMetaStoreTest, gid_to_lid_map_can_be_compacted)
{
    auto dms = std::make_shared<DocumentMetaStore>(createBucketDB());
    dms->constructFreeList();
    static constexpr uint32_t full_size = 1000;
    for (uint32_t i = 1; i < full_size; ++i) {
        addLid(*dms, i);
    }
    dms->commit(CommitParam::UpdateStats::FORCE);
    AttributeGuard guard(dms);
    remove(full_size - 1, 100, *dms);
    dms->commit(CommitParam::UpdateStats::FORCE);
    auto status_before = dms->getStatus();
    EXPECT_LT(0, status_before.getOnHold());
    guard = AttributeGuard();
    try_compact_document_meta_store(*dms);
    auto status_early = dms->getStatus();
    EXPECT_LT(status_before.getDead(), status_early.getDead());
    EXPECT_EQ(0, status_early.getOnHold());
    bool compaction_done = false;
    for (uint32_t i = 0; i < 15 && !compaction_done; ++i) {
        AttributeGuard guard2(dms);
        auto status_loop_iteration_start = dms->getStatus();
        try_compact_document_meta_store(*dms);
        try_compact_document_meta_store(*dms);
        auto status_second = dms->getStatus();
        if (i > 0) {
            EXPECT_GT(status_before.getUsed(), status_second.getUsed());
        }
        EXPECT_GT(status_early.getDead(), status_second.getDead());
        try_compact_document_meta_store(*dms);
        auto status_third = dms->getStatus();
        EXPECT_EQ(status_second.getDead(), status_third.getDead());
        EXPECT_EQ(status_second.getUsed(), status_third.getUsed());
        EXPECT_EQ(status_second.getOnHold(), status_third.getOnHold());
        EXPECT_GE(status_loop_iteration_start.getDead(), status_third.getDead());
        if (status_loop_iteration_start.getDead() == status_third.getDead()) {
            compaction_done = true;
        }
    }
    EXPECT_TRUE(compaction_done);
    auto status_after = dms->getStatus();
    EXPECT_GT(status_before.getUsed(), status_after.getUsed());
    EXPECT_GT(status_early.getDead(), status_after.getDead());
    EXPECT_EQ(0, status_after.getOnHold());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
