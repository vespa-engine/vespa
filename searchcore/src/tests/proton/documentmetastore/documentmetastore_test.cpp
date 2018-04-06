// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/base/documentid.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_listener.h>
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchcore/proton/server/itlssyncer.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/log/log.h>
LOG_SETUP("documentmetastore_test");

using namespace document;
using proton::bucketdb::BucketState;
using proton::bucketdb::IBucketCreateListener;
using search::AttributeFileSaveTarget;
using search::AttributeGuard;
using search::AttributeVector;
using search::DocumentMetaData;
using search::GrowStrategy;
using search::LidUsageStats;
using search::QueryTermSimple;
using search::SingleValueBitNumericAttribute;
using search::TuneFileAttributes;
using search::attribute::SearchContextParams;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldMatchData;
using search::index::DummyFileHeaderContext;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleResult;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using vespalib::GenerationHandler;
using vespalib::GenerationHolder;
using searchcorespi::IFlushTarget;
using namespace std::literals;

namespace proton {

namespace {

static constexpr uint32_t numBucketBits = UINT32_C(20);
static constexpr uint64_t timestampBias = UINT64_C(2000000000000);

}


class DummyTlsSyncer : public ITlsSyncer
{
public:
    virtual ~DummyTlsSyncer() = default;

    virtual void sync() override { }
};

class ReverseGidCompare : public DocumentMetaStore::IGidCompare
{
    GlobalId::BucketOrderCmp _comp;
public:
    ReverseGidCompare()
        : IGidCompare(),
          _comp()
    {
    }

    virtual bool
    operator()(const GlobalId &lhs, const GlobalId &rhs) const override
    {
        return _comp(rhs, lhs);
    }
};


struct BoolVector : public std::vector<bool> {
    BoolVector() : std::vector<bool>() {}
    BoolVector(size_t sz) : std::vector<bool>(sz) {}
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

typedef DocumentMetaStore::Result PutRes;
typedef DocumentMetaStore::Result Result;

BucketDBOwner::SP
createBucketDB()
{
    return std::make_shared<BucketDBOwner>();
}

bool
assertPut(const BucketId &bucketId,
          const Timestamp &timestamp,
          uint32_t lid,
          const GlobalId &gid,
          DocumentMetaStore &dms)
{
    Result inspect = dms.inspect(gid);
    PutRes putRes;
    uint32_t docSize = 1;
    if (!EXPECT_TRUE((putRes = dms.put(gid, bucketId, timestamp, docSize, inspect.getLid())).
            ok())) return false;
    return EXPECT_EQUAL(lid, putRes.getLid());
}

bool
compare(const GlobalId &lhs, const GlobalId &rhs)
{
    return EXPECT_EQUAL(lhs.toString(), rhs.toString());
}

bool
assertGid(const GlobalId &exp, uint32_t lid, const DocumentMetaStore &dms)
{
    GlobalId act;
    if (!EXPECT_TRUE(dms.getGid(lid, act))) return false;
    return compare(exp, act);
}

bool
assertGid(const GlobalId &exp,
          uint32_t lid,
          const DocumentMetaStore &dms,
          const BucketId &expBucketId,
          const Timestamp &expTimestamp)
{
    GlobalId act;
    BucketId bucketId;
    Timestamp timestamp(1);
    if (!EXPECT_TRUE(dms.getGid(lid, act)))
        return false;
    if (!compare(exp, act))
        return false;
    DocumentMetaData meta = dms.getMetaData(act);
    if (!EXPECT_TRUE(meta.valid()))
        return false;
    bucketId = meta.bucketId;
    timestamp = meta.timestamp;
    if (!EXPECT_EQUAL(expBucketId.getRawId(), bucketId.getRawId()))
        return false;
    if (!EXPECT_EQUAL(expBucketId.getId(), bucketId.getId()))
        return false;
    if (!EXPECT_EQUAL(expTimestamp, timestamp))
        return false;
    return true;
}

bool
assertLid(uint32_t exp, const GlobalId &gid, const DocumentMetaStore &dms)
{
    uint32_t act;
    if (!EXPECT_TRUE(dms.getLid(gid, act))) return false;
    return EXPECT_EQUAL(exp, act);
}

bool
assertMetaData(const DocumentMetaData &exp, const DocumentMetaData &act)
{
    if (!EXPECT_EQUAL(exp.lid, act.lid)) return false;
    if (!EXPECT_EQUAL(exp.timestamp, act.timestamp)) return false;
    if (!EXPECT_EQUAL(exp.bucketId, act.bucketId)) return false;
    if (!EXPECT_EQUAL(exp.gid, act.gid)) return false;
    if (!EXPECT_EQUAL(exp.removed, act.removed)) return false;
    return true;
}

bool
assertActiveLids(const BoolVector &exp, const search::BitVector &act)
{
    // lid 0 is reserved
    if (!EXPECT_EQUAL(exp.size() + 1, act.size())) return false;
    for (size_t i = 0; i < exp.size(); ++i) {
        if (!EXPECT_EQUAL(exp[i], act.testBit(i + 1))) return false;
    }
    return true;
}

bool
assertWhiteList(const SimpleResult &exp, Blueprint::UP whiteListBlueprint, bool strict, uint32_t docIdLimit)
{
    MatchDataLayout mdl;
    MatchData::UP md = mdl.createMatchData();
    whiteListBlueprint->fetchPostings(strict);
    whiteListBlueprint->setDocIdLimit(docIdLimit);

    SearchIterator::UP sb = whiteListBlueprint->createSearch(*md, strict);
    SimpleResult act;
    act.searchStrict(*sb, docIdLimit);
    return EXPECT_EQUAL(exp, act);
}

bool
assertSearchResult(const SimpleResult &exp, const DocumentMetaStore &dms,
                   const vespalib::string &term, const QueryTermSimple::SearchTerm &termType,
                   bool strict, uint32_t docIdLimit = 100)
{
    AttributeVector::SearchContext::UP sc =
            dms.getSearch(QueryTermSimple::UP(new QueryTermSimple(term, termType)), SearchContextParams());
    TermFieldMatchData tfmd;
    SearchIterator::UP sb = sc->createIterator(&tfmd, strict);
    SimpleResult act;
    if (strict) {
        act.search(*sb);
    } else {
        act.search(*sb, docIdLimit);
    }
    return EXPECT_EQUAL(exp, act);
}

bool
assertBucketInfo(uint32_t expDocCount,
                 uint32_t expMetaCount,
                 const BucketInfo &act)
{
    if (!EXPECT_EQUAL(expDocCount, act.getDocumentCount()))
        return false;
    if (!EXPECT_EQUAL(expMetaCount, act.getEntryCount()))
        return false;
    return true;
}

GlobalId gid1("111111111111");
GlobalId gid2("222222222222");
GlobalId gid3("333333333333");
GlobalId gid4("444444444444");
GlobalId gid5("555555555555");
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
addGid(DocumentMetaStore &dms, const GlobalId &gid, const BucketId &bid, Timestamp timestamp, uint32_t docSize = 1)
{
    Result inspect = dms.inspect(gid);
    PutRes putRes;
    EXPECT_TRUE((putRes = dms.put(gid, bid, timestamp, docSize, inspect.getLid())).ok());
    return putRes.getLid();
}

uint32_t
addGid(DocumentMetaStore &dms, const GlobalId &gid, Timestamp timestamp)
{
    BucketId bid(minNumBits, gid.convertToBucketId().getRawId());
    return addGid(dms, gid, bid, timestamp);
}

void
putGid(DocumentMetaStore &dms, const GlobalId &gid, uint32_t lid, Timestamp timestamp = Timestamp())
{
    BucketId bid(minNumBits, gid.convertToBucketId().getRawId());
    uint32_t docSize = 1;
    EXPECT_TRUE(dms.put(gid, bid, timestamp, docSize, lid).ok());
}

TEST("require that removed documents are bucketized to bucket 0")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    EXPECT_EQUAL(1u, dms.getNumDocs());
    EXPECT_EQUAL(0u, dms.getNumUsedLids());

    vespalib::GenerationHandler::Guard guard = dms.getGuard();
    EXPECT_EQUAL(BucketId(), dms.getBucketOf(guard, 1));
    EXPECT_TRUE(assertPut(bucketId1, time1, 1, gid1, dms));
    EXPECT_EQUAL(bucketId1, dms.getBucketOf(guard, 1));
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, dms));
    EXPECT_EQUAL(bucketId2, dms.getBucketOf(guard, 2));
    EXPECT_TRUE(dms.remove(1));
    EXPECT_EQUAL(BucketId(), dms.getBucketOf(guard, 1));
    EXPECT_EQUAL(bucketId2, dms.getBucketOf(guard, 2));
}

