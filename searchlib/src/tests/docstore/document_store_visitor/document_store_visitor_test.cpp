// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/docstore/documentstore.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("document_store_visitor_test");

using namespace search;

using vespalib::string;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using vespalib::compression::CompressionConfig;
using vespalib::asciistream;
using index::DummyFileHeaderContext;

namespace {

const string doc_type_name = "test";
const string header_name = doc_type_name + ".header";
const string body_name = doc_type_name + ".body";

document::DocumenttypesConfig
makeDocTypeRepoConfig()
{
    const int32_t doc_type_id = 787121340;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id,
                     doc_type_name,
                     document::config_builder::Struct(header_name),
                     document::config_builder::Struct(body_name).
                     addField("main", DataType::T_STRING).
                     addField("extra", DataType::T_STRING));
    return builder.config();
}


Document::UP
makeDoc(const DocumentTypeRepo &repo, uint32_t i, bool before)
{
    asciistream idstr;
    idstr << "id:test:test:: " << i;
    DocumentId id(idstr.str());
    const DocumentType *docType = repo.getDocumentType(doc_type_name);
    Document::UP doc(new Document(*docType, id));
    ASSERT_TRUE(doc.get());
    asciistream mainstr;
    mainstr << "static text" << i << " body something";
    for (uint32_t j = 0; j < 10; ++j) {
        mainstr << (j + i * 1000) << " ";
    }
    mainstr << " and end field";
    doc->set("main", mainstr.c_str());
    if (!before) {
        doc->set("extra", "foo");
    }
    
    return doc;
}

}

class MyTlSyncer : public transactionlog::SyncProxy
{
    SerialNum _syncedTo;
    
public:
    MyTlSyncer() : _syncedTo(0) {}
    void sync(SerialNum syncTo) override { _syncedTo = syncTo; }
};


class MyVisitorBase
{
public:
    DocumentTypeRepo &_repo;
    uint32_t _visitCount;
    uint32_t _visitRmCount;
    uint32_t _docIdLimit;
    BitVector::UP _valid;
    bool _before;

    MyVisitorBase(DocumentTypeRepo &repo, uint32_t docIdLimit, bool before);
};

MyVisitorBase::MyVisitorBase(DocumentTypeRepo &repo, uint32_t docIdLimit, bool before)
    : _repo(repo),
      _visitCount(0u),
      _visitRmCount(0u),
      _docIdLimit(docIdLimit),
      _valid(BitVector::create(docIdLimit)),
      _before(before)
{
}


class MyVisitor : public MyVisitorBase,
                  public IDocumentStoreReadVisitor
{
public:
    using MyVisitorBase::MyVisitorBase;

    void visit(uint32_t lid, const std::shared_ptr<Document> &doc) override;
    void visit(uint32_t lid) override;
};


void
MyVisitor::visit(uint32_t lid, const std::shared_ptr<Document> &doc)
{
    ++_visitCount;
    assert(lid < _docIdLimit);
    Document::UP expDoc(makeDoc(_repo, lid, _before));
    EXPECT_TRUE(*expDoc == *doc);
    _valid->slowSetBit(lid);
}


void
MyVisitor::visit(uint32_t lid)
{
    ++_visitRmCount;
    assert(lid < _docIdLimit);
    _valid->slowClearBit(lid);
}


class MyRewriteVisitor : public MyVisitorBase,
                         public IDocumentStoreRewriteVisitor
{
public:
    using MyVisitorBase::MyVisitorBase;

    virtual void
    visit(uint32_t lid, const std::shared_ptr<Document> &doc) override;
};


void
MyRewriteVisitor::visit(uint32_t lid, const std::shared_ptr<Document> &doc)
{
    ++_visitCount;
    assert(lid < _docIdLimit);
    Document::UP expDoc(makeDoc(_repo, lid, _before));
    EXPECT_TRUE(*expDoc == *doc);
    _valid->slowSetBit(lid);
    doc->set("extra", "foo");
}


