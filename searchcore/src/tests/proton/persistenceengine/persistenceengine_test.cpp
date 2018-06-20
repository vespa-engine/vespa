// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-stor-distribution.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/persistence/spi/documentselection.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/searchcore/proton/persistenceengine/bucket_guard.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/metrics/loadmetric.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <algorithm>
#include <set>

using document::BucketId;
using document::BucketSpace;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::test::makeBucketSpace;
using search::DocumentMetaData;
using storage::spi::Bucket;
using storage::spi::BucketChecksum;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfo;
using storage::spi::BucketInfoResult;
using storage::spi::ClusterState;
using storage::spi::Context;
using storage::spi::CreateIteratorResult;
using storage::spi::DocumentSelection;
using storage::spi::GetResult;
using storage::spi::IterateResult;
using storage::spi::IteratorId;
using storage::spi::PartitionId;
using storage::spi::PersistenceProvider;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Selection;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using storage::spi::test::makeSpiBucket;
using namespace proton;
using namespace vespalib;

DocumentType
createDocType(const vespalib::string &name, int32_t id)
{
    return DocumentType(name, id);
}


document::Document::SP
createDoc(const DocumentType &docType, const DocumentId &docId)
{
    return document::Document::SP(new document::Document(docType, docId));
}


document::DocumentUpdate::SP
createUpd(const DocumentType& docType, const DocumentId &docId)
{
    static std::vector<std::unique_ptr<document::DocumentTypeRepo>> repoList;
    repoList.emplace_back(std::make_unique<document::DocumentTypeRepo>(docType));
    return std::make_shared<document::DocumentUpdate>(*repoList.back(), docType, docId);
}

storage::spi::ClusterState
createClusterState(const storage::lib::State& nodeState = storage::lib::State::UP)
{
    using storage::lib::Distribution;
    using storage::lib::Node;
    using storage::lib::NodeState;
    using storage::lib::NodeType;
    using storage::lib::State;
    using vespa::config::content::StorDistributionConfigBuilder;
    typedef StorDistributionConfigBuilder::Group Group;
    typedef Group::Nodes Nodes;
    storage::lib::ClusterState cstate;
    StorDistributionConfigBuilder dc;

    cstate.setNodeState(Node(NodeType::STORAGE, 0),
                        NodeState(NodeType::STORAGE, nodeState, "dummy desc", 1.0, 1));
    cstate.setClusterState(State::UP);
    dc.redundancy = 1;
    dc.readyCopies = 1;
    dc.group.push_back(Group());
    Group &g(dc.group[0]);
    g.index = "invalid";
    g.name = "invalid";
    g.capacity = 1.0;
    g.partitions = "";
    g.nodes.push_back(Nodes());
    Nodes &n(g.nodes[0]);
    n.index = 0;
    Distribution dist(dc);
    return ClusterState(cstate, 0, dist);
}


struct MyDocumentRetriever : DocumentRetrieverBaseForTest {
    document::DocumentTypeRepo repo;
    const Document *document;
    Timestamp timestamp;
    DocumentId &last_doc_id;

    MyDocumentRetriever(const Document *d, Timestamp ts, DocumentId &last_id)
        : repo(), document(d), timestamp(ts), last_doc_id(last_id) {}
    const document::DocumentTypeRepo &getDocumentTypeRepo() const override {
        return repo;
    }
    void getBucketMetaData(const storage::spi::Bucket &, search::DocumentMetaData::Vector &v) const override {
        if (document != 0) {
            v.push_back(getDocumentMetaData(document->getId()));
        }
    }
    DocumentMetaData getDocumentMetaData(const DocumentId &id) const override {
        last_doc_id = id;
        if (document != 0) {
            return DocumentMetaData(1, timestamp, document::BucketId(1), document->getId().getGlobalId());
        }
        return DocumentMetaData();
    }
    document::Document::UP getDocument(search::DocumentIdT) const override {
        if (document != 0) {
            return Document::UP(document->clone());
        }
        return Document::UP();
    }

    CachedSelect::SP parseSelect(const vespalib::string &) const override {
        return CachedSelect::SP();
    }
};