TEST("requireThatGidsCanBeInsertedAndRetrieved")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    // put()
    EXPECT_EQUAL(1u, dms.getNumDocs());
    EXPECT_EQUAL(0u, dms.getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId1, time1, 1, gid1, dms));
    EXPECT_EQUAL(2u, dms.getNumDocs());
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, dms));
    EXPECT_EQUAL(3u, dms.getNumDocs());
    EXPECT_EQUAL(2u, dms.getNumUsedLids());
    // gid1 already inserted
    EXPECT_TRUE(assertPut(bucketId1, time1, 1, gid1, dms));
    // gid2 already inserted
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, dms));


    // getGid()
    GlobalId gid;
    EXPECT_TRUE(assertGid(gid1, 1, dms));
    EXPECT_TRUE(assertGid(gid2, 2, dms));
    EXPECT_TRUE(!dms.getGid(3, gid));

    // getLid()
    uint32_t lid = 0;
    EXPECT_TRUE(assertLid(1, gid1, dms));
    EXPECT_TRUE(assertLid(2, gid2, dms));
    EXPECT_TRUE(!dms.getLid(gid3, lid));
}

TEST("requireThatGidsCanBeCleared")
{
    DocumentMetaStore dms(createBucketDB());
    GlobalId gid;
    uint32_t lid = 0u;
    dms.constructFreeList();
    addGid(dms, gid1, bucketId1, time1);
    EXPECT_TRUE(assertGid(gid1, 1, dms));
    EXPECT_TRUE(assertLid(1, gid1, dms));
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.remove(1));
    dms.removeComplete(1);
    EXPECT_EQUAL(0u, dms.getNumUsedLids());
    EXPECT_TRUE(!dms.getGid(1, gid));
    EXPECT_TRUE(!dms.getLid(gid1, lid));
    // reuse lid
    addGid(dms, gid2, bucketId2, time2);
    EXPECT_TRUE(assertGid(gid2, 1, dms));
    EXPECT_TRUE(assertLid(1, gid2, dms));
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.remove(1));
    dms.removeComplete(1);
    EXPECT_EQUAL(0u, dms.getNumUsedLids());
    EXPECT_TRUE(!dms.getGid(1, gid));
    EXPECT_TRUE(!dms.getLid(gid2, lid));
    EXPECT_TRUE(!dms.remove(1)); // not used
    EXPECT_TRUE(!dms.remove(2)); // outside range
}

TEST("requireThatGenerationHandlingIsWorking")
{
    AttributeVector::SP av(new DocumentMetaStore(createBucketDB()));
    DocumentMetaStore * dms = static_cast<DocumentMetaStore *>(av.get());
    dms->constructFreeList();
    const GenerationHandler & gh = dms->getGenerationHandler();
    EXPECT_EQUAL(1u, gh.getCurrentGeneration());
    addGid(*dms, gid1, bucketId1, time1);
    EXPECT_EQUAL(2u, gh.getCurrentGeneration());
    EXPECT_EQUAL(0u, gh.getGenerationRefCount());
    {
        AttributeGuard g1(av);
        EXPECT_EQUAL(1u, gh.getGenerationRefCount());
        {
            AttributeGuard g2(av);
            EXPECT_EQUAL(2u, gh.getGenerationRefCount());
        }
        EXPECT_EQUAL(1u, gh.getGenerationRefCount());
    }
    EXPECT_EQUAL(0u, gh.getGenerationRefCount());
    dms->remove(1);
    dms->removeComplete(1);
    EXPECT_EQUAL(4u, gh.getCurrentGeneration());
}

TEST("requireThatBasicFreeListIsWorking")
{
    GenerationHolder genHold;
    LidStateVector freeLids(100, 100, genHold, true, false);
    LidHoldList list;
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQUAL(0u, freeLids.count());
    EXPECT_EQUAL(0u, list.size());

    list.add(10, 10);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQUAL(0u, freeLids.count());
    EXPECT_EQUAL(1u, list.size());

    list.add(20, 20);
    list.add(30, 30);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQUAL(0u, freeLids.count());
    EXPECT_EQUAL(3u, list.size());

    list.trimHoldLists(20, freeLids);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQUAL(1u, freeLids.count());

    EXPECT_EQUAL(10u, freeLids.getLowest());
    freeLids.clearBit(10);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQUAL(0u, freeLids.count());
    EXPECT_EQUAL(2u, list.size());

    list.trimHoldLists(31, freeLids);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQUAL(2u, freeLids.count());

    EXPECT_EQUAL(20u, freeLids.getLowest());
    freeLids.clearBit(20);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQUAL(1u, freeLids.count());
    EXPECT_EQUAL(0u, list.size());

    EXPECT_EQUAL(30u, freeLids.getLowest());
    freeLids.clearBit(30);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQUAL(0u, list.size());
    EXPECT_EQUAL(0u, freeLids.count());
}

void
assertLidStateVector(const std::vector<uint32_t> &expLids, uint32_t lowest, uint32_t highest,
                     const LidStateVector &actLids)
{
    if (!expLids.empty()) {
        EXPECT_EQUAL(expLids.size(), actLids.count());
        uint32_t trueBit = 0;
        for (auto i : expLids) {
            EXPECT_TRUE(actLids.testBit(i));
            trueBit = actLids.getNextTrueBit(trueBit);
            EXPECT_EQUAL(i, trueBit);
            ++trueBit;
        }
        trueBit = actLids.getNextTrueBit(trueBit);
        EXPECT_EQUAL(actLids.size(), trueBit);
        EXPECT_EQUAL(lowest, actLids.getLowest());
        EXPECT_EQUAL(highest, actLids.getHighest());
    } else {
        EXPECT_TRUE(actLids.empty());
    }
}

TEST("requireThatLidStateVectorResizingIsWorking")
{
    GenerationHolder genHold;
    LidStateVector lids(1000, 1000, genHold, true, true);
    lids.setBit(3);
    lids.setBit(150);
    lids.setBit(270);
    lids.setBit(310);
    lids.setBit(440);
    lids.setBit(780);
    lids.setBit(930);
    assertLidStateVector({3,150,270,310,440,780,930}, 3, 930, lids);

    lids.resizeVector(1500, 1500);
    assertLidStateVector({3,150,270,310,440,780,930}, 3, 930, lids);
    lids.clearBit(3);
    assertLidStateVector({150,270,310,440,780,930}, 150, 930, lids);
    lids.clearBit(150);
    assertLidStateVector({270,310,440,780,930}, 270, 930, lids);
    lids.setBit(170);
    assertLidStateVector({170,270,310,440,780,930}, 170, 930, lids);
    lids.setBit(1490);
    assertLidStateVector({170,270,310,440,780,930,1490}, 170, 1490, lids);

    lids.resizeVector(2000, 2000);
    assertLidStateVector({170,270,310,440,780,930,1490}, 170, 1490, lids);
    lids.clearBit(170);
    assertLidStateVector({270,310,440,780,930,1490}, 270, 1490, lids);
    lids.clearBit(270);
    assertLidStateVector({310,440,780,930,1490}, 310, 1490, lids);
    lids.setBit(1990);
    assertLidStateVector({310,440,780,930,1490,1990}, 310, 1990, lids);
    lids.clearBit(310);
    assertLidStateVector({440,780,930,1490,1990}, 440, 1990, lids);
    lids.clearBit(440);
    assertLidStateVector({780,930,1490,1990}, 780, 1990, lids);
    lids.clearBit(780);
    assertLidStateVector({930,1490,1990}, 930, 1990, lids);
    lids.clearBit(930);
    assertLidStateVector({1490,1990}, 1490, 1990, lids);
    lids.clearBit(1490);
    assertLidStateVector({1990}, 1990, 1990, lids);
    lids.clearBit(1990);
    assertLidStateVector({}, 0, 0, lids);

    genHold.clearHoldLists();
}

