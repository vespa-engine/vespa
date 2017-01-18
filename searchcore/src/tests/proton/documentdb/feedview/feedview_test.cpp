// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("feedview_test");
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/common/bucketfactory.h>
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/metrics/feed_metrics.h>
#include <vespa/searchcore/proton/server/ifrozenbuckethandler.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/searchable_feed_view.h>
#include <vespa/searchcore/proton/server/isummaryadapter.h>
#include <vespa/searchcore/proton/server/matchview.h>
#include <vespa/searchcore/proton/documentmetastore/lidreusedelayer.h>
#include <vespa/searchcore/proton/test/document_meta_store_context_observer.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/searchcore/proton/test/dummy_summary_manager.h>
#include <vespa/searchcore/proton/test/mock_index_writer.h>
#include <vespa/searchcore/proton/test/mock_index_manager.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchcore/proton/test/threading_service_observer.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <mutex>


using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using documentapi::DocumentProtocol;
using documentapi::RemoveDocumentReply;
using fastos::TimeStamp;
using namespace proton;
using proton::matching::SessionManager;
using search::AttributeVector;
using search::CacheStats;
using search::DocumentMetaData;
using search::SearchableStats;
using namespace search::index;
using searchcorespi::IndexSearchable;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::PartitionId;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::eval::ValueType;

typedef SearchableFeedView::SerialNum SerialNum;
typedef search::DocumentIdT DocumentIdT;
typedef DocumentProtocol::MessageType MessageType;

struct MyLidVector : public std::vector<DocumentIdT>
{
    MyLidVector &add(DocumentIdT lid) { push_back(lid); return *this; }
};


const uint32_t subdb_id = 0;
const vespalib::string indexAdapterTypeName = "index";
const vespalib::string attributeAdapterTypeName = "attribute";

struct MyTracer
{
    vespalib::asciistream _os;
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;
    Mutex _mutex;

    MyTracer()
        : _os(),
          _mutex()
    {
    }

    void addComma() { if (!_os.empty()) { _os << ","; } }

    void traceAck(const ResultUP &result) {
        Guard guard(_mutex);
        addComma();
        _os << "ack(";
        if (result) {
            _os << result->toString();
        } else {
            _os << "null";
        }
        _os << ")";
    }

    void tracePut(const vespalib::string &adapterType,
                  SerialNum serialNum, uint32_t lid, bool immediateCommit) {
        Guard guard(_mutex);
        addComma();
        _os << "put(adapter=" << adapterType <<
            ",serialNum=" << serialNum << ",lid=" << lid << ",commit=" << immediateCommit << ")";
    }

    void traceRemove(const vespalib::string &adapterType,
                     SerialNum serialNum, uint32_t lid, bool immediateCommit) {
        Guard guard(_mutex);
        addComma();
        _os << "remove(adapter=" << adapterType <<
            ",serialNum=" << serialNum << ",lid=" << lid << ",commit=" << immediateCommit << ")";
    }

    void traceCommit(const vespalib::string &adapterType, SerialNum serialNum) {
        Guard guard(_mutex);
        addComma();
        _os << "commit(adapter=" << adapterType <<
            ",serialNum=" << serialNum << ")";
    }
};

struct ParamsContext
{
    DocTypeName                          _docTypeName;
    FeedMetrics                          _feedMetrics;
    PerDocTypeFeedMetrics                _metrics;
    SearchableFeedView::PersistentParams _params;

    ParamsContext(const vespalib::string &docType,
                  const vespalib::string &baseDir)
        : _docTypeName(docType),
          _feedMetrics(),
          _metrics(&_feedMetrics),
          _params(0,
                  0,
                  _docTypeName,
                  _metrics,
                  subdb_id,
                  SubDbType::READY)
    {
        (void) baseDir;
    }
    const SearchableFeedView::PersistentParams &getParams() const { return _params; }
};

struct MyIndexWriter : public test::MockIndexWriter
{
    MyLidVector _removes;
    int _heartBeatCount;
    uint32_t _commitCount;
    MyTracer &_tracer;
    MyIndexWriter(MyTracer &tracer)
        : test::MockIndexWriter(IIndexManager::SP(new test::MockIndexManager())),
          _removes(),
          _heartBeatCount(0),
          _commitCount(0),
          _tracer(tracer)
    {}
    virtual void put(SerialNum serialNum, const document::Document &doc,
                     const DocumentIdT lid) override {
        (void) doc;
        _tracer.tracePut(indexAdapterTypeName, serialNum, lid, false);
    }
    virtual void remove(SerialNum serialNum, const search::DocumentIdT lid) override {
        LOG(info, "MyIndexAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)",
            serialNum, lid);
        _removes.push_back(lid);
        _tracer.traceRemove(indexAdapterTypeName, serialNum, lid, false);
    }
    virtual void commit(SerialNum serialNum, OnWriteDoneType) override {
        ++_commitCount;
        _tracer.traceCommit(indexAdapterTypeName, serialNum);
    }
    virtual void heartBeat(SerialNum) override { ++_heartBeatCount; }
};

