// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/result.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/removefieldpathupdate.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/server/configstore.h>
#include <vespa/document/util/feed_reject_helper.h>
#include <vespa/searchcore/proton/server/ddbstate.h>
#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/i_feed_handler_owner.h>
#include <vespa/searchcore/proton/server/ireplayconfig.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("feedhandler_test");

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::GlobalId;
using document::TensorDataType;
using document::TensorFieldValue;
using vespalib::IDestructorCallback;
using search::SerialNum;
using vespalib::makeLambdaTask;
using search::test::DocBuilder;
using search::transactionlog::TransLogServer;
using search::transactionlog::DomainConfig;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;
using vespalib::ThreadStackExecutor;
using vespalib::ThreadStackExecutorBase;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

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
    bool run(vespalib::duration timeout = 80s) {
        enter.countDown();
        bool retval = leave.await(timeout);
        gone.countDown();
        return retval;
    }
    bool waitForEnter(vespalib::duration timeout = 80s) {
        return enter.await(timeout);
    }
    bool leaveAndWait(vespalib::duration timeout = 80s) {
        leave.countDown();
        return gone.await(timeout);
    }
    bool await(vespalib::duration timeout = 80s) {
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
    void onTransactionLogReplayDone() override {
        LOG(info, "MyOwner::onTransactionLogReplayDone()");
    }
    void enterRedoReprocessState() override {}
    void onPerformPrune(SerialNum) override {}

    bool getAllowPrune() const override { return _allowPrune; }
};


struct MyResourceWriteFilter : public IResourceWriteFilter
{
    bool _acceptWriteOperation;
    vespalib::string _message;
    MyResourceWriteFilter()
        : _acceptWriteOperation(true),
          _message()
    {}

    bool acceptWriteOperation() const override { return _acceptWriteOperation; }
    State getAcceptState() const override {
        return IResourceWriteFilter::State(acceptWriteOperation(), _message);
    }
};


struct MyReplayConfig : public IReplayConfig {
    void replayConfig(SerialNum) override {}
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
        return nullptr;
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
    const DocumentType *documentType;
    MyFeedView(const std::shared_ptr<const DocumentTypeRepo> &dtr,
               const DocTypeName &docTypeName);
    ~MyFeedView() override;
    void preparePut(PutOperation &op) override {
        prepareDocumentOperation(op, op.getDocument()->getId().getGlobalId());
    }
    void prepareDocumentOperation(DocumentOperation &op, const GlobalId &gid) const {
        const MyDocumentMetaStore::Entry *entry = metaStore.get(gid);
        if (entry != nullptr) {
            op.setDbDocumentId(entry->_id);
            op.setPrevDbDocumentId(entry->_prevId);
            op.setPrevTimestamp(entry->_prevTimestamp);
        }
    }
    void handlePut(FeedToken token, const PutOperation &putOp) override {
        (void) token;
        LOG(info, "MyFeedView::handlePut(): docId(%s), putCount(%u), putLatchCount(%u)",
            putOp.getDocument()->getId().toString().c_str(), put_count,
            (putLatch ? putLatch->getCount() : 0u));
        if (usePutRdz) {
            putRdz.run();
        }
        EXPECT_EQUAL(_docTypeRepo.get(), putOp.getDocument()->getRepo());
        EXPECT_EQUAL(documentType, &putOp.getDocument()->getType());
        ++put_count;
        put_serial = putOp.getSerialNum();
        metaStore.allocate(putOp.getDocument()->getId().getGlobalId());
        if (putLatch) {
            putLatch->countDown();
        }
    }
    void prepareUpdate(UpdateOperation &op) override {
        prepareDocumentOperation(op, op.getUpdate()->getId().getGlobalId());
    }
    void handleUpdate(FeedToken token, const UpdateOperation &op) override {
        (void) token;

        EXPECT_EQUAL(documentType, &op.getUpdate()->getType());
        ++update_count;
        update_serial = op.getSerialNum();
    }
    void handleRemove(FeedToken token, const RemoveOperation &) override {
        (void) token;
        ++remove_count;
    }
    void handleMove(const MoveOperation &, DoneCallback) override { ++move_count; }
    void heartBeat(SerialNum, DoneCallback) override { ++heartbeat_count; }
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &, DoneCallback) override { ++prune_removed_count; }
    const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override {
        return nullptr;
    }
    void checkCounts(int exp_update_count, SerialNum exp_update_serial, int exp_put_count, SerialNum exp_put_serial) const {
        EXPECT_EQUAL(exp_update_count, update_count);
        EXPECT_EQUAL(exp_update_serial, update_serial);
        EXPECT_EQUAL(exp_put_count, put_count);
        EXPECT_EQUAL(exp_put_serial, put_serial);
    }
};

