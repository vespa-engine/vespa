// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-stor-distribution.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/persistence/spi/documentselection.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/server/ibucketfreezer.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <set>

using document::BucketId;
using document::BucketSpace;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::GlobalId;
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
createDocType(const std::string &name, int32_t id)
{
    return DocumentType(name, id);
}


Document::SP
createDoc(const DocumentType &docType, const DocumentId &docId)
{
    return Document::SP(Document::make_without_repo(docType, docId).release());
}


DocumentUpdate::SP
createUpd(const DocumentType& docType, const DocumentId &docId)
{
    static std::vector<std::unique_ptr<DocumentTypeRepo>> repoList;
    repoList.emplace_back(std::make_unique<DocumentTypeRepo>(docType));
    return std::make_shared<DocumentUpdate>(*repoList.back(), docType, docId);
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
    using Group = StorDistributionConfigBuilder::Group;
    using Nodes = Group::Nodes;
    storage::lib::ClusterState cstate;
    StorDistributionConfigBuilder dc;

    cstate.setNodeState(Node(NodeType::STORAGE, 0),
                        NodeState(NodeType::STORAGE, nodeState, "dummy desc", 1.0));
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
    DocumentTypeRepo repo;
    const Document *document;
    Timestamp timestamp;
    DocumentId &last_doc_id;

    MyDocumentRetriever(const Document *d, Timestamp ts, DocumentId &last_id)
        : repo(), document(d), timestamp(ts), last_doc_id(last_id) {}
    const DocumentTypeRepo &getDocumentTypeRepo() const override {
        return repo;
    }
    void getBucketMetaData(const storage::spi::Bucket &, search::DocumentMetaData::Vector &v) const override {
        if (document != nullptr) {
            v.push_back(getDocumentMetaData(document->getId()));
        }
    }
    DocumentMetaData getDocumentMetaData(const DocumentId &id) const override {
        last_doc_id = id;
        if (document != nullptr) {
            return DocumentMetaData(1, timestamp, BucketId(1), document->getId().getGlobalId());
        }
        return DocumentMetaData();
    }
    Document::UP getFullDocument(search::DocumentIdT) const override {
        if (document != nullptr) {
            return Document::UP(document->clone());
        }
        return Document::UP();
    }

    CachedSelect::SP parseSelect(const std::string &) const override {
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
    const Document              *document;
    std::multiset<uint64_t>      frozen;
    std::multiset<uint64_t>      was_frozen;
    DocTypeName                  _doc_type_name;

    MyHandler(const DocTypeName &type_name)
        : initialized(false),
          lastBucket(),
          lastTimestamp(),
          lastDocId(),
          existingTimestamp(),
          lastCalc(nullptr),
          lastBucketState(),
          bucketList(),
          bucketStateResult(),
          bucketInfo(),
          deleteBucketResult(),
          modBucketList(),
          _splitResult(),
          _joinResult(),
          _createBucketResult(),
          document(nullptr),
          frozen(),
          was_frozen(),
          _doc_type_name(type_name)
    {
    }

    void setExistingTimestamp(Timestamp ts) {
        existingTimestamp = ts;
    }
    void setDocument(const Document &doc, Timestamp ts) {
        document = &doc;
        setExistingTimestamp(ts);
    }
    void handle(const FeedToken & token, const Bucket &bucket, Timestamp timestamp, const DocumentId &docId) {
        (void) token;
        lastBucket = bucket;
        lastTimestamp = timestamp;
        lastDocId = docId;
    }

    void initialize() override { initialized = true; }

    void handlePut(FeedToken token, const Bucket& bucket,
                   Timestamp timestamp, DocumentSP doc) override {
        token->setResult(std::make_unique<Result>(), false);
        handle(token, bucket, timestamp, doc->getId());
    }

    void handleUpdate(FeedToken token, const Bucket& bucket,
                      Timestamp timestamp, DocumentUpdateSP upd) override {
        token->setResult(std::make_unique<UpdateResult>(existingTimestamp), existingTimestamp > 0);
        handle(token, bucket, timestamp, upd->getId());
    }

    void handleRemove(FeedToken token, const Bucket& bucket,
                      Timestamp timestamp, const DocumentId& id) override {
        bool wasFound = existingTimestamp > 0;
        token->setResult(std::make_unique<RemoveResult>(wasFound), wasFound);
        handle(token, bucket, timestamp, id);
    }

    void handleRemoveByGid(FeedToken token, const Bucket& bucket,
                           Timestamp timestamp,
                           std::string_view, const GlobalId&) override {
        bool wasFound = existingTimestamp > 0;
        token->setResult(std::make_unique<RemoveResult>(wasFound), wasFound);
        handle(token, bucket, timestamp, DocumentId());
    }

    void handleListBuckets(IBucketIdListResultHandler &resultHandler) override {
        resultHandler.handle(BucketIdListResult(BucketId::List(bucketList.begin(), bucketList.end())));
    }

    void handleSetClusterState(const ClusterState &calc, IGenericResultHandler &resultHandler) override {
        lastCalc = &calc;
        resultHandler.handle(Result());
    }

    void handleSetActiveState(const Bucket &bucket, storage::spi::BucketInfo::ActiveState newState,
                              std::shared_ptr<IGenericResultHandler> resultHandler) override {
        lastBucket = bucket;
        lastBucketState = newState;
        resultHandler->handle(bucketStateResult);
    }

    void handleGetBucketInfo(const Bucket &, IBucketInfoResultHandler &resultHandler) override {
        resultHandler.handle(BucketInfoResult(bucketInfo));
    }

    void handleCreateBucket(FeedToken token, const storage::spi::Bucket &) override {
        token->setResult(std::make_unique<Result>(_createBucketResult), true);
    }

    void handleDeleteBucket(FeedToken token, const storage::spi::Bucket &) override {
        token->setResult(std::make_unique<Result>(deleteBucketResult), true);
    }

    void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) override {
        resultHandler.handle(BucketIdListResult(std::move(modBucketList)));
    }

    void handleSplit(FeedToken token, const storage::spi::Bucket &, const storage::spi::Bucket &,
                     const storage::spi::Bucket &) override
    {
        token->setResult(std::make_unique<Result>(_splitResult), true);
    }

    void handleJoin(FeedToken token, const storage::spi::Bucket &, const storage::spi::Bucket &,
               const storage::spi::Bucket &) override
    {
        token->setResult(std::make_unique<Result>(_joinResult), true);
    }

    RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency) override {
        auto ret = std::make_shared<std::vector<IDocumentRetriever::SP>>();
        ret->push_back(std::make_shared<MyDocumentRetriever>(nullptr, Timestamp(), lastDocId));
        ret->push_back(std::make_shared<MyDocumentRetriever>(document, existingTimestamp, lastDocId));
        return ret;
    }

    void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) override {
        resultHandler.handle(BucketIdListResult());
    }

    void handlePopulateActiveBuckets(BucketId::List buckets, IGenericResultHandler &resultHandler) override {
        (void) buckets;
        resultHandler.handle(Result());
    }

    const DocTypeName &doc_type_name() const noexcept override {
        return _doc_type_name;
    }

    void freezeBucket(BucketId bucket) override {
        frozen.insert(bucket.getId());
        was_frozen.insert(bucket.getId());
    }
    void thawBucket(BucketId bucket) override {
        auto it = frozen.find(bucket.getId());
        ASSERT_TRUE(it != frozen.end());
        frozen.erase(it);
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
    : phandler1(std::make_shared<MyHandler>(DocTypeName("type1"))),
      phandler2(std::make_shared<MyHandler>(DocTypeName("type2"))),
      handler1(dynamic_cast<MyHandler &>(*phandler1.get())),
      handler2(dynamic_cast<MyHandler &>(*phandler2.get()))
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
DocumentUpdate::SP upd1(createUpd(type1, docId1));
DocumentUpdate::SP upd2(createUpd(type2, docId2));
DocumentUpdate::SP upd3(createUpd(type3, docId3));
DocumentUpdate::SP bad_id_upd(createUpd(type1, docId2));
BucketId bckId1(1);
BucketId bckId2(2);
BucketId bckId3(3);
Bucket bucket0;
Bucket bucket1(makeSpiBucket(bckId1));
Bucket bucket2(makeSpiBucket(bckId2));
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
    std::string _message;
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
    test::DiskMemUsageNotifier _disk_mem_usage_notifier;
    PersistenceEngine engine;
    HandlerSet hset;
    explicit SimpleFixture(BucketSpace bucketSpace2)
        : _owner(),
          engine(_owner, _writeFilter, _disk_mem_usage_notifier, -1, false),
          hset()
    {
        engine.putHandler(engine.getWLock(), makeBucketSpace(), DocTypeName(doc1->getType()), hset.phandler1);
        engine.putHandler(engine.getWLock(), bucketSpace2, DocTypeName(doc2->getType()), hset.phandler2);
    }
    SimpleFixture()
        : SimpleFixture(makeBucketSpace())
    {
    }
    ~SimpleFixture();
};