class MyVisitorProgress : public IDocumentStoreVisitorProgress
{
public:
    double _progress;
    uint32_t _updates;

    MyVisitorProgress();

    void updateProgress(double progress) override;
    double getProgress() const;
};


MyVisitorProgress::MyVisitorProgress()
    : _progress(0.0),
      _updates(0)
{
}


void
MyVisitorProgress::updateProgress(double progress)
{
    EXPECT_TRUE(progress >= _progress);
    _progress = progress;
    ++_updates;
    LOG(info,
        "updateProgress(%6.2f), %u updates",
        progress, _updates);
}


double
MyVisitorProgress::getProgress() const
{
    return _progress;
}


struct Fixture
{
    string _baseDir;
    DocumentTypeRepo _repo;
    LogDocumentStore::Config _storeConfig;
    vespalib::ThreadStackExecutor _executor;
    DummyFileHeaderContext _fileHeaderContext;
    MyTlSyncer _tlSyncer;
    std::unique_ptr<LogDocumentStore> _store;
    uint64_t _syncToken;
    uint32_t _docIdLimit;
    BitVector::UP _valid;

    Fixture();
    ~Fixture();

    Document::UP makeDoc(uint32_t i);
    void resetDocStore();
    void mkdir();
    void rmdir();
    void setDocIdLimit(uint32_t docIdLimit);
    void put(const Document &doc, uint32_t lid);
    void remove(uint32_t lid);
    void flush();
    void populate(uint32_t low, uint32_t high, uint32_t docIdLimit);
    void applyRemoves(uint32_t rmDocs);
    void checkRemovePostCond(uint32_t numDocs, uint32_t docIdLimit, uint32_t rmDocs, bool before);
};

Fixture::Fixture()
    : _baseDir("visitor"),
      _repo(makeDocTypeRepoConfig()),
      _storeConfig(DocumentStore::Config(CompressionConfig::NONE, 0, 0),
                   LogDataStore::Config().setMaxFileSize(50000).setMaxBucketSpread(3.0)
                           .setFileConfig(WriteableFileChunk::Config(CompressionConfig(), 16384))),
      _executor(1, 128 * 1024),
      _fileHeaderContext(),
      _tlSyncer(),
      _store(),
      _syncToken(0u),
      _docIdLimit(0u),
      _valid(BitVector::create(0u))
{
    rmdir();
    mkdir();
    resetDocStore();
}


Fixture::~Fixture()
{
    _store.reset();
    rmdir();
}

Document::UP
Fixture::makeDoc(uint32_t i)
{
    return ::makeDoc(_repo, i, true);
}

void
Fixture::resetDocStore()
{
    _store.reset(new LogDocumentStore(_executor, _baseDir, _storeConfig, GrowStrategy(),
                                      TuneFileSummary(), _fileHeaderContext, _tlSyncer, nullptr));
}


void
Fixture::rmdir()
{
    vespalib::rmdir(_baseDir, true);
}

void
Fixture::mkdir()
{
    vespalib::mkdir(_baseDir, false);
}


void
Fixture::setDocIdLimit(uint32_t docIdLimit)
{
    _docIdLimit = docIdLimit;
    _valid->resize(_docIdLimit);
}

void
Fixture::put(const Document &doc, uint32_t lid)
{
    ++_syncToken;
    assert(lid < _docIdLimit);
    _store->write(_syncToken, lid, doc);
    _valid->slowSetBit(lid);
}


void
Fixture::remove(uint32_t lid)
{
    ++_syncToken;
    assert(lid < _docIdLimit);
    _store->remove(_syncToken, lid);
    _valid->slowClearBit(lid);
}


void
Fixture::flush()
{
    _store->initFlush(_syncToken);
    _store->flush(_syncToken);
}