MyFeedView::MyFeedView(const std::shared_ptr<const DocumentTypeRepo> &dtr, const DocTypeName &docTypeName)
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
      update_serial(0),
      documentType(dtr->getDocumentType(docTypeName.getName()))
{}
MyFeedView::~MyFeedView() = default;


struct SchemaContext {
    DocBuilder builder;
    SchemaContext();
    SchemaContext(bool has_i2);
    ~SchemaContext();
    DocTypeName getDocType() const {
        return DocTypeName(builder.get_document_type().getName());
    }
    std::shared_ptr<const document::DocumentTypeRepo> getRepo() const { return builder.get_repo_sp(); }
};

SchemaContext::SchemaContext()
    : SchemaContext(false)
{
}

SchemaContext::SchemaContext(bool has_i2)
    : builder([has_i2](auto& header) {
                  header.addTensorField("tensor", "tensor(x{},y{})")
                      .addTensorField("tensor2", "tensor(x{},y{})")
                      .addField("i1", DataType::T_STRING);
                  if (has_i2) {
                      header.addField("i2", DataType::T_STRING);
                  }
              })
{
}

SchemaContext::~SchemaContext() = default;

struct DocumentContext {
    Document::SP  doc;
    BucketId      bucketId;
    DocumentContext(const vespalib::string &docId, DocBuilder &builder) :
        doc(builder.make_document(docId)),
        bucketId(BucketFactory::getBucketId(doc->getId()))
    {
    }
};

struct TwoFieldsSchemaContext : public SchemaContext {
    TwoFieldsSchemaContext()
        : SchemaContext(true)
    {
    }
};

TensorDataType tensor1DType(ValueType::from_spec("tensor(x{})"));

struct UpdateContext {
    DocumentUpdate::SP update;
    BucketId           bucketId;
    UpdateContext(const vespalib::string &docId, DocBuilder &builder) :
        update(std::make_shared<DocumentUpdate>(builder.get_repo(), builder.get_document_type(), DocumentId(docId))),
        bucketId(BucketFactory::getBucketId(update->getId()))
    {
    }
    void addFieldUpdate(const vespalib::string &fieldName) {
        const auto &docType = update->getType();
        const auto &field = docType.getField(fieldName);
        auto fieldValue = field.createValue();
        if (fieldName == "tensor") {
            dynamic_cast<TensorFieldValue &>(*fieldValue) =
                SimpleValue::from_spec(TensorSpec("tensor(x{},y{})").
                                       add({{"x","8"},{"y","9"}}, 11));
        } else if (fieldName == "tensor2") {
            auto tensorFieldValue = std::make_unique<TensorFieldValue>(tensor1DType);
            *tensorFieldValue =
                SimpleValue::from_spec(TensorSpec("tensor(x{})").
                                       add({{"x","8"}}, 11));
            fieldValue = std::move(tensorFieldValue);
        } else {
            fieldValue->assign(document::StringFieldValue("new value"));
        }
        update->addUpdate(document::FieldUpdate(field).addUpdate(std::make_unique<document::AssignValueUpdate>(std::move(fieldValue))));
    }
};


struct MyTransport : public feedtoken::ITransport {
    vespalib::Gate gate;
    ResultUP result;
    bool documentWasFound;
    MyTransport();
    ~MyTransport() override;
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
    bool await(vespalib::duration timeout = 80s) { return transport.gate.await(timeout); }
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

struct MyTlsWriter : TlsWriter {
    int store_count;
    int erase_count;
    bool erase_return;

    MyTlsWriter() : store_count(0), erase_count(0), erase_return(true) {}
    void appendOperation(const FeedOperation &, DoneCallback) override { ++store_count; }
    CommitResult startCommit(DoneCallback) override { return CommitResult(); }
    bool erase(SerialNum) override { ++erase_count; return erase_return; }

