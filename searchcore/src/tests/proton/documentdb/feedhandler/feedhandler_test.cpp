// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/result.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/searchcore/proton/feedoperation/wipehistoryoperation.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/server/configstore.h>
#include <vespa/searchcore/proton/server/ddbstate.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/i_feed_handler_owner.h>
#include <vespa/searchcore/proton/server/ireplayconfig.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP("feedhandler_test");

using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::GlobalId;
using search::IDestructorCallback;
using search::SerialNum;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespalib::makeLambdaTask;
using search::transactionlog::TransLogServer;
using storage::spi::PartitionId;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::BlockingThreadStackExecutor;
using vespalib::ThreadStackExecutor;
using vespalib::ThreadStackExecutorBase;
using vespalib::makeClosure;
using vespalib::makeTask;

using namespace proton;
using namespace search::index;

typedef std::unique_ptr<vespalib::CountDownLatch> CountDownLatchUP;

namespace {

struct Rendezvous {
    vespalib::Gate enter;
    vespalib::Gate leave;
    vespalib::Gate gone;
    typedef std::unique_ptr<Rendezvous> UP;
    Rendezvous() : enter(), leave(), gone() {}
    bool run(uint32_t timeout = 80000) {
        enter.countDown();
        bool retval = leave.await(timeout);
        gone.countDown();
        return retval;
    }
    bool waitForEnter(uint32_t timeout = 80000) {
        return enter.await(timeout);
    }
    bool leaveAndWait(uint32_t timeout = 80000) {
        leave.countDown();
        return gone.await(timeout);
    }
    bool await(uint32_t timeout = 80000) {
        if (waitForEnter(timeout)) {
            return leaveAndWait(timeout);
        }
        return false;
    }
};


struct MyOwner : public IFeedHandlerOwner
{
    bool _allowPrune;
    
    MyOwner()
        :
        _allowPrune(false)
    {
    }
    virtual void onTransactionLogReplayDone() override {
        LOG(info, "MyOwner::onTransactionLogReplayDone()");
    }
    virtual void enterRedoReprocessState() override {}
    virtual void onPerformPrune(SerialNum) override {}

    virtual bool
    getAllowPrune() const override
    {
        return _allowPrune;
    }
};


struct MyResourceWriteFilter : public IResourceWriteFilter
{
    bool _acceptWriteOperation;
    vespalib::string _message;
    MyResourceWriteFilter()
        : _acceptWriteOperation(true),
          _message()
    {}

