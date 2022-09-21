// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace vespalib::slime::convenience;
using namespace search::docsummary;
using vespalib::slime::BinaryFormat;
using search::MatchingElements;
using document::ByteFieldValue;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DoubleFieldValue;
using document::Field;
using document::FloatFieldValue;
using document::IntFieldValue;
using document::LongFieldValue;
using document::RawFieldValue;
using document::ShortFieldValue;
using document::StringFieldValue;
using document::StructDataType;
using document::StructFieldValue;

namespace {

struct SlimeSummaryTest : testing::Test, IDocsumStore, GetDocsumsStateCallback {
    std::unique_ptr<DynamicDocsumWriter> writer;
    StructDataType  int_pair_type;
    DocumentType    doc_type;
    GetDocsumsState state;
    bool            fail_get_mapped_docsum;
    bool            empty_get_mapped_docsum;
    SlimeSummaryTest();
    ~SlimeSummaryTest() override;
    void getDocsum(Slime &slime) {
        Slime slimeOut;
        SlimeInserter inserter(slimeOut);
        auto rci = writer->resolveClassInfo(state._args.getResultClassName(), {});
        writer->insertDocsum(rci, 1u, state, *this, inserter);
        vespalib::SmartBuffer buf(4_Ki);
        BinaryFormat::encode(slimeOut, buf);
        EXPECT_GT(BinaryFormat::decode(buf.obtain(), slime), 0u);
    }
    std::unique_ptr<const IDocsumStoreDocument> get_document(uint32_t docid) override {
        EXPECT_EQ(1u, docid);
        if (fail_get_mapped_docsum) {
            return {};
        }
        if (empty_get_mapped_docsum) {
            return std::make_unique<DocsumStoreDocument>(std::unique_ptr<Document>());
        }
        auto doc = std::make_unique<Document>(doc_type, DocumentId("id:test:test::0"));
        doc->setValue("int_field", IntFieldValue(4));
        doc->setValue("short_field", ShortFieldValue(2));
        doc->setValue("byte_field", ByteFieldValue(1));
        doc->setValue("float_field", FloatFieldValue(4.5));
        doc->setValue("double_field", DoubleFieldValue(8.75));
        doc->setValue("int64_field", LongFieldValue(8));
        doc->setValue("string_field", StringFieldValue("string"));
        doc->setValue("data_field", RawFieldValue("data"));
        doc->setValue("longstring_field", StringFieldValue("long_string"));
        doc->setValue("longdata_field", RawFieldValue("long_data"));
        {
            StructFieldValue int_pair(int_pair_type);
            int_pair.setValue("foo", IntFieldValue(1));
            int_pair.setValue("bar", IntFieldValue(2));
            doc->setValue("int_pair_field", int_pair);
        }
        return std::make_unique<DocsumStoreDocument>(std::move(doc));
    }
    void fillSummaryFeatures(GetDocsumsState&) override { }
    void fillRankFeatures(GetDocsumsState&) override { }
    std::unique_ptr<MatchingElements> fill_matching_elements(const search::MatchingElementsFields &) override { abort(); }
};


SlimeSummaryTest::SlimeSummaryTest()
    : writer(),
      int_pair_type("int_pair"),
      doc_type("test"),
      state(*this),
      fail_get_mapped_docsum(false),
      empty_get_mapped_docsum(false)
{
    auto config = std::make_unique<ResultConfig>();
    ResultClass *cfg = config->addResultClass("default", 0);
    EXPECT_TRUE(cfg != nullptr);
    EXPECT_TRUE(cfg->addConfigEntry("int_field"));
    EXPECT_TRUE(cfg->addConfigEntry("short_field"));
    EXPECT_TRUE(cfg->addConfigEntry("byte_field"));
    EXPECT_TRUE(cfg->addConfigEntry("float_field"));
    EXPECT_TRUE(cfg->addConfigEntry("double_field"));
    EXPECT_TRUE(cfg->addConfigEntry("int64_field"));
    EXPECT_TRUE(cfg->addConfigEntry("string_field"));
    EXPECT_TRUE(cfg->addConfigEntry("data_field"));
    EXPECT_TRUE(cfg->addConfigEntry("longstring_field"));
    EXPECT_TRUE(cfg->addConfigEntry("longdata_field"));
    EXPECT_TRUE(cfg->addConfigEntry("int_pair_field"));
    config->set_default_result_class_id(0);
    writer = std::make_unique<DynamicDocsumWriter>(std::move(config), std::unique_ptr<KeywordExtractor>());
    int_pair_type.addField(Field("foo", *DataType::INT));
    int_pair_type.addField(Field("bar", *DataType::INT));
    doc_type.addField(Field("int_field", *DataType::INT));
    doc_type.addField(Field("short_field", *DataType::SHORT));
    doc_type.addField(Field("byte_field", *DataType::BYTE));
    doc_type.addField(Field("float_field", *DataType::FLOAT));
    doc_type.addField(Field("double_field", *DataType::DOUBLE));
    doc_type.addField(Field("int64_field", *DataType::LONG));
    doc_type.addField(Field("string_field", *DataType::STRING));
    doc_type.addField(Field("data_field", *DataType::RAW));
    doc_type.addField(Field("longstring_field", *DataType::STRING));
    doc_type.addField(Field("longdata_field", *DataType::RAW));
    doc_type.addField(Field("int_pair_field", int_pair_type));
}
SlimeSummaryTest::~SlimeSummaryTest() = default;

} // namespace <unnamed>