SimpleFixture::~SimpleFixture() =  default;

void
assertHandler(const Bucket &expBucket, Timestamp expTimestamp,
              const DocumentId &expDocId, const MyHandler &handler, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(expBucket, handler.lastBucket);
    EXPECT_EQ(expTimestamp, handler.lastTimestamp);
    EXPECT_EQ(expDocId, handler.lastDocId);
}

void assertBucketList(const BucketIdListResult &result, const std::vector<BucketId> &expBuckets)
{
    const BucketIdListResult::List &bucketList = result.getList();
    EXPECT_EQ(expBuckets.size(), bucketList.size());
    for (const auto &expBucket : expBuckets) {
        EXPECT_TRUE(std::find(bucketList.begin(), bucketList.end(), expBucket) != bucketList.end());
    }
}

void assertBucketList(PersistenceProvider &spi, BucketSpace bucketSpace, const std::vector<BucketId> &expBuckets, const std::string& label)
{
    SCOPED_TRACE("assertBucketList " + label);
    BucketIdListResult result = spi.listBuckets(bucketSpace);
    assertBucketList(result, expBuckets);
}

void assertModifiedBuckets(PersistenceProvider &spi, BucketSpace bucketSpace, const std::vector<BucketId> &expBuckets, const std::string& label)
{
    SCOPED_TRACE("assertModifiedBuckets " + label);
    BucketIdListResult result = spi.getModifiedBuckets(bucketSpace);
    assertBucketList(result, expBuckets);
}