TEST("requireThatLidAndGidSpaceIsReused")
{
    AttributeVector::SP av(new DocumentMetaStore(createBucketDB()));
    DocumentMetaStore * dms = static_cast<DocumentMetaStore *>(av.get());
    dms->constructFreeList();
    EXPECT_EQUAL(1u, dms->getNumDocs());
    EXPECT_EQUAL(0u, dms->getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId1, time1, 1, gid1, *dms)); // -> gen 1
    EXPECT_EQUAL(2u, dms->getNumDocs());
    EXPECT_EQUAL(1u, dms->getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, *dms)); // -> gen 2
    EXPECT_EQUAL(3u, dms->getNumDocs());
    EXPECT_EQUAL(2u, dms->getNumUsedLids());
    dms->remove(2); // -> gen 3
    dms->removeComplete(2); // -> gen 4
    EXPECT_EQUAL(3u, dms->getNumDocs());
    EXPECT_EQUAL(1u, dms->getNumUsedLids());
    // -> gen 5 (reuse of lid 2)
    EXPECT_TRUE(assertPut(bucketId3, time3, 2, gid3, *dms));
    EXPECT_EQUAL(3u, dms->getNumDocs());
    EXPECT_EQUAL(2u, dms->getNumUsedLids()); // reuse
    EXPECT_TRUE(assertGid(gid3, 2, *dms));
    {
        AttributeGuard g1(av); // guard on gen 5
        dms->remove(2);
        dms->removeComplete(2);
        EXPECT_EQUAL(3u, dms->getNumDocs());
        EXPECT_EQUAL(1u, dms->getNumUsedLids()); // lid 2 free but guarded
        EXPECT_TRUE(assertPut(bucketId4, time4, 3, gid4, *dms));
        EXPECT_EQUAL(4u, dms->getNumDocs()); // generation guarded, new lid
        EXPECT_EQUAL(2u, dms->getNumUsedLids());
        EXPECT_TRUE(assertGid(gid4, 3, *dms));
    }
    EXPECT_TRUE(assertPut(bucketId5, time5, 4, gid5, *dms));
    EXPECT_EQUAL(5u, dms->getNumDocs()); // reuse blocked by previous guard. released at end of put()
    EXPECT_EQUAL(3u, dms->getNumUsedLids());
    EXPECT_TRUE(assertGid(gid5, 4, *dms));
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, *dms)); // reuse of lid 2
    EXPECT_EQUAL(5u, dms->getNumDocs());
    EXPECT_EQUAL(4u, dms->getNumUsedLids());
    EXPECT_TRUE(assertGid(gid2, 2, *dms));
}

GlobalId
createGid(uint32_t lid)
{
    DocumentId docId(vespalib::make_string("doc:id:%u", lid));
    return docId.getGlobalId();
}

GlobalId
createGid(uint32_t userId, uint32_t lid)
{
    DocumentId docId(vespalib::make_string("userdoc:id:%u:%u", userId, lid));
    return docId.getGlobalId();
}

TEST("requireThatWeCanStoreBucketIdAndTimestamp")
{
    DocumentMetaStore dms(createBucketDB());
    uint32_t numLids = 1000;

    dms.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        uint32_t addLid = addGid(dms, gid, bucketId, Timestamp(lid + timestampBias));
        EXPECT_EQUAL(lid, addLid);
    }
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        EXPECT_TRUE(assertGid(gid, lid, dms, bucketId,
                              Timestamp(lid + timestampBias)));
        EXPECT_TRUE(assertLid(lid, gid, dms));
    }
}

TEST("requireThatGidsCanBeSavedAndLoaded")
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
        GlobalId gid = createGid(lid);
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        uint32_t addLid = addGid(dms1, gid, bucketId, Timestamp(lid + timestampBias));
        EXPECT_EQUAL(lid, addLid);
    }
    for (size_t i = 0; i < removeLids.size(); ++i) {
        dms1.remove(removeLids[i]);
        dms1.removeComplete(removeLids[i]);
    }
    uint64_t expSaveBytesSize = DocumentMetaStore::minHeaderLen +
                                (1000 - 4) * DocumentMetaStore::entrySize;
    EXPECT_EQUAL(expSaveBytesSize, dms1.getEstimatedSaveByteSize());
    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    EXPECT_TRUE(dms1.saveAs("documentmetastore2", saveTarget));

    DocumentMetaStore dms2(createBucketDB(), "documentmetastore2");
    EXPECT_TRUE(dms2.load());
    dms2.constructFreeList();
    EXPECT_EQUAL(numLids + 1, dms2.getNumDocs());
    EXPECT_EQUAL(numLids - 4, dms2.getNumUsedLids()); // 4 removed
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(numBucketBits);
        if (std::count(removeLids.begin(), removeLids.end(), lid) == 0) {
            EXPECT_TRUE(assertGid(gid, lid, dms2, bucketId,
                                  Timestamp(lid + timestampBias)));
            EXPECT_TRUE(assertLid(lid, gid, dms2));
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
        GlobalId gid = createGid(removeLids[i]);
        BucketId bucketId(numBucketBits,
                          gid.convertToBucketId().getRawId());
        // re-use removeLid[i]
        uint32_t addLid = addGid(dms2, gid, bucketId, Timestamp(43u + i));
        EXPECT_EQUAL(removeLids[i], addLid);
        EXPECT_EQUAL(numLids + 1, dms2.getNumDocs());
        EXPECT_EQUAL(numLids - (3 - i), dms2.getNumUsedLids());
    }
}

TEST("requireThatStatsAreUpdated")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    size_t perGidUsed = sizeof(uint32_t) + GlobalId::LENGTH;
    EXPECT_EQUAL(1u, dms.getStatus().getNumDocs());
    EXPECT_EQUAL(1u, dms.getStatus().getNumValues());
    uint64_t lastAllocated = dms.getStatus().getAllocated();
    uint64_t lastUsed = dms.getStatus().getUsed();
    EXPECT_GREATER(lastAllocated, perGidUsed);
    EXPECT_GREATER(lastUsed, perGidUsed);

    std::this_thread::sleep_for(2200ms);
    addGid(dms, gid1, bucketId1, time1);
    EXPECT_EQUAL(2u, dms.getStatus().getNumDocs());
    EXPECT_EQUAL(2u, dms.getStatus().getNumValues());
    EXPECT_GREATER_EQUAL(dms.getStatus().getAllocated(), lastAllocated);
    EXPECT_GREATER_EQUAL(dms.getStatus().getAllocated(), lastUsed);
    EXPECT_GREATER(dms.getStatus().getUsed(), lastUsed);
    EXPECT_GREATER(dms.getStatus().getUsed(), 2 * perGidUsed);
    lastAllocated = dms.getStatus().getAllocated();
    lastUsed = dms.getStatus().getUsed();

    addGid(dms, gid2, bucketId2, time2);
    dms.commit(true);
    EXPECT_EQUAL(3u, dms.getStatus().getNumDocs());
    EXPECT_EQUAL(3u, dms.getStatus().getNumValues());
    EXPECT_GREATER_EQUAL(dms.getStatus().getAllocated(), lastAllocated);
    EXPECT_GREATER_EQUAL(dms.getStatus().getAllocated(), lastUsed);
    EXPECT_GREATER(dms.getStatus().getUsed(), lastUsed);
    EXPECT_GREATER(dms.getStatus().getUsed(), 3 * perGidUsed);
    LOG(info,
        "stats after 2 gids added: allocated %d, used is %d > %d (3 * %d)",
        static_cast<int>(dms.getStatus().getAllocated()),
        static_cast<int>(dms.getStatus().getUsed()),
        static_cast<int>(3 * perGidUsed),
        static_cast<int>(perGidUsed));
}

