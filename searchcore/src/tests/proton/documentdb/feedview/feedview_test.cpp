// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/documentmetastore/lidreusedelayer.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/isummaryadapter.h>
#include <vespa/searchcore/proton/server/matchview.h>
#include <vespa/searchcore/proton/server/searchable_feed_view.h>
#include <vespa/searchcore/proton/test/document_meta_store_context_observer.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/searchcore/proton/test/dummy_summary_manager.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/test/mock_index_manager.h>
#include <vespa/searchcore/proton/test/mock_index_writer.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchcore/proton/test/threading_service_observer.h>
#include <vespa/searchlib/attribute/attributefactory.h>

#include <vespa/searchlib/index/docbuilder.h>

#include <vespa/log/log.h>
LOG_SETUP("feedview_test");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using fastos::TimeStamp;
using proton::matching::SessionManager;
using proton::test::MockGidToLidChangeHandler;
using search::AttributeVector;
using search::CacheStats;
using search::DocumentMetaData;
using search::IDestructorCallback;
using search::SearchableStats;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using searchcorespi::IndexSearchable;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::PartitionId;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::eval::ValueType;

using namespace proton;
using namespace search::index;

typedef SearchableFeedView::SerialNum SerialNum;
typedef search::DocumentIdT DocumentIdT;

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
    SearchableFeedView::PersistentParams _params;

    ParamsContext(const vespalib::string &docType, const vespalib::string &baseDir);
    ~ParamsContext();
    const SearchableFeedView::PersistentParams &getParams() const { return _params; }
};

ParamsContext::ParamsContext(const vespalib::string &docType, const vespalib::string &baseDir)
    : _docTypeName(docType),
      _params(0, 0, _docTypeName, subdb_id, SubDbType::READY)
{
    (void) baseDir;
}
ParamsContext::~ParamsContext() {}

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

struct MyGidToLidChangeHandler : public MockGidToLidChangeHandler
{
    document::GlobalId _changeGid;
    uint32_t _changeLid;
    uint32_t _changes;
    std::map<document::GlobalId, uint32_t> _gidToLid;
public:
    MyGidToLidChangeHandler()
        : MockGidToLidChangeHandler(),
          _changeGid(),
          _changeLid(std::numeric_limits<uint32_t>::max()),
          _changes(0u),
          _gidToLid()
    {
    }

    virtual void notifyPutDone(document::GlobalId gid, uint32_t lid, SerialNum)  override {
        _changeGid = gid;
        _changeLid = lid;
        _gidToLid[gid] = lid;
        ++_changes;
    }

    virtual void notifyRemove(document::GlobalId gid, SerialNum)  override {
        _changeGid = gid;
        _changeLid = 0;
        _gidToLid[gid] = 0;
        ++_changes;
    }

    virtual void notifyRemoveDone(document::GlobalId, SerialNum)  override {
    }

    void assertChanges(document::GlobalId expGid, uint32_t expLid, uint32_t expChanges) {
        EXPECT_EQUAL(expGid, _changeGid);
        EXPECT_EQUAL(expLid, _changeLid);
        EXPECT_EQUAL(expChanges, _changes);
    }
    void assertNumChanges(uint32_t expChanges) {
        EXPECT_EQUAL(expChanges, _changes);
    }
    void assertLid(document::GlobalId gid, uint32_t expLid) {
        uint32_t lid = _gidToLid[gid];
        EXPECT_EQUAL(expLid, lid);
    }
};

struct MyDocumentStore : public test::DummyDocumentStore
{
    typedef std::map<DocumentIdT, document::Document::SP> DocMap;
    const document::DocumentTypeRepo & _repo;
    DocMap           _docs;
    uint64_t         _lastSyncToken;
    uint32_t         _compactLidSpaceLidLimit;
    MyDocumentStore(const document::DocumentTypeRepo & repo)
        : test::DummyDocumentStore("."),
          _repo(repo),
          _docs(),
          _lastSyncToken(0),
          _compactLidSpaceLidLimit(0)
    {}
    ~MyDocumentStore() override;
    Document::UP read(DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        DocMap::const_iterator itr = _docs.find(lid);
        if (itr != _docs.end()) {
            Document::UP retval(itr->second->clone());
            return retval;
        }
        return Document::UP();
    }
    void write(uint64_t syncToken, DocumentIdT lid, const document::Document& doc) override {
        _lastSyncToken = syncToken;
        _docs[lid] = Document::SP(doc.clone());
    }
    void write(uint64_t syncToken, DocumentIdT lid, const vespalib::nbostream & os) override {
        _lastSyncToken = syncToken;
        _docs[lid] = std::make_shared<Document>(_repo, const_cast<vespalib::nbostream &>(os));
    }
    void remove(uint64_t syncToken, DocumentIdT lid) override {
        _lastSyncToken = syncToken;
        _docs.erase(lid);
    }
    uint64_t initFlush(uint64_t syncToken) override {
        return syncToken;
    }
    uint64_t lastSyncToken() const override { return _lastSyncToken; }
    void compactLidSpace(uint32_t wantedDocLidLimit) override {
        _compactLidSpaceLidLimit = wantedDocLidLimit;
    }
};