    SerialNum sync(SerialNum syncTo) override {
        return syncTo;
    }
};

struct FeedHandlerFixture
{
    DummyFileHeaderContext       _fileHeaderContext;
    TransportAndExecutorService  _service;
    TransLogServer               tls;
    vespalib::string             tlsSpec;
    SchemaContext                schema;
    MyOwner                      owner;
    MyResourceWriteFilter        writeFilter;
    DDBState                     _state;
    MyReplayConfig               replayConfig;
    MyFeedView                   feedView;
    MyTlsWriter                  tls_writer;
    bucketdb::BucketDBOwner      _bucketDB;
    bucketdb::BucketDBHandler    _bucketDBHandler;
    FeedHandler                  handler;
    FeedHandlerFixture()
        : _fileHeaderContext(),
          _service(1),
          tls(_service.transport(), "mytls", 9016, "mytlsdir", _fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000)),
          tlsSpec("tcp/localhost:9016"),
          schema(),
          owner(),
          _state(),
          replayConfig(),
          feedView(schema.getRepo(), schema.getDocType()),
          _bucketDB(),
          _bucketDBHandler(_bucketDB),
          handler(_service.write(), tlsSpec, schema.getDocType(), owner,
                  writeFilter, replayConfig, tls, &tls_writer)
    {
        _state.enterLoadState();
        _state.enterReplayTransactionLogState();
        handler.setActiveFeedView(&feedView);
        handler.setBucketDBHandler(&_bucketDBHandler);
        handler.init(1);
    }

    ~FeedHandlerFixture() {
        _service.shutdown();
    }
    template <class FunctionType>
    inline void runAsMaster(FunctionType &&function) {
        _service.write().master().execute(makeLambdaTask(std::move(function)));
        syncMaster();
    }
    void syncMaster() {
        _service.write().master().sync();
    }
};

TEST_F("require that heartBeat calls FeedView's heartBeat",
       FeedHandlerFixture)
{
    f.runAsMaster([&]() { f.handler.heartBeat(); });
    EXPECT_EQUAL(1, f.feedView.heartbeat_count);
}