TEST("requireThatWeCanPutAndRemoveBeforeFreeListConstruct")
{
    DocumentMetaStore dms(createBucketDB());
    EXPECT_TRUE(dms.put(gid4, bucketId4, time4, docSize4, 4).ok());
    EXPECT_TRUE(assertLid(4, gid4, dms));
    EXPECT_TRUE(assertGid(gid4, 4, dms));
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
    EXPECT_TRUE(dms.put(gid1, bucketId1, time1, docSize1, 1).ok());
    // already there, nothing changes
    EXPECT_TRUE(dms.put(gid1, bucketId1, time1, docSize1, 1).ok());
    EXPECT_TRUE(assertLid(1, gid1, dms));
    EXPECT_TRUE(assertGid(gid1, 1, dms));
    EXPECT_EQUAL(2u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
    // gid1 already there with lid 1
    EXPECT_EXCEPTION(!dms.put(gid1, bucketId1, time1, docSize1, 2).ok(),
                     vespalib::IllegalStateException,
                     "gid found, but using another lid");
    EXPECT_EXCEPTION(!dms.put(gid5, bucketId5, time5, docSize5, 1).ok(),
                     vespalib::IllegalStateException,
                     "gid not found, but lid is used by another gid");
    EXPECT_TRUE(assertLid(1, gid1, dms));
    EXPECT_TRUE(assertGid(gid1, 1, dms));
    EXPECT_EQUAL(2u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
    EXPECT_TRUE(dms.remove(4)); // -> goes to free list. cleared and re-applied in constructFreeList().
    uint32_t lid;
    GlobalId gid;
    EXPECT_TRUE(!dms.getLid(gid4, lid));
    EXPECT_TRUE(!dms.getGid(4, gid));
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
    dms.constructFreeList();
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
    EXPECT_TRUE(assertPut(bucketId2, time2, 2, gid2, dms));
    EXPECT_TRUE(assertPut(bucketId3, time3, 3, gid3, dms));
    EXPECT_EQUAL(3u, dms.getNumUsedLids());
    EXPECT_EQUAL(5u, dms.getNumDocs());
}

TEST("requireThatWeCanSortGids")
{
    DocumentMetaStore dms(createBucketDB());
    DocumentMetaStore rdms(createBucketDB(),
                         DocumentMetaStore::getFixedName(),
                         GrowStrategy(),
                         DocumentMetaStore::IGidCompare::SP(
                                 new ReverseGidCompare));

    dms.constructFreeList();
    rdms.constructFreeList();
    uint32_t numLids = 1000;
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        Timestamp oldTimestamp;
        BucketId bucketId(minNumBits,
                          gid.convertToBucketId().getRawId());
        uint32_t addLid = addGid(dms, gid, bucketId, Timestamp(0u));
        EXPECT_EQUAL(lid, addLid);
        uint32_t addLid2 = addGid(rdms, gid, bucketId, Timestamp(0u));
        EXPECT_EQUAL(lid, addLid2);
    }
    std::vector<uint32_t> lids;
    std::vector<uint32_t> rlids;
    for (DocumentMetaStore::ConstIterator it = dms.beginFrozen(); it.valid(); ++it)
        lids.push_back(it.getKey());
    for (DocumentMetaStore::ConstIterator rit = rdms.beginFrozen();
         rit.valid(); ++rit)
        rlids.push_back(rit.getKey());
    EXPECT_EQUAL(numLids, lids.size());
    EXPECT_EQUAL(numLids, rlids.size());
    for (uint32_t i = 0; i < numLids; ++i) {
        EXPECT_EQUAL(lids[numLids - 1 - i], rlids[i]);
    }
}

TEST("requireThatBasicBucketInfoWorks")
{
    DocumentMetaStore dms(createBucketDB());
    typedef std::pair<BucketId, GlobalId> Elem;
    typedef std::map<Elem, Timestamp> Map;
    Map m;
    uint32_t numLids = 2000;
    dms.constructFreeList();
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        Timestamp timestamp(UINT64_C(123456789) * lid);
        Timestamp oldTimestamp;
        BucketId bucketId(minNumBits,
                          gid.convertToBucketId().getRawId());
        uint32_t addLid = addGid(dms, gid, bucketId, timestamp);
        EXPECT_EQUAL(lid, addLid);
        m[std::make_pair(bucketId, gid)] = timestamp;
    }
    for (uint32_t lid = 2; lid <= numLids; lid += 7) {
        GlobalId gid = createGid(lid);
        Timestamp timestamp(UINT64_C(14735) * lid);
        Timestamp oldTimestamp;
        BucketId bucketId(minNumBits,
                          gid.convertToBucketId().getRawId());
        uint32_t addLid = addGid(dms, gid, bucketId, timestamp);
        EXPECT_EQUAL(lid, addLid);
        m[std::make_pair(bucketId, gid)] = timestamp;
    }
    for (uint32_t lid = 3; lid <= numLids; lid += 5) {
        GlobalId gid = createGid(lid);
        BucketId bucketId(minNumBits,
                          gid.convertToBucketId().getRawId());
        EXPECT_TRUE(dms.remove(lid));
        dms.removeComplete(lid);
        m.erase(std::make_pair(bucketId, gid));
    }
    assert(!m.empty());
    BucketChecksum cksum;
    BucketId prevBucket = m.begin()->first.first;
    uint32_t cnt = 0u;
    uint32_t maxcnt = 0u;
    BucketDBOwner::Guard bucketDB = dms.getBucketDB().takeGuard();
    for (Map::const_iterator i = m.begin(), ie = m.end(); i != ie; ++i) {
        if (i->first.first == prevBucket) {
            cksum = BucketChecksum(cksum +
                                   BucketState::calcChecksum(i->first.second,
                                                             i->second));
            ++cnt;
        } else {
            BucketInfo bi = bucketDB->get(prevBucket);
            EXPECT_EQUAL(cnt, bi.getDocumentCount());
            EXPECT_EQUAL(cksum, bi.getChecksum());
            prevBucket = i->first.first;
            cksum = BucketState::calcChecksum(i->first.second,
                                              i->second);
            maxcnt = std::max(maxcnt, cnt);
            cnt = 1u;
        }
    }
    maxcnt = std::max(maxcnt, cnt);
    BucketInfo bi = bucketDB->get(prevBucket);
    EXPECT_EQUAL(cnt, bi.getDocumentCount());
    EXPECT_EQUAL(cksum, bi.getChecksum());
    LOG(info, "Largest bucket: %u elements", maxcnt);
}

TEST("requireThatWeCanRetrieveListOfLidsFromBucketId")
{
    typedef std::vector<uint32_t> LidVector;
    typedef std::map<BucketId, LidVector> Map;
    DocumentMetaStore dms(createBucketDB());
    const uint32_t bucketBits = 2; // -> 4 buckets
    uint32_t numLids = 1000;
    Map m;

    dms.constructFreeList();
    // insert global ids
    for (uint32_t lid = 1; lid <= numLids; ++lid) {
        GlobalId gid = createGid(lid);
        BucketId bucketId(bucketBits,
                          gid.convertToBucketId().getRawId());
        uint32_t addLid = addGid(dms, gid, bucketId, Timestamp(0));
        EXPECT_EQUAL(lid, addLid);
        m[bucketId].push_back(lid);
    }

    // Verify that bucket id x has y lids
    EXPECT_EQUAL(4u, m.size());
    for (Map::const_iterator itr = m.begin(); itr != m.end(); ++itr) {
        const BucketId &bucketId = itr->first;
        const LidVector &expLids = itr->second;
        LOG(info, "Verify that bucket id '%s' has %zu lids",
            bucketId.toString().c_str(), expLids.size());
        LidVector actLids;
        dms.getLids(bucketId, actLids);
        EXPECT_EQUAL(expLids.size(), actLids.size());
        for (size_t i = 0; i < expLids.size(); ++i) {
            EXPECT_TRUE(std::find(actLids.begin(), actLids.end(), expLids[i]) != actLids.end());
        }
    }

    // Remove and verify empty buckets
    for (Map::const_iterator itr = m.begin(); itr != m.end(); ++itr) {
        const BucketId &bucketId = itr->first;
        const LidVector &expLids = itr->second;
        for (size_t i = 0; i < expLids.size(); ++i) {
            EXPECT_TRUE(dms.remove(expLids[i]));
            dms.removeComplete(expLids[i]);
        }
        LOG(info, "Verify that bucket id '%s' has 0 lids", bucketId.toString().c_str());
        LidVector actLids;
        dms.getLids(bucketId, actLids);
        EXPECT_TRUE(actLids.empty());
    }
}

struct Comparator {
    bool operator() (const DocumentMetaData &lhs, const DocumentMetaData &rhs) const {
        return lhs.lid < rhs.lid;
    }
};

struct UserDocFixture {
    std::shared_ptr<BucketDBOwner> _bucketDB;
    DocumentMetaStore dms;
    std::vector<GlobalId> gids;
    BucketId bid1;
    BucketId bid2;
    BucketId bid3;
    bucketdb::BucketDBHandler _bucketDBHandler;
    UserDocFixture();
    ~UserDocFixture();
    void addGlobalId(const GlobalId &gid, uint32_t expLid, uint32_t timestampConst = 100) {
        uint32_t actLid = addGid(dms, gid, Timestamp(expLid + timestampConst));
        EXPECT_EQUAL(expLid, actLid);
    }
    void putGlobalId(const GlobalId &gid, uint32_t lid, uint32_t timestampConst = 100) {
        putGid(dms, gid, lid, Timestamp(lid + timestampConst));
    }
    void addGlobalIds(size_t numGids=7) __attribute__((noinline));
};