MyDocumentStore::~MyDocumentStore() = default;

struct MySummaryManager : public test::DummySummaryManager
{
    MyDocumentStore _store;
    MySummaryManager(const document::DocumentTypeRepo & repo) : _store(repo) {}
    virtual search::IDocumentStore &getBackingStore() override { return _store; }
};

struct MySummaryAdapter : public test::MockSummaryAdapter
{
    ISummaryManager::SP _sumMgr;
    MyDocumentStore    &_store;
    MyLidVector         _removes;

    MySummaryAdapter(const document::DocumentTypeRepo & repo)
        : _sumMgr(new MySummaryManager(repo)),
          _store(static_cast<MyDocumentStore &>(_sumMgr->getBackingStore())),
          _removes()
    {}
    virtual void put(SerialNum serialNum, DocumentIdT lid, const Document &doc) override {
        _store.write(serialNum, lid, doc);
    }
    virtual void put(SerialNum serialNum, DocumentIdT lid, const vespalib::nbostream & os) override {
        _store.write(serialNum, lid, os);
    }
    virtual void remove(SerialNum serialNum, const DocumentIdT lid) override {
        LOG(info, "MySummaryAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
        _store.remove(serialNum, lid);
        _removes.push_back(lid);
    }
    virtual const search::IDocumentStore &getDocumentStore() const override {
        return _store;
    }
    virtual std::unique_ptr<Document> get(const DocumentIdT lid, const DocumentTypeRepo &repo) override {
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
    using AttrMap = std::map<vespalib::string, std::shared_ptr<AttributeVector>>;
    AttrMap _attrMap;
    std::set<vespalib::string> _attrs;
    proton::IAttributeManager::SP _mgr;
    MyTracer &_tracer;

    MyAttributeWriter(MyTracer &tracer);
    ~MyAttributeWriter();

    std::vector<AttributeVector *>
    getWritableAttributes() const override {
        return std::vector<AttributeVector *>();
    }
    AttributeVector *getWritableAttribute(const vespalib::string &attrName) const override {
        if (_attrs.count(attrName) == 0) {
            return nullptr;
        }
        AttrMap::const_iterator itr = _attrMap.find(attrName);
        return ((itr == _attrMap.end()) ? nullptr : itr->second.get());
    }
    void put(SerialNum serialNum, const document::Document &doc, DocumentIdT lid,
             bool immediateCommit, OnWriteDoneType) override {
        _putSerial = serialNum;
        _putDocId = doc.getId();
        _putLid = lid;
        _tracer.tracePut(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        if (immediateCommit) {
            ++_commitCount;
        }
    }
    void remove(SerialNum serialNum, DocumentIdT lid,
                bool immediateCommit, OnWriteDoneType) override {
        _removeSerial = serialNum;
        _removeLid = lid;
        _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        if (immediateCommit) {
            ++_commitCount;
        }
    }
    void remove(const LidVector & lidsToRemove, SerialNum serialNum,
                bool immediateCommit, OnWriteDoneType) override {
        for (uint32_t lid : lidsToRemove) {
            LOG(info, "MyAttributeAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
           _removes.push_back(lid);
           _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid, immediateCommit);
        }
    }
    void update(SerialNum serialNum, const document::DocumentUpdate &upd,
                DocumentIdT lid, bool, OnWriteDoneType, IFieldUpdateCallback & onUpdate) override {
        _updateSerial = serialNum;
        _updateDocId = upd.getId();
        _updateLid = lid;
        for (const auto & fieldUpdate : upd.getUpdates()) {
            search::AttributeVector * attr = getWritableAttribute(fieldUpdate.getField().getName());
            onUpdate.onUpdateField(fieldUpdate.getField().getName(), attr);
        }
    }
    void update(SerialNum serialNum, const document::Document &doc, DocumentIdT lid,
                bool immediateCommit, OnWriteDoneType) override {
        (void) serialNum;
        (void) doc;
        (void) lid;
        (void) immediateCommit;
    }
    void heartBeat(SerialNum) override { ++_heartBeatCount; }
    void compactLidSpace(uint32_t wantedLidLimit, SerialNum ) override {
        _wantedLidLimit = wantedLidLimit;
    }
    const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void forceCommit(SerialNum serialNum, OnWriteDoneType) override {
        ++_commitCount;
        _tracer.traceCommit(attributeAdapterTypeName, serialNum);
    }

    void onReplayDone(uint32_t ) override { }
    bool getHasStructFieldAttribute() const override { return false; }
};

MyAttributeWriter::MyAttributeWriter(MyTracer &tracer)
    : _removes(), _putSerial(0), _putDocId(), _putLid(0),
      _updateSerial(0), _updateDocId(), _updateLid(0),
      _removeSerial(0), _removeLid(0), _heartBeatCount(0),
      _commitCount(0), _wantedLidLimit(0),
      _attrMap(), _attrs(), _mgr(), _tracer(tracer)
{
    search::attribute::Config cfg(search::attribute::BasicType::INT32);
    _attrMap["a1"] = search::AttributeFactory::createAttribute("test", cfg);
    search::attribute::Config cfg2(search::attribute::BasicType::PREDICATE);
    _attrMap["a2"] = search::AttributeFactory::createAttribute("test2", cfg2);
    search::attribute::Config cfg3(search::attribute::BasicType::TENSOR);
    cfg3.setTensorType(ValueType::from_spec("tensor(x[10])"));
    _attrMap["a3"] = search::AttributeFactory::createAttribute("test3", cfg3);
}
MyAttributeWriter::~MyAttributeWriter() = default;

struct MyTransport : public feedtoken::ITransport
{
    ResultUP lastResult;
    vespalib::Gate _gate;
    MyTracer &_tracer;
    MyTransport(MyTracer &tracer);
    ~MyTransport();
    void send(ResultUP result, bool ) override {
        lastResult = std::move(result);
        _tracer.traceAck(lastResult);
        _gate.countDown();
    }
    void await() { _gate.await(); }
};

MyTransport::MyTransport(MyTracer &tracer) : lastResult(), _gate(), _tracer(tracer) {}
MyTransport::~MyTransport() = default;

struct MyResultHandler : public IGenericResultHandler
{
    vespalib::Gate _gate;
    MyResultHandler() : _gate() {}
    void handle(const storage::spi::Result &) override {
        _gate.countDown();
    }
    void await() { _gate.await(); }
};

struct SchemaContext
{
    Schema::SP                _schema;
    std::unique_ptr<DocBuilder> _builder;
    SchemaContext();
    ~SchemaContext();
    const std::shared_ptr<const document::DocumentTypeRepo> &getRepo() const { return _builder->getDocumentTypeRepo(); }
};

SchemaContext::SchemaContext() :
    _schema(new Schema()),
    _builder()
{
    _schema->addIndexField(Schema::IndexField("i1", DataType::STRING, CollectionType::SINGLE));
    _schema->addAttributeField(Schema::AttributeField("a1", DataType::STRING, CollectionType::SINGLE));
    _schema->addAttributeField(Schema::AttributeField("a2", DataType::BOOLEANTREE, CollectionType::SINGLE));
    _schema->addAttributeField(Schema::AttributeField("a3", DataType::TENSOR, CollectionType::SINGLE));
    _schema->addSummaryField(Schema::SummaryField("s1", DataType::STRING, CollectionType::SINGLE));
    _builder.reset(new DocBuilder(*_schema));
}
SchemaContext::~SchemaContext() = default;


struct DocumentContext
{
    Document::SP       doc;
    DocumentUpdate::SP upd;
    BucketId           bid;
    Timestamp          ts;
    typedef std::vector<DocumentContext> List;
    DocumentContext(const vespalib::string &docId, uint64_t timestamp, DocBuilder &builder);
    ~DocumentContext();
    void addFieldUpdate(DocBuilder &builder, const vespalib::string &fieldName) {
        const document::Field &field = builder.getDocumentType().getField(fieldName);
        upd->addUpdate(document::FieldUpdate(field));
    }
    document::GlobalId gid() const { return doc->getId().getGlobalId(); }
};

DocumentContext::DocumentContext(const vespalib::string &docId, uint64_t timestamp, DocBuilder &builder)
    : doc(builder.startDocument(docId).startSummaryField("s1").addStr(docId).endField().endDocument().release()),
      upd(new DocumentUpdate(builder.getDocumentType(), doc->getId())),
      bid(BucketFactory::getNumBucketBits(), doc->getId().getGlobalId().convertToBucketId().getRawId()),
      ts(timestamp)
{}
DocumentContext::~DocumentContext() {}

struct FeedTokenContext
{
    MyTransport mt;
    FeedToken   ft;
    typedef std::shared_ptr<FeedTokenContext> SP;
    typedef std::vector<SP> List;
    FeedTokenContext(MyTracer &tracer);
    ~FeedTokenContext();
};

FeedTokenContext::FeedTokenContext(MyTracer &tracer)
    : mt(tracer), ft(feedtoken::make(mt))
{}
FeedTokenContext::~FeedTokenContext() = default;

struct FixtureBase
{
    MyTracer             _tracer;
    SchemaContext        sc;
    IIndexWriter::SP     iw;
    ISummaryAdapter::SP  sa;
    IAttributeWriter::SP aw;
    MyIndexWriter        &miw;
    MySummaryAdapter     &msa;
    MyAttributeWriter    &maw;
    DocIdLimit           _docIdLimit;
    DocumentMetaStoreContext::SP _dmscReal;
    test::DocumentMetaStoreContextObserver::SP _dmsc;
    ParamsContext         pc;
    ExecutorThreadingService _writeServiceReal;
    test::ThreadingServiceObserver _writeService;
    documentmetastore::LidReuseDelayer _lidReuseDelayer;
    CommitTimeTracker     _commitTimeTracker;
    SerialNum             serial;
    std::shared_ptr<MyGidToLidChangeHandler> _gidToLidChangeHandler;
    FixtureBase(TimeStamp visibilityDelay);

    virtual ~FixtureBase();

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

    void performPut(FeedToken token, PutOperation &op) {
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
        FeedTokenContext token(_tracer);
        PutOperation op(docCtx.bid, docCtx.ts, docCtx.doc);
        runInMaster([&] () { performPut(token.ft, op); });
    }

    void performUpdate(FeedToken token, UpdateOperation &op) {
        getFeedView().prepareUpdate(op);
        op.setSerialNum(++serial);
        getFeedView().handleUpdate(token, op);
    }

    void updateAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer);
        UpdateOperation op(docCtx.bid, docCtx.ts, docCtx.upd);
        runInMaster([&] () { performUpdate(token.ft, op); });
    }

    void performRemove(FeedToken token, RemoveOperation &op) {
        getFeedView().prepareRemove(op);
        if (op.getValidNewOrPrevDbdId()) {
            op.setSerialNum(++serial);
            getFeedView().handleRemove(std::move(token), op);
        }
    }

    void removeAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer);
        RemoveOperation op(docCtx.bid, docCtx.ts, docCtx.doc->getId());
        runInMaster([&] () { performRemove(token.ft, op); });
    }

    void removeAndWait(const DocumentContext::List &docs) {
        for (size_t i = 0; i < docs.size(); ++i) {
            removeAndWait(docs[i]);
        }
    }

    void performMove(MoveOperation &op) {
        op.setSerialNum(++serial);
        getFeedView().handleMove(op, IDestructorCallback::SP());
    }

    void moveAndWait(const DocumentContext &docCtx, uint32_t fromLid, uint32_t toLid) {
        MoveOperation op(docCtx.bid, docCtx.ts, docCtx.doc, DbDocumentId(pc._params._subDbId, fromLid), pc._params._subDbId);
        op.setTargetLid(toLid);
        runInMaster([&]() { performMove(op); });
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
    void assertChangeHandler(document::GlobalId expGid, uint32_t expLid, uint32_t expChanges) {
        _gidToLidChangeHandler->assertChanges(expGid, expLid, expChanges);
    }
    void assertChangeHandlerCount(uint32_t expChanges) {
        _gidToLidChangeHandler->assertNumChanges(expChanges);
    }
    void assertChangeNotified(document::GlobalId gid, uint32_t expLid) {
        _gidToLidChangeHandler->assertLid(gid, expLid);
    }
    void populateBeforeCompactLidSpace();
};


