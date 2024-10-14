// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/server/isummaryadapter.h>
#include <vespa/searchcore/proton/server/matchview.h>
#include <vespa/searchcore/proton/server/searchable_feed_view.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/test/document_meta_store_context_observer.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/searchcore/proton/test/dummy_summary_manager.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/test/mock_index_manager.h>
#include <vespa/searchcore/proton/test/mock_index_writer.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchcore/proton/test/threading_service_observer.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <tuple>

#include <vespa/log/log.h>
LOG_SETUP(".feedview_test");

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using document::StringFieldValue;
using proton::matching::SessionManager;
using proton::test::MockGidToLidChangeHandler;
using search::AttributeVector;
using search::DocumentMetaData;
using vespalib::IDestructorCallback;
using vespalib::Gate;
using vespalib::GateCallback;
using search::SearchableStats;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using searchcorespi::IndexSearchable;
using storage::spi::BucketChecksum;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::CacheStats;
using vespalib::eval::ValueType;

using namespace proton;
using namespace search::index;

using SerialNum = SearchableFeedView::SerialNum;
using DocumentIdT = search::DocumentIdT;

namespace {
struct MyLidVector : public std::vector<DocumentIdT>
{
    MyLidVector &add(DocumentIdT lid) { push_back(lid); return *this; }
};


const uint32_t subdb_id = 0;
const std::string indexAdapterTypeName = "index";
const std::string attributeAdapterTypeName = "attribute";

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

    void tracePut(const std::string &adapterType, SerialNum serialNum, uint32_t lid) {
        Guard guard(_mutex);
        addComma();
        _os << "put(adapter=" << adapterType <<
            ",serialNum=" << serialNum << ",lid=" << lid << ")";
    }

    void traceRemove(const std::string &adapterType, SerialNum serialNum, uint32_t lid) {
        Guard guard(_mutex);
        addComma();
        _os << "remove(adapter=" << adapterType << ",serialNum=" << serialNum << ",lid=" << lid << ")";
    }

    void traceCommit(const std::string &adapterType, SerialNum serialNum) {
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

    ParamsContext(const std::string &docType, const std::string &baseDir);
    ~ParamsContext();
    const SearchableFeedView::PersistentParams &getParams() const { return _params; }
};

ParamsContext::ParamsContext(const std::string &docType, const std::string &baseDir)
    : _docTypeName(docType),
      _params(0, 0, _docTypeName, subdb_id, SubDbType::READY)
{
    (void) baseDir;
}
ParamsContext::~ParamsContext() = default;

struct MyIndexWriter : public test::MockIndexWriter
{
    MyLidVector _removes;
    int _heartBeatCount;
    uint32_t _commitCount;
    uint32_t _wantedLidLimit;
    MyTracer &_tracer;
    MyIndexWriter(MyTracer &tracer)
        : test::MockIndexWriter(std::make_shared<test::MockIndexManager>()),
          _removes(),
          _heartBeatCount(0),
          _commitCount(0),
          _wantedLidLimit(0),
          _tracer(tracer)
    {}
    void put(SerialNum serialNum, const document::Document &doc, const DocumentIdT lid, const OnWriteDoneType&) override {
        (void) doc;
        _tracer.tracePut(indexAdapterTypeName, serialNum, lid);
    }
    void removeDocs(SerialNum serialNum,  LidVector lids) override {
        for (search::DocumentIdT lid : lids) {
            LOG(info, "MyIndexAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
            _removes.push_back(lid);
            _tracer.traceRemove(indexAdapterTypeName, serialNum, lid);
        }
    }
    void commit(SerialNum serialNum, const OnWriteDoneType&) override {
        ++_commitCount;
        _tracer.traceCommit(indexAdapterTypeName, serialNum);
    }
    void heartBeat(SerialNum) override { ++_heartBeatCount; }
    void compactLidSpace(SerialNum, uint32_t lidLimit) override {
        _wantedLidLimit = lidLimit;
    }
};

using LastChange = std::tuple<document::GlobalId, uint32_t, uint32_t>;

struct MyGidToLidChangeHandler : public MockGidToLidChangeHandler
{
    document::GlobalId _changeGid;
    uint32_t _changeLid;
    uint32_t _changes;
    std::map<document::GlobalId, uint32_t> _gidToLid;
public:
    MyGidToLidChangeHandler() noexcept
        : MockGidToLidChangeHandler(),
          _changeGid(),
          _changeLid(std::numeric_limits<uint32_t>::max()),
          _changes(0u),
          _gidToLid()
    {
    }

    void notifyPut(IDestructorCallbackSP, document::GlobalId gid, uint32_t lid, SerialNum) override {
        _changeGid = gid;
        _changeLid = lid;
        _gidToLid[gid] = lid;
        ++_changes;
    }