TEST_F("require that outdated remove is ignored", FeedHandlerFixture)
{
    DocumentContext doc_context("id:ns:searchdocument::foo", f.schema.builder);
    auto op = std::make_unique<RemoveOperationWithDocId>(doc_context.bucketId, Timestamp(10), doc_context.doc->getId());
    static_cast<DocumentOperation &>(*op).setPrevDbDocumentId(DbDocumentId(4));
    static_cast<DocumentOperation &>(*op).setPrevTimestamp(Timestamp(10000));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.remove_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

TEST_F("require that outdated put is ignored", FeedHandlerFixture)
{
    DocumentContext doc_context("id:ns:searchdocument::foo", f.schema.builder);
    auto op =std::make_unique<PutOperation>(doc_context.bucketId, Timestamp(10), std::move(doc_context.doc));
    static_cast<DocumentOperation &>(*op).setPrevTimestamp(Timestamp(10000));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

void
addLidToRemove(RemoveDocumentsOperation &op)
{
    auto lids = std::make_shared<LidVectorContext>(42);
    lids->addLid(4);
    op.setLidsToRemove(0, lids);
}


TEST_F("require that handleMove calls FeedView", FeedHandlerFixture)
{
    DocumentContext doc_context("id:ns:searchdocument::foo", f.schema.builder);
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
    DocumentContext doc_context("id:test:searchdocument::foo", f.schema.builder);
    auto op = std::make_unique<RemoveOperationWithDocId>(doc_context.bucketId, Timestamp(10), doc_context.doc->getId());
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(1, f.feedView.remove_count);
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that partial update for non-existing document is tagged as such", FeedHandlerFixture)
{
    UpdateContext upCtx("id:test:searchdocument::foo", f.schema.builder);
    auto  op = std::make_unique<UpdateOperation>(upCtx.bucketId, Timestamp(10), upCtx.update);
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    const auto *result = dynamic_cast<const UpdateResult *>(token_context.getResult());

    EXPECT_FALSE(token_context.transport.documentWasFound);
    EXPECT_EQUAL(0u, result->getExistingTimestamp());
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(0, f.feedView.update_count);
    EXPECT_EQUAL(0, f.tls_writer.store_count);
}

TEST_F("require that partial update for non-existing document is created if specified", FeedHandlerFixture)
{
    f.handler.setSerialNum(15);
    UpdateContext upCtx("id:test:searchdocument::foo", f.schema.builder);
    upCtx.update->setCreateIfNonExistent(true);
    f.feedView.metaStore.insert(upCtx.update->getId().getGlobalId(), MyDocumentMetaStore::Entry(5, 5, Timestamp(10)));
    auto op = std::make_unique<UpdateOperation>(upCtx.bucketId, Timestamp(10), upCtx.update);
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    const auto * result = dynamic_cast<const UpdateResult *>(token_context.getResult());

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

    DocumentContext docCtx("id:test:searchdocument::foo", f.schema.builder);
    auto op = std::make_unique<PutOperation>(docCtx.bucketId, Timestamp(10), std::move(docCtx.doc));
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.put_count);
    EXPECT_EQUAL(Result::ErrorType::RESOURCE_EXHAUSTED, token.getResult()->getErrorCode());
    EXPECT_EQUAL("Put operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Attribute resource limit reached'",
                 token.getResult()->getErrorMessage());
}

TEST_F("require that update is rejected if resource limit is reached", FeedHandlerFixture)
{
    f.writeFilter._acceptWriteOperation = false;
    f.writeFilter._message = "Attribute resource limit reached";

    UpdateContext updCtx("id:test:searchdocument::foo", f.schema.builder);
    updCtx.addFieldUpdate("tensor");
    auto op = std::make_unique<UpdateOperation>(updCtx.bucketId, Timestamp(10), updCtx.update);
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(0, f.feedView.update_count);
    EXPECT_TRUE(dynamic_cast<const UpdateResult *>(token.getResult()));
    EXPECT_EQUAL(Result::ErrorType::RESOURCE_EXHAUSTED, token.getResult()->getErrorCode());
    EXPECT_EQUAL("Update operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Attribute resource limit reached'",
                 token.getResult()->getErrorMessage());
}

TEST_F("require that remove is NOT rejected if resource limit is reached", FeedHandlerFixture)
{
    f.writeFilter._acceptWriteOperation = false;
    f.writeFilter._message = "Attribute resource limit reached";

    DocumentContext docCtx("id:test:searchdocument::foo", f.schema.builder);
    auto op = std::make_unique<RemoveOperationWithDocId>(docCtx.bucketId, Timestamp(10), docCtx.doc->getId());
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_EQUAL(1, f.feedView.remove_count);
    EXPECT_EQUAL(Result::ErrorType::NONE, token.getResult()->getErrorCode());
    EXPECT_EQUAL("", token.getResult()->getErrorMessage());
}

void
checkUpdate(FeedHandlerFixture &f, SchemaContext &schemaContext,
            const vespalib::string &fieldName, bool expectReject, bool existing)
{
    f.handler.setSerialNum(15);
    UpdateContext updCtx("id:test:searchdocument::foo", schemaContext.builder);
    updCtx.addFieldUpdate(fieldName);
    if (existing) {
        f.feedView.metaStore.insert(updCtx.update->getId().getGlobalId(), MyDocumentMetaStore::Entry(5, 5, Timestamp(9)));
        f.feedView.metaStore.allocate(updCtx.update->getId().getGlobalId());
    } else {
        updCtx.update->setCreateIfNonExistent(true);
    }
    auto op = std::make_unique<UpdateOperation>(updCtx.bucketId, Timestamp(10), updCtx.update);
    FeedTokenContext token;
    f.handler.performOperation(std::move(token.token), std::move(op));
    EXPECT_TRUE(dynamic_cast<const UpdateResult *>(token.getResult()));
    if (expectReject) {
        TEST_DO(f.feedView.checkCounts(0, 0u, 0, 0u));
        EXPECT_EQUAL(Result::ErrorType::TRANSIENT_ERROR, token.getResult()->getErrorCode());
        if (fieldName == "tensor2") {
            EXPECT_EQUAL("Update operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Wrong tensor type: Field tensor type is 'tensor(x{},y{})' but other tensor type is 'tensor(x{})''",
                         token.getResult()->getErrorMessage());
        } else {
            EXPECT_EQUAL("Update operation rejected for document 'id:test:searchdocument::foo' of type 'searchdocument': 'Field not found'",
                         token.getResult()->getErrorMessage());
        }
    } else {
        if (existing) {
            TEST_DO(f.feedView.checkCounts(1, 16u, 0, 0u));
        } else {
            TEST_DO(f.feedView.checkCounts(0, 0u, 1, 16u));
        }
        EXPECT_EQUAL(Result::ErrorType::NONE, token.getResult()->getErrorCode());
        EXPECT_EQUAL("", token.getResult()->getErrorMessage());
    }
}

TEST_F("require that update with same document type repo is ok", FeedHandlerFixture)
{
    checkUpdate(f, f.schema, "i1", false, true);
}

TEST_F("require that update with different document type repo can be ok", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "i1", false, true);
}

TEST_F("require that update with different document type repo can be rejected", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "i2", true, true);
}