struct MyDocumentStore : public test::DummyDocumentStore
{
    typedef std::map<DocumentIdT, document::Document::SP> DocMap;
    DocMap           _docs;
    uint64_t         _lastSyncToken;
    uint32_t         _compactLidSpaceLidLimit;
    MyDocumentStore()
        : test::DummyDocumentStore("."),
          _docs(),
          _lastSyncToken(0),
          _compactLidSpaceLidLimit(0)
    {}
    virtual Document::UP read(DocumentIdT lid, const document::DocumentTypeRepo &) const {
        DocMap::const_iterator itr = _docs.find(lid);
        if (itr != _docs.end()) {
            Document::UP retval(itr->second->clone());
            return retval;
        }
        return Document::UP();
    }
    virtual void write(uint64_t syncToken, const document::Document& doc, DocumentIdT lid) {
        _lastSyncToken = syncToken;
        _docs[lid] = Document::SP(doc.clone());
    }
    virtual void remove(uint64_t syncToken, DocumentIdT lid) {
        _lastSyncToken = syncToken;
        _docs.erase(lid);
    }
    virtual uint64_t initFlush(uint64_t syncToken) {
        return syncToken;
    }
    virtual uint64_t lastSyncToken() const { return _lastSyncToken; }
    virtual void compactLidSpace(uint32_t wantedDocLidLimit) override {
        _compactLidSpaceLidLimit = wantedDocLidLimit;
    }
};

struct MySummaryManager : public test::DummySummaryManager
{
    MyDocumentStore _store;
    MySummaryManager() : _store() {}
    virtual search::IDocumentStore &getBackingStore() { return _store; }
};

struct MySummaryAdapter : public test::MockSummaryAdapter
{
    ISummaryManager::SP _sumMgr;
    MyDocumentStore    &_store;
    MyLidVector         _removes;

    MySummaryAdapter()
        : _sumMgr(new MySummaryManager()),
          _store(static_cast<MyDocumentStore &>(_sumMgr->getBackingStore())),
          _removes()
    {
    }
    virtual void put(SerialNum serialNum, const document::Document &doc, const DocumentIdT lid) override {
        (void) serialNum;
        _store.write(serialNum, doc, lid);
    }
    virtual void remove(SerialNum serialNum, const DocumentIdT lid) override {
        LOG(info,
            "MySummaryAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)",
            serialNum, lid);
        _store.remove(serialNum, lid);
        _removes.push_back(lid);
    }
    virtual const search::IDocumentStore &getDocumentStore() const override {
        return _store;
    }
    virtual std::unique_ptr<document::Document> get(const search::DocumentIdT lid,
                                                    const document::DocumentTypeRepo &repo) override {
        return _store.read(lid, repo);
    }
    virtual void compactLidSpace(uint32_t wantedDocIdLimit) override {
        _store.compactLidSpace(wantedDocIdLimit);
    }
};