void
Fixture::populate(uint32_t low, uint32_t high, uint32_t docIdLimit)
{
    setDocIdLimit(docIdLimit);
    for (uint32_t lid = low; lid < high; ++lid) {
        Document::UP doc = makeDoc(lid);
        put(*doc, lid);
    }
}


void
Fixture::applyRemoves(uint32_t rmDocs)
{
    for (uint32_t lid = 20; lid < 20 + rmDocs; ++lid) {
        remove(lid);
    }
    put(*makeDoc(25), 25);
    remove(25);
    put(*makeDoc(25), 25);
}


void
Fixture::checkRemovePostCond(uint32_t numDocs,
                             uint32_t docIdLimit,
                             uint32_t rmDocs,
                             bool before)
{                             
    MyVisitor visitor(_repo, docIdLimit, before);
    MyVisitorProgress visitorProgress;
    EXPECT_EQUAL(0.0, visitorProgress.getProgress());
    EXPECT_EQUAL(0u, visitorProgress._updates);
    _store->accept(visitor, visitorProgress, _repo);
    EXPECT_EQUAL(numDocs - rmDocs + 1, visitor._visitCount);
    EXPECT_EQUAL(rmDocs - 1, visitor._visitRmCount);
    EXPECT_EQUAL(1.0, visitorProgress.getProgress());
    EXPECT_NOT_EQUAL(0u, visitorProgress._updates);
    EXPECT_TRUE(*_valid == *visitor._valid);
}


TEST_F("require that basic visit works", Fixture())
{
    uint32_t numDocs = 3000;
    uint32_t docIdLimit = numDocs + 1;
    f.populate(1, docIdLimit, docIdLimit);
    f.flush();
    MyVisitor visitor(f._repo, docIdLimit, true);
    MyVisitorProgress visitorProgress;
    EXPECT_EQUAL(0.0, visitorProgress.getProgress());
    EXPECT_EQUAL(0u, visitorProgress._updates);
    f._store->accept(visitor, visitorProgress, f._repo);
    EXPECT_EQUAL(numDocs, visitor._visitCount);
    EXPECT_EQUAL(0u, visitor._visitRmCount);
    EXPECT_EQUAL(1.0, visitorProgress.getProgress());
    EXPECT_NOT_EQUAL(0u, visitorProgress._updates);
    EXPECT_TRUE(*f._valid == *visitor._valid);
}


TEST_F("require that visit with remove works", Fixture())
{
    uint32_t numDocs = 1000;
    uint32_t docIdLimit = numDocs + 1;
    f.populate(1, docIdLimit, docIdLimit);
    uint32_t rmDocs = 20;
    f.applyRemoves(rmDocs);
    f.flush();
    f.checkRemovePostCond(numDocs, docIdLimit, rmDocs, true);
}

TEST_F("require that visit with rewrite and remove works", Fixture())
{
    uint32_t numDocs = 1000;
    uint32_t docIdLimit = numDocs + 1;
    f.populate(1, docIdLimit, docIdLimit);
    uint32_t rmDocs = 20;
    f.applyRemoves(rmDocs);
    f.flush();
    f.checkRemovePostCond(numDocs, docIdLimit, rmDocs, true);
    {
        MyRewriteVisitor visitor(f._repo, docIdLimit, true);
        MyVisitorProgress visitorProgress;
        EXPECT_EQUAL(0.0, visitorProgress.getProgress());
        EXPECT_EQUAL(0u, visitorProgress._updates);
        f._store->accept(visitor, visitorProgress, f._repo);
        EXPECT_EQUAL(numDocs - rmDocs + 1, visitor._visitCount);
        EXPECT_EQUAL(1.0, visitorProgress.getProgress());
        EXPECT_NOT_EQUAL(0u, visitorProgress._updates);
        EXPECT_TRUE(*f._valid == *visitor._valid);
        f.flush();
    }
    f.checkRemovePostCond(numDocs, docIdLimit, rmDocs, false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