struct MyHandler : public IPersistenceHandler, IBucketFreezer {
    bool                         initialized;
    Bucket                       lastBucket;
    Timestamp                    lastTimestamp;
    DocumentId                   lastDocId;
    Timestamp                    existingTimestamp;
    const ClusterState*          lastCalc;
    storage::spi::BucketInfo::ActiveState lastBucketState;
    BucketIdListResult::List     bucketList;
    Result                       bucketStateResult;
    BucketInfo                   bucketInfo;
    Result                       deleteBucketResult;
    BucketIdListResult::List     modBucketList;
    Result                       _splitResult;
    Result                       _joinResult;
    Result                       _createBucketResult;
    const Document *document;
    std::multiset<uint64_t> frozen;
    std::multiset<uint64_t> was_frozen;

    MyHandler()
        : initialized(false),
          lastBucket(),
          lastTimestamp(),
          lastDocId(),
          existingTimestamp(),
          lastCalc(NULL),
          lastBucketState(),
          bucketList(),
          bucketStateResult(),
          bucketInfo(),
          deleteBucketResult(),
          modBucketList(),
          _splitResult(),
          _joinResult(),
          _createBucketResult(),
          document(0),
          frozen(),
          was_frozen()
    {
    }

    void setExistingTimestamp(Timestamp ts) {
        existingTimestamp = ts;
    }
    void setDocument(const Document &doc, Timestamp ts) {
        document = &doc;
        setExistingTimestamp(ts);
    }
    void handle(FeedToken token, const Bucket &bucket, Timestamp timestamp, const DocumentId &docId) {
        (void) token;
        lastBucket = bucket;
        lastTimestamp = timestamp;
        lastDocId = docId;
    }

    void initialize() override { initialized = true; }

    void handlePut(FeedToken token, const Bucket& bucket,
                   Timestamp timestamp, const document::Document::SP& doc) override {
        token->setResult(ResultUP(new storage::spi::Result()), false);
        handle(token, bucket, timestamp, doc->getId());
    }

    void handleUpdate(FeedToken token, const Bucket& bucket,
                      Timestamp timestamp, const document::DocumentUpdate::SP& upd) override {
        token->setResult(ResultUP(new storage::spi::UpdateResult(existingTimestamp)), existingTimestamp > 0);
        handle(token, bucket, timestamp, upd->getId());
    }

    void handleRemove(FeedToken token, const Bucket& bucket,
                      Timestamp timestamp, const DocumentId& id) override {
        bool wasFound = existingTimestamp > 0;
        token->setResult(ResultUP(new storage::spi::RemoveResult(wasFound)), wasFound);
        handle(token, bucket, timestamp, id);
    }

    void handleListBuckets(IBucketIdListResultHandler &resultHandler) override {
        resultHandler.handle(BucketIdListResult(bucketList));
    }

    void handleSetClusterState(const ClusterState &calc, IGenericResultHandler &resultHandler) override {
        lastCalc = &calc;
        resultHandler.handle(Result());
    }

    void handleSetActiveState(const Bucket &bucket, storage::spi::BucketInfo::ActiveState newState,
                              IGenericResultHandler &resultHandler) override {
        lastBucket = bucket;
        lastBucketState = newState;
        resultHandler.handle(bucketStateResult);
    }

    void handleGetBucketInfo(const Bucket &, IBucketInfoResultHandler &resultHandler) override {
        resultHandler.handle(BucketInfoResult(bucketInfo));
    }

    void handleCreateBucket(FeedToken token, const storage::spi::Bucket &) override {
        token->setResult(ResultUP(new Result(_createBucketResult)), true);
    }

    void handleDeleteBucket(FeedToken token, const storage::spi::Bucket &) override {
        token->setResult(ResultUP(new Result(deleteBucketResult)), true);
    }