struct MyAttributeWriter : public IAttributeWriter
{
    MyLidVector _removes;
    SerialNum _putSerial;
    DocumentId _putDocId;
    DocumentIdT _putLid;
    SerialNum _updateSerial;
    DocumentId _updateDocId;
    DocumentIdT _updateLid;
    SerialNum _removeSerial;
    DocumentIdT _removeLid;
    int _heartBeatCount;
    uint32_t _commitCount;
    uint32_t _wantedLidLimit;
    using AttrMap = std::map<vespalib::string,
                             std::shared_ptr<AttributeVector>>;
    AttrMap _attrMap;
    std::set<vespalib::string> _attrs;
    proton::IAttributeManager::SP _mgr;
    MyTracer &_tracer;
    MyAttributeWriter(MyTracer &tracer)
        : _removes(),
          _putSerial(0),
          _putDocId(),
          _putLid(0),
          _updateSerial(0),
          _updateDocId(),
          _updateLid(0),
          _removeSerial(0),
          _removeLid(0),
          _heartBeatCount(0),
          _commitCount(0),
          _wantedLidLimit(0),
          _attrMap(),
          _attrs(),
          _mgr(),
          _tracer(tracer)
    {
        search::attribute::Config cfg(search::attribute::BasicType::INT32);
        _attrMap["a1"] = search::AttributeFactory::createAttribute("test", cfg);
        search::attribute::Config
            cfg2(search::attribute::BasicType::PREDICATE);
        _attrMap["a2"] = search::AttributeFactory::createAttribute("test2",
                                                                   cfg2);
        search::attribute::Config cfg3(search::attribute::BasicType::TENSOR);
        cfg3.setTensorType(ValueType::from_spec("tensor(x[10])"));
        _attrMap["a3"] = search::AttributeFactory::createAttribute("test3",
                                                                   cfg3);
    }
    virtual std::vector<AttributeVector *>
    getWritableAttributes() const override {
        return std::vector<AttributeVector *>();
    }
    virtual AttributeVector *getWritableAttribute(const vespalib::string &attrName) const override {
        if (_attrs.count(attrName) == 0) {
            return nullptr;
        }
        AttrMap::const_iterator itr = _attrMap.find(attrName);
        return ((itr == _attrMap.end()) ? nullptr : itr->second.get());
    }
    virtual void put(SerialNum serialNum, const document::Document &doc, DocumentIdT lid,
                     bool immediateCommit, OnWriteDoneType) override {
        _putSerial = serialNum;
        _putDocId = doc.getId();
        _putLid = lid;
        _tracer.tracePut(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        if (immediateCommit) {
            ++_commitCount;
        }
    }
    virtual void remove(SerialNum serialNum, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType) override {
        _removeSerial = serialNum;
        _removeLid = lid;
        _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        if (immediateCommit) {
            ++_commitCount;
        }
    }
    virtual void remove(const LidVector & lidsToRemove, SerialNum serialNum,
                        bool immediateCommit, OnWriteDoneType) override {
        for (uint32_t lid : lidsToRemove) {
            LOG(info, "MyAttributeAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
           _removes.push_back(lid);
           _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        }
    }
    virtual void update(SerialNum serialNum, const document::DocumentUpdate &upd,
                        DocumentIdT lid, bool, OnWriteDoneType) override {
        _updateSerial = serialNum;
        _updateDocId = upd.getId();
        _updateLid = lid;
    }
    virtual void heartBeat(SerialNum) override { ++_heartBeatCount; }
    virtual void compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum) override {
        (void) serialNum;
        _wantedLidLimit = wantedLidLimit;
    }
    virtual const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void commit(SerialNum serialNum, OnWriteDoneType) override {
        (void) serialNum; ++_commitCount;
        _tracer.traceCommit(attributeAdapterTypeName, serialNum);
    }

    virtual void onReplayDone(uint32_t docIdLimit) override
    {
        (void) docIdLimit;
    }
};

struct MyTransport : public FeedToken::ITransport
{
    ResultUP lastResult;
    vespalib::Gate _gate;
    MyTracer &_tracer;
    MyTransport(MyTracer &tracer) : lastResult(), _gate(), _tracer(tracer) {}
    virtual void send(mbus::Reply::UP reply,
                      ResultUP result,
                      bool documentWasFound,
                      double latency_ms) {
        (void) reply; (void) documentWasFound, (void) latency_ms;
        lastResult = std::move(result);
        _tracer.traceAck(lastResult);
        _gate.countDown();
    }
    void await() { _gate.await(); }
};


struct MyResultHandler : public IGenericResultHandler
{
    vespalib::Gate _gate;
    MyResultHandler() : _gate() {}
    virtual void handle(const storage::spi::Result &) {
        _gate.countDown();
    }
    void await() { _gate.await(); }
};

struct SchemaContext
{
    Schema::SP                _schema;
    std::unique_ptr<DocBuilder> _builder;
    SchemaContext() :
        _schema(new Schema()),
        _builder()
    {
        _schema->addIndexField(Schema::IndexField("i1", schema::STRING, schema::SINGLE));
        _schema->addAttributeField(Schema::AttributeField("a1", schema::STRING, schema::SINGLE));
        _schema->addAttributeField(Schema::AttributeField("a2", schema::BOOLEANTREE, schema::SINGLE));
        _schema->addAttributeField(Schema::AttributeField("a3", schema::TENSOR, schema::SINGLE));
        _schema->addSummaryField(Schema::SummaryField("s1", schema::STRING, schema::SINGLE));
        _builder.reset(new DocBuilder(*_schema));
    }
    const document::DocumentTypeRepo::SP &getRepo() const { return _builder->getDocumentTypeRepo(); }
};

struct DocumentContext
{
    Document::SP       doc;
    DocumentUpdate::SP upd;
    BucketId           bid;
    Timestamp          ts;
    typedef std::vector<DocumentContext> List;
    DocumentContext(const vespalib::string &docId, uint64_t timestamp, DocBuilder &builder) :
        doc(builder.startDocument(docId)
                   .startSummaryField("s1").addStr(docId).endField()
                   .endDocument().release()),
        upd(new DocumentUpdate(builder.getDocumentType(), doc->getId())),
        bid(BucketFactory::getNumBucketBits(),
            doc->getId().getGlobalId().convertToBucketId().getRawId()),
        ts(timestamp)
    {
    }
    void addFieldUpdate(DocBuilder &builder,
                        const vespalib::string &fieldName) {
        const document::Field &field =
            builder.getDocumentType().getField(fieldName);
        upd->addUpdate(document::FieldUpdate(field));
    }
};

namespace {

mbus::Reply::UP
createReply(MessageType mtype)
{
    if (mtype == DocumentProtocol::REPLY_UPDATEDOCUMENT) {
        return mbus::Reply::UP(new documentapi::UpdateDocumentReply);
    } else if (mtype == DocumentProtocol::REPLY_REMOVEDOCUMENT) {
        return mbus::Reply::UP(new documentapi::RemoveDocumentReply);
    } else {
        return mbus::Reply::UP(new documentapi::DocumentReply(mtype));
    }
}

}  // namespace

struct FeedTokenContext
{
    MyTransport mt;
    FeedToken   ft;
    typedef std::shared_ptr<FeedTokenContext> SP;
    typedef std::vector<SP> List;
    FeedTokenContext(MyTracer &tracer, MessageType mtype) :
        mt(tracer),
        ft(mt, createReply(mtype))
    {
    }
};

struct FixtureBase
{
    MyTracer             _tracer;
    IIndexWriter::SP     iw;
    ISummaryAdapter::SP  sa;
    IAttributeWriter::SP aw;
    MyIndexWriter        &miw;
    MySummaryAdapter     &msa;
    MyAttributeWriter    &maw;
    SchemaContext        sc;
    DocIdLimit           _docIdLimit;
    DocumentMetaStoreContext::SP _dmscReal;
    test::DocumentMetaStoreContextObserver::SP _dmsc;
    ParamsContext         pc;
    ExecutorThreadingService _writeServiceReal;
    test::ThreadingServiceObserver _writeService;
    documentmetastore::LidReuseDelayer _lidReuseDelayer;
    CommitTimeTracker     _commitTimeTracker;
    SerialNum             serial;
    FixtureBase(TimeStamp visibilityDelay) :
        _tracer(),
        iw(new MyIndexWriter(_tracer)),
        sa(new MySummaryAdapter),
        aw(new MyAttributeWriter(_tracer)),
        miw(static_cast<MyIndexWriter&>(*iw)),
        msa(static_cast<MySummaryAdapter&>(*sa)),
        maw(static_cast<MyAttributeWriter&>(*aw)),
        sc(),
        _docIdLimit(0u),
        _dmscReal(new DocumentMetaStoreContext(std::make_shared<BucketDBOwner>())),
        _dmsc(new test::DocumentMetaStoreContextObserver(*_dmscReal)),
        pc(sc._builder->getDocumentType().getName(), "fileconfig_test"),
        _writeServiceReal(),
        _writeService(_writeServiceReal),
        _lidReuseDelayer(_writeService, _dmsc->get()),
        _commitTimeTracker(visibilityDelay),
        serial(0)
    {
        _dmsc->constructFreeList();
        _lidReuseDelayer.setImmediateCommit(visibilityDelay == 0);
    }