    void notifyRemoves(IDestructorCallbackSP, const std::vector<document::GlobalId> & gids, SerialNum) override {
        for (const auto & gid : gids) {
            _changeGid = gid;
            _changeLid = 0;
            _gidToLid[gid] = 0;
            ++_changes;
        }
    }
    LastChange get_last_change() const noexcept { return std::make_tuple(_changeGid, _changeLid, _changes); }
    uint32_t get_num_changes() const noexcept { return _changes; }
    uint32_t get_lid(document::GlobalId gid) { return _gidToLid[gid]; }
};

struct MyDocumentStore : public test::DummyDocumentStore
{
    using DocMap = std::map<DocumentIdT, document::Document::SP>;
    const document::DocumentTypeRepo & _repo;
    DocMap           _docs;
    uint64_t         _lastSyncToken;
    uint32_t         _compactLidSpaceLidLimit;
    MyDocumentStore(const document::DocumentTypeRepo & repo) noexcept
        : test::DummyDocumentStore("."),
          _repo(repo),
          _docs(),
          _lastSyncToken(0),
          _compactLidSpaceLidLimit(0)
    {}
    ~MyDocumentStore() override;
    Document::UP read(DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        auto itr = _docs.find(lid);
        if (itr != _docs.end()) {
            Document::UP retval(itr->second->clone());
            return retval;
        }
        return {};
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
    MySummaryManager(const document::DocumentTypeRepo & repo) noexcept : _store(repo) {}
    ~MySummaryManager() override;
    search::IDocumentStore &getBackingStore() override { return _store; }
};

MySummaryManager::~MySummaryManager() = default;

struct MySummaryAdapter : public test::MockSummaryAdapter
{
    ISummaryManager::SP _sumMgr;
    MyDocumentStore    &_store;
    MyLidVector         _removes;

    MySummaryAdapter(const document::DocumentTypeRepo & repo) noexcept
        : _sumMgr(std::make_shared<MySummaryManager>(repo)),
          _store(static_cast<MyDocumentStore &>(_sumMgr->getBackingStore())),
          _removes()
    {}
    ~MySummaryAdapter() override;
    void put(SerialNum serialNum, DocumentIdT lid, const Document &doc) override {
        _store.write(serialNum, lid, doc);
    }
    void put(SerialNum serialNum, DocumentIdT lid, const vespalib::nbostream & os) override {
        _store.write(serialNum, lid, os);
    }
    void remove(SerialNum serialNum, const DocumentIdT lid) override {
        LOG(info, "MySummaryAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
        _store.remove(serialNum, lid);
        _removes.push_back(lid);
    }
    const search::IDocumentStore &getDocumentStore() const override {
        return _store;
    }
    std::unique_ptr<Document> get(const DocumentIdT lid, const DocumentTypeRepo &repo) override {
        return _store.read(lid, repo);
    }
    void compactLidSpace(uint32_t wantedDocIdLimit) override {
        _store.compactLidSpace(wantedDocIdLimit);
    }
};
MySummaryAdapter::~MySummaryAdapter() = default;

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
    using AttrMap = std::map<std::string, std::shared_ptr<AttributeVector>>;
    AttrMap _attrMap;
    std::set<std::string> _attrs;
    proton::IAttributeManager::SP _mgr;
    MyTracer &_tracer;

    MyAttributeWriter(MyTracer &tracer);
    ~MyAttributeWriter() override;

    std::vector<AttributeVector *>
    getWritableAttributes() const override {
        return std::vector<AttributeVector *>();
    }
    AttributeVector *getWritableAttribute(const std::string &attrName) const override {
        if (_attrs.count(attrName) == 0) {
            return nullptr;
        }
        auto itr = _attrMap.find(attrName);
        return ((itr == _attrMap.end()) ? nullptr : itr->second.get());
    }
    void put(SerialNum serialNum, const document::Document &doc, DocumentIdT lid, const OnWriteDoneType&) override {
        _putSerial = serialNum;
        _putDocId = doc.getId();
        _putLid = lid;
        _tracer.tracePut(attributeAdapterTypeName, serialNum, lid);
    }
    void remove(SerialNum serialNum, DocumentIdT lid, const OnWriteDoneType&) override {
        _removeSerial = serialNum;
        _removeLid = lid;
        _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid);
    }
    void remove(const LidVector & lidsToRemove, SerialNum serialNum, const OnWriteDoneType&) override {
        for (uint32_t lid : lidsToRemove) {
            LOG(info, "MyAttributeAdapter::remove(): serialNum(%" PRIu64 "), docId(%u)", serialNum, lid);
           _removes.push_back(lid);
           _tracer.traceRemove(attributeAdapterTypeName, serialNum, lid);
        }
    }
    void update(SerialNum serialNum, const document::DocumentUpdate &upd,
                DocumentIdT lid, const OnWriteDoneType&, IFieldUpdateCallback & onUpdate) override {
        _updateSerial = serialNum;
        _updateDocId = upd.getId();
        _updateLid = lid;
        for (const auto & fieldUpdate : upd.getUpdates()) {
            search::AttributeVector * attr = getWritableAttribute(fieldUpdate.getField().getName());
            onUpdate.onUpdateField(fieldUpdate.getField(), attr);
        }
    }
    void update(SerialNum serialNum, const document::Document &doc, DocumentIdT lid, const OnWriteDoneType&) override {
        (void) serialNum;
        (void) doc;
        (void) lid;
    }
    void heartBeat(SerialNum, const OnWriteDoneType&) override { ++_heartBeatCount; }
    void compactLidSpace(uint32_t wantedLidLimit, SerialNum ) override {
        _wantedLidLimit = wantedLidLimit;
    }
    const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void forceCommit(const CommitParam & param, const OnWriteDoneType&) override {
        ++_commitCount;
        _tracer.traceCommit(attributeAdapterTypeName, param.lastSerialNum());
    }
    void drain(const OnWriteDoneType& onDone) override {
        (void) onDone;
    }