    virtual bool acceptWriteOperation() const override { return _acceptWriteOperation; }
    virtual State getAcceptState() const override {
        return IResourceWriteFilter::State(acceptWriteOperation(), _message);
    }
};


struct MyReplayConfig : public IReplayConfig {
    virtual void replayConfig(SerialNum) override {}
};

struct MyDocumentMetaStore {
    struct Entry {
        DbDocumentId _id;
        DbDocumentId _prevId;
        Timestamp    _prevTimestamp;
        Entry() : _id(0, 0), _prevId(0, 0), _prevTimestamp(0) {}
        Entry(uint32_t lid, uint32_t prevLid, Timestamp prevTimestamp)
            : _id(0, lid),
              _prevId(0, prevLid),
              _prevTimestamp(prevTimestamp)
        {}
    };
    std::map<GlobalId, Entry> _pool;
    std::map<GlobalId, Entry> _allocated;
    MyDocumentMetaStore() : _pool(), _allocated() {}
    MyDocumentMetaStore &insert(const GlobalId &gid, const Entry &e) {
        _pool[gid] = e;
        return *this;
    }
    MyDocumentMetaStore &allocate(const GlobalId &gid) {
        auto itr = _pool.find(gid);
        if (itr != _pool.end()) {
            _allocated[gid] = itr->second;
        }
        return *this;
    }
    const Entry *get(const GlobalId &gid) const {
        auto itr = _allocated.find(gid);
        if (itr != _allocated.end()) {
            return &itr->second;
        }
        return NULL;
    }
};

struct MyFeedView : public test::DummyFeedView {
    Rendezvous putRdz;
    bool usePutRdz;
    CountDownLatchUP putLatch;
    MyDocumentMetaStore metaStore;
    int put_count;
    SerialNum put_serial;
    int heartbeat_count;
    int remove_count;
    int move_count;
    int prune_removed_count;
    int update_count;
    SerialNum update_serial;
    MyFeedView(const DocumentTypeRepo::SP &dtr);
    ~MyFeedView() override;
    void resetPutLatch(uint32_t count) { putLatch.reset(new vespalib::CountDownLatch(count)); }
    void preparePut(PutOperation &op) override {
        prepareDocumentOperation(op, op.getDocument()->getId().getGlobalId());
    }
    void prepareDocumentOperation(DocumentOperation &op, const GlobalId &gid) {
        const MyDocumentMetaStore::Entry *entry = metaStore.get(gid);
        if (entry != NULL) {
            op.setDbDocumentId(entry->_id);
            op.setPrevDbDocumentId(entry->_prevId);
            op.setPrevTimestamp(entry->_prevTimestamp);
        }
    }
    void handlePut(FeedToken token, const PutOperation &putOp) override {
        (void) token;
        LOG(info, "MyFeedView::handlePut(): docId(%s), putCount(%u), putLatchCount(%u)",
            putOp.getDocument()->getId().toString().c_str(), put_count,
            (putLatch.get() != NULL ? putLatch->getCount() : 0u));
        if (usePutRdz) {
            putRdz.run();
        }
        ++put_count;
        put_serial = putOp.getSerialNum();
        metaStore.allocate(putOp.getDocument()->getId().getGlobalId());
        if (putLatch.get() != NULL) {
            putLatch->countDown();
        }
    }
    void prepareUpdate(UpdateOperation &op) override {
        prepareDocumentOperation(op, op.getUpdate()->getId().getGlobalId());
    }
    void handleUpdate(FeedToken token, const UpdateOperation &op) override {
        (void) token;

        ++update_count;
        update_serial = op.getSerialNum();
    }
    void handleRemove(FeedToken token, const RemoveOperation &) override {
        (void) token;
        ++remove_count;
    }
    void handleMove(const MoveOperation &, IDestructorCallback::SP) override { ++move_count; }
    void heartBeat(SerialNum) override { ++heartbeat_count; }
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &) override { ++prune_removed_count; }
    const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override {
        return NULL;
    }
};

MyFeedView::MyFeedView(const DocumentTypeRepo::SP &dtr)
    : test::DummyFeedView(dtr),
      putRdz(),
      usePutRdz(false),
      putLatch(),
      metaStore(),
      put_count(0),
      put_serial(0),
      heartbeat_count(0),
      remove_count(0),
      move_count(0),
      prune_removed_count(0),
      update_count(0),
      update_serial(0)
{}
MyFeedView::~MyFeedView() {}


struct SchemaContext {
    Schema::SP                schema;
    std::unique_ptr<DocBuilder> builder;
    SchemaContext() :
        schema(new Schema()),
        builder()
    {
        schema->addIndexField(Schema::IndexField("i1", DataType::STRING, CollectionType::SINGLE));
        builder.reset(new DocBuilder(*schema));
    }
    DocTypeName getDocType() const {
        return DocTypeName(builder->getDocumentType().getName());
    }
    const document::DocumentTypeRepo::SP &getRepo() const { return builder->getDocumentTypeRepo(); }
};


struct DocumentContext {
    Document::SP  doc;
    BucketId      bucketId;
    DocumentContext(const vespalib::string &docId, DocBuilder &builder) :
        doc(builder.startDocument(docId).endDocument().release()),
        bucketId(BucketFactory::getBucketId(doc->getId()))
    {
    }
};


struct UpdateContext {
    DocumentUpdate::SP update;
    BucketId           bucketId;
    UpdateContext(const vespalib::string &docId, DocBuilder &builder) :
        update(new DocumentUpdate(builder.getDocumentType(), DocumentId(docId))),
        bucketId(BucketFactory::getBucketId(update->getId()))
    {
    }
};


struct MyTransport : public feedtoken::ITransport {
    vespalib::Gate gate;
    ResultUP result;
    bool documentWasFound;
    MyTransport();
    ~MyTransport();
    void send(ResultUP res, bool documentWasFound_) override {
        result = std::move(res);
        documentWasFound = documentWasFound_;
        gate.countDown();
    }
};

MyTransport::MyTransport() : gate(), result(), documentWasFound(false) {}
MyTransport::~MyTransport() = default;