    virtual ~FixtureBase() {
        _writeServiceReal.sync();
    }

    void syncMaster() {
        _writeService.master().sync();
    }

    void syncIndex() {
        _writeService.sync();
    }

    void sync() {
        _writeServiceReal.sync();
    }

    const test::DocumentMetaStoreObserver &metaStoreObserver() {
        return _dmsc->getObserver();
    }

    const test::ThreadingServiceObserver &writeServiceObserver() {
        return _writeService;
    }

    template <typename FunctionType>
    void runInMaster(FunctionType func) {
        test::runInMaster(_writeService, func);
    }

    virtual IFeedView &getFeedView() = 0;

    const IDocumentMetaStore &getMetaStore() const {
        return _dmsc->get();
    }
    const MyDocumentStore &getDocumentStore() const {
        return msa._store;
    }

    BucketDBOwner::Guard getBucketDB() const {
        return getMetaStore().getBucketDB().takeGuard();
    }

    DocumentMetaData getMetaData(const DocumentContext &doc_) const {
        return getMetaStore().getMetaData(doc_.doc->getId().getGlobalId());
    }

    DocBuilder &getBuilder() { return *sc._builder; }

    DocumentContext doc(const vespalib::string &docId, uint64_t timestamp) {
        return DocumentContext(docId, timestamp, getBuilder());
    }

    DocumentContext doc1(uint64_t timestamp = 10) {
        return doc("doc:test:1", timestamp);
    }

    void performPut(FeedToken *token, PutOperation &op) {
        getFeedView().preparePut(op);
        op.setSerialNum(++serial);
        getFeedView().handlePut(token, op);
    }

    void putAndWait(const DocumentContext::List &docs) {
        for (size_t i = 0; i < docs.size(); ++i) {
            putAndWait(docs[i]);
        }
    }

    void putAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer, DocumentProtocol::REPLY_PUTDOCUMENT);
        PutOperation op(docCtx.bid, docCtx.ts, docCtx.doc);
        runInMaster([&] () { performPut(&token.ft, op); });
    }

    void performUpdate(FeedToken *token, UpdateOperation &op) {
        getFeedView().prepareUpdate(op);
        op.setSerialNum(++serial);
        getFeedView().handleUpdate(token, op);
    }

    void updateAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer, DocumentProtocol::REPLY_UPDATEDOCUMENT);
        UpdateOperation op(docCtx.bid, docCtx.ts, docCtx.upd);
        runInMaster([&] () { performUpdate(&token.ft, op); });
    }

    void performRemove(FeedToken *token, RemoveOperation &op) {
        getFeedView().prepareRemove(op);
        if (op.getValidNewOrPrevDbdId()) {
            op.setSerialNum(++serial);
            getFeedView().handleRemove(token, op);
        } else {
            if (token != NULL) {
                token->ack(op.getType(), pc._metrics);
            }
        }
    }

    void removeAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer, DocumentProtocol::REPLY_REMOVEDOCUMENT);
        RemoveOperation op(docCtx.bid, docCtx.ts, docCtx.doc->getId());
        runInMaster([&] () { performRemove(&token.ft, op); });
    }

    void removeAndWait(const DocumentContext::List &docs) {
        for (size_t i = 0; i < docs.size(); ++i) {
            removeAndWait(docs[i]);
        }
    }
    void performDeleteBucket(DeleteBucketOperation &op) {
        getFeedView().prepareDeleteBucket(op);
        op.setSerialNum(++serial);
        getFeedView().handleDeleteBucket(op);
    }

    void performForceCommit() { getFeedView().forceCommit(serial); }
    void forceCommitAndWait() { runInMaster([&]() { performForceCommit(); }); }

    bool assertTrace(const vespalib::string &exp) {
        return EXPECT_EQUAL(exp, _tracer._os.str());
    }

    DocumentContext::List
    makeDummyDocs(uint32_t first, uint32_t count, uint64_t tsfirst) {
        DocumentContext::List docs;
        for (uint32_t i = 0; i < count; ++i) {
            uint32_t id = first + i;
            uint64_t ts = tsfirst + i;
            vespalib::asciistream os;
            os << "doc:test:" << id;
            docs.push_back(doc(os.str(), ts));
        }
        return docs;
    }

    void performCompactLidSpace(uint32_t wantedLidLimit) {
        auto &fv = getFeedView();
        CompactLidSpaceOperation op(0, wantedLidLimit);
        op.setSerialNum(++serial);
        fv.handleCompactLidSpace(op);
    }
    void compactLidSpaceAndWait(uint32_t wantedLidLimit) {
        runInMaster([&] () { performCompactLidSpace(wantedLidLimit); });
    }
};