    void onReplayDone(uint32_t ) override { }
    bool hasStructFieldAttribute() const override { return false; }
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
    Gate _gate;
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

struct SchemaContext
{
    DocBuilder  _builder;
    std::shared_ptr<const Schema>  _schema;
    SchemaContext();
    ~SchemaContext();
    std::shared_ptr<const document::DocumentTypeRepo> getRepo() const { return _builder.get_repo_sp(); }
};

SchemaContext::SchemaContext() :
    _builder([](auto &header) { header.addField("i1", DataType::T_STRING)
                                       .addField("a1", DataType::T_STRING)
                                       .addField("a2", DataType::T_PREDICATE)
                                       .addTensorField("a3", "")
                                       .addField("s1", DataType::T_STRING); }),
    _schema(std::make_shared<Schema>(SchemaBuilder(_builder).add_indexes({"i1"}).add_attributes({"a1", "a2", "a3"}).build()))
{
}

SchemaContext::~SchemaContext() = default;

struct DocumentContext
{
    Document::SP       doc;
    DocumentUpdate::SP upd;
    BucketId           bid;
    Timestamp          ts;
    using List = std::vector<DocumentContext>;
    DocumentContext(const std::string &docId, uint64_t timestamp, DocBuilder &builder);
    ~DocumentContext();
    void addFieldUpdate(DocBuilder &builder, const std::string &fieldName) {
        const document::Field &field = builder.get_document_type().getField(fieldName);
        upd->addUpdate(document::FieldUpdate(field));
    }
    document::GlobalId gid() const { return doc->getId().getGlobalId(); }
};

DocumentContext::DocumentContext(const std::string &docId, uint64_t timestamp, DocBuilder& builder)
    : doc(builder.make_document(docId)),
      upd(std::make_shared<DocumentUpdate>(builder.get_repo(), builder.get_document_type(), doc->getId())),
      bid(BucketFactory::getNumBucketBits(), doc->getId().getGlobalId().convertToBucketId().getRawId()),
      ts(timestamp)
{
    doc->setValue("s1", StringFieldValue(docId));
}


DocumentContext::~DocumentContext() = default;

struct FeedTokenContext
{
    MyTransport mt;
    FeedToken   ft;
    using SP = std::shared_ptr<FeedTokenContext>;
    using List = std::vector<SP>;
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
    std::shared_ptr<PendingLidTracker>    _pendingLidsForCommit;
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
    TransportAndExecutorService    _service;
    test::ThreadingServiceObserver _writeService;
    SerialNum             serial;
    std::shared_ptr<MyGidToLidChangeHandler> _gidToLidChangeHandler;
    FixtureBase() __attribute__((noinline));

    virtual ~FixtureBase() __attribute__((noinline));

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

    bucketdb::Guard getBucketDB() const {
        return getMetaStore().getBucketDB().takeGuard();
    }

    DocumentMetaData getMetaData(const DocumentContext &doc_) const {
        return getMetaStore().getMetaData(doc_.doc->getId().getGlobalId());
    }

    DocBuilder &getBuilder() { return sc._builder; }

    DocumentContext doc(const std::string &docId, uint64_t timestamp) {
        return DocumentContext(docId, timestamp, getBuilder());
    }

    DocumentContext doc1(uint64_t timestamp = 10) {
        return doc("id:ns:searchdocument::1", timestamp);
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
        runInMaster([this, ft = std::move(token.ft), &op]() mutable { performPut(std::move(ft), op); });
        token.mt.await();
    }

    void performUpdate(FeedToken token, UpdateOperation &op) {
        getFeedView().prepareUpdate(op);
        op.setSerialNum(++serial);
        getFeedView().handleUpdate(token, op);
    }

    void updateAndWait(const DocumentContext &docCtx) {
        FeedTokenContext token(_tracer);
        UpdateOperation op(docCtx.bid, docCtx.ts, docCtx.upd);
        runInMaster([this, ft = std::move(token.ft), &op]() mutable { performUpdate(std::move(ft), op); });
        token.mt.await();
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
        RemoveOperationWithDocId op(docCtx.bid, docCtx.ts, docCtx.doc->getId());
        runInMaster([this, ft = std::move(token.ft), &op]() mutable { performRemove(std::move(ft), op); });
        token.mt.await();
    }

    void removeAndWait(const DocumentContext::List &docs) {
        for (size_t i = 0; i < docs.size(); ++i) {
            removeAndWait(docs[i]);
        }
    }

    void performMove(MoveOperation &op, IDestructorCallback::SP onDone) {
        op.setSerialNum(++serial);
        getFeedView().handleMove(op, std::move(onDone));
    }

    void moveAndWait(const DocumentContext &docCtx, uint32_t fromLid, uint32_t toLid) {
        MoveOperation op(docCtx.bid, docCtx.ts, docCtx.doc, DbDocumentId(pc._params._subDbId, fromLid), pc._params._subDbId);
        op.setTargetLid(toLid);
        Gate gate;
        runInMaster([&, onDone=std::make_shared<GateCallback>(gate)]() { performMove(op, std::move(onDone)); });
        gate.await();
    }

    void performDeleteBucket(DeleteBucketOperation &op, IDestructorCallback::SP onDone) {
        getFeedView().prepareDeleteBucket(op);
        op.setSerialNum(++serial);
        getFeedView().handleDeleteBucket(op, onDone);
    }

    void performForceCommit(IDestructorCallback::SP onDone) {
        getFeedView().forceCommit(serial, std::move(onDone));
    }
    void forceCommitAndWait() {
        Gate gate;
        runInMaster([this, onDone=std::make_shared<GateCallback>(gate)]() {
            performForceCommit(std::move(onDone));
        });
        gate.await();
        _writeService.master().sync();
    }

    std::string get_trace() { return _tracer._os.str(); }