    void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) override {
        resultHandler.handle(BucketIdListResult(modBucketList));
    }

    void handleSplit(FeedToken token, const storage::spi::Bucket &, const storage::spi::Bucket &,
                     const storage::spi::Bucket &) override
    {
        token->setResult(ResultUP(new Result(_splitResult)), true);
    }

    void handleJoin(FeedToken token, const storage::spi::Bucket &, const storage::spi::Bucket &,
               const storage::spi::Bucket &) override
    {
        token->setResult(ResultUP(new Result(_joinResult)), true);
    }

    RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency) override {
        RetrieversSP ret(new std::vector<IDocumentRetriever::SP>);
        ret->push_back(IDocumentRetriever::SP(new MyDocumentRetriever(0, Timestamp(), lastDocId)));
        ret->push_back(IDocumentRetriever::SP(new MyDocumentRetriever(document, existingTimestamp, lastDocId)));
        return ret;
    }

    BucketGuard::UP lockBucket(const storage::spi::Bucket &b) override {
        return BucketGuard::UP(new BucketGuard(b.getBucketId(), *this));
    }

    void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) override {
        BucketIdListResult::List list;
        resultHandler.handle(BucketIdListResult(list));
    }

    void handlePopulateActiveBuckets(document::BucketId::List &buckets, IGenericResultHandler &resultHandler) override {
        (void) buckets;
        resultHandler.handle(Result());
    }

    void freezeBucket(BucketId bucket) override {
        frozen.insert(bucket.getId());
        was_frozen.insert(bucket.getId());
    }
    void thawBucket(BucketId bucket) override {
        std::multiset<uint64_t>::iterator it = frozen.find(bucket.getId());
        ASSERT_TRUE(it != frozen.end());
        frozen.erase(it);
    }
    bool isFrozen(const Bucket &bucket) {
        return frozen.find(bucket.getBucketId().getId()) != frozen.end();
    }
    bool wasFrozen(const Bucket &bucket) {
        return was_frozen.find(bucket.getBucketId().getId()) != was_frozen.end();
    }
};


struct HandlerSet {
    IPersistenceHandler::SP phandler1;
    IPersistenceHandler::SP phandler2;
    MyHandler              &handler1;
    MyHandler              &handler2;
    HandlerSet();
    ~HandlerSet();
    void prepareListBuckets();
    void prepareGetModifiedBuckets();
};

HandlerSet::HandlerSet()
    : phandler1(new MyHandler()),
      phandler2(new MyHandler()),
      handler1(static_cast<MyHandler &>(*phandler1.get())),
      handler2(static_cast<MyHandler &>(*phandler2.get()))
{}
HandlerSet::~HandlerSet() = default;

DocumentType type1(createDocType("type1", 1));
DocumentType type2(createDocType("type2", 2));
DocumentType type3(createDocType("type3", 3));
DocumentId docId0;
DocumentId docId1("id:type1:type1::1");
DocumentId docId2("id:type2:type2::1");
DocumentId docId3("id:type3:type3::1");
Document::SP doc1(createDoc(type1, docId1));
Document::SP doc2(createDoc(type2, docId2));
Document::SP doc3(createDoc(type3, docId3));
Document::SP old_doc(createDoc(type1, DocumentId("doc:old:id-scheme")));
document::DocumentUpdate::SP upd1(createUpd(type1, docId1));
document::DocumentUpdate::SP upd2(createUpd(type2, docId2));
document::DocumentUpdate::SP upd3(createUpd(type3, docId3));
PartitionId partId(0);
BucketId bckId1(1);
BucketId bckId2(2);
BucketId bckId3(3);
Bucket bucket0;
Bucket bucket1(makeSpiBucket(bckId1, partId));
Bucket bucket2(makeSpiBucket(bckId2, partId));
BucketChecksum checksum1(1);
BucketChecksum checksum2(2);
BucketChecksum checksum3(1+2);
BucketInfo bucketInfo1(checksum1, 1, 0, 1, 0);
BucketInfo bucketInfo2(checksum2, 2, 0, 2, 0);
BucketInfo bucketInfo3(checksum3, 3, 0, 3, 0);
Timestamp tstamp0;
Timestamp tstamp1(1);
Timestamp tstamp2(2);
Timestamp tstamp3(3);
DocumentSelection doc_sel("");
Selection selection(doc_sel);
BucketSpace altBucketSpace(2);


