// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/common/i_document_id_provider.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/document_id_dfw.h>
#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <map>
#include <memory>

using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using search::MatchingElements;
using search::MatchingElementsFields;
using search::common::ElementIds;
using search::docsummary::DocsumStoreDocument;
using search::docsummary::DocumentIdDFW;
using search::docsummary::GetDocsumsState;
using search::docsummary::GetDocsumsStateCallback;
using search::docsummary::IDocsumStoreDocument;
using search::docsummary::ResultClass;
using search::docsummary::ResultConfig;
using vespalib::Slime;
using vespalib::slime::Cursor;
using vespalib::slime::ObjectInserter;
using vespalib::slime::SlimeInserter;

namespace {

const int32_t     doc_type_id = 787121340;
const std::string doc_type_name = "test";
const std::string provided_document_id("id:provided:test::1");

std::unique_ptr<const DocumentTypeRepo> make_doc_type_repo() {
    document::new_config_builder::NewConfigBuilder builder;
    builder.document(doc_type_name, doc_type_id);
    return std::make_unique<const DocumentTypeRepo>(builder.config());
}

struct MyGetDocsumsStateCallback : GetDocsumsStateCallback {
    void fillSummaryFeatures(GetDocsumsState&) override {}
    void fillRankFeatures(GetDocsumsState&) override {}
    std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields&) override { abort(); }
};

class MyDocumentIdProvider : public search::IDocumentIdProvider {
    std::map<uint32_t, std::string> _ids;

public:
    MyDocumentIdProvider();
    ~MyDocumentIdProvider() override;
    [[nodiscard]] std::string_view get_document_id_string_view(uint32_t lid) const noexcept override;
};

MyDocumentIdProvider::MyDocumentIdProvider() : search::IDocumentIdProvider(), _ids() {
    _ids.emplace(1, provided_document_id);
}

MyDocumentIdProvider::~MyDocumentIdProvider() = default;

std::string_view MyDocumentIdProvider::get_document_id_string_view(uint32_t lid) const noexcept {
    auto itr = _ids.find(lid);
    if (itr != _ids.end()) {
        return itr->second;
    } else {
        return {};
    }
}

} // namespace

class DocumentIdDFWTest : public ::testing::Test {
    std::string                                        _field_name;
    vespalib::Memory                                   _field_name_view;
    std::unique_ptr<ResultConfig>                      _result_config;
    std::unique_ptr<const DocumentTypeRepo>            _repo;
    const DocumentType*                                _document_type;
    std::shared_ptr<const search::IDocumentIdProvider> _document_id_provider;

protected:
    DocumentIdDFWTest();
    ~DocumentIdDFWTest() override;

    std::unique_ptr<IDocsumStoreDocument> make_docsum_store_document(const std::string& id);
    Slime write(uint32_t lid, const IDocsumStoreDocument* doc);
    vespalib::Memory get_field_name_view() const noexcept { return _field_name_view; }
    void enable_document_id_provider() { _document_id_provider = std::make_shared<MyDocumentIdProvider>(); }
};

DocumentIdDFWTest::DocumentIdDFWTest()
    : testing::Test(),
      _field_name("documentid"),
      _field_name_view(_field_name.data(), _field_name.size()),
      _result_config(std::make_unique<ResultConfig>()),
      _repo(make_doc_type_repo()),
      _document_type(_repo->getDocumentType(doc_type_name)),
      _document_id_provider() {
    auto* cfg = _result_config->addResultClass("default", 0);
    cfg->addConfigEntry(_field_name);
}

DocumentIdDFWTest::~DocumentIdDFWTest() = default;

std::unique_ptr<IDocsumStoreDocument> DocumentIdDFWTest::make_docsum_store_document(const std::string& id) {
    auto doc = std::make_unique<Document>(*_repo, *_document_type, DocumentId(id));
    return std::make_unique<DocsumStoreDocument>(std::move(doc));
}

vespalib::Slime DocumentIdDFWTest::write(uint32_t lid, const IDocsumStoreDocument* doc) {
    Slime                     slime;
    SlimeInserter             top_inserter(slime);
    Cursor&                   docsum = top_inserter.insertObject();
    ObjectInserter            field_inserter(docsum, _field_name_view);
    DocumentIdDFW             writer(_document_id_provider);
    MyGetDocsumsStateCallback callback;
    GetDocsumsState           state(callback);
    writer.insert_field(lid, doc, state, ElementIds::select_all(), field_inserter);
    return slime;
}

TEST_F(DocumentIdDFWTest, insert_document_id) {
    std::string id("id::test::0");
    auto        doc = make_docsum_store_document(id);
    auto        slime = write(1, doc.get());
    EXPECT_TRUE(slime.get()[get_field_name_view()].valid());
    EXPECT_EQ(id, slime.get()[get_field_name_view()].asString().make_string());
}

TEST_F(DocumentIdDFWTest, insert_document_id_no_document_doc) {
    auto doc = std::make_unique<DocsumStoreDocument>(std::unique_ptr<Document>());
    auto slime = write(1, doc.get());
    EXPECT_FALSE(slime.get()[get_field_name_view()].valid());
}

TEST_F(DocumentIdDFWTest, insert_document_id_no_docsum_store_doc) {
    auto slime = write(1, nullptr);
    EXPECT_FALSE(slime.get()[get_field_name_view()].valid());
}

TEST_F(DocumentIdDFWTest, insert_document_id_no_document_doc_but_document_id_provider) {
    enable_document_id_provider();
    auto doc = std::make_unique<DocsumStoreDocument>(std::unique_ptr<Document>());
    auto slime = write(1, doc.get());
    EXPECT_TRUE(slime.get()[get_field_name_view()].valid());
    EXPECT_EQ(provided_document_id, slime.get()[get_field_name_view()].asString().make_string());
    slime = write(2, doc.get());
    EXPECT_FALSE(slime.get()[get_field_name_view()].valid());
}

TEST_F(DocumentIdDFWTest, insert_document_id_no_docsum_store_doc_but_document_id_provider) {
    enable_document_id_provider();
    auto slime = write(1, nullptr);
    EXPECT_TRUE(slime.get()[get_field_name_view()].valid());
    EXPECT_EQ(provided_document_id, slime.get()[get_field_name_view()].asString().make_string());
    slime = write(2, nullptr);
    EXPECT_FALSE(slime.get()[get_field_name_view()].valid());
}

GTEST_MAIN_RUN_ALL_TESTS()