    DocumentContext::List makeDummyDocs(uint32_t first, uint32_t count, uint64_t tsfirst) __attribute__((noinline));

    void performCompactLidSpace(uint32_t wantedLidLimit, IDestructorCallback::SP onDone) {
        auto &fv = getFeedView();
        CompactLidSpaceOperation op(0, wantedLidLimit);
        op.setSerialNum(++serial);
        fv.handleCompactLidSpace(op, onDone);
    }
    void compactLidSpaceAndWait(uint32_t wantedLidLimit) {
        Gate gate;
        runInMaster([&]() {
            performCompactLidSpace(wantedLidLimit, std::make_shared<GateCallback>(gate));
        });
        gate.await();
        _writeService.master().sync();
    }
    LastChange get_last_change() { return _gidToLidChangeHandler->get_last_change(); }
    uint32_t get_change_handler_count() { return _gidToLidChangeHandler->get_num_changes(); }
    uint32_t get_notified_lid(document::GlobalId gid) { return _gidToLidChangeHandler->get_lid(gid); }
    void populateBeforeCompactLidSpace();

    void dms_commit() { _dmsc->get().commit(search::CommitParam(serial)); }
};


FixtureBase::FixtureBase()
    : _tracer(),
      _pendingLidsForCommit(std::make_shared<PendingLidTracker>()),
      sc(),
      iw(std::make_shared<MyIndexWriter>(_tracer)),
      sa(std::make_shared<MySummaryAdapter>(sc._builder.get_repo())),
      aw(std::make_shared<MyAttributeWriter>(_tracer)),
      miw(static_cast<MyIndexWriter&>(*iw)),
      msa(static_cast<MySummaryAdapter&>(*sa)),
      maw(static_cast<MyAttributeWriter&>(*aw)),
      _docIdLimit(0u),
      _dmscReal(std::make_shared<DocumentMetaStoreContext>(std::make_shared<bucketdb::BucketDBOwner>())),
      _dmsc(std::make_shared<test::DocumentMetaStoreContextObserver>(*_dmscReal)),
      pc(sc._builder.get_document_type().getName(), "fileconfig_test"),
      _service(1),
      _writeService(_service.write()),
      serial(0),
      _gidToLidChangeHandler(std::make_shared<MyGidToLidChangeHandler>())
{
    _dmsc->constructFreeList();
}

FixtureBase::~FixtureBase() {
    _service.shutdown();
}

DocumentContext::List
FixtureBase::makeDummyDocs(uint32_t first, uint32_t count, uint64_t tsfirst) {
    DocumentContext::List docs;
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t id = first + i;
        uint64_t ts = tsfirst + i;
        vespalib::asciistream os;
        os << "id:ns:searchdocument::" << id;
        docs.push_back(doc(os.str(), ts));
    }
    return docs;
}

void
FixtureBase::populateBeforeCompactLidSpace()
{
    putAndWait(makeDummyDocs(0, 2, 1000));
    removeAndWait(makeDummyDocs(1, 1, 2000));
    forceCommitAndWait();
}

struct SearchableFeedViewFixture : public FixtureBase
{
    SearchableFeedView fv;
    SearchableFeedViewFixture() __attribute__((noinline));
    ~SearchableFeedViewFixture() override __attribute__((noinline));
    IFeedView &getFeedView() override { return fv; }
};

SearchableFeedViewFixture::SearchableFeedViewFixture()
    : FixtureBase(),
      fv(StoreOnlyFeedView::Context(sa, sc._schema, _dmsc,
                                    sc.getRepo(), _pendingLidsForCommit,
                                    *_gidToLidChangeHandler, _writeService),
      pc.getParams(),
      FastAccessFeedView::Context(aw, _docIdLimit),
      SearchableFeedView::Context(iw))
{ }
SearchableFeedViewFixture::~SearchableFeedViewFixture() {
    forceCommitAndWait();
}

struct FastAccessFeedViewFixture : public FixtureBase
{
    FastAccessFeedView fv;
    FastAccessFeedViewFixture() __attribute__((noinline));
    ~FastAccessFeedViewFixture() override __attribute__((noinline));
    IFeedView &getFeedView() override { return fv; }
};

FastAccessFeedViewFixture::FastAccessFeedViewFixture()
    : FixtureBase(),
      fv(StoreOnlyFeedView::Context(sa, sc._schema, _dmsc, sc.getRepo(), _pendingLidsForCommit,
                                    *_gidToLidChangeHandler, _writeService),
      pc.getParams(),
      FastAccessFeedView::Context(aw, _docIdLimit))
{ }

FastAccessFeedViewFixture::~FastAccessFeedViewFixture() {
    forceCommitAndWait();
}

void assertBucketInfo(const BucketId &ebid, const Timestamp &ets, uint32_t lid, const IDocumentMetaStore &metaStore) __attribute__((noinline));
void assertBucketInfo(const BucketId &ebid, const Timestamp &ets, uint32_t lid, const IDocumentMetaStore &metaStore)
{
    document::GlobalId gid;
    EXPECT_TRUE(metaStore.getGid(lid, gid));
    search::DocumentMetaData meta = metaStore.getMetaData(gid);
    EXPECT_TRUE(meta.valid());
    EXPECT_EQ(ebid, meta.bucketId);
    Timestamp ats;
    EXPECT_EQ(ets, meta.timestamp);
}

void assertLidVector(const MyLidVector &exp, const MyLidVector &act) __attribute__((noinline));
void assertLidVector(const MyLidVector &exp, const MyLidVector &act)
{
    EXPECT_EQ(exp.size(), act.size());
    for (size_t i = 0; i < exp.size(); ++i) {
        EXPECT_TRUE(std::find(act.begin(), act.end(), exp[i]) != act.end());
    }
}

void
assertAttributeUpdate(SerialNum serialNum, const document::DocumentId &docId,
                      DocumentIdT lid, const MyAttributeWriter & adapter)
{
    EXPECT_EQ(serialNum, adapter._updateSerial);
    EXPECT_EQ(docId, adapter._updateDocId);
    EXPECT_EQ(lid, adapter._updateLid);
}

}


TEST(FeedViewTest, require_that_put_updates_document_meta_store_with_bucket_info)
{
    SearchableFeedViewFixture f;
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    f.dms_commit();

    assertBucketInfo(dc.bid, dc.ts, 1, f.getMetaStore());
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc.bid);
    EXPECT_EQ(1u, bucketInfo.getDocumentCount());
    EXPECT_NE(bucketInfo.getChecksum(), BucketChecksum(0));
}