UserDocFixture::UserDocFixture()
    : _bucketDB(createBucketDB()),
      dms(_bucketDB), gids(), bid1(), bid2(), bid3(),
      _bucketDBHandler(*_bucketDB)
{
    _bucketDBHandler.addDocumentMetaStore(&dms, 0);
    gids.push_back(createGid(10, 1));
    gids.push_back(createGid(10, 2));
    gids.push_back(createGid(20, 3));
    gids.push_back(createGid(10, 4));
    gids.push_back(createGid(10, 5));
    gids.push_back(createGid(20, 6));
    gids.push_back(createGid(20, 7));
    gids.push_back(createGid(30, 8)); // extra
    gids.push_back(createGid(10, 9)); // extra
    // 3 users -> 3 buckets
    bid1 = BucketId(minNumBits, gids[0].convertToBucketId().getRawId());
    bid2 = BucketId(minNumBits, gids[2].convertToBucketId().getRawId());
    bid3 = BucketId(minNumBits, gids[7].convertToBucketId().getRawId());
}
UserDocFixture::~UserDocFixture() {}

void
UserDocFixture::addGlobalIds(size_t numGids) {
    for (size_t i = 0; i < numGids; ++i) {
        uint32_t expLid = i + 1;
        addGlobalId(gids[i], expLid);
    }
}

TEST("requireThatWeCanRetrieveListOfMetaDataFromBucketId")
{
    UserDocFixture f;
    { // empty bucket
        DocumentMetaData::Vector result;
        f.dms.getMetaData(f.bid1, result);
        EXPECT_EQUAL(0u, result.size());
    }
    f.dms.constructFreeList();
    f.addGlobalIds();
    { // verify bucket 1
        DocumentMetaData::Vector result;
        f.dms.getMetaData(f.bid1, result);
        std::sort(result.begin(), result.end(), Comparator());
        EXPECT_EQUAL(4u, result.size());
        EXPECT_TRUE(assertMetaData(DocumentMetaData(1, Timestamp(101), f.bid1,
                                                    f.gids[0]), result[0]));
        EXPECT_TRUE(assertMetaData(DocumentMetaData(2, Timestamp(102), f.bid1,
                                                    f.gids[1]), result[1]));
        EXPECT_TRUE(assertMetaData(DocumentMetaData(4, Timestamp(104), f.bid1,
                                                    f.gids[3]), result[2]));
        EXPECT_TRUE(assertMetaData(DocumentMetaData(5, Timestamp(105), f.bid1,
                                                    f.gids[4]), result[3]));
    }
    { // verify bucket 2
        DocumentMetaData::Vector result;
        f.dms.getMetaData(f.bid2, result);
        std::sort(result.begin(), result.end(), Comparator());
        EXPECT_EQUAL(3u, result.size());
        EXPECT_TRUE(assertMetaData(DocumentMetaData(3, Timestamp(103), f.bid2,
                                                    f.gids[2]), result[0]));
        EXPECT_TRUE(assertMetaData(DocumentMetaData(6, Timestamp(106), f.bid2,
                                                    f.gids[5]), result[1]));
        EXPECT_TRUE(assertMetaData(DocumentMetaData(7, Timestamp(107), f.bid2,
                                                    f.gids[6]), result[2]));
    }
}

