// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>
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

struct DocsumFixture : IDocsumStore, GetDocsumsStateCallback {
    std::unique_ptr<DynamicDocsumWriter> writer;
    StructDataType  int_pair_type;
    DocumentType    doc_type;
    GetDocsumsState state;
    DocsumFixture();
    ~DocsumFixture() override;
    void getDocsum(Slime &slime) {
        Slime slimeOut;
        SlimeInserter inserter(slimeOut);
        writer->WriteDocsum(1u, &state, this, inserter);
        vespalib::SmartBuffer buf(4_Ki);
        BinaryFormat::encode(slimeOut, buf);
        EXPECT_GREATER(BinaryFormat::decode(buf.obtain(), slime), 0u);
    }
    uint32_t getNumDocs() const override { return 2; }
    std::unique_ptr<const IDocsumStoreDocument> getMappedDocsum(uint32_t docid) override {
        EXPECT_EQUAL(1u, docid);
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
    void FillSummaryFeatures(GetDocsumsState&) override { }
    void FillRankFeatures(GetDocsumsState&) override { }
    std::unique_ptr<MatchingElements> fill_matching_elements(const search::MatchingElementsFields &) override { abort(); }
};


DocsumFixture::DocsumFixture()
    : writer(),
      int_pair_type("int_pair"),
      doc_type("test"),
      state(*this)
{
    auto config = std::make_unique<ResultConfig>();
    ResultClass *cfg = config->AddResultClass("default", 0);
    EXPECT_TRUE(cfg != nullptr);
    EXPECT_TRUE(cfg->AddConfigEntry("int_field", RES_INT));
    EXPECT_TRUE(cfg->AddConfigEntry("short_field", RES_SHORT));
    EXPECT_TRUE(cfg->AddConfigEntry("byte_field", RES_BYTE));
    EXPECT_TRUE(cfg->AddConfigEntry("float_field", RES_FLOAT));
    EXPECT_TRUE(cfg->AddConfigEntry("double_field", RES_DOUBLE));
    EXPECT_TRUE(cfg->AddConfigEntry("int64_field", RES_INT64));
    EXPECT_TRUE(cfg->AddConfigEntry("string_field", RES_STRING));
    EXPECT_TRUE(cfg->AddConfigEntry("data_field", RES_DATA));
    EXPECT_TRUE(cfg->AddConfigEntry("longstring_field", RES_LONG_STRING));
    EXPECT_TRUE(cfg->AddConfigEntry("longdata_field", RES_LONG_DATA));
    EXPECT_TRUE(cfg->AddConfigEntry("int_pair_field", RES_JSONSTRING));
    config->set_default_result_class_id(0);
    config->CreateEnumMaps();
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
DocsumFixture::~DocsumFixture() = default;

} // namespace <unnamed>

TEST_FF("require that docsum can be written as slime", DocsumFixture(), Slime()) {
    f1.getDocsum(f2);
    EXPECT_EQUAL(f2.get()["int_field"].asLong(), 4u);
    EXPECT_EQUAL(f2.get()["short_field"].asLong(), 2u);
    EXPECT_EQUAL(f2.get()["byte_field"].asLong(), 1u);
    EXPECT_EQUAL(f2.get()["float_field"].asDouble(), 4.5);
    EXPECT_EQUAL(f2.get()["double_field"].asDouble(), 8.75);
    EXPECT_EQUAL(f2.get()["int64_field"].asLong(), 8u);
    EXPECT_EQUAL(f2.get()["string_field"].asString().make_string(), std::string("string"));
    EXPECT_EQUAL(f2.get()["data_field"].asData().make_string(), std::string("data"));
    EXPECT_EQUAL(f2.get()["longstring_field"].asString().make_string(), std::string("long_string"));
    EXPECT_EQUAL(f2.get()["longdata_field"].asData().make_string(), std::string("long_data"));
    EXPECT_EQUAL(f2.get()["int_pair_field"]["foo"].asLong(), 1u);
    EXPECT_EQUAL(f2.get()["int_pair_field"]["bar"].asLong(), 2u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