TEST(FeedViewTest, require_that_put_calls_attribute_adapter)
{
    SearchableFeedViewFixture f;
    DocumentContext dc = f.doc1();
    EXPECT_EQ(0u, f._docIdLimit.get());
    f.putAndWait(dc);
    f.forceCommitAndWait();

    EXPECT_EQ(1u, f.maw._putSerial);
    EXPECT_EQ(DocumentId("id:ns:searchdocument::1"), f.maw._putDocId);
    EXPECT_EQ(1u, f.maw._putLid);
    EXPECT_EQ(2u, f._docIdLimit.get());
}

TEST(FeedViewTest, require_that_put_notifies_gid_to_lid_change_handler)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    EXPECT_EQ((LastChange{dc1.gid(), 1u, 1u}), f.get_last_change());
    f.putAndWait(dc2);
    EXPECT_EQ((LastChange{dc2.gid(), 1u, 1u}), f.get_last_change());
}

TEST(FeedViewTest, require_that_update_updates_document_meta_store_with_bucket_info)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    BucketChecksum bcs = f.getBucketDB()->get(dc1.bid).getChecksum();
    f.updateAndWait(dc2);
    f.dms_commit();

    assertBucketInfo(dc1.bid, Timestamp(20), 1, f.getMetaStore());
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc1.bid);
    EXPECT_EQ(1u, bucketInfo.getDocumentCount());
    EXPECT_NE(bucketInfo.getChecksum(), bcs);
    EXPECT_NE(bucketInfo.getChecksum(), BucketChecksum(0));
}

TEST(FeedViewTest, require_that_update_calls_attribute_adapter)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    f.updateAndWait(dc2);

    assertAttributeUpdate(2u, DocumentId("id:ns:searchdocument::1"), 1u, f.maw);
}

TEST(FeedViewTest, require_that_remove_updates_document_meta_store_with_bucket_info)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc("id:test:searchdocument:n=1:1", 10);
    DocumentContext dc2 = f.doc("id:test:searchdocument:n=1:2", 11);
    f.putAndWait(dc1);
    BucketChecksum bcs1 = f.getBucketDB()->get(dc1.bid).getChecksum();
    f.putAndWait(dc2);
    BucketChecksum bcs2 = f.getBucketDB()->get(dc2.bid).getChecksum();
    f.removeAndWait(DocumentContext("id:test:searchdocument:n=1:2", 20, f.getBuilder()));
    f.dms_commit();

    assertBucketInfo(dc1.bid, Timestamp(10), 1, f.getMetaStore());
    EXPECT_FALSE(f.getMetaStore().validLid(2)); // don't remember remove
    // TODO: rewrite to use getBucketInfo() when available
    BucketInfo bucketInfo = f.getBucketDB()->get(dc1.bid);
    EXPECT_EQ(1u, bucketInfo.getDocumentCount());
    EXPECT_NE(bucketInfo.getChecksum(), bcs2);
    EXPECT_EQ(bucketInfo.getChecksum(), bcs1);
}

TEST(FeedViewTest, require_that_remove_calls_attribute_adapter)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    f.removeAndWait(dc2);

    EXPECT_EQ(2u, f.maw._removeSerial);
    EXPECT_EQ(1u, f.maw._removeLid);
}

TEST(FeedViewTest, require_that_remove_notifies_gid_to_lid_change_handler)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc1(10);
    DocumentContext dc2 = f.doc1(20);
    f.putAndWait(dc1);
    EXPECT_EQ((LastChange{dc1.gid(), 1u, 1u}), f.get_last_change());
    f.removeAndWait(dc2);
    EXPECT_EQ((LastChange{dc2.gid(), 0u, 2u}), f.get_last_change());
}

namespace {

std::tuple<uint32_t, uint32_t, uint32_t> get_execute_counts(const test::ThreadingServiceObserver &observer) {
    return std::make_tuple(observer.masterObserver().getExecuteCnt(),
                         observer.indexObserver().getExecuteCnt(),
                         observer.summaryObserver().getExecuteCnt());
}

}

