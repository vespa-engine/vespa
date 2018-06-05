// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("document_reprocessing_handler_test");

#include <vespa/searchcore/proton/reprocessing/document_reprocessing_handler.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;
using namespace proton;
using namespace search::index;

template <typename ReprocessingType>
struct MyProcessor : public ReprocessingType
{
    typedef std::shared_ptr<MyProcessor<ReprocessingType> > SP;
    uint32_t _lid;
    DocumentId _docId;

    MyProcessor() : _lid(0), _docId() {}
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<Document> &doc) override {
        _lid = lid;
        _docId = doc->getId();
    }
};

typedef MyProcessor<IReprocessingReader> MyReader;
typedef MyProcessor<IReprocessingRewriter> MyRewriter;

const vespalib::string DOC_ID = "id:test:searchdocument::0";

struct FixtureBase
{
    DocumentReprocessingHandler _handler;
    DocBuilder _docBuilder;
    FixtureBase(uint32_t docIdLimit);
    ~FixtureBase();
    std::shared_ptr<Document> createDoc() {
        return _docBuilder.startDocument(DOC_ID).endDocument();
    }
};

FixtureBase::FixtureBase(uint32_t docIdLimit)
    : _handler(docIdLimit),
      _docBuilder(Schema())
{ }
FixtureBase::~FixtureBase() {}

struct ReaderFixture : public FixtureBase
{
    MyReader::SP _reader1;
    MyReader::SP _reader2;
    ReaderFixture()
        : ReaderFixture(std::numeric_limits<uint32_t>::max())
    {
    }
    ReaderFixture(uint32_t docIdLimit)
        : FixtureBase(docIdLimit),
          _reader1(new MyReader()),
          _reader2(new MyReader())
    {
        _handler.addReader(_reader1);
        _handler.addReader(_reader2);
    }
};

struct RewriterFixture : public FixtureBase
{
    MyRewriter::SP _rewriter1;
    MyRewriter::SP _rewriter2;
    RewriterFixture()
        : RewriterFixture(std::numeric_limits<uint32_t>::max())
    {
    }
    RewriterFixture(uint32_t docIdLimit)
        : FixtureBase(docIdLimit),
          _rewriter1(new MyRewriter()),
          _rewriter2(new MyRewriter())
    {
        _handler.addRewriter(_rewriter1);
        _handler.addRewriter(_rewriter2);
    }
};

TEST_F("require that handler propagates visit of existing document to readers", ReaderFixture)
{
    f._handler.visit(23u, f.createDoc());
    EXPECT_EQUAL(23u, f._reader1->_lid);
    EXPECT_EQUAL(DOC_ID, f._reader1->_docId.toString());
    EXPECT_EQUAL(23u, f._reader2->_lid);
    EXPECT_EQUAL(DOC_ID, f._reader2->_docId.toString());
}

TEST_F("require that handler propagates visit of existing document to rewriters", RewriterFixture)
{
    f._handler.getRewriteVisitor().visit(23u, f.createDoc());
    EXPECT_EQUAL(23u, f._rewriter1->_lid);
    EXPECT_EQUAL(DOC_ID, f._rewriter1->_docId.toString());
    EXPECT_EQUAL(23u, f._rewriter2->_lid);
    EXPECT_EQUAL(DOC_ID, f._rewriter2->_docId.toString());
}

TEST_F("require that handler skips out of range visit to readers",
       ReaderFixture(10))
{
    f._handler.visit(23u, f.createDoc());
    EXPECT_EQUAL(0u, f._reader1->_lid);
    EXPECT_EQUAL(DocumentId().toString(), f._reader1->_docId.toString());
    EXPECT_EQUAL(0u, f._reader2->_lid);
    EXPECT_EQUAL(DocumentId().toString(), f._reader2->_docId.toString());
}

TEST_F("require that handler skips out of range visit to rewriters",
       RewriterFixture(10))
{
    f._handler.getRewriteVisitor().visit(23u, f.createDoc());
    EXPECT_EQUAL(0u, f._rewriter1->_lid);
    EXPECT_EQUAL(DocumentId().toString(), f._rewriter1->_docId.toString());
    EXPECT_EQUAL(0u, f._rewriter2->_lid);
    EXPECT_EQUAL(DocumentId().toString(), f._rewriter2->_docId.toString());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