TEST("requireThatBucketStateCanBeUpdated")
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_EQUAL(1u, f.dms.getActiveLids().size()); // lid 0 is reserved

    f.addGlobalIds();
    EXPECT_TRUE(assertActiveLids(BoolVector().F().F().F().F().F().F().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.dms.setBucketState(f.bid1, true);
    EXPECT_TRUE(assertActiveLids(BoolVector().T().T().F().T().T().F().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(4u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.dms.setBucketState(f.bid2, true);
    EXPECT_TRUE(assertActiveLids(BoolVector().T().T().T().T().T().T().T(), f.dms.getActiveLids()));
    EXPECT_EQUAL(7u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());

    f.addGlobalId(createGid(30, 8), 8);
    f.addGlobalId(createGid(10, 9), 9); // bid1 is active so added document should be active as well
    EXPECT_TRUE(assertActiveLids(BoolVector().T().T().T().T().T().T().T().F().T(), f.dms.getActiveLids()));
    EXPECT_EQUAL(8u, f.dms.getNumActiveLids());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());

    f.dms.setBucketState(f.bid1, false);
    EXPECT_TRUE(assertActiveLids(BoolVector().F().F().T().F().F().T().T().F().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(3u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());

    f.dms.setBucketState(f.bid2, false);
    EXPECT_TRUE(assertActiveLids(BoolVector().F().F().F().F().F().F().F().F().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid1).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid2).isActive());
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->get(f.bid3).isActive());
}


TEST("requireThatRemovedLidsAreClearedAsActive")
{
    UserDocFixture f;
    f.dms.constructFreeList();
    f.addGlobalIds(2);
    f.dms.setBucketState(f.bid1, true);
    EXPECT_TRUE(assertActiveLids(BoolVector().T().T(), f.dms.getActiveLids()));
    EXPECT_EQUAL(2u, f.dms.getNumActiveLids());
    f.dms.remove(2);
    f.dms.removeComplete(2);
    EXPECT_TRUE(assertActiveLids(BoolVector().T().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(1u, f.dms.getNumActiveLids());
    f.addGlobalId(f.gids[2], 2); // from bid2
    EXPECT_TRUE(assertActiveLids(BoolVector().T().F(), f.dms.getActiveLids()));
    EXPECT_EQUAL(1u, f.dms.getNumActiveLids());
    f.dms.remove(2);
    f.dms.removeComplete(2);
    f.addGlobalId(f.gids[3], 2); // from bid1
    EXPECT_TRUE(assertActiveLids(BoolVector().T().T(), f.dms.getActiveLids()));
    EXPECT_EQUAL(2u, f.dms.getNumActiveLids());
}

TEST("require that whitelist blueprint is created")
{
    UserDocFixture f;
    f.dms.constructFreeList();
    f.addGlobalIds();

    f.dms.setBucketState(f.bid1, true);
    EXPECT_TRUE(assertWhiteList(SimpleResult().addHit(1).addHit(2).addHit(4).addHit(5), f.dms.createWhiteListBlueprint(),
                                true, f.dms.getCommittedDocIdLimit()));

    f.dms.setBucketState(f.bid2, true);
    EXPECT_TRUE(assertWhiteList(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(4).addHit(5).addHit(6).addHit(7), f.dms.createWhiteListBlueprint(),
                                true,  f.dms.getCommittedDocIdLimit()));
}

TEST("requireThatDocumentAndMetaEntryCountIsUpdated")
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f.addGlobalIds();
    EXPECT_EQUAL(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQUAL(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQUAL(3u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQUAL(3u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f.dms.remove(3); // from bid2
    f.dms.removeComplete(3);
    EXPECT_EQUAL(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getDocumentCount());
    EXPECT_EQUAL(4u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    EXPECT_EQUAL(2u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getDocumentCount());
    EXPECT_EQUAL(2u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
}

TEST("requireThatEmptyBucketsAreRemoved")
{
    UserDocFixture f;
    f.dms.constructFreeList();
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.addGlobalIds(3);
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(3); // from bid2
    f.dms.removeComplete(3);
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid2).getEntryCount());
    f._bucketDBHandler.handleDeleteBucket(f.bid2);
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(1); // from bid1
    f.dms.removeComplete(1);
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
    f.dms.remove(2); // from bid1
    f.dms.removeComplete(2);
    EXPECT_TRUE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_EQUAL(0u, f.dms.getBucketDB().takeGuard()->get(f.bid1).getEntryCount());
    f._bucketDBHandler.handleDeleteBucket(f.bid1);
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid1));
    EXPECT_FALSE(f.dms.getBucketDB().takeGuard()->hasBucket(f.bid2));
}


struct GlobalIdEntry {
    uint32_t lid;
    GlobalId gid;
    BucketId bid1;
    BucketId bid2;
    BucketId bid3;
    GlobalIdEntry(uint32_t lid_) :
        lid(lid_),
        gid(createGid(lid_)),
        bid1(1, gid.convertToBucketId().getRawId()),
        bid2(2, gid.convertToBucketId().getRawId()),
        bid3(3, gid.convertToBucketId().getRawId())
    {}
};

typedef std::vector<GlobalIdEntry> GlobalIdVector;

struct MyBucketCreateListener : public IBucketCreateListener
{
    std::vector<document::BucketId> _buckets;

    MyBucketCreateListener();
    ~MyBucketCreateListener();
    virtual void notifyCreateBucket(const document::BucketId &bucket) override;
};

MyBucketCreateListener::MyBucketCreateListener()
{
}

MyBucketCreateListener::~MyBucketCreateListener()
{
}


void
MyBucketCreateListener::notifyCreateBucket(const document::BucketId &bucket)
{
    _buckets.emplace_back(bucket);
}

struct SplitAndJoinEmptyFixture
{
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

    void assertNotifyCreateBuckets(std::vector<document::BucketId> expBuckets) {
        EXPECT_EQUAL(expBuckets, _bucketCreateListener._buckets);
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


struct SplitAndJoinFixture : public SplitAndJoinEmptyFixture
{
    typedef std::map<BucketId, GlobalIdVector> BucketMap;
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
            EXPECT_TRUE(dms.put(gids[i].gid, gids[i].bid1, Timestamp(0),
                                docSize,
                                gids[i].lid).ok());
        }
    }
    void insertGids2() {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            EXPECT_TRUE(dms.put(gids[i].gid, gids[i].bid2, Timestamp(0),
                                docSize,
                                gids[i].lid).ok());
        }
    }

    void
    insertGids1Mostly(const BucketId &alt)
    {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            const GlobalIdEntry &g(gids[i]);
            BucketId b(g.bid3 == alt ? g.bid2 : g.bid1);
            EXPECT_TRUE(dms.put(g.gid, b, Timestamp(0), docSize, g.lid).ok());
        }
    }

    void
    insertGids2Mostly(const BucketId &alt)
    {
        uint32_t docSize = 1;
        for (size_t i = 0; i < gids.size(); ++i) {
            const GlobalIdEntry &g(gids[i]);
            BucketId b(g.bid3 == alt ? g.bid1 : g.bid2);
            EXPECT_TRUE(dms.put(g.gid, b, Timestamp(0), docSize, g.lid).ok());
        }
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
    ASSERT_EQUAL(2u, bid1s.size());
    ASSERT_EQUAL(4u, bid2s.size());
    ASSERT_EQUAL(8u, bid3s.size());
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
        ASSERT_TRUE(lid <= sz && lid > 0u);
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
        ASSERT_TRUE(lid <= sz && lid > 0u);
        if (g.bid3 == skip)
            continue;
        retval[lid - 1] = true;
    }
    return retval;
}

TEST("requireThatBucketInfoIsCorrectAfterSplit")
{
    SplitAndJoinFixture f;
    f.insertGids1();
    BucketInfo bi10 = f.getInfo(f.bid10);
    BucketInfo bi11 = f.getInfo(f.bid11);
    LOG(info, "%s: %s", f.bid10.toString().c_str(), bi10.toString().c_str());
    LOG(info, "%s: %s", f.bid11.toString().c_str(), bi11.toString().c_str());
    EXPECT_TRUE(assertBucketInfo(f.bid10Gids->size(), f.bid10Gids->size(), bi10));
    EXPECT_TRUE(assertBucketInfo(f.bid11Gids->size(), f.bid11Gids->size(), bi11));
    EXPECT_NOT_EQUAL(bi10.getEntryCount(), bi11.getEntryCount());
    EXPECT_EQUAL(31u, bi10.getEntryCount() + bi11.getEntryCount());

    f._bucketDBHandler.handleSplit(10, f.bid11, f.bid21, f.bid23);

    BucketInfo nbi10 = f.getInfo(f.bid10);
    BucketInfo nbi11 = f.getInfo(f.bid11);
    BucketInfo bi21 = f.getInfo(f.bid21);
    BucketInfo bi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid10.toString().c_str(), nbi10.toString().c_str());
    LOG(info, "%s: %s", f.bid11.toString().c_str(), nbi11.toString().c_str());
    LOG(info, "%s: %s", f.bid21.toString().c_str(), bi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), bi23.toString().c_str());
    EXPECT_TRUE(assertBucketInfo(f.bid10Gids->size(),
                                 f.bid10Gids->size(),
                                 nbi10));
    EXPECT_TRUE(assertBucketInfo(0u, 0u, nbi11));
    EXPECT_TRUE(assertBucketInfo(f.bid21Gids->size(),
                                 f.bid21Gids->size(),
                                 bi21));
    EXPECT_TRUE(assertBucketInfo(f.bid23Gids->size(),
                                 f.bid23Gids->size(),
                                 bi23));
    EXPECT_EQUAL(bi11.getEntryCount(),
                 bi21.getEntryCount() + bi23.getEntryCount());
    EXPECT_EQUAL(bi11.getDocumentCount(),
                 bi21.getDocumentCount() +
                 bi23.getDocumentCount());
    f.assertNotifyCreateBuckets({ f.bid21, f.bid23 });
}

TEST("requireThatActiveStateIsPreservedAfterSplit")
{
    { // non-active bucket
        SplitAndJoinFixture f;
        f.insertGids1();
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // non-active source, active overlapping target1
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid30);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
        f.dms.setBucketState(f.bid20, true);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        assertActiveLids(getBoolVector(*f.bid30Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid30Gids->size(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
    }
    { // non-active source, active overlapping target2
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid32);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
        f.dms.setBucketState(f.bid22, true);
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid32Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid32Gids->size(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
    }
    { // active source, non-active overlapping target1
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid30);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid30));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQUAL(filtered.countTrue(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
    { // active source, non-active overlapping target2
        SplitAndJoinFixture f;
        f.insertGids1Mostly(f.bid32);
        f.dms.setBucketState(f.bid10, true);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid32));
        assertActiveLids(filtered, f.dms.getActiveLids());
        EXPECT_EQUAL(filtered.countTrue(), f.dms.getNumActiveLids());
        f._bucketDBHandler.handleSplit(10, f.bid10, f.bid20, f.bid22);
        EXPECT_TRUE(f.getInfo(f.bid20).isActive());
        EXPECT_TRUE(f.getInfo(f.bid22).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
}

TEST("requireThatActiveStateIsPreservedAfterEmptySplit")
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

TEST("requireThatBucketInfoIsCorrectAfterJoin")
{
    SplitAndJoinFixture f;
    f.insertGids2();
    BucketInfo bi21 = f.getInfo(f.bid21);
    BucketInfo bi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid21.toString().c_str(), bi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), bi23.toString().c_str());
    EXPECT_TRUE(assertBucketInfo(f.bid21Gids->size(), f.bid21Gids->size(), bi21));
    EXPECT_TRUE(assertBucketInfo(f.bid23Gids->size(), f.bid23Gids->size(), bi23));
    EXPECT_NOT_EQUAL(bi21.getEntryCount(), bi23.getEntryCount());
    EXPECT_EQUAL(f.bid11Gids->size(), bi21.getEntryCount() + bi23.getEntryCount());

    f._bucketDBHandler.handleJoin(10, f.bid21, f.bid23, f.bid11);
    BucketInfo bi11 = f.getInfo(f.bid11);
    BucketInfo nbi21 = f.getInfo(f.bid21);
    BucketInfo nbi23 = f.getInfo(f.bid23);
    LOG(info, "%s: %s", f.bid11.toString().c_str(), bi11.toString().c_str());
    LOG(info, "%s: %s", f.bid21.toString().c_str(), nbi21.toString().c_str());
    LOG(info, "%s: %s", f.bid23.toString().c_str(), nbi23.toString().c_str());
    EXPECT_TRUE(assertBucketInfo(f.bid11Gids->size(),
                                 f.bid11Gids->size(), bi11));
    EXPECT_TRUE(assertBucketInfo(0u, 0u, nbi21));
    EXPECT_TRUE(assertBucketInfo(0u, 0u, nbi23));
    EXPECT_EQUAL(bi21.getEntryCount() + bi23.getEntryCount(),
                 bi11.getEntryCount());
    EXPECT_EQUAL(bi21.getDocumentCount() +
                 bi23.getDocumentCount(),
                 bi11.getDocumentCount());
    f.assertNotifyCreateBuckets({ f.bid11 });
}

TEST("requireThatActiveStateIsPreservedAfterJoin")
{
    { // non-active buckets
        SplitAndJoinFixture f;
        f.insertGids2();
        EXPECT_FALSE(f.getInfo(f.bid20).isActive());
        EXPECT_FALSE(f.getInfo(f.bid22).isActive());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid30Gids->size(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(f.bid32Gids->size(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_FALSE(f.getInfo(f.bid10).isActive());
        assertActiveLids(BoolVector(31), f.dms.getActiveLids());
        EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(filtered.countTrue(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
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
        EXPECT_EQUAL(filtered.countTrue(), f.dms.getNumActiveLids());

        f._bucketDBHandler.handleJoin(10, f.bid20, f.bid22, f.bid10);
        EXPECT_TRUE(f.getInfo(f.bid10).isActive());
        assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                         f.dms.getActiveLids());
        EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
    }
}

TEST("requireThatActiveStateIsPreservedAfterEmptyJoin")
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

TEST("requireThatOverlappingBucketActiveStateWorks")
{
    SplitAndJoinFixture f;
    f.insertGids1Mostly(f.bid30);
    assertActiveLids(BoolVector(31), f.dms.getActiveLids());
    EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid10, true);
    BoolVector filtered(getBoolVectorFiltered(*f.bid10Gids, 31, f.bid30));
    assertActiveLids(filtered, f.dms.getActiveLids());
    EXPECT_EQUAL(filtered.countTrue(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid20, true);
    assertActiveLids(getBoolVector(*f.bid10Gids, 31),
                     f.dms.getActiveLids());
    EXPECT_EQUAL(f.bid10Gids->size(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid10, false);
    assertActiveLids(getBoolVector(*f.bid30Gids, 31),
                     f.dms.getActiveLids());
    EXPECT_EQUAL(f.bid30Gids->size(), f.dms.getNumActiveLids());
    f.dms.setBucketState(f.bid20, false);
    assertActiveLids(BoolVector(31), f.dms.getActiveLids());
    EXPECT_EQUAL(0u, f.dms.getNumActiveLids());
}

struct RemovedFixture
{
    std::shared_ptr<BucketDBOwner> _bucketDB;
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
          DocumentMetaStore::IGidCompare::SP(new DocumentMetaStore::DefaultGidCompare),
          SubDbType::REMOVED),
      _bucketDBHandler(dms.getBucketDB())
{
    _bucketDBHandler.addDocumentMetaStore(&dms, 0);
}
RemovedFixture::~RemovedFixture() {}

TEST("requireThatRemoveChangedBucketWorks")
{
    RemovedFixture f;
    GlobalIdEntry g(1);
    f.dms.constructFreeList();
    f._bucketDBHandler.handleCreateBucket(g.bid1);
    uint32_t addLid1 = addGid(f.dms, g.gid, g.bid1, Timestamp(0));
    EXPECT_EQUAL(1u, addLid1);
    uint32_t addLid2 = addGid(f.dms, g.gid, g.bid2, Timestamp(0));
    EXPECT_TRUE(1u == addLid2);
    EXPECT_TRUE(f.dms.remove(1u));
    f.dms.removeComplete(1u);
}

TEST("requireThatGetLidUsageStatsWorks")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();

    LidUsageStats s = dms.getLidUsageStats();
    EXPECT_EQUAL(1u, s.getLidLimit());
    EXPECT_EQUAL(0u, s.getUsedLids());
    EXPECT_EQUAL(1u, s.getLowestFreeLid());
    EXPECT_EQUAL(0u, s.getHighestUsedLid());

    putGid(dms, createGid(1), 1);
    
    s = dms.getLidUsageStats();
    EXPECT_EQUAL(2u, s.getLidLimit());
    EXPECT_EQUAL(1u, s.getUsedLids());
    EXPECT_EQUAL(2u, s.getLowestFreeLid());
    EXPECT_EQUAL(1u, s.getHighestUsedLid());

    putGid(dms, createGid(2), 2);

    s = dms.getLidUsageStats();
    EXPECT_EQUAL(3u, s.getLidLimit());
    EXPECT_EQUAL(2u, s.getUsedLids());
    EXPECT_EQUAL(3u, s.getLowestFreeLid());
    EXPECT_EQUAL(2u, s.getHighestUsedLid());


    putGid(dms, createGid(3), 3);

    s = dms.getLidUsageStats();
    EXPECT_EQUAL(4u, s.getLidLimit());
    EXPECT_EQUAL(3u, s.getUsedLids());
    EXPECT_EQUAL(4u, s.getLowestFreeLid());
    EXPECT_EQUAL(3u, s.getHighestUsedLid());

    dms.remove(1);
    dms.removeComplete(1);
    
    s = dms.getLidUsageStats();
    EXPECT_EQUAL(4u, s.getLidLimit());
    EXPECT_EQUAL(2u, s.getUsedLids());
    EXPECT_EQUAL(1u, s.getLowestFreeLid());
    EXPECT_EQUAL(3u, s.getHighestUsedLid());

    dms.remove(3);
    dms.removeComplete(3);
    
    s = dms.getLidUsageStats();
    EXPECT_EQUAL(4u, s.getLidLimit());
    EXPECT_EQUAL(1u, s.getUsedLids());
    EXPECT_EQUAL(1u, s.getLowestFreeLid());
    EXPECT_EQUAL(2u, s.getHighestUsedLid());

    dms.remove(2);
    dms.removeComplete(2);
    
    s = dms.getLidUsageStats();
    EXPECT_EQUAL(4u, s.getLidLimit());
    EXPECT_EQUAL(0u, s.getUsedLids());
    EXPECT_EQUAL(1u, s.getLowestFreeLid());
    EXPECT_EQUAL(0u, s.getHighestUsedLid());
}

bool
assertLidBloat(uint32_t expBloat, uint32_t lidLimit, uint32_t usedLids)
{
    LidUsageStats stats(lidLimit, usedLids, 0, 0);
    return  EXPECT_EQUAL(expBloat, stats.getLidBloat());
}

TEST("require that LidUsageStats::getLidBloat() works")
{
    assertLidBloat(4, 10, 5);
    assertLidBloat(0, 1, 0);
    assertLidBloat(0, 1, 1);
}

TEST("requireThatMoveWorks")
{
    DocumentMetaStore dms(createBucketDB());
    GlobalId gid;
    uint32_t lid = 0u;
    dms.constructFreeList();
    
    EXPECT_EQUAL(1u, dms.getNumDocs());
    EXPECT_EQUAL(0u, dms.getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId1, time1, 1u, gid1, dms));
    EXPECT_EQUAL(2u, dms.getNumDocs());
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    EXPECT_TRUE(assertPut(bucketId2, time2, 2u, gid2, dms));
    EXPECT_EQUAL(3u, dms.getNumDocs());
    EXPECT_EQUAL(2u, dms.getNumUsedLids());
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_TRUE(dms.getLid(gid2, lid));
    EXPECT_EQUAL(gid1, gid);
    EXPECT_EQUAL(2u, lid);
    EXPECT_TRUE(dms.remove(1));
    EXPECT_FALSE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGidEvenIfMoved(1u, gid));
    EXPECT_TRUE(dms.getGid(2u, gid));
    dms.removeComplete(1u);
    EXPECT_FALSE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGidEvenIfMoved(1u, gid));
    EXPECT_TRUE(dms.getGid(2u, gid));
    EXPECT_EQUAL(1u, dms.getNumUsedLids());
    dms.move(2u, 1u);
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGid(2u, gid));
    EXPECT_TRUE(dms.getGidEvenIfMoved(2u, gid));
    dms.removeComplete(2u);
    EXPECT_TRUE(dms.getGid(1u, gid));
    EXPECT_FALSE(dms.getGid(2u, gid));
    EXPECT_TRUE(dms.getGidEvenIfMoved(2u, gid));
    EXPECT_TRUE(dms.getLid(gid2, lid));
    EXPECT_EQUAL(gid2, gid);
    EXPECT_EQUAL(1u, lid);
}

bool
assertLidSpace(uint32_t numDocs,
               uint32_t committedDocIdLimit,
               uint32_t numUsedLids,
               bool wantShrinkLidSpace,
               bool canShrinkLidSpace,
               const DocumentMetaStore &dms)
{
    if (!EXPECT_EQUAL(numDocs, dms.getNumDocs())) return false;
    if (!EXPECT_EQUAL(committedDocIdLimit, dms.getCommittedDocIdLimit())) return false;
    if (!EXPECT_EQUAL(numUsedLids, dms.getNumUsedLids())) return false;
    if (!EXPECT_EQUAL(wantShrinkLidSpace, dms.wantShrinkLidSpace())) return false;
    if (!EXPECT_EQUAL(canShrinkLidSpace, dms.canShrinkLidSpace())) return false;
    return true;
}

void
populate(uint32_t endLid, DocumentMetaStore &dms)
{
    for (uint32_t lid = 1; lid < endLid; ++lid) {
        GlobalId gid = createGid(lid);
        putGid(dms, gid, lid, Timestamp(10000 + lid));
    }
    EXPECT_TRUE(assertLidSpace(endLid, endLid, endLid - 1, false, false, dms));
}

void
remove(uint32_t startLid, uint32_t shrinkTarget, DocumentMetaStore &dms)
{
    for (uint32_t lid = startLid; lid >= shrinkTarget; --lid) {
        dms.remove(lid);
        dms.removeComplete(lid);
    }
}

TEST("requireThatShrinkWorks")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();

    populate(10, dms);

    uint32_t shrinkTarget = 5;
    remove(9, shrinkTarget, dms);
    EXPECT_TRUE(assertLidSpace(10, 10, shrinkTarget - 1, false, false, dms));

    dms.compactLidSpace(shrinkTarget);
    EXPECT_TRUE(assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, dms));

    dms.holdUnblockShrinkLidSpace();
    EXPECT_TRUE(assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, true, dms));

    dms.shrinkLidSpace();
    EXPECT_TRUE(assertLidSpace(shrinkTarget, shrinkTarget, shrinkTarget - 1, false, false, dms));
}