TEST(FeedViewTest, require_that_remove_calls_removes_complete_via_delayed_thread_service)
{
    SearchableFeedViewFixture f;
    EXPECT_EQ(std::make_tuple(0u, 0u, 0u), get_execute_counts(f.writeServiceObserver()));
    f.putAndWait(f.doc1(10));
    f.forceCommitAndWait();
    // put index fields handled in index thread
    EXPECT_EQ(std::make_tuple(2u, 2u, 2u), get_execute_counts(f.writeServiceObserver()));
    f.removeAndWait(f.doc1(20));
    f.forceCommitAndWait();
    // remove index fields handled in index thread
    // delayed remove complete handled in same index thread, then master thread
    EXPECT_EQ(std::make_tuple(5u, 4u, 4u), get_execute_counts(f.writeServiceObserver()));
    EXPECT_EQ(1u, f.metaStoreObserver()._removes_complete_cnt);
    ASSERT_FALSE(f.metaStoreObserver()._removes_complete_lids.empty());
    EXPECT_EQ(1u, f.metaStoreObserver()._removes_complete_lids.back());
}

TEST(FeedViewTest, require_that_handleDeleteBucket_removes_documents)
{
    SearchableFeedViewFixture f;
    DocumentContext::List docs;
    docs.push_back(f.doc("id:test:searchdocument:n=1:1", 10));
    docs.push_back(f.doc("id:test:searchdocument:n=1:2", 11));
    docs.push_back(f.doc("id:test:searchdocument:n=1:3", 12));
    docs.push_back(f.doc("id:test:searchdocument:n=2:1", 13));
    docs.push_back(f.doc("id:test:searchdocument:n=2:2", 14));

    f.putAndWait(docs);
    EXPECT_EQ((LastChange{docs.back().gid(), 5u, 5u}), f.get_last_change());
    EXPECT_EQ(1, f.get_notified_lid(docs[0].gid()));
    EXPECT_EQ(2, f.get_notified_lid(docs[1].gid()));
    EXPECT_EQ(3, f.get_notified_lid(docs[2].gid()));
    EXPECT_EQ(4, f.get_notified_lid(docs[3].gid()));
    EXPECT_EQ(5, f.get_notified_lid(docs[4].gid()));
    f.dms_commit();

    DocumentIdT lid;
    EXPECT_TRUE(f.getMetaStore().getLid(docs[0].doc->getId().getGlobalId(), lid));
    EXPECT_EQ(1u, lid);
    EXPECT_TRUE(f.getMetaStore().getLid(docs[1].doc->getId().getGlobalId(), lid));
    EXPECT_EQ(2u, lid);
    EXPECT_TRUE(f.getMetaStore().getLid(docs[2].doc->getId().getGlobalId(), lid));
    EXPECT_EQ(3u, lid);

    // delete bucket for user 1
    DeleteBucketOperation op(docs[0].bid);
    vespalib::Gate gate;
    f.runInMaster([&, onDone=std::make_shared<GateCallback>(gate)]() {
        f.performDeleteBucket(op, std::move(onDone));
    });
    gate.await();
    f.dms_commit();

    EXPECT_EQ(0u, f.getBucketDB()->get(docs[0].bid).getDocumentCount());
    EXPECT_EQ(2u, f.getBucketDB()->get(docs[3].bid).getDocumentCount());
    EXPECT_FALSE(f.getMetaStore().getLid(docs[0].doc->getId().getGlobalId(), lid));
    EXPECT_FALSE(f.getMetaStore().getLid(docs[1].doc->getId().getGlobalId(), lid));
    EXPECT_FALSE(f.getMetaStore().getLid(docs[2].doc->getId().getGlobalId(), lid));
    MyLidVector exp = MyLidVector().add(1).add(2).add(3);
    assertLidVector(exp, f.miw._removes);
    assertLidVector(exp, f.msa._removes);
    assertLidVector(exp, f.maw._removes);
    EXPECT_EQ(8, f.get_change_handler_count());
    EXPECT_EQ(0, f.get_notified_lid(docs[0].gid()));
    EXPECT_EQ(0, f.get_notified_lid(docs[1].gid()));
    EXPECT_EQ(0, f.get_notified_lid(docs[2].gid()));
    EXPECT_EQ(4, f.get_notified_lid(docs[3].gid()));
    EXPECT_EQ(5, f.get_notified_lid(docs[4].gid()));
}

void
assertPostConditionAfterRemoves(const DocumentContext::List &docs,
                                SearchableFeedViewFixture &f)
{
    EXPECT_EQ(3u, f.getMetaStore().getNumUsedLids());
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
    EXPECT_EQ(3u, sdocs.size());
    EXPECT_TRUE(sdocs.find(1) == sdocs.end());
    EXPECT_TRUE(sdocs.find(4) == sdocs.end());
}

TEST(FeedViewTest, require_that_removes_are_not_remembered)
{
    SearchableFeedViewFixture f;
    DocumentContext::List docs;
    docs.push_back(f.doc("id:test:searchdocument:n=1:1", 10));
    docs.push_back(f.doc("id:test:searchdocument:n=1:2", 11));
    docs.push_back(f.doc("id:test:searchdocument:n=1:3", 12));
    docs.push_back(f.doc("id:test:searchdocument:n=2:1", 13));
    docs.push_back(f.doc("id:test:searchdocument:n=2:2", 14));

    f.putAndWait(docs);
    f.forceCommitAndWait();
    f.removeAndWait(docs[0]);
    f.forceCommitAndWait();
    f.removeAndWait(docs[3]);
    f.forceCommitAndWait();
    assertPostConditionAfterRemoves(docs, f);

    // try to remove again : should have little effect
    f.removeAndWait(docs[0]);
    f.forceCommitAndWait();
    f.removeAndWait(docs[3]);
    f.forceCommitAndWait();
    assertPostConditionAfterRemoves(docs, f);

    // re-add docs
    f.putAndWait(docs[3]);
    f.forceCommitAndWait();
    f.putAndWait(docs[0]);
    f.forceCommitAndWait();
    EXPECT_EQ(5u, f.getMetaStore().getNumUsedLids());
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
    EXPECT_EQ(5u, f.msa._store._docs.size());
    const Document::SP &doc1 = f.msa._store._docs[1];
    EXPECT_EQ(docs[3].doc->getId(), doc1->getId());
    EXPECT_EQ(docs[3].doc->getId().toString(), doc1->getValue("s1")->toString());
    const Document::SP &doc4 = f.msa._store._docs[4];
    EXPECT_EQ(docs[0].doc->getId(), doc4->getId());
    EXPECT_EQ(docs[0].doc->getId().toString(), doc4->getValue("s1")->toString());
    EXPECT_EQ(5u, f.msa._store._docs.size());

    f.removeAndWait(docs[0]);
    f.forceCommitAndWait();
    f.removeAndWait(docs[3]);
    f.forceCommitAndWait();
    EXPECT_EQ(3u, f.msa._store._docs.size());
}