struct FeedTokenContext {
    MyTransport transport;
    FeedToken token;

    FeedTokenContext();
    ~FeedTokenContext();
    bool await(uint32_t timeout = 80000) { return transport.gate.await(timeout); }
    const Result *getResult() {
        if (transport.result.get()) {
            return transport.result.get();
        }
        return &token->getResult();
    }
};

FeedTokenContext::FeedTokenContext()
    : transport(),
      token(feedtoken::make(transport))
{}

FeedTokenContext::~FeedTokenContext() = default;

struct PutContext {
    FeedTokenContext tokenCtx;
    DocumentContext  docCtx;
    typedef std::shared_ptr<PutContext> SP;
    PutContext(const vespalib::string &docId, DocBuilder &builder) :
        tokenCtx(),
        docCtx(docId, builder)
    {}
};


struct PutHandler {
    FeedHandler &handler;
    DocBuilder &builder;
    Timestamp timestamp;
    std::vector<PutContext::SP> puts;
    PutHandler(FeedHandler &fh, DocBuilder &db) :
        handler(fh),
        builder(db),
        timestamp(0),
        puts()
    {}
    void put(const vespalib::string &docId) {
        PutContext::SP pc(new PutContext(docId, builder));
        FeedOperation::UP op(new PutOperation(pc->docCtx.bucketId, timestamp, pc->docCtx.doc));
        handler.handleOperation(pc->tokenCtx.token, std::move(op));
        timestamp = Timestamp(timestamp + 1);
        puts.push_back(pc);
    }
    bool await(uint32_t timeout = 80000) {
        for (size_t i = 0; i < puts.size(); ++i) {
            if (!puts[i]->tokenCtx.await(timeout)) {
                return false;
            }
        }
        return true;
    }
};


struct MyTlsWriter : TlsWriter {
    int store_count;
    int erase_count;
    bool erase_return;

    MyTlsWriter() : store_count(0), erase_count(0), erase_return(true) {}
    void storeOperation(const FeedOperation &, DoneCallback) override { ++store_count; }
    bool erase(SerialNum) override { ++erase_count; return erase_return; }

    SerialNum sync(SerialNum syncTo) override {
        return syncTo;
    } 
};

struct FeedHandlerFixture
{
    DummyFileHeaderContext       _fileHeaderContext;
    TransLogServer               tls;
    vespalib::string             tlsSpec;
    ExecutorThreadingService     writeService;
    SchemaContext                schema;
    MyOwner                      owner;
    MyResourceWriteFilter        writeFilter;
    DDBState                     _state;
    MyReplayConfig               replayConfig;
    MyFeedView                   feedView;
    MyTlsWriter                  tls_writer;
    BucketDBOwner                _bucketDB;
    bucketdb::BucketDBHandler    _bucketDBHandler;
    FeedHandler                  handler;
    FeedHandlerFixture()
        : _fileHeaderContext(),
          tls("mytls", 9016, "mytlsdir", _fileHeaderContext, 0x10000),
          tlsSpec("tcp/localhost:9016"),
          writeService(),
          schema(),
          owner(),
          _state(),
          replayConfig(),
          feedView(schema.getRepo()),
          _bucketDB(),
          _bucketDBHandler(_bucketDB),
          handler(writeService, tlsSpec, schema.getDocType(), _state, owner,
                  writeFilter, replayConfig, tls, &tls_writer)
    {
        _state.enterLoadState();
        _state.enterReplayTransactionLogState();
        handler.setActiveFeedView(&feedView);
        handler.setBucketDBHandler(&_bucketDBHandler);
        handler.init(1);
    }

    ~FeedHandlerFixture() {
        writeService.sync();
    }
    template <class FunctionType>
    inline void runAsMaster(FunctionType &&function) {
        writeService.master().execute(makeLambdaTask(std::move(function)));
        writeService.master().sync();
    }
    void syncMaster() {
        writeService.master().sync();
    }
};