void
HandlerSet::prepareListBuckets()
{
    handler1.bucketList.push_back(bckId1);
    handler1.bucketList.push_back(bckId2);
    handler2.bucketList.push_back(bckId2);
    handler2.bucketList.push_back(bckId3);
}

void
HandlerSet::prepareGetModifiedBuckets()
{
    handler1.modBucketList.push_back(bckId1);
    handler1.modBucketList.push_back(bckId2);
    handler2.modBucketList.push_back(bckId2);
    handler2.modBucketList.push_back(bckId3);
}

class SimplePersistenceEngineOwner : public IPersistenceEngineOwner
{
    void setClusterState(BucketSpace, const storage::spi::ClusterState &calc) override { (void) calc; }
};

struct SimpleResourceWriteFilter : public IResourceWriteFilter
{
    bool _acceptWriteOperation;
    vespalib::string _message;
    SimpleResourceWriteFilter()
        : _acceptWriteOperation(true),
          _message()
    {}

    bool acceptWriteOperation() const override { return _acceptWriteOperation; }
    State getAcceptState() const override {
        return IResourceWriteFilter::State(acceptWriteOperation(), _message);
    }
};


struct SimpleFixture {
    SimplePersistenceEngineOwner _owner;
    SimpleResourceWriteFilter _writeFilter;
    PersistenceEngine engine;
    HandlerSet hset;
    SimpleFixture(BucketSpace bucketSpace2)
        : _owner(),
          engine(_owner, _writeFilter, -1, false),
          hset()
    {
        engine.putHandler(makeBucketSpace(), DocTypeName(doc1->getType()), hset.phandler1);
        engine.putHandler(bucketSpace2, DocTypeName(doc2->getType()), hset.phandler2);
    }
    SimpleFixture()
        : SimpleFixture(makeBucketSpace())
    {
    }
};


void
assertHandler(const Bucket &expBucket, Timestamp expTimestamp,
              const DocumentId &expDocId, const MyHandler &handler)
{
    EXPECT_EQUAL(expBucket, handler.lastBucket);
    EXPECT_EQUAL(expTimestamp, handler.lastTimestamp);
    EXPECT_EQUAL(expDocId, handler.lastDocId);
}

void assertBucketList(const BucketIdListResult &result, const std::vector<BucketId> &expBuckets)
{
    const BucketIdListResult::List &bucketList = result.getList();
    EXPECT_EQUAL(expBuckets.size(), bucketList.size());
    for (const auto &expBucket : expBuckets) {
        EXPECT_TRUE(std::find(bucketList.begin(), bucketList.end(), expBucket) != bucketList.end());
    }
}

void assertBucketList(PersistenceProvider &spi, BucketSpace bucketSpace, const std::vector<BucketId> &expBuckets)
{
    BucketIdListResult result = spi.listBuckets(bucketSpace, partId);
    TEST_DO(assertBucketList(result, expBuckets));
}

void assertModifiedBuckets(PersistenceProvider &spi, BucketSpace bucketSpace, const std::vector<BucketId> &expBuckets)
{
    BucketIdListResult result = spi.getModifiedBuckets(bucketSpace);
    TEST_DO(assertBucketList(result, expBuckets));
}

TEST_F("require that getPartitionStates() prepares all handlers", SimpleFixture)
{
    EXPECT_FALSE(f.hset.handler1.initialized);
    EXPECT_FALSE(f.hset.handler2.initialized);
    f.engine.initialize();
    EXPECT_TRUE(f.hset.handler1.initialized);
    EXPECT_TRUE(f.hset.handler2.initialized);
}


TEST_F("require that puts are routed to handler", SimpleFixture)
{
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    f.engine.put(bucket1, tstamp1, doc1, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2);

    f.engine.put(bucket1, tstamp1, doc2, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2);

    EXPECT_EQUAL(Result(Result::PERMANENT_ERROR, "No handler for document type 'type3'"),
                 f.engine.put(bucket1, tstamp1, doc3, context));
}