struct SearchableFeedViewFixture : public FixtureBase
{
    SearchableFeedView fv;
    SearchableFeedViewFixture(TimeStamp visibilityDelay = 0) :
        FixtureBase(visibilityDelay),
        fv(StoreOnlyFeedView::Context(sa,
                sc._schema,
                _dmsc,
                sc.getRepo(),
                _writeService,
                _lidReuseDelayer,
                _commitTimeTracker),
           pc.getParams(),
           FastAccessFeedView::Context(aw, _docIdLimit),
           SearchableFeedView::Context(iw))
    {
        runInMaster([&]() { _lidReuseDelayer.setHasIndexedOrAttributeFields(true); });
    }
    virtual IFeedView &getFeedView() { return fv; }
};

struct FastAccessFeedViewFixture : public FixtureBase
{
    FastAccessFeedView fv;
    FastAccessFeedViewFixture(TimeStamp visibilityDelay = 0) :
        FixtureBase(visibilityDelay),
        fv(StoreOnlyFeedView::Context(sa,
                sc._schema,
                _dmsc,
                sc.getRepo(),
                _writeService,
                _lidReuseDelayer,
                _commitTimeTracker),
           pc.getParams(),
           FastAccessFeedView::Context(aw, _docIdLimit))
    {
    }
    virtual IFeedView &getFeedView() { return fv; }
};

void
assertBucketInfo(const BucketId &ebid,
                 const Timestamp &ets,
                 uint32_t lid,
                 const IDocumentMetaStore &metaStore)
{
    document::GlobalId gid;
    EXPECT_TRUE(metaStore.getGid(lid, gid));
    search::DocumentMetaData meta = metaStore.getMetaData(gid);
    EXPECT_TRUE(meta.valid());
    BucketId abid;
    EXPECT_EQUAL(ebid, meta.bucketId);
    Timestamp ats;
    EXPECT_EQUAL(ets, meta.timestamp);
}

void
assertLidVector(const MyLidVector &exp, const MyLidVector &act)
{
    EXPECT_EQUAL(exp.size(), act.size());
    for (size_t i = 0; i < exp.size(); ++i) {
        EXPECT_TRUE(std::find(act.begin(), act.end(), exp[i]) != act.end());
    }
}

void
assertAttributeUpdate(SerialNum serialNum,
                      const document::DocumentId &docId,
                      DocumentIdT lid,
                      MyAttributeWriter adapter)
{
    EXPECT_EQUAL(serialNum, adapter._updateSerial);
    EXPECT_EQUAL(docId, adapter._updateDocId);
    EXPECT_EQUAL(lid, adapter._updateLid);
}


TEST_F("require that put() updates document meta store with bucket info",
       SearchableFeedViewFixture)
{
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);

    assertBucketInfo(dc.bid, dc.ts, 1, f.getMetaStore());
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc.bid);
    EXPECT_EQUAL(1u, bucketInfo.getDocumentCount());
    EXPECT_NOT_EQUAL(bucketInfo.getChecksum(), BucketChecksum(0));
}

TEST_F("require that put() calls attribute adapter", SearchableFeedViewFixture)
{
    DocumentContext dc = f.doc1();
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.putAndWait(dc);

    EXPECT_EQUAL(1u, f.maw._putSerial);
    EXPECT_EQUAL(DocumentId("doc:test:1"), f.maw._putDocId);
    EXPECT_EQUAL(1u, f.maw._putLid);
    EXPECT_EQUAL(2u, f._docIdLimit.get());
}

TEST_F("require that update() updates document meta store with bucket info",
       SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    BucketChecksum bcs = f.getBucketDB()->get(dc1.bid).getChecksum();
    f.updateAndWait(dc2);

    assertBucketInfo(dc1.bid, Timestamp(20), 1, f.getMetaStore());
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc1.bid);
    EXPECT_EQUAL(1u, bucketInfo.getDocumentCount());
    EXPECT_NOT_EQUAL(bucketInfo.getChecksum(), bcs);
    EXPECT_NOT_EQUAL(bucketInfo.getChecksum(), BucketChecksum(0));
}

TEST_F("require that update() calls attribute adapter", SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    f.updateAndWait(dc2);

    assertAttributeUpdate(2u, DocumentId("doc:test:1"), 1u, f.maw);
}

TEST_F("require that remove() updates document meta store with bucket info",
       SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc("userdoc:test:1:1", 10);
    DocumentContext dc2 = f.doc("userdoc:test:1:2", 11);
    f.putAndWait(dc1);
    BucketChecksum bcs1 = f.getBucketDB()->get(dc1.bid).getChecksum();
    f.putAndWait(dc2);
    BucketChecksum bcs2 = f.getBucketDB()->get(dc2.bid).getChecksum();
    f.removeAndWait(DocumentContext("userdoc:test:1:2", 20, f.getBuilder()));

    assertBucketInfo(dc1.bid, Timestamp(10), 1, f.getMetaStore());
    EXPECT_FALSE(f.getMetaStore().validLid(2)); // don't remember remove
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc1.bid);
    EXPECT_EQUAL(1u, bucketInfo.getDocumentCount());
    EXPECT_NOT_EQUAL(bucketInfo.getChecksum(), bcs2);
    EXPECT_EQUAL(bucketInfo.getChecksum(), bcs1);
}