TEST(PersistenceEngineTest, require_that_getPartitionStates_prepares_all_handlers)
{
    SimpleFixture f;
    EXPECT_FALSE(f.hset.handler1.initialized);
    EXPECT_FALSE(f.hset.handler2.initialized);
    f.engine.initialize();
    EXPECT_TRUE(f.hset.handler1.initialized);
    EXPECT_TRUE(f.hset.handler2.initialized);
}


TEST(PersistenceEngineTest, require_that_puts_are_routed_to_handler)
{
    SimpleFixture f;
    f.engine.put(bucket1, tstamp1, doc1);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 before put");
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2, "handler2 before put");

    f.engine.put(bucket1, tstamp1, doc2);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 after put");
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2, "handler2 after put");

    EXPECT_EQ(Result(Result::ErrorType::PERMANENT_ERROR, "No handler for document type 'type3'"),
              f.engine.put(bucket1, tstamp1, doc3));
}


TEST(PersistenceEngineTest, require_that_put_is_rejected_if_resource_limit_is_reached)
{
    SimpleFixture f;
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    EXPECT_EQ(
            Result(Result::ErrorType::RESOURCE_EXHAUSTED,
                   "Put operation rejected for document 'id:type3:type3::1': 'Disk is full'"),
            f.engine.put(bucket1, tstamp1, doc3));
}


