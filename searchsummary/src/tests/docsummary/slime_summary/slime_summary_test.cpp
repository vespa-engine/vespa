// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/resultpacker.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchlib/common/transport.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>

using namespace vespalib::slime::convenience;
using namespace search::docsummary;

namespace {

struct FieldBlock {
    Slime slime;
    search::RawBuf binary;

    explicit FieldBlock(const vespalib::string &jsonInput)
        : slime(), binary(1024)
    {
        size_t used = vespalib::slime::JsonFormat::decode(jsonInput, slime);
        EXPECT_TRUE(used > 0);
        search::SlimeOutputRawBufAdapter adapter(binary);
        vespalib::slime::BinaryFormat::encode(slime, adapter);
    }
    const char *data() const { return binary.GetDrainPos(); }
    size_t dataLen() const { return binary.GetUsedLen(); }
};

struct DocsumFixture : IDocsumStore, GetDocsumsStateCallback {
    std::unique_ptr<DynamicDocsumWriter> writer;
    std::unique_ptr<ResultPacker> packer;
    GetDocsumsState state;
    DocsumFixture();
    ~DocsumFixture();
    void getDocsum(Slime &slime) {
        uint32_t classId;
        search::RawBuf buf(4096);
        writer->WriteDocsum(1u, &state, this, &buf);
        ASSERT_GREATER(buf.GetUsedLen(), sizeof(classId));
        memcpy(&classId, buf.GetDrainPos(), sizeof(classId));
        buf.Drain(sizeof(classId));
        EXPECT_EQUAL(classId, ::search::fs4transport::SLIME_MAGIC_ID);
        EXPECT_GREATER(vespalib::slime::BinaryFormat
                       ::decode(Memory(buf.GetDrainPos(), buf.GetUsedLen()), slime), 0u);
    }
    uint32_t getNumDocs() const override { return 2; }
    DocsumStoreValue getMappedDocsum(uint32_t docid) override {
        EXPECT_EQUAL(1u, docid);
        EXPECT_TRUE(packer->Init(0));
        EXPECT_TRUE(packer->AddInteger(4));
        EXPECT_TRUE(packer->AddShort(2));
        EXPECT_TRUE(packer->AddByte(1));
        EXPECT_TRUE(packer->AddFloat(4.5));
        EXPECT_TRUE(packer->AddDouble(8.75));
        EXPECT_TRUE(packer->AddInt64(8));
        EXPECT_TRUE(packer->AddString(       "string",
                                      strlen("string")));
        EXPECT_TRUE(packer->AddData(       "data",
                                    strlen("data")));
        EXPECT_TRUE(packer->AddLongString(       "long_string",
                                          strlen("long_string")));
        EXPECT_TRUE(packer->AddLongData(       "long_data",
                                        strlen("long_data")));
        EXPECT_TRUE(packer->AddLongString(       "xml_string",
                                          strlen("xml_string")));
        FieldBlock jsf1("{foo:1, bar:2}");
        EXPECT_TRUE(packer->AddLongData(jsf1.data(), jsf1.dataLen()));
        EXPECT_TRUE(packer->AddLongString("abc", 3));
        const char *buf;
        uint32_t len;
        EXPECT_TRUE(packer->GetDocsumBlob(&buf, &len));
        return DocsumStoreValue(buf, len);
    }
    uint32_t getSummaryClassId() const override { return 0; }
    void FillSummaryFeatures(GetDocsumsState *, IDocsumEnvironment *) override { }
    void FillRankFeatures(GetDocsumsState *, IDocsumEnvironment *) override { }
    void ParseLocation(GetDocsumsState *) override { }
};


DocsumFixture::DocsumFixture()
    : writer(), packer(), state(*this)
{
    ResultConfig *config = new ResultConfig();
    ResultClass *cfg = config->AddResultClass("default", 0);
    EXPECT_TRUE(cfg != 0);
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
    EXPECT_TRUE(cfg->AddConfigEntry("xmlstring_field", RES_XMLSTRING));
    EXPECT_TRUE(cfg->AddConfigEntry("jsonstring_field", RES_JSONSTRING));
    EXPECT_TRUE(cfg->AddConfigEntry("bad_jsonstring_field", RES_JSONSTRING));
    config->CreateEnumMaps();
    writer.reset(new DynamicDocsumWriter(config, 0));
    packer.reset(new ResultPacker(writer->GetResultConfig()));
}
DocsumFixture::~DocsumFixture() {}

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
    EXPECT_EQUAL(f2.get()["xmlstring_field"].asString().make_string(), std::string("xml_string"));
    EXPECT_EQUAL(f2.get()["jsonstring_field"]["foo"].asLong(), 1u);
    EXPECT_EQUAL(f2.get()["jsonstring_field"]["bar"].asLong(), 2u);
    EXPECT_EQUAL(f2.get()["bad_jsonstring_field"].type().getId(), 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