TEST(FeedViewTest, require_that_heartbeat_propagates_to_index_and_attribute_adapter)
{
    SearchableFeedViewFixture f;
    vespalib::Gate gate;
    f.runInMaster([&, onDone = std::make_shared<vespalib::GateCallback>(gate)]() {
        f.fv.heartBeat(2, std::move(onDone));
    });
    gate.await();
    EXPECT_EQ(1, f.miw._heartBeatCount);
    EXPECT_EQ(1, f.maw._heartBeatCount);
}

namespace {

template <typename Fixture>
void putDocumentAndUpdate(Fixture &f, const std::string &fieldName)
{
    DocumentContext dc1 = f.doc1();
    f.putAndWait(dc1);
    f.forceCommitAndWait();
    EXPECT_EQ(1u, f.msa._store._lastSyncToken);

    DocumentContext dc2("id:ns:searchdocument::1", 20, f.getBuilder());
    dc2.addFieldUpdate(f.getBuilder(), fieldName);
    f.updateAndWait(dc2);
    f.forceCommitAndWait();
}

template <typename Fixture>
void requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(Fixture &f,
                                                              const std::string &fieldName)
{
    putDocumentAndUpdate(f, fieldName);

    EXPECT_EQ(1u, f.msa._store._lastSyncToken); // document store not updated
    assertAttributeUpdate(2u, DocumentId("id:ns:searchdocument::1"), 1, f.maw);
}

template <typename Fixture>
void requireThatUpdateUpdatesAttributeAndDocumentStore(Fixture &f,
                                                       const std::string &fieldName)
{
    putDocumentAndUpdate(f, fieldName);

    EXPECT_EQ(2u, f.msa._store._lastSyncToken); // document store updated
    assertAttributeUpdate(2u, DocumentId("id:ns:searchdocument::1"), 1, f.maw);
}

}

TEST(FeedViewTest, require_that_update_to_fast_access_attribute_only_updates_attribute_and_not_document_store)
{
    FastAccessFeedViewFixture f;
    f.maw._attrs.insert("a1"); // mark a1 as fast-access attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f, "a1");
}

TEST(FeedViewTest, require_that_update_to_attribute_only_updates_attribute_and_not_document_store)
{
    SearchableFeedViewFixture f;
    f.maw._attrs.insert("a1"); // mark a1 as attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f, "a1");
}

TEST(FeedViewTest, require_that_update_to_non_fast_access_attribute_also_updates_document_store)
{
    FastAccessFeedViewFixture f;
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a1");
}

TEST(FeedViewTest, require_that_update_to_fast_access_predicate_attribute_updates_attribute_and_document_store)
{
    FastAccessFeedViewFixture f;
    f.maw._attrs.insert("a2"); // mark a2 as fast-access attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a2");
}

TEST(FeedViewTest, require_that_update_to_predicate_attribute_updates_attribute_and_document_store)
{
    SearchableFeedViewFixture f;
    f.maw._attrs.insert("a2"); // mark a2 as attribute field
    requireThatUpdateUpdatesAttributeAndDocumentStore(f, "a2");
}

TEST(FeedViewTest, require_that_update_to_fast_access_tensor_attribute_only_updates_attribute_and_not_document_store)
{
    FastAccessFeedViewFixture f;
    f.maw._attrs.insert("a3"); // mark a3 as fast-access attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f, "a3");
}

TEST(FeedViewTest, require_that_update_to_tensor_attribute_only_updates_attribute_and_not_document_store)
{
    SearchableFeedViewFixture f;
    f.maw._attrs.insert("a3"); // mark a3 as attribute field
    requireThatUpdateOnlyUpdatesAttributeAndNotDocumentStore(f, "a3");
}