TEST(PersistenceEngineTest, require_that_updates_are_routed_to_handler)
{
    SimpleFixture f;
    f.hset.handler1.setExistingTimestamp(tstamp2);
    UpdateResult ur = f.engine.update(bucket1, tstamp1, upd1);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 before update");
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2, "handler2 before update");
    EXPECT_EQ(tstamp2, ur.getExistingTimestamp());

    f.hset.handler2.setExistingTimestamp(tstamp3);
    ur = f.engine.update(bucket1, tstamp1, upd2);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 after update");
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2, "handler2 after update");
    EXPECT_EQ(tstamp3, ur.getExistingTimestamp());

    EXPECT_EQ(Result(Result::ErrorType::PERMANENT_ERROR, "No handler for document type 'type3'"),
              f.engine.update(bucket1, tstamp1, upd3));
}

TEST(PersistenceEngineTest, require_that_updates_with_bad_ids_are_rejected)
{
    SimpleFixture f;
    EXPECT_EQ(UpdateResult(Result::ErrorType::PERMANENT_ERROR, "Update operation rejected due to bad id (id:type2:type2::1, type1)"),
              f.engine.update(bucket1, tstamp1, bad_id_upd));
}

TEST(PersistenceEngineTest, require_that_simple_cheap_update_is_not_rejected_if_resource_limit_is_reached)
{
    SimpleFixture f;
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    EXPECT_EQ(Result(Result::ErrorType::NONE, ""),
              f.engine.update(bucket1, tstamp1, upd1));
}

TEST(PersistenceEngineTest, require_that_update_is_rejected_if_resource_limit_is_reached)
{
    SimpleFixture f;
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    DocumentType type(createDocType("type_with_one_string", 1));
    document::Field field("string", 1, *document::DataType::STRING);
    type.addField(field);
    DocumentUpdate::SP upd = createUpd(type, docId1);
    upd->addUpdate(std::move(document::FieldUpdate(field).addUpdate(std::make_unique<document::AssignValueUpdate>(std::make_unique<document::StringFieldValue>("new value")))));

    EXPECT_EQ(
            Result(Result::ErrorType::RESOURCE_EXHAUSTED,
                   "Update operation rejected for document 'id:type1:type1::1': 'Disk is full'"),
            f.engine.update(bucket1, tstamp1, upd));
}

TEST(PersistenceEngineTest, require_that_removes_are_routed_to_handlers)
{
    SimpleFixture f;
    RemoveResult rr = f.engine.remove(bucket1, tstamp1, docId3);
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler1, "handler1 before remove");
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2, "handler2 before remove");
    EXPECT_FALSE(rr.wasFound());
    EXPECT_TRUE(rr.hasError());
    EXPECT_EQ(Result(Result::ErrorType::PERMANENT_ERROR, "No handler for document type 'type3'"), rr);

    f.hset.handler1.setExistingTimestamp(tstamp2);
    rr = f.engine.remove(bucket1, tstamp1, docId1);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 after remove");
    assertHandler(bucket0, tstamp0, docId0, f.hset.handler2, "handler2 after remove");
    EXPECT_TRUE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());

    f.hset.handler1.setExistingTimestamp(tstamp0);
    f.hset.handler2.setExistingTimestamp(tstamp3);
    rr = f.engine.remove(bucket1, tstamp1, docId2);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 after 2nd remove");
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2, "handler2 after 2nd remove");
    EXPECT_TRUE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());

    f.hset.handler2.setExistingTimestamp(tstamp0);
    rr = f.engine.remove(bucket1, tstamp1, docId2);
    assertHandler(bucket1, tstamp1, docId1, f.hset.handler1, "handler1 after 3rd remove");
    assertHandler(bucket1, tstamp1, docId2, f.hset.handler2, "handler2 after 3rd remove");
    EXPECT_FALSE(rr.wasFound());
    EXPECT_FALSE(rr.hasError());
}

TEST(PersistenceEngineTest, require_that_remove_is_NOT_rejected_if_resource_limit_is_reached)
{
    SimpleFixture f;
    f._writeFilter._acceptWriteOperation = false;
    f._writeFilter._message = "Disk is full";

    EXPECT_EQ(RemoveResult(false), f.engine.remove(bucket1, tstamp1, docId1));
}