TEST_F("require that puts with old id scheme are rejected", SimpleFixture) {
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    EXPECT_EQUAL(Result(Result::PERMANENT_ERROR, "Old id scheme not supported in elastic mode (doc:old:id-scheme)"),
                 f.engine.put(bucket1, tstamp1, old_doc, context));
}


TEST_F("require that put is rejected if resource limit is reached", SimpleFixture)
{
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    EXPECT_EQUAL(
            Result(Result::RESOURCE_EXHAUSTED,
                   "Put operation rejected for document 'doc:old:id-scheme': 'Disk is full'"),
            f.engine.put(bucket1, tstamp1, old_doc, context));
}


TEST_F("require that updates are routed to handler", SimpleFixture)
{
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    f.hset.handler1.setExistingTimestamp(tstamp2);
    UpdateResult ur = f.engine.update(bucket1, tstamp1, upd1, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2);
    EXPECT_EQUAL(tstamp2, ur.getExistingTimestamp());

    f.hset.handler2.setExistingTimestamp(tstamp3);
    ur = f.engine.update(bucket1, tstamp1, upd2, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2);
    EXPECT_EQUAL(tstamp3, ur.getExistingTimestamp());

    EXPECT_EQUAL(Result(Result::PERMANENT_ERROR, "No handler for document type 'type3'"),
                 f.engine.update(bucket1, tstamp1, upd3, context));
}


TEST_F("require that update is rejected if resource limit is reached", SimpleFixture)
{
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));

    EXPECT_EQUAL(
            Result(Result::RESOURCE_EXHAUSTED,
                   "Update operation rejected for document 'id:type1:type1::1': 'Disk is full'"),
            f.engine.update(bucket1, tstamp1, upd1, context));
}


TEST_F("require that removes are routed to handlers", SimpleFixture)
{
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    RemoveResult rr = f.engine.remove(bucket1, tstamp1, docId3, context);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler1);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2);
    EXPECT_FALSE(rr.wasFound());
    EXPECT_TRUE(rr.hasError());
    EXPECT_EQUAL(Result(Result::PERMANENT_ERROR, "No handler for document type 'type3'"), rr);

    f.hset.handler1.setExistingTimestamp(tstamp2);
    rr = f.engine.remove(bucket1, tstamp1, docId1, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2);
    EXPECT_TRUE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());

    f.hset.handler1.setExistingTimestamp(tstamp0);
    f.hset.handler2.setExistingTimestamp(tstamp3);
    rr = f.engine.remove(bucket1, tstamp1, docId2, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2);
    EXPECT_TRUE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());

    f.hset.handler2.setExistingTimestamp(tstamp0);
    rr = f.engine.remove(bucket1, tstamp1, docId2, context);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1);
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2);
    EXPECT_FALSE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());
}


TEST_F("require that remove is NOT rejected if resource limit is reached", SimpleFixture)
{
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));

    EXPECT_EQUAL(RemoveResult(false), f.engine.remove(bucket1, tstamp1, docId1, context));
}


TEST_F("require that listBuckets() is routed to handlers and merged", SimpleFixture)
{
    f.hset.prepareListBuckets();
    EXPECT_TRUE(f.engine.listBuckets(makeBucketSpace(), PartitionId(1)).getList().empty());
    TEST_DO(assertBucketList(f.engine, makeBucketSpace(), { bckId1, bckId2, bckId3 }));
}


TEST_F("require that setClusterState() is routed to handlers", SimpleFixture)
{
    ClusterState state(createClusterState());

    f.engine.setClusterState(makeBucketSpace(), state);
    EXPECT_EQUAL(&state, f.hset.handler1.lastCalc);
    EXPECT_EQUAL(&state, f.hset.handler2.lastCalc);
}


