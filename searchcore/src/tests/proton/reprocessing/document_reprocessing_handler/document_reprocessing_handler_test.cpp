// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/reprocessing/document_reprocessing_handler.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("document_reprocessing_handler_test");

using namespace document;
using namespace proton;
using search::test::DocBuilder;

template <typename ReprocessingType>
struct MyProcessor : public ReprocessingType
{
    using SP = std::shared_ptr<MyProcessor<ReprocessingType> >;
    uint32_t _lid;
    DocumentId _docId;

    MyProcessor() : _lid(0), _docId() {}
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<Document> &doc) override {
        _lid = lid;
        _docId = doc->getId();
    }
};

using MyReader = MyProcessor<IReprocessingReader>;
using MyRewriter = MyProcessor<IReprocessingRewriter>;

const std::string DOC_ID = "id:test:searchdocument::0";

struct FixtureBase
{
    DocumentReprocessingHandler _handler;
    DocBuilder _docBuilder;
    FixtureBase(uint32_t docIdLimit);
    ~FixtureBase();
    std::shared_ptr<Document> createDoc() {
        return _docBuilder.make_document(DOC_ID);
    }
};

FixtureBase::FixtureBase(uint32_t docIdLimit)
    : _handler(docIdLimit),
      _docBuilder()
{ }

FixtureBase::~FixtureBase() = default;

struct ReaderFixture : public FixtureBase
{
    MyReader::SP _reader1;
    MyReader::SP _reader2;
    ReaderFixture();
    ReaderFixture(uint32_t docIdLimit);
    ~ReaderFixture();
};

ReaderFixture::ReaderFixture()
    : ReaderFixture(std::numeric_limits<uint32_t>::max())
{
}

ReaderFixture::ReaderFixture(uint32_t docIdLimit)
    : FixtureBase(docIdLimit),
      _reader1(new MyReader()),
      _reader2(new MyReader())
{
    _handler.addReader(_reader1);
    _handler.addReader(_reader2);
}

ReaderFixture::~ReaderFixture() = default;

struct RewriterFixture : public FixtureBase
{
    MyRewriter::SP _rewriter1;
    MyRewriter::SP _rewriter2;
    RewriterFixture();
    RewriterFixture(uint32_t docIdLimit);
    ~RewriterFixture();
};

RewriterFixture::RewriterFixture()
    : RewriterFixture(std::numeric_limits<uint32_t>::max())
{
}

RewriterFixture::RewriterFixture(uint32_t docIdLimit)
    : FixtureBase(docIdLimit),
      _rewriter1(new MyRewriter()),
      _rewriter2(new MyRewriter())
{
    _handler.addRewriter(_rewriter1);
    _handler.addRewriter(_rewriter2);
}

RewriterFixture::~RewriterFixture() = default;

TEST(DocumentReprocessingHandlerTest, require_that_handler_propagates_visit_of_existing_document_to_readers)
{
    ReaderFixture f;
    f._handler.visit(23u, f.createDoc());
    EXPECT_EQ(23u, f._reader1->_lid);
    EXPECT_EQ(DOC_ID, f._reader1->_docId.toString());
    EXPECT_EQ(23u, f._reader2->_lid);
    EXPECT_EQ(DOC_ID, f._reader2->_docId.toString());
}

TEST(DocumentReprocessingHandlerTest, require_that_handler_propagates_visit_of_existing_document_to_rewriters)
{
    RewriterFixture f;
    f._handler.getRewriteVisitor().visit(23u, f.createDoc());
    EXPECT_EQ(23u, f._rewriter1->_lid);
    EXPECT_EQ(DOC_ID, f._rewriter1->_docId.toString());
    EXPECT_EQ(23u, f._rewriter2->_lid);
    EXPECT_EQ(DOC_ID, f._rewriter2->_docId.toString());
}

TEST(DocumentReprocessingHandlerTest, require_that_handler_skips_out_of_range_visit_to_readers)
{
    ReaderFixture f(10);
    f._handler.visit(23u, f.createDoc());
    EXPECT_EQ(0u, f._reader1->_lid);
    EXPECT_EQ(DocumentId().toString(), f._reader1->_docId.toString());
    EXPECT_EQ(0u, f._reader2->_lid);
    EXPECT_EQ(DocumentId().toString(), f._reader2->_docId.toString());
}

TEST(DocumentReprocessingHandlerTest, require_that_handler_skips_out_of_range_visit_to_rewriters)
{
    RewriterFixture f(10);
    f._handler.getRewriteVisitor().visit(23u, f.createDoc());
    EXPECT_EQ(0u, f._rewriter1->_lid);
    EXPECT_EQ(DocumentId().toString(), f._rewriter1->_docId.toString());
    EXPECT_EQ(0u, f._rewriter2->_lid);
    EXPECT_EQ(DocumentId().toString(), f._rewriter2->_docId.toString());
}

GTEST_MAIN_RUN_ALL_TESTS()