TEST_F("require that update with same document type repo is ok, fallback to create document", FeedHandlerFixture)
{
    checkUpdate(f, f.schema, "i1", false, false);
}

TEST_F("require that update with different document type repo can be ok, fallback to create document", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "i1", false, false);
}

TEST_F("require that update with different document type repo can be rejected, preventing fallback to create document", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "i2", true, false);
}

TEST_F("require that tensor update with correct tensor type works", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "tensor", false, true);
}

TEST_F("require that tensor update with wrong tensor type fails", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    checkUpdate(f, schema, "tensor2", true, true);
}

TEST_F("require that put with different document type repo is ok", FeedHandlerFixture)
{
    TwoFieldsSchemaContext schema;
    DocumentContext doc_context("id:ns:searchdocument::foo", schema.builder);
    auto op = std::make_unique<PutOperation>(doc_context.bucketId,
                                             Timestamp(10), std::move(doc_context.doc));
    FeedTokenContext token_context;
    EXPECT_EQUAL(schema.getRepo().get(), op->getDocument()->getRepo());
    EXPECT_NOT_EQUAL(f.schema.getRepo().get(), op->getDocument()->getRepo());
    EXPECT_NOT_EQUAL(f.feedView.documentType, &op->getDocument()->getType());
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    EXPECT_EQUAL(1, f.feedView.put_count);
    EXPECT_EQUAL(1, f.tls_writer.store_count);
}

TEST_F("require that feed stats are updated", FeedHandlerFixture)
{
    DocumentContext doc_context("id:ns:searchdocument::foo", f.schema.builder);
    auto op =std::make_unique<PutOperation>(doc_context.bucketId, Timestamp(10), std::move(doc_context.doc));
    FeedTokenContext token_context;
    f.handler.performOperation(std::move(token_context.token), std::move(op));
    f.syncMaster(); // wait for initateCommit
    f.syncMaster(); // wait for onCommitDone
    auto stats = f.handler.get_stats(false);
    EXPECT_EQUAL(1u, stats.get_commits());
    EXPECT_EQUAL(1u, stats.get_operations());
    EXPECT_LESS(0.0, stats.get_total_latency());
}

using namespace document;

TEST_F("require that update with a fieldpath update will be rejected", SchemaContext) {
    const DocumentType *docType = f.getRepo()->getDocumentType(f.getDocType().getName());
    auto docUpdate = std::make_unique<DocumentUpdate>(*f.getRepo(), *docType, DocumentId("id:ns:" + docType->getName() + "::1"));
    docUpdate->addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>());
    EXPECT_TRUE(FeedRejectHelper::mustReject(*docUpdate));
}

TEST_F("require that all value updates will be inspected before rejected", SchemaContext) {
    const DocumentType *docType = f.getRepo()->getDocumentType(f.getDocType().getName());
    auto docUpdate = std::make_unique<DocumentUpdate>(*f.getRepo(), *docType, DocumentId("id:ns:" + docType->getName() + "::1"));
    docUpdate->addUpdate(std::move(FieldUpdate(docType->getField("i1")).addUpdate(std::make_unique<ClearValueUpdate>())));
    EXPECT_FALSE(FeedRejectHelper::mustReject(*docUpdate));
    docUpdate->addUpdate(std::move(FieldUpdate(docType->getField("i1")).addUpdate(std::make_unique<ClearValueUpdate>())));
    EXPECT_FALSE(FeedRejectHelper::mustReject(*docUpdate));
    docUpdate->addUpdate(std::move(FieldUpdate(docType->getField("i1")).addUpdate(std::make_unique<AssignValueUpdate>(StringFieldValue::make()))));
    EXPECT_TRUE(FeedRejectHelper::mustReject(*docUpdate));
}

}  // namespace

TEST_MAIN()
{
    DummyFileHeaderContext::setCreator("feedhandler_test");
    TEST_RUN_ALL();
    std::filesystem::remove_all(std::filesystem::path("mytlsdir"));
}