TEST_F("require that setActiveState() is routed to handlers and merged", SimpleFixture)
{
    f.hset.handler1.bucketStateResult = Result(Result::TRANSIENT_ERROR, "err1");
    f.hset.handler2.bucketStateResult = Result(Result::PERMANENT_ERROR, "err2");

    Result result = f.engine.setActiveState(bucket1, storage::spi::BucketInfo::NOT_ACTIVE);
    EXPECT_EQUAL(Result::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQUAL("err1, err2", result.getErrorMessage());
    EXPECT_EQUAL(storage::spi::BucketInfo::NOT_ACTIVE, f.hset.handler1.lastBucketState);
    EXPECT_EQUAL(storage::spi::BucketInfo::NOT_ACTIVE, f.hset.handler2.lastBucketState);

    f.engine.setActiveState(bucket1, storage::spi::BucketInfo::ACTIVE);
    EXPECT_EQUAL(storage::spi::BucketInfo::ACTIVE, f.hset.handler1.lastBucketState);
    EXPECT_EQUAL(storage::spi::BucketInfo::ACTIVE, f.hset.handler2.lastBucketState);
}


TEST_F("require that getBucketInfo() is routed to handlers and merged", SimpleFixture)
{
    f.hset.handler1.bucketInfo = bucketInfo1;
    f.hset.handler2.bucketInfo = bucketInfo2;

    BucketInfoResult result = f.engine.getBucketInfo(bucket1);
    EXPECT_EQUAL(bucketInfo3, result.getBucketInfo());
}


TEST_F("require that createBucket() is routed to handlers and merged", SimpleFixture)
{
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    f.hset.handler1._createBucketResult = Result(Result::TRANSIENT_ERROR, "err1a");
    f.hset.handler2._createBucketResult = Result(Result::PERMANENT_ERROR, "err2a");

    Result result = f.engine.createBucket(bucket1, context);
    EXPECT_EQUAL(Result::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQUAL("err1a, err2a", result.getErrorMessage());
}


TEST_F("require that deleteBucket() is routed to handlers and merged", SimpleFixture)
{
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    f.hset.handler1.deleteBucketResult = Result(Result::TRANSIENT_ERROR, "err1");
    f.hset.handler2.deleteBucketResult = Result(Result::PERMANENT_ERROR, "err2");

    Result result = f.engine.deleteBucket(bucket1, context);
    EXPECT_EQUAL(Result::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQUAL("err1, err2", result.getErrorMessage());
}


TEST_F("require that getModifiedBuckets() is routed to handlers and merged", SimpleFixture)
{
    f.hset.prepareGetModifiedBuckets();
    TEST_DO(assertModifiedBuckets(f.engine, makeBucketSpace(), { bckId1, bckId2, bckId3 }));
}


TEST_F("require that get is sent to all handlers", SimpleFixture) {
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    GetResult result = f.engine.get(bucket1, document::AllFields(), docId1, context);

    EXPECT_EQUAL(docId1, f.hset.handler1.lastDocId);
    EXPECT_EQUAL(docId1, f.hset.handler2.lastDocId);
}

TEST_F("require that get freezes the bucket", SimpleFixture) {
    EXPECT_FALSE(f.hset.handler1.wasFrozen(bucket1));
    EXPECT_FALSE(f.hset.handler2.wasFrozen(bucket1));
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    f.engine.get(bucket1, document::AllFields(), docId1, context);
    EXPECT_TRUE(f.hset.handler1.wasFrozen(bucket1));
    EXPECT_TRUE(f.hset.handler2.wasFrozen(bucket1));
    EXPECT_FALSE(f.hset.handler1.isFrozen(bucket1));
    EXPECT_FALSE(f.hset.handler2.isFrozen(bucket1));
}

TEST_F("require that get returns the first document found", SimpleFixture) {
    f.hset.handler1.setDocument(*doc1, tstamp1);
    f.hset.handler2.setDocument(*doc2, tstamp2);
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    GetResult result = f.engine.get(bucket1, document::AllFields(), docId1, context);

    EXPECT_EQUAL(docId1, f.hset.handler1.lastDocId);
    EXPECT_EQUAL(DocumentId(), f.hset.handler2.lastDocId);

    EXPECT_EQUAL(tstamp1, result.getTimestamp());
    ASSERT_TRUE(result.hasDocument());
    EXPECT_EQUAL(*doc1, result.getDocument());
}

TEST_F("require that createIterator does", SimpleFixture) {
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_FALSE(result.hasError());
    EXPECT_TRUE(result.getIteratorId());

    uint64_t max_size = 1024;
    IterateResult it_result = f.engine.iterate(result.getIteratorId(), max_size, context);
    EXPECT_FALSE(it_result.hasError());
}

TEST_F("require that iterator ids are unique", SimpleFixture) {
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    CreateIteratorResult result2 =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_FALSE(result.hasError());
    EXPECT_FALSE(result2.hasError());
    EXPECT_NOT_EQUAL(result.getIteratorId(), result2.getIteratorId());
}

TEST_F("require that iterate requires valid iterator", SimpleFixture) {
    uint64_t max_size = 1024;
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    IterateResult it_result = f.engine.iterate(IteratorId(1), max_size, context);
    EXPECT_TRUE(it_result.hasError());
    EXPECT_EQUAL(Result::PERMANENT_ERROR, it_result.getErrorCode());
    EXPECT_EQUAL("Unknown iterator with id 1", it_result.getErrorMessage());

    CreateIteratorResult result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(result.getIteratorId());

    it_result = f.engine.iterate(result.getIteratorId(), max_size, context);
    EXPECT_FALSE(it_result.hasError());
}

TEST_F("require that iterate returns documents", SimpleFixture) {
    f.hset.handler1.setDocument(*doc1, tstamp1);
    f.hset.handler2.setDocument(*doc2, tstamp2);

    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    uint64_t max_size = 1024;
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(result.getIteratorId());

    IterateResult it_result = f.engine.iterate(result.getIteratorId(), max_size, context);
    EXPECT_FALSE(it_result.hasError());
    EXPECT_EQUAL(2u, it_result.getEntries().size());
}

TEST_F("require that destroyIterator prevents iteration", SimpleFixture) {
    f.hset.handler1.setDocument(*doc1, tstamp1);

    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult create_result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(create_result.getIteratorId());

    Result result = f.engine.destroyIterator(create_result.getIteratorId(), context);
    EXPECT_FALSE(result.hasError());

    uint64_t max_size = 1024;
    IterateResult it_result = f.engine.iterate(create_result.getIteratorId(), max_size, context);
    EXPECT_TRUE(it_result.hasError());
    EXPECT_EQUAL(Result::PERMANENT_ERROR, it_result.getErrorCode());
    string msg_prefix = "Unknown iterator with id";
    EXPECT_EQUAL(msg_prefix, it_result.getErrorMessage().substr(0, msg_prefix.size()));
}

TEST_F("require that buckets are frozen during iterator life", SimpleFixture) {
    EXPECT_FALSE(f.hset.handler1.isFrozen(bucket1));
    EXPECT_FALSE(f.hset.handler2.isFrozen(bucket1));
    storage::spi::LoadType loadType(0, "default");
    Context context(loadType, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult create_result =
        f.engine.createIterator(bucket1, document::AllFields(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(f.hset.handler1.isFrozen(bucket1));
    EXPECT_TRUE(f.hset.handler2.isFrozen(bucket1));
    f.engine.destroyIterator(create_result.getIteratorId(), context);
    EXPECT_FALSE(f.hset.handler1.isFrozen(bucket1));
    EXPECT_FALSE(f.hset.handler2.isFrozen(bucket1));
}

TEST_F("require that multiple bucket spaces works", SimpleFixture(altBucketSpace)) {
    f.hset.prepareListBuckets();
    TEST_DO(assertBucketList(f.engine, makeBucketSpace(), { bckId1, bckId2 }));
    TEST_DO(assertBucketList(f.engine, altBucketSpace, { bckId2, bckId3 }));
    f.hset.prepareGetModifiedBuckets();
    TEST_DO(assertModifiedBuckets(f.engine, makeBucketSpace(), { bckId1, bckId2 }));
    TEST_DO(assertModifiedBuckets(f.engine, altBucketSpace, { bckId2, bckId3 }));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}