TEST("requireThatShrinkViaFlushTargetWorks")
{
    DocumentMetaStore::SP dms(new DocumentMetaStore(createBucketDB()));
    dms->constructFreeList();
    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    DummyTlsSyncer dummyTlsSyncer;
    HwInfo hwInfo;
    vespalib::rmdir("dmsflush", true);
    vespalib::mkdir("dmsflush");
    using Type = IFlushTarget::Type;
    using Component = IFlushTarget::Component;
    IFlushTarget::SP ft(std::make_shared<ShrinkLidSpaceFlushTarget>
                        ("documentmetastore.shrink", Type::GC, Component::ATTRIBUTE, 0, IFlushTarget::Time(), dms));
    populate(10, *dms);

    uint32_t shrinkTarget = 5;
    remove(9, shrinkTarget, *dms);
    EXPECT_TRUE(assertLidSpace(10, 10, shrinkTarget - 1, false, false, *dms));
    EXPECT_EQUAL(ft->getApproxMemoryGain().getBefore(),
                 ft->getApproxMemoryGain().getAfter());

    dms->compactLidSpace(shrinkTarget);
    EXPECT_TRUE(assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, *dms));
    EXPECT_EQUAL(ft->getApproxMemoryGain().getBefore(),
                 ft->getApproxMemoryGain().getAfter());
    AttributeGuard::UP g(new AttributeGuard(dms));

    dms->holdUnblockShrinkLidSpace();
    EXPECT_TRUE(assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, false, *dms));
    EXPECT_EQUAL(ft->getApproxMemoryGain().getBefore(),
                 ft->getApproxMemoryGain().getAfter());

    g.reset();
    dms->removeAllOldGenerations();
    EXPECT_TRUE(assertLidSpace(10, shrinkTarget, shrinkTarget - 1, true, true, *dms));
    EXPECT_TRUE(ft->getApproxMemoryGain().getBefore() >
                ft->getApproxMemoryGain().getAfter());

    vespalib::ThreadStackExecutor exec(1, 128 * 1024);
    vespalib::Executor::Task::UP task = ft->initFlush(11);
    exec.execute(std::move(task));
    exec.sync();
    exec.shutdown();
    EXPECT_TRUE(assertLidSpace(shrinkTarget, shrinkTarget, shrinkTarget - 1, false, false, *dms));
    EXPECT_EQUAL(ft->getApproxMemoryGain().getBefore(),
                 ft->getApproxMemoryGain().getAfter());
}