FixtureBase::FixtureBase(TimeStamp visibilityDelay)
    : _tracer(),
      sc(),
      iw(new MyIndexWriter(_tracer)),
      sa(new MySummaryAdapter(*sc._builder->getDocumentTypeRepo())),
      aw(new MyAttributeWriter(_tracer)),
      miw(static_cast<MyIndexWriter&>(*iw)),
      msa(static_cast<MySummaryAdapter&>(*sa)),
      maw(static_cast<MyAttributeWriter&>(*aw)),
      _docIdLimit(0u),
      _dmscReal(new DocumentMetaStoreContext(std::make_shared<BucketDBOwner>())),
      _dmsc(new test::DocumentMetaStoreContextObserver(*_dmscReal)),
      pc(sc._builder->getDocumentType().getName(), "fileconfig_test"),
      _writeServiceReal(),
      _writeService(_writeServiceReal),
      _lidReuseDelayer(_writeService, _dmsc->get()),
      _commitTimeTracker(visibilityDelay),
      serial(0),
      _gidToLidChangeHandler(std::make_shared<MyGidToLidChangeHandler>())
{
    _dmsc->constructFreeList();
    _lidReuseDelayer.setImmediateCommit(visibilityDelay == 0);
}

FixtureBase::~FixtureBase() {
    _writeServiceReal.sync();
}