TEST(FeedViewTest, require_that_compactLidSpace_propagates_to_document_meta_store_and_document_store_and_blocks_lid_space_shrinkage_until_generation_is_no_longer_used)
{
    SearchableFeedViewFixture f;
    f.populateBeforeCompactLidSpace();
    EXPECT_EQ(std::make_tuple(5u, 4u, 4u), get_execute_counts(f.writeServiceObserver()));
    f.compactLidSpaceAndWait(2);
    // performIndexForceCommit in index thread, then completion callback
    // in master thread.
    EXPECT_EQ(std::make_tuple(7u, 7u, 7u), get_execute_counts(f.writeServiceObserver()));
    EXPECT_EQ(2u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQ(2u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQ(1u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
    EXPECT_EQ(2u, f._docIdLimit.get());
}

TEST(FeedViewTest, require_that_compactLidSpace_doesnt_propagate_to_document_meta_store_and_document_store_and_blocks_lid_space_shrinkage_until_generation_is_no_longer_used)
{
    SearchableFeedViewFixture f;
    f.populateBeforeCompactLidSpace();
    EXPECT_EQ(std::make_tuple(5u, 4u, 4u), get_execute_counts(f.writeServiceObserver()));
    CompactLidSpaceOperation op(0, 2);
    op.setSerialNum(0);
    Gate gate;
    f.runInMaster([&, onDone=std::make_shared<GateCallback>(gate)]() {
        f.fv.handleCompactLidSpace(op, std::move(onDone));
    });
    gate.await();
    f._writeService.master().sync();
    // Delayed holdUnblockShrinkLidSpace() in index thread, then master thread
    EXPECT_EQ(std::make_tuple(6u, 6u, 5u), get_execute_counts(f.writeServiceObserver()));
    EXPECT_EQ(0u, f.metaStoreObserver()._compactLidSpaceLidLimit);
    EXPECT_EQ(0u, f.getDocumentStore()._compactLidSpaceLidLimit);
    EXPECT_EQ(0u, f.metaStoreObserver()._holdUnblockShrinkLidSpaceCnt);
}

TEST(FeedViewTest, require_that_compactLidSpace_propagates_to_attribute_adapter)
{
    FastAccessFeedViewFixture f;
    f.populateBeforeCompactLidSpace();
    f.compactLidSpaceAndWait(2);
    EXPECT_EQ(2u, f.maw._wantedLidLimit);
}

TEST(FeedViewTest, require_that_compactLidSpace_propagates_to_index_writer)
{
    SearchableFeedViewFixture f;
    f.populateBeforeCompactLidSpace();
    f.compactLidSpaceAndWait(2);
    EXPECT_EQ(2u, f.miw._wantedLidLimit);
}

TEST(FeedViewTest, require_that_commit_is_not_implicitly_called)
{
    SearchableFeedViewFixture f;
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQ(0u, f.miw._commitCount);
    EXPECT_EQ(0u, f.maw._commitCount);
    EXPECT_EQ(0u, f._docIdLimit.get());
    f.removeAndWait(dc);
    EXPECT_EQ(0u, f.miw._commitCount);
    EXPECT_EQ(0u, f.maw._commitCount);
    EXPECT_EQ(0u, f._docIdLimit.get());
    EXPECT_EQ("put(adapter=attribute,serialNum=1,lid=1),"
              "put(adapter=index,serialNum=1,lid=1),"
              "ack(Result(0, )),"
              "remove(adapter=attribute,serialNum=2,lid=1),"
              "remove(adapter=index,serialNum=2,lid=1),"
              "ack(Result(0, ))", f.get_trace());
    f.forceCommitAndWait();
}

TEST(FeedViewTest, require_that_forceCommit_updates_docid_limit)
{
    SearchableFeedViewFixture f;
    DocumentContext dc = f.doc1();
    f.putAndWait(dc);
    EXPECT_EQ(0u, f.miw._commitCount);
    EXPECT_EQ(0u, f.maw._commitCount);
    EXPECT_EQ(0u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQ(1u, f.miw._commitCount);
    EXPECT_EQ(1u, f.maw._commitCount);
    EXPECT_EQ(2u, f._docIdLimit.get());
    EXPECT_EQ("put(adapter=attribute,serialNum=1,lid=1),"
              "put(adapter=index,serialNum=1,lid=1),"
              "ack(Result(0, )),"
              "commit(adapter=attribute,serialNum=1),"
              "commit(adapter=index,serialNum=1)", f.get_trace());
}

TEST(FeedViewTest, require_that_forceCommit_updates_docid_limit_during_shrink)
{
    SearchableFeedViewFixture f;
    f.putAndWait(f.makeDummyDocs(0, 3, 1000));
    EXPECT_EQ(0u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQ(4u, f._docIdLimit.get());
    f.removeAndWait(f.makeDummyDocs(1, 2, 2000));
    EXPECT_EQ(4u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQ(4u, f._docIdLimit.get());
    f.compactLidSpaceAndWait(2);
    EXPECT_EQ(2u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQ(2u, f._docIdLimit.get());
    f.putAndWait(f.makeDummyDocs(1, 1, 3000));
    EXPECT_EQ(2u, f._docIdLimit.get());
    f.forceCommitAndWait();
    EXPECT_EQ(3u, f._docIdLimit.get());
}

TEST(FeedViewTest, require_that_move_notifies_gid_to_lid_change_handler)
{
    SearchableFeedViewFixture f;
    DocumentContext dc1 = f.doc("id::searchdocument::1", 10);
    DocumentContext dc2 = f.doc("id::searchdocument::2", 20);
    f.putAndWait(dc1);
    f.forceCommitAndWait();
    EXPECT_EQ((LastChange{dc1.gid(), 1u, 1u}), f.get_last_change());
    f.putAndWait(dc2);
    f.forceCommitAndWait();
    EXPECT_EQ((LastChange{dc2.gid(), 2u, 2u}), f.get_last_change());
    DocumentContext dc3 = f.doc("id::searchdocument::1", 30);
    f.removeAndWait(dc3);
    f.forceCommitAndWait();
    EXPECT_EQ((LastChange{dc3.gid(), 0u, 3u}), f.get_last_change());
    f.moveAndWait(dc2, 2, 1);
    f.forceCommitAndWait();
    EXPECT_EQ((LastChange{dc2.gid(), 1u, 4u}), f.get_last_change());
}