TEST_F("require that remove() calls attribute adapter", SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    f.removeAndWait(dc2);

    EXPECT_EQUAL(2u, f.maw._removeSerial);
    EXPECT_EQUAL(1u, f.maw._removeLid);
}

bool
assertThreadObserver(uint32_t masterExecuteCnt,
                     uint32_t indexExecuteCnt,
                     const test::ThreadingServiceObserver &observer)
{
    if (!EXPECT_EQUAL(masterExecuteCnt, observer.masterObserver().getExecuteCnt())) return false;
    if (!EXPECT_EQUAL(indexExecuteCnt, observer.indexObserver().getExecuteCnt())) return false;
    return true;
}

TEST_F("require that remove() calls removeComplete() via delayed thread service",
        SearchableFeedViewFixture)
{
    EXPECT_TRUE(assertThreadObserver(1, 0, f.writeServiceObserver()));
    f.putAndWait(f.doc1(10));
    // put index fields handled in index thread
    EXPECT_TRUE(assertThreadObserver(2, 1, f.writeServiceObserver()));
    f.removeAndWait(f.doc1(20));
    // remove index fields handled in index thread
    // delayed remove complete handled in same index thread, then master thread
    EXPECT_TRUE(assertThreadObserver(4, 2, f.writeServiceObserver()));
    EXPECT_EQUAL(1u, f.metaStoreObserver()._removeCompleteCnt);
    EXPECT_EQUAL(1u, f.metaStoreObserver()._removeCompleteLid);
}

TEST_F("require that handleDeleteBucket() removes documents", SearchableFeedViewFixture)
{
    DocumentContext::List docs;
    docs.push_back(f.doc("userdoc:test:1:1", 10));
    docs.push_back(f.doc("userdoc:test:1:2", 11));
    docs.push_back(f.doc("userdoc:test:1:3", 12));
    docs.push_back(f.doc("userdoc:test:2:1", 13));
    docs.push_back(f.doc("userdoc:test:2:2", 14));

    f.putAndWait(docs);

    DocumentIdT lid;
    EXPECT_TRUE(f.getMetaStore().getLid(docs[0].doc->getId().getGlobalId(), lid));
    EXPECT_EQUAL(1u, lid);
    EXPECT_TRUE(f.getMetaStore().getLid(docs[1].doc->getId().getGlobalId(), lid));
    EXPECT_EQUAL(2u, lid);
    EXPECT_TRUE(f.getMetaStore().getLid(docs[2].doc->getId().getGlobalId(), lid));
    EXPECT_EQUAL(3u, lid);

    // delete bucket for user 1
    DeleteBucketOperation op(docs[0].bid);
    f.runInMaster([&] () { f.performDeleteBucket(op); });

    EXPECT_EQUAL(0u, f.getBucketDB()->get(docs[0].bid).getDocumentCount());
    EXPECT_EQUAL(2u, f.getBucketDB()->get(docs[3].bid).getDocumentCount());
    EXPECT_FALSE(f.getMetaStore().getLid(docs[0].doc->getId().getGlobalId(), lid));
    EXPECT_FALSE(f.getMetaStore().getLid(docs[1].doc->getId().getGlobalId(), lid));
    EXPECT_FALSE(f.getMetaStore().getLid(docs[2].doc->getId().getGlobalId(), lid));
    MyLidVector exp = MyLidVector().add(1).add(2).add(3);
    assertLidVector(exp, f.miw._removes);
    assertLidVector(exp, f.msa._removes);
    assertLidVector(exp, f.maw._removes);
}

void
assertPostConditionAfterRemoves(const DocumentContext::List &docs,
                                SearchableFeedViewFixture &f)
{
    EXPECT_EQUAL(3u, f.getMetaStore().getNumUsedLids());
    EXPECT_FALSE(f.getMetaData(docs[0]).valid());
    EXPECT_TRUE(f.getMetaData(docs[1]).valid());
    EXPECT_FALSE(f.getMetaData(docs[1]).removed);
    EXPECT_TRUE(f.getMetaData(docs[2]).valid());
    EXPECT_FALSE(f.getMetaData(docs[2]).removed);
    EXPECT_FALSE(f.getMetaData(docs[3]).valid());
    EXPECT_TRUE(f.getMetaData(docs[4]).valid());
    EXPECT_FALSE(f.getMetaData(docs[4]).removed);

    assertLidVector(MyLidVector().add(1).add(4), f.miw._removes);
    assertLidVector(MyLidVector().add(1).add(4), f.msa._removes);
    MyDocumentStore::DocMap &sdocs = f.msa._store._docs;
    EXPECT_EQUAL(3u, sdocs.size());
    EXPECT_TRUE(sdocs.find(1) == sdocs.end());
    EXPECT_TRUE(sdocs.find(4) == sdocs.end());
}