struct MyConfigStore : ConfigStore {
    virtual SerialNum getBestSerialNum() const override { return 1; }
    virtual SerialNum getOldestSerialNum() const override { return 1; }
    virtual void saveConfig(const DocumentDBConfig &, SerialNum) override {}
    virtual void loadConfig(const DocumentDBConfig &, SerialNum,
                            DocumentDBConfig::SP &) override {}
    virtual void removeInvalid() override {}
    void prune(SerialNum) override {}
    virtual bool hasValidSerial(SerialNum) const override { return true; }
    virtual SerialNum getPrevValidSerial(SerialNum) const override { return 1; }
    virtual void serializeConfig(SerialNum, vespalib::nbostream &) override {}
    virtual void deserializeConfig(SerialNum, vespalib::nbostream &) override {}
    virtual void setProtonConfig(const ProtonConfigSP &) override { }
};


struct ReplayTransactionLogContext {
    IIndexWriter::SP iwriter;
    MyConfigStore config_store;
    DocumentDBConfig::SP cfgSnap;
};


TEST_F("require that heartBeat calls FeedView's heartBeat",
       FeedHandlerFixture)
{
    f.runAsMaster([&]() { f.handler.heartBeat(); });
    EXPECT_EQUAL(1, f.feedView.heartbeat_count);
}