TEST_F(SlimeSummaryTest, docsum_can_be_written_as_slime)
{
    Slime s;
    getDocsum(s);
    EXPECT_EQ(s.get()["int_field"].asLong(), 4u);
    EXPECT_EQ(s.get()["short_field"].asLong(), 2u);
    EXPECT_EQ(s.get()["byte_field"].asLong(), 1u);
    EXPECT_EQ(s.get()["float_field"].asDouble(), 4.5);
    EXPECT_EQ(s.get()["double_field"].asDouble(), 8.75);
    EXPECT_EQ(s.get()["int64_field"].asLong(), 8u);
    EXPECT_EQ(s.get()["string_field"].asString().make_string(), std::string("string"));
    EXPECT_EQ(s.get()["data_field"].asData().make_string(), std::string("data"));
    EXPECT_EQ(s.get()["longstring_field"].asString().make_string(), std::string("long_string"));
    EXPECT_EQ(s.get()["longdata_field"].asData().make_string(), std::string("long_data"));
    EXPECT_EQ(s.get()["int_pair_field"]["foo"].asLong(), 1u);
    EXPECT_EQ(s.get()["int_pair_field"]["bar"].asLong(), 2u);
}

TEST_F(SlimeSummaryTest, unknown_summary_class_gives_empty_slime)
{
    state._args.setResultClassName("unknown");
    Slime s;
    getDocsum(s);
    EXPECT_TRUE(s.get().valid());
    EXPECT_EQ(vespalib::slime::NIX::ID, s.get().type().getId());
}

TEST_F(SlimeSummaryTest, failure_to_retrieve_docsum_store_document_gives_empty_slime)
{
    fail_get_mapped_docsum = true;
    Slime s;
    getDocsum(s);
    EXPECT_TRUE(s.get().valid());
    EXPECT_EQ(vespalib::slime::NIX::ID, s.get().type().getId());
}

TEST_F(SlimeSummaryTest, empty_docsum_store_document_gives_empty_object)
{
    empty_get_mapped_docsum = true;
    Slime s;
    getDocsum(s);
    EXPECT_TRUE(s.get().valid());
    EXPECT_EQ(vespalib::slime::OBJECT::ID, s.get().type().getId());
    EXPECT_EQ(0u, s.get().fields());
}

GTEST_MAIN_RUN_ALL_TESTS()