void
FixtureBase::populateBeforeCompactLidSpace()
{
    putAndWait(makeDummyDocs(0, 2, 1000));
    removeAndWait(makeDummyDocs(1, 1, 2000));
}

struct SearchableFeedViewFixture : public FixtureBase
{
    SearchableFeedView fv;
    SearchableFeedViewFixture(TimeStamp visibilityDelay = 0) :
        FixtureBase(visibilityDelay),
        fv(StoreOnlyFeedView::Context(sa,
                sc._schema,
                _dmsc,
                *_gidToLidChangeHandler,
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
    virtual IFeedView &getFeedView() override { return fv; }
};

struct FastAccessFeedViewFixture : public FixtureBase
{
    FastAccessFeedView fv;
    FastAccessFeedViewFixture(TimeStamp visibilityDelay = 0) :
        FixtureBase(visibilityDelay),
        fv(StoreOnlyFeedView::Context(sa,
                sc._schema,
                _dmsc,
                *_gidToLidChangeHandler,
                sc.getRepo(),
                _writeService,
                _lidReuseDelayer,
                _commitTimeTracker),
           pc.getParams(),
           FastAccessFeedView::Context(aw, _docIdLimit))
    {
    }
    virtual IFeedView &getFeedView() override { return fv; }
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
assertAttributeUpdate(SerialNum serialNum, const document::DocumentId &docId,
                      DocumentIdT lid, const MyAttributeWriter & adapter)
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

TEST_F("require that put() notifies gid to lid change handler", SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    TEST_DO(f.assertChangeHandler(dc1.gid(), 1u, 1u));
    f.putAndWait(dc2);
    TEST_DO(f.assertChangeHandler(dc2.gid(), 1u, 1u));
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

TEST_F("require that remove() notifies gid to lid change handler", SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    TEST_DO(f.assertChangeHandler(dc1.gid(), 1u, 1u));
    f.removeAndWait(dc2);
    TEST_DO(f.assertChangeHandler(dc2.gid(), 0u, 2u));
}

bool
assertThreadObserver(uint32_t masterExecuteCnt,
                     uint32_t indexExecuteCnt,
                     uint32_t summaryExecuteCnt,
                     const test::ThreadingServiceObserver &observer)
{
    if (!EXPECT_EQUAL(masterExecuteCnt, observer.masterObserver().getExecuteCnt())) return false;
    if (!EXPECT_EQUAL(indexExecuteCnt, observer.indexObserver().getExecuteCnt())) return false;
    if (!EXPECT_EQUAL(summaryExecuteCnt, observer.summaryObserver().getExecuteCnt())) return false;
    return true;
}

TEST_F("require that remove() calls removeComplete() via delayed thread service",
        SearchableFeedViewFixture)
{
    EXPECT_TRUE(assertThreadObserver(1, 0, 0, f.writeServiceObserver()));
    f.putAndWait(f.doc1(10));
    // put index fields handled in index thread
    EXPECT_TRUE(assertThreadObserver(2, 1, 1, f.writeServiceObserver()));
    f.removeAndWait(f.doc1(20));
    // remove index fields handled in index thread
    // delayed remove complete handled in same index thread, then master thread
    EXPECT_TRUE(assertThreadObserver(4, 2, 2, f.writeServiceObserver()));
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
    TEST_DO(f.assertChangeHandler(docs.back().gid(), 5u, 5u));
    TEST_DO(f.assertChangeNotified(docs[0].gid(), 1));
    TEST_DO(f.assertChangeNotified(docs[1].gid(), 2));
    TEST_DO(f.assertChangeNotified(docs[2].gid(), 3));
    TEST_DO(f.assertChangeNotified(docs[3].gid(), 4));
    TEST_DO(f.assertChangeNotified(docs[4].gid(), 5));

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
    TEST_DO(f.assertChangeHandlerCount(8));
    TEST_DO(f.assertChangeNotified(docs[0].gid(), 0));
    TEST_DO(f.assertChangeNotified(docs[1].gid(), 0));
    TEST_DO(f.assertChangeNotified(docs[2].gid(), 0));
    TEST_DO(f.assertChangeNotified(docs[3].gid(), 4));
    TEST_DO(f.assertChangeNotified(docs[4].gid(), 5));
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
    f.populateBeforeCompactLidSpace();
    EXPECT_TRUE(assertThreadObserver(5, 3, 3, f.writeServiceObserver()));
    f.compactLidSpaceAndWait(2);
    // performIndexForceCommit in index thread, then completion callback
    // in master thread.
    EXPECT_TRUE(assertThreadObserver(7, 4, 4, f.writeServiceObserver()));
    EXPECT_EQUAL(2u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(2u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(1u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
    EXPECT_EQUAL(2u, f._docIdLimit.get());
}

TEST_F("require that compactLidSpace() doesn't propagate to "
       "document meta store and document store and "
       "blocks lid space shrinkage until generation is no longer used",
       SearchableFeedViewFixture)
{
    f.populateBeforeCompactLidSpace();
    EXPECT_TRUE(assertThreadObserver(5, 3, 3, f.writeServiceObserver()));
    CompactLidSpaceOperation op(0, 2);
    op.setSerialNum(0);
    f.runInMaster([&] () { f.fv.handleCompactLidSpace(op); });
    // Delayed holdUnblockShrinkLidSpace() in index thread, then master thread
    EXPECT_TRUE(assertThreadObserver(6, 3, 3, f.writeServiceObserver()));
    EXPECT_EQUAL(0u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(0u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQUAL(0u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
}

TEST_F("require that compactLidSpace() propagates to attributeadapter",
       FastAccessFeedViewFixture)
{
    f.populateBeforeCompactLidSpace();
    f.compactLidSpaceAndWait(2);
    EXPECT_EQUAL(2u, f.maw._wantedLidLimit);
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
    f.assertTrace("put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=0),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0),"
                  "ack(Result(0, ))");
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
    f.assertTrace("put(adapter=attribute,serialNum=1,lid=1,commit=1),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=1),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=1),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0),"
                  "commit(adapter=index,serialNum=2),"
                  "ack(Result(0, ))");
}


TEST_F("require that commit is not implicitly called after handover to maintenance job",
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
    f.assertTrace("put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "ack(Result(0, )),"
                  "remove(adapter=attribute,serialNum=2,lid=1,commit=0),"
                  "remove(adapter=index,serialNum=2,lid=1,commit=0),"
                  "ack(Result(0, ))");
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
    f.assertTrace("put(adapter=attribute,serialNum=1,lid=1,commit=0),"
                  "put(adapter=index,serialNum=1,lid=1,commit=0),"
                  "ack(Result(0, )),"
                  "commit(adapter=attribute,serialNum=1),"
                  "commit(adapter=index,serialNum=1)");
}

TEST_F("require that forceCommit updates docid limit during shrink", SearchableFeedViewFixture(LONG_DELAY))
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

TEST_F("require that move() notifies gid to lid change handler", SearchableFeedViewFixture)
{
    DocumentContext dc1 = f.doc("id::searchdocument::1", 10);
    DocumentContext dc2 = f.doc("id::searchdocument::2", 20);
    f.putAndWait(dc1);
    TEST_DO(f.assertChangeHandler(dc1.gid(), 1u, 1u));
    f.putAndWait(dc2);
    TEST_DO(f.assertChangeHandler(dc2.gid(), 2u, 2u));
    DocumentContext dc3 = f.doc("id::searchdocument::1", 30);
    f.removeAndWait(dc3);
    TEST_DO(f.assertChangeHandler(dc3.gid(), 0u, 3u));
    f.moveAndWait(dc2, 2, 1);
    TEST_DO(f.assertChangeHandler(dc2.gid(), 1u, 4u));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}