TEST_F("require that outdated remove is ignored", FeedHandlerFixture)
{
    DocumentContext doc_context("doc:test:foo", *f.schema.builder);
    FeedOperation::UP op(new RemoveOperation(doc_context.bucketId, Timestamp(10), doc_context.doc->getId()));
    static_cast<DocumentOperation &>(*op).setPrevDbDocumentId(DbDocumentId(4));
    static_cast<DocumentOperation &>(*op).setPrevTimestamp(Timestamp(10000));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.remove_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

TEST_F("require that outdated put is ignored", FeedHandlerFixture)
{
    DocumentContext doc_context("doc:test:foo", *f.schema.builder);
    FeedOperation::UP op(new PutOperation(doc_context.bucketId,
                                          Timestamp(10), doc_context.doc));
    static_cast<DocumentOperation &>(*op).setPrevTimestamp(Timestamp(10000));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

void
addLidToRemove(RemoveDocumentsOperation &op)
{
    LidVectorContext::SP lids(new LidVectorContext(42));
    lids->addLid(4);
    op.setLidsToRemove(0, lids);
}


TEST_F("require that handleMove calls FeedView", FeedHandlerFixture)
{
    DocumentContext doc_context("doc:test:foo", *f.schema.builder);
    MoveOperation op(doc_context.bucketId, Timestamp(2), doc_context.doc, DbDocumentId(0, 2), 1);
    op.setDbDocumentId(DbDocumentId(1, 2));
    f.runAsMaster([&]() { f.handler.handleMove(op, IDestructorCallback::SP()); });
    EXPECT_EQUAL(1, f.feedView.move_count);
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that performPruneRemovedDocuments calls FeedView",
       FeedHandlerFixture)
{
    PruneRemovedDocumentsOperation op;
    f.handler.performPruneRemovedDocuments(op);
    EXPECT_EQUAL(0, f.feedView.prune_removed_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);

    addLidToRemove(op);
    f.handler.performPruneRemovedDocuments(op);
    EXPECT_EQUAL(1, f.feedView.prune_removed_count);
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that failed prune throws", FeedHandlerFixture)
{
    f.tls_writer.erase_return = false;
    EXPECT_EXCEPTION(f.handler.tlsPrune(10), vespalib::IllegalStateException,
                     "Failed to prune TLS to token 10.");
}

TEST_F("require that flush done calls prune", FeedHandlerFixture)
{
    f.handler.changeToNormalFeedState();
    f.owner._allowPrune = true;
    f.handler.flushDone(10);
    f.syncMaster();
    EXPECT_EQUAL(1, f.tls_writer.erase_count);
    EXPECT_EQUAL(10u, f.handler.getPrunedSerialNum());
}

TEST_F("require that flush in init state delays pruning", FeedHandlerFixture)
{
    f.handler.flushDone(10);
    f.syncMaster();
    EXPECT_EQUAL(0, f.tls_writer.erase_count);
    EXPECT_EQUAL(10u, f.handler.getPrunedSerialNum());
}

TEST_F("require that flush cannot unprune", FeedHandlerFixture)
{
    f.handler.flushDone(10);
    f.syncMaster();
    EXPECT_EQUAL(10u, f.handler.getPrunedSerialNum());

    f.handler.flushDone(5);  // Try to unprune.
    f.syncMaster();
    EXPECT_EQUAL(10u, f.handler.getPrunedSerialNum());
}

TEST_F("require that remove of unknown document with known data type stores remove", FeedHandlerFixture)
{
    DocumentContext doc_context("id:test:searchdocument::foo", *f.schema.builder);
    FeedOperation::UP op(new RemoveOperation(doc_context.bucketId, Timestamp(10), doc_context.doc->getId()));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(1, f.feedView.remove_count);
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that partial update for non-existing document is tagged as such", FeedHandlerFixture)
{
    UpdateContext upCtx("id:test:searchdocument::foo", *f.schema.builder);
    FeedOperation::UP op(new UpdateOperation(upCtx.bucketId, Timestamp(10), upCtx.update));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    const UpdateResult *result = static_cast<const UpdateResult *>(token_context.getResult());

    EXPECT_FALSE(token_context.transport.documentWasFound);
    EXPECT_EQUAL(0u, result->getExistingTimestamp());
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(0, f.feedView.update_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

TEST_F("require that partial update for non-existing document is created if specified", FeedHandlerFixture)
{
    f.handler.setSerialNum(15);
    UpdateContext upCtx("id:test:searchdocument::foo", *f.schema.builder);
    upCtx.update->setCreateIfNonExistent(true);
    f.feedView.metaStore.insert(upCtx.update->getId().getGlobalId(), MyDocumentMetaStore::Entry(5, 5, Timestamp(10)));
    FeedOperation::UP op(new UpdateOperation(upCtx.bucketId, Timestamp(10), upCtx.update));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    const UpdateResult *result = static_cast<const UpdateResult *>(token_context.getResult());

    EXPECT_TRUE(token_context.transport.documentWasFound);
    EXPECT_EQUAL(10u, result->getExistingTimestamp());
    EXPECT_EQUAL(1, f.feedView.put_count);
    EXPECT_EQUAL(16u, f.feedView.put_serial);
    EXPECT_EQUAL(0, f.feedView.update_count);
    EXPECT_EQUAL(0u, f.feedView.update_serial);
    EXPECT_EQUAL(1u, f.feedView.metaStore._allocated.size());
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that put is rejected if resource limit is reached", FeedHandlerFixture)
{
    f.writeFilter._acceptWriteOperation = false;
    f.writeFilter._message = "Attribute resource limit reached";

    DocumentContext docCtx("id:test:searchdocument::foo", *f.schema.builder);
    FeedOperation::UP op = std::make_unique<PutOperation>(docCtx.bucketId, Timestamp(10), docCtx.doc);
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(Result::RESOURCE_EXHAUSTED, token.getResult()->getErrorCode());
    EXPECT_EQUAL("Put operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Attribute resource limit reached'",
                 token.getResult()->getErrorMessage());
}

TEST_F("require that update is rejected if resource limit is reached", FeedHandlerFixture)
{
    f.writeFilter._acceptWriteOperation = false;
    f.writeFilter._message = "Attribute resource limit reached";

    UpdateContext updCtx("id:test:searchdocument::foo", *f.schema.builder);
    FeedOperation::UP op = std::make_unique<UpdateOperation>(updCtx.bucketId, Timestamp(10), updCtx.update);
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.update_count);
    EXPECT_TRUE(dynamic_cast<const UpdateResult *>(token.getResult()));
    EXPECT_EQUAL(Result::RESOURCE_EXHAUSTED, token.getResult()->getErrorCode());
    EXPECT_EQUAL("Update operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Attribute resource limit reached'",
                 token.getResult()->getErrorMessage());
}

TEST_F("require that remove is NOT rejected if resource limit is reached", FeedHandlerFixture)
{
    f.writeFilter._acceptWriteOperation = false;
    f.writeFilter._message = "Attribute resource limit reached";

    DocumentContext docCtx("id:test:searchdocument::foo", *f.schema.builder);
    FeedOperation::UP op = std::make_unique<RemoveOperation>(docCtx.bucketId, Timestamp(10), docCtx.doc->getId());
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(1, f.feedView.remove_count);
    EXPECT_EQUAL(Result::NONE, token.getResult()->getErrorCode());
    EXPECT_EQUAL("", token.getResult()->getErrorMessage());
}

}  // namespace

TEST_MAIN()
{
    DummyFileHeaderContext::setCreator("feedhandler_test");
    TEST_RUN_ALL();
}