TEST(PersistenceEngineTest, require_that_listBuckets_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.prepareListBuckets();
    assertBucketList(f.engine, makeBucketSpace(), { bckId1, bckId2, bckId3 }, "default");
}


TEST(PersistenceEngineTest, require_that_setClusterState_is_routed_to_handlers)
{
    SimpleFixture f;
    ClusterState state(createClusterState());

    f.engine.setClusterState(makeBucketSpace(), state);
    EXPECT_EQ(&state, f.hset.handler1.lastCalc);
    EXPECT_EQ(&state, f.hset.handler2.lastCalc);
}


TEST(PersistenceEngineTest, require_that_setActiveState_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.handler1.bucketStateResult = Result(Result::ErrorType::TRANSIENT_ERROR, "err1");
    f.hset.handler2.bucketStateResult = Result(Result::ErrorType::PERMANENT_ERROR, "err2");

    Result result = f.engine.setActiveState(bucket1, storage::spi::BucketInfo::NOT_ACTIVE);
    EXPECT_EQ(Result::ErrorType::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQ("err1, err2", result.getErrorMessage());
    EXPECT_EQ(storage::spi::BucketInfo::NOT_ACTIVE, f.hset.handler1.lastBucketState);
    EXPECT_EQ(storage::spi::BucketInfo::NOT_ACTIVE, f.hset.handler2.lastBucketState);

    f.engine.setActiveState(bucket1, storage::spi::BucketInfo::ACTIVE);
    EXPECT_EQ(storage::spi::BucketInfo::ACTIVE, f.hset.handler1.lastBucketState);
    EXPECT_EQ(storage::spi::BucketInfo::ACTIVE, f.hset.handler2.lastBucketState);
}


TEST(PersistenceEngineTest, require_that_getBucketInfo_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.handler1.bucketInfo = bucketInfo1;
    f.hset.handler2.bucketInfo = bucketInfo2;

    BucketInfoResult result = f.engine.getBucketInfo(bucket1);
    EXPECT_EQ(bucketInfo3, result.getBucketInfo());
}


TEST(PersistenceEngineTest, require_that_createBucket_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.handler1._createBucketResult = Result(Result::ErrorType::TRANSIENT_ERROR, "err1a");
    f.hset.handler2._createBucketResult = Result(Result::ErrorType::PERMANENT_ERROR, "err2a");

    Result result = f.engine.createBucket(bucket1);
    EXPECT_EQ(Result::ErrorType::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQ("err1a, err2a", result.getErrorMessage());
}


TEST(PersistenceEngineTest, require_that_deleteBucket_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.handler1.deleteBucketResult = Result(Result::ErrorType::TRANSIENT_ERROR, "err1");
    f.hset.handler2.deleteBucketResult = Result(Result::ErrorType::PERMANENT_ERROR, "err2");

    Result result = f.engine.deleteBucket(bucket1);
    EXPECT_EQ(Result::ErrorType::PERMANENT_ERROR, result.getErrorCode());
    EXPECT_EQ("err1, err2", result.getErrorMessage());
}


TEST(PersistenceEngineTest, require_that_getModifiedBuckets_is_routed_to_handlers_and_merged)
{
    SimpleFixture f;
    f.hset.prepareGetModifiedBuckets();
    assertModifiedBuckets(f.engine, makeBucketSpace(), { bckId1, bckId2, bckId3 }, "default");
}


TEST(PersistenceEngineTest, require_that_get_is_sent_to_all_handlers) {
    SimpleFixture f;
    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    GetResult result = f.engine.get(bucket1, document::AllFields(), docId1, context);

    EXPECT_EQ(docId1, f.hset.handler1.lastDocId);
    EXPECT_EQ(docId1, f.hset.handler2.lastDocId);
}

TEST(PersistenceEngineTest, require_that_get_returns_the_first_document_found) {
    SimpleFixture f;
    f.hset.handler1.setDocument(*doc1, tstamp1);
    f.hset.handler2.setDocument(*doc2, tstamp2);
    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    GetResult result = f.engine.get(bucket1, document::AllFields(), docId1, context);

    EXPECT_EQ(docId1, f.hset.handler1.lastDocId);
    EXPECT_EQ(DocumentId(), f.hset.handler2.lastDocId);

    EXPECT_EQ(tstamp1, result.getTimestamp());
    ASSERT_TRUE(result.hasDocument());
    EXPECT_EQ(*doc1, result.getDocument());
    EXPECT_FALSE(result.is_tombstone());
}

TEST(PersistenceEngineTest, require_that_createIterator_does) {
    SimpleFixture f;
    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_FALSE(result.hasError());
    EXPECT_TRUE(result.getIteratorId());

    uint64_t max_size = 1024;
    IterateResult it_result = f.engine.iterate(result.getIteratorId(), max_size);
    EXPECT_FALSE(it_result.hasError());
}

TEST(PersistenceEngineTest, require_that_iterator_ids_are_unique) {
    SimpleFixture f;
    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    CreateIteratorResult result2 =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_FALSE(result.hasError());
    EXPECT_FALSE(result2.hasError());
    EXPECT_NE(result.getIteratorId(), result2.getIteratorId());
}

TEST(PersistenceEngineTest, require_that_iterate_requires_valid_iterator) {
    SimpleFixture f;
    uint64_t max_size = 1024;
    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    IterateResult it_result = f.engine.iterate(IteratorId(1), max_size);
    EXPECT_TRUE(it_result.hasError());
    EXPECT_EQ(Result::ErrorType::PERMANENT_ERROR, it_result.getErrorCode());
    EXPECT_EQ("Unknown iterator with id 1", it_result.getErrorMessage());

    CreateIteratorResult result =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(result.getIteratorId());

    it_result = f.engine.iterate(result.getIteratorId(), max_size);
    EXPECT_FALSE(it_result.hasError());
}

TEST(PersistenceEngineTest, require_that_iterate_returns_documents) {
    SimpleFixture f;
    f.hset.handler1.setDocument(*doc1, tstamp1);
    f.hset.handler2.setDocument(*doc2, tstamp2);

    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    uint64_t max_size = 1024;
    CreateIteratorResult result =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(result.getIteratorId());

    IterateResult it_result = f.engine.iterate(result.getIteratorId(), max_size);
    EXPECT_FALSE(it_result.hasError());
    EXPECT_EQ(2u, it_result.getEntries().size());
}

TEST(PersistenceEngineTest, require_that_destroyIterator_prevents_iteration) {
    SimpleFixture f;
    f.hset.handler1.setDocument(*doc1, tstamp1);

    Context context(storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));
    CreateIteratorResult create_result =
        f.engine.createIterator(bucket1, std::make_shared<document::AllFields>(), selection,
                                storage::spi::NEWEST_DOCUMENT_ONLY, context);
    EXPECT_TRUE(create_result.getIteratorId());

    Result result = f.engine.destroyIterator(create_result.getIteratorId());
    EXPECT_FALSE(result.hasError());

    uint64_t max_size = 1024;
    IterateResult it_result = f.engine.iterate(create_result.getIteratorId(), max_size);
    EXPECT_TRUE(it_result.hasError());
    EXPECT_EQ(Result::ErrorType::PERMANENT_ERROR, it_result.getErrorCode());
    std::string msg_prefix = "Unknown iterator with id";
    EXPECT_EQ(msg_prefix, it_result.getErrorMessage().substr(0, msg_prefix.size()));
}

TEST(PersistenceEngineTest, require_that_multiple_bucket_spaces_works) {
    SimpleFixture f(altBucketSpace);
    f.hset.prepareListBuckets();
    assertBucketList(f.engine, makeBucketSpace(), { bckId1, bckId2 }, "default");
    assertBucketList(f.engine, altBucketSpace, { bckId2, bckId3 }, "alt");
    f.hset.prepareGetModifiedBuckets();
    assertModifiedBuckets(f.engine, makeBucketSpace(), { bckId1, bckId2 }, "default");
    assertModifiedBuckets(f.engine, altBucketSpace, { bckId2, bckId3 }, "alt");
}

GTEST_MAIN_RUN_ALL_TESTS()