namespace {

void
addLid(DocumentMetaStore &dms, uint32_t lid, uint32_t docSize = 1)
{
    GlobalId gid = createGid(lid);
    BucketId bucketId(gid.convertToBucketId());
    bucketId.setUsedBits(numBucketBits);
    uint32_t addedLid = addGid(dms, gid, bucketId, Timestamp(lid + timestampBias), docSize);
    EXPECT_EQUAL(lid, addedLid);
}

void
assertSize(DocumentMetaStore &dms, uint32_t lid, uint32_t expSize)
{
    EXPECT_TRUE(dms.validLid(lid));
    const auto &metaData = dms.getRawMetaData(lid);
    EXPECT_EQUAL(expSize, metaData.getDocSize());
}

void
removeLid(DocumentMetaStore &dms, uint32_t lid)
{
    dms.remove(lid);
    dms.removeComplete(lid);
}


void
assertCompact(DocumentMetaStore &dms, uint32_t docIdLimit,
              uint32_t committedDocIdLimit,
              uint32_t compactTarget, uint32_t numUsedLids)
{
    EXPECT_TRUE(assertLidSpace(docIdLimit, committedDocIdLimit, numUsedLids, false, false, dms));
    dms.compactLidSpace(compactTarget);
    EXPECT_TRUE(assertLidSpace(docIdLimit, compactTarget, numUsedLids, true, false, dms));
    dms.holdUnblockShrinkLidSpace();
    EXPECT_TRUE(assertLidSpace(docIdLimit, compactTarget, numUsedLids, true, true, dms));
}


void
assertShrink(DocumentMetaStore &dms, uint32_t shrinkTarget,
             uint32_t numUsedLids)
{
    dms.shrinkLidSpace();
    TEST_DO(EXPECT_TRUE(assertLidSpace(shrinkTarget, shrinkTarget, numUsedLids, false, false, dms)));
}

}


TEST("requireThatSecondShrinkWorksAfterCompactAndInactiveInsert")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    TEST_DO(addLid(dms, 1));
    TEST_DO(addLid(dms, 2));
    TEST_DO(addLid(dms, 3));
    removeLid(dms, 2);
    removeLid(dms, 3);
    EXPECT_TRUE(assertLidSpace(4, 4, 1, false, false, dms));
    TEST_DO(assertCompact(dms, 4, 4, 2, 1));
    TEST_DO(addLid(dms, 2));
    TEST_DO(assertShrink(dms, 3, 2));
    removeLid(dms, 2);
    TEST_DO(assertCompact(dms, 3, 3, 2, 1));
    TEST_DO(assertShrink(dms, 2, 1));
}

TEST("require that document sizes are saved")
{
    DocumentMetaStore dms1(createBucketDB());
    dms1.constructFreeList();
    TEST_DO(addLid(dms1, 1, 100));
    TEST_DO(addLid(dms1, 2, 10000));
    TEST_DO(addLid(dms1, 3, 100000000));
    TEST_DO(assertSize(dms1, 1, 100));
    TEST_DO(assertSize(dms1, 2, 10000));
    TEST_DO(assertSize(dms1, 3, (1u << 24) - 1));

    TuneFileAttributes tuneFileAttributes;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    EXPECT_TRUE(dms1.saveAs("documentmetastore3", saveTarget));
    dms1.setTrackDocumentSizes(false);
    EXPECT_TRUE(dms1.saveAs("documentmetastore4", saveTarget));

    DocumentMetaStore dms3(createBucketDB(), "documentmetastore3");
    EXPECT_TRUE(dms3.load());
    dms3.constructFreeList();
    TEST_DO(assertSize(dms3, 1, 100));
    TEST_DO(assertSize(dms3, 2, 10000));
    TEST_DO(assertSize(dms3, 3, (1u << 24) - 1));

    DocumentMetaStore dms4(createBucketDB(), "documentmetastore4");
    EXPECT_TRUE(dms4.load());
    dms4.constructFreeList();
    TEST_DO(assertSize(dms4, 1, 1));
    TEST_DO(assertSize(dms4, 2, 1));
    TEST_DO(assertSize(dms4, 3, 1));
}

namespace {

void
assertLidGidFound(uint32_t lid, DocumentMetaStore &dms)
{
    GlobalId gid = createGid(lid);
    EXPECT_TRUE(assertLid(lid, gid, dms));
    EXPECT_TRUE(assertGid(gid, lid, dms));
    EXPECT_TRUE(dms.validLid(lid));
}

void
assertLidGidNotFound(uint32_t lid, DocumentMetaStore &dms)
{
    GlobalId gid = createGid(lid);
    uint32_t resultLid;
    GlobalId resultGid;
    EXPECT_FALSE(dms.getLid(gid, resultLid));
    EXPECT_FALSE(dms.getGid(lid, resultGid));
    EXPECT_FALSE(dms.validLid(lid));
}

}

TEST("require that multiple lids can be removed with removeBatch()")
{
    DocumentMetaStore dms(createBucketDB());
    dms.constructFreeList();
    TEST_DO(addLid(dms, 1));
    TEST_DO(addLid(dms, 2));
    TEST_DO(addLid(dms, 3));
    TEST_DO(addLid(dms, 4));

    TEST_DO(assertLidGidFound(1, dms));
    TEST_DO(assertLidGidFound(2, dms));
    TEST_DO(assertLidGidFound(3, dms));
    TEST_DO(assertLidGidFound(4, dms));

    dms.removeBatch({1, 3}, 5);
    dms.removeBatchComplete({1, 3});

    TEST_DO(assertLidGidNotFound(1, dms));
    TEST_DO(assertLidGidFound(2, dms));
    TEST_DO(assertLidGidNotFound(3, dms));
    TEST_DO(assertLidGidFound(4, dms));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