TEST_F("require that removes are not remembered", SearchableFeedViewFixture)
{
    DocumentContext::List docs;
    docs.push_back(f.doc("userdoc:test:1:1", 10));
    docs.push_back(f.doc("userdoc:test:1:2", 11));
    docs.push_back(f.doc("userdoc:test:1:3", 12));
    docs.push_back(f.doc("userdoc:test:2:1", 13));
    docs.push_back(f.doc("userdoc:test:2:2", 14));

    f.putAndWait(docs);
    f.removeAndWait(docs[0]);
    f.removeAndWait(docs[3]);
    assertPostConditionAfterRemoves(docs, f);

    // try to remove again : should have little effect
    f.removeAndWait(docs[0]);
    f.removeAndWait(docs[3]);
    assertPostConditionAfterRemoves(docs, f);

    // re-add docs
    f.putAndWait(docs[3]);
    f.putAndWait(docs[0]);
    EXPECT_EQUAL(5u, f.getMetaStore().getNumUsedLids());
    EXPECT_TRUE(f.getMetaData(docs[0]).valid());
    EXPECT_TRUE(f.getMetaData(docs[1]).valid());
    EXPECT_TRUE(f.getMetaData(docs[2]).valid());
    EXPECT_TRUE(f.getMetaData(docs[3]).valid());
    EXPECT_TRUE(f.getMetaData(docs[4]).valid());
    EXPECT_FALSE(f.getMetaData(docs[0]).removed);
    EXPECT_FALSE(f.getMetaData(docs[1]).removed);
    EXPECT_FALSE(f.getMetaData(docs[2]).removed);
    EXPECT_FALSE(f.getMetaData(docs[3]).removed);
    EXPECT_FALSE(f.getMetaData(docs[4]).removed);
    EXPECT_EQUAL(5u, f.msa._store._docs.size());
    const Document::SP &doc1 = f.msa._store._docs[1];
    EXPECT_EQUAL(docs[3].doc->getId(), doc1->getId());
    EXPECT_EQUAL(docs[3].doc->getId().toString(),
                 doc1->getValue("s1")->toString());
    const Document::SP &doc4 = f.msa._store._docs[4];
    EXPECT_EQUAL(docs[0].doc->getId(), doc4->getId());
    EXPECT_EQUAL(docs[0].doc->getId().toString(),
                 doc4->getValue("s1")->toString());
    EXPECT_EQUAL(5u, f.msa._store._docs.size());

    f.removeAndWait(docs[0]);
    f.removeAndWait(docs[3]);
    EXPECT_EQUAL(3u, f.msa._store._docs.size());
}

TEST_F("require that heartbeat propagates to index- and attributeadapter",
       SearchableFeedViewFixture)
{
    f.runInMaster([&] () { f.fv.heartBeat(2); });
    EXPECT_EQUAL(1, f.miw._heartBeatCount);
    EXPECT_EQUAL(1, f.maw._heartBeatCount);
}

template <typename Fixture>
void putDocumentAndUpdate(Fixture &f, const vespalib::string &fieldName)
{
    DocumentContext dc1 = f.doc1();
    f.putAndWait(dc1);
    EXPECT_EQUAL(1u, f.msa._store._lastSyncToken);

    DocumentContext dc2("doc:test:1", 20, f.getBuilder());
    dc2.addFieldUpdate(f.getBuilder(), fieldName);
    f.updateAndWait(dc2);
}

template <typename Fixture>
void requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(Fixture &f)
{
    putDocumentAndUpdate(f, "a1");

    EXPECT_EQUAL(1u, f.msa._store._lastSyncToken); // document store not updated
    assertAttributeUpdate(2u, DocumentId("doc:test:1"), 1, f.maw);
}

TEST_F("require that update() to fast-access attribute only updates attribute and not document store",
       FastAccessFeedViewFixture)
{
    f.maw._attrs.insert("a1"); // mark a1 as fast-access attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f);
}

TEST_F("require that update() to attribute only updates attribute and not document store",
       SearchableFeedViewFixture)
{
    f.maw._attrs.insert("a1"); // mark a1 as attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f);
}

TEST_F("require that update to non fast-access attribute also updates document store",
        FastAccessFeedViewFixture)
{
    putDocumentAndUpdate(f, "a1");

    EXPECT_EQUAL(2u, f.msa._store._lastSyncToken); // document store updated
    assertAttributeUpdate(2u, DocumentId("doc:test:1"), 1, f.maw);
}

template <typename Fixture>
void requireThatUpdateUpdatesAttributeAndDocumentStore(Fixture &f,
                                                       const vespalib::string &
                                                       fieldName)
{
    putDocumentAndUpdate(f, fieldName);

    EXPECT_EQUAL(2u, f.msa._store._lastSyncToken); // document store updated
    assertAttributeUpdate(2u, DocumentId("doc:test:1"), 1, f.maw);
}

TEST_F("require that update() to fast-access predicate attribute updates attribute and document store",
       FastAccessFeedViewFixture)
{
    f.maw._attrs.insert("a2"); // mark a2 as fast-access attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a2");
}

TEST_F("require that update() to predicate attribute updates attribute and document store",
       SearchableFeedViewFixture)
{
    f.maw._attrs.insert("a2"); // mark a2 as attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a2");
}

TEST_F("require that update() to fast-access tensor attribute updates attribute and document store",
       FastAccessFeedViewFixture)
{
    f.maw._attrs.insert("a3"); // mark a3 as fast-access attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a3");
}

TEST_F("require that update() to tensor attribute updates attribute and document store",
       SearchableFeedViewFixture)
{
    f.maw._attrs.insert("a3"); // mark a3 as attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a3");
}

TEST_F("require that compactLidSpace() propagates to document meta store and document store and "
       "blocks lid space shrinkage until generation is no longer used",
       SearchableFeedViewFixture)
{
    EXPECT_TRUE(assertThreadObserver(1, 0, f.writeServiceObserver()));
    CompactLidSpaceOperation op(0, 99);
    op.setSerialNum(1);
    f.runInMaster([&] () { f.fv.handleCompactLidSpace(op); });
    // performIndexForceCommit in index thread, then completion callback
    // in master thread.
    EXPECT_TRUE(assertThreadObserver(3, 1, f.writeServiceObserver()));
    EXPECT_EQUAL(99u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(99u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(1u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
    EXPECT_EQUAL(99u, f._docIdLimit.get());
}

TEST_F("require that compactLidSpace() doesn't propagate to "
       "document meta store and document store and "
       "blocks lid space shrinkage until generation is no longer used",
       SearchableFeedViewFixture)
{
    EXPECT_TRUE(assertThreadObserver(1, 0, f.writeServiceObserver()));
    CompactLidSpaceOperation op(0, 99);
    op.setSerialNum(0);
    f.runInMaster([&] () { f.fv.handleCompactLidSpace(op); });
    // Delayed holdUnblockShrinkLidSpace() in index thread, then master thread
    EXPECT_TRUE(assertThreadObserver(2, 0, f.writeServiceObserver()));
    EXPECT_EQUAL(0u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(0u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(0u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
}

TEST_F("require that compactLidSpace() propagates to attributeadapter",
       FastAccessFeedViewFixture)
{
    f.runInMaster([&] () { f.fv.handleCompactLidSpace(CompactLidSpaceOperation(0, 99)); });
    EXPECT_EQUAL(99u, f.maw._wantedLidLimit);
}


TEST_F("require that commit is called if visibility delay is 0",
       SearchableFeedViewFixture)
{
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQUAL(1u, f.miw._commitCount);
    EXPECT_EQUAL(1u, f.maw._commitCount);
    f.removeAndWait(dc);
    EXPECT_EQUAL(2u, f.miw._commitCount);
    EXPECT_EQUAL(2u, f.maw._commitCount);
    f.assertTrace("put(adapter=attribute,serialNum=1,lid=1,commit=1),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=1),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=1),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=2),"
                  "ack(Result(0, ))");
}

const TimeStamp LONG_DELAY(TimeStamp::Seconds(60.0));
const TimeStamp SHORT_DELAY(TimeStamp::Seconds(0.5));

TEST_F("require that commit is not called when inside a commit interval",
       SearchableFeedViewFixture(LONG_DELAY))
{
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQUAL(0u, f.miw._commitCount);
    EXPECT_EQUAL(0u, f.maw._commitCount);
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.removeAndWait(dc);
    EXPECT_EQUAL(0u, f.miw._commitCount);
    EXPECT_EQUAL(0u, f.maw._commitCount);
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.assertTrace("ack(Result(0, )),"
                  "put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=0),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0)");
}

TEST_F("require that commit is called when crossing a commit interval",
       SearchableFeedViewFixture(SHORT_DELAY))
{
    FastOS_Thread::Sleep(SHORT_DELAY.ms() + 10);
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQUAL(1u, f.miw._commitCount);
    EXPECT_EQUAL(1u, f.maw._commitCount);
    EXPECT_EQUAL(2u, f._docIdLimit.get());
    FastOS_Thread::Sleep(SHORT_DELAY.ms() + 10);
    f.removeAndWait(dc);
    EXPECT_EQUAL(2u, f.miw._commitCount);
    EXPECT_EQUAL(2u, f.maw._commitCount);
    f.assertTrace("ack(Result(0, )),"
                  "put(adapter=attribute,serialNum=1,lid=1,commit=1),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=1),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=1),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=2)");
}


TEST_F("require that commit is not implicitly called after "
       "handover to maintenance job",
       SearchableFeedViewFixture(SHORT_DELAY))
{
    f._commitTimeTracker.setReplayDone();
    FastOS_Thread::Sleep(SHORT_DELAY.ms() + 10);
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQUAL(0u, f.miw._commitCount);
    EXPECT_EQUAL(0u, f.maw._commitCount);
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    FastOS_Thread::Sleep(SHORT_DELAY.ms() + 10);
    f.removeAndWait(dc);
    EXPECT_EQUAL(0u, f.miw._commitCount);
    EXPECT_EQUAL(0u, f.maw._commitCount);
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.assertTrace("ack(Result(0, )),"
                  "put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=0),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0)");
}

TEST_F("require that forceCommit updates docid limit",
       SearchableFeedViewFixture(LONG_DELAY))
{
    f._commitTimeTracker.setReplayDone();
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQUAL(0u, f.miw._commitCount);
    EXPECT_EQUAL(0u, f.maw._commitCount);
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQUAL(1u, f.miw._commitCount);
    EXPECT_EQUAL(1u, f.maw._commitCount);
    EXPECT_EQUAL(2u, f._docIdLimit.get());
    f.assertTrace("ack(Result(0, )),"
                  "put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "commit(adapter=attribute,serialNum=1),"
                  "commit(adapter=index,serialNum=1)");
}

TEST_F("require that forceCommit updates docid limit during shrink",
       SearchableFeedViewFixture(LONG_DELAY))
{
    f._commitTimeTracker.setReplayDone();
    f.putAndWait(f.makeDummyDocs(0, 3, 1000));
    EXPECT_EQUAL(0u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQUAL(4u, f._docIdLimit.get());
    f.removeAndWait(f.makeDummyDocs(1, 2, 2000));
    EXPECT_EQUAL(4u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQUAL(4u, f._docIdLimit.get());
    f.compactLidSpaceAndWait(2);
    EXPECT_EQUAL(2u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQUAL(2u, f._docIdLimit.get());
    f.putAndWait(f.makeDummyDocs(1, 1, 3000));
    EXPECT_EQUAL(2u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQUAL(3u, f._docIdLimit.get());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}

