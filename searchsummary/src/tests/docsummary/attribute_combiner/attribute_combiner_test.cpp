// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer_state.h>
#include <vespa/searchsummary/docsummary/attribute_combiner_dfw.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_combiner_test");

using search::AttributeFactory;
using search::AttributeManager;
using search::AttributeVector;
using search::IntegerAttribute;
using search::FloatingPointAttribute;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::getUndefined;
using search::docsummary::AttributeCombinerDFW;
using search::docsummary::GetDocsumsState;
using search::docsummary::GetDocsumsStateCallback;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::IDocsumFieldWriter;

namespace {

vespalib::string
toCompactJsonString(const vespalib::Slime &slime)
{
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

struct FieldBlock {
    vespalib::string input;
    vespalib::Slime slime;
    search::RawBuf binary;
    vespalib::string json;

    explicit FieldBlock(const vespalib::string &jsonInput)
        : input(jsonInput), slime(), binary(1024), json()
    {
        size_t used = vespalib::slime::JsonFormat::decode(jsonInput, slime);
        EXPECT_TRUE(used > 0);
        json = toCompactJsonString(slime);
        search::SlimeOutputRawBufAdapter adapter(binary);
        vespalib::slime::BinaryFormat::encode(slime, adapter);
    }
    const char *data() const { return binary.GetDrainPos(); }
    size_t dataLen() const { return binary.GetUsedLen(); }
};

struct AttributeManagerFixture
{
    AttributeManager mgr;

    AttributeManagerFixture();

    ~AttributeManagerFixture();

    template <typename AttributeType, typename ValueType>
    void
    buildAttribute(const vespalib::string &name,
                   BasicType type,
                   std::vector<std::vector<ValueType>> values);

    void
    buildStringAttribute(const vespalib::string &name,
                         std::vector<std::vector<vespalib::string>> values);
    void
    buildFloatAttribute(const vespalib::string &name,
                        std::vector<std::vector<double>> values);

    void
    buildIntegerAttribute(const vespalib::string &name,
                          BasicType type,
                          std::vector<std::vector<IAttributeVector::largeint_t>> values);
};

AttributeManagerFixture::AttributeManagerFixture()
    : mgr()
{
    buildStringAttribute("array.name", {{"n1.1", "n1.2"}, {"n2"}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    buildIntegerAttribute("array.val", BasicType::Type::INT8, {{ 10, 11}, {20, 21 }, {30}, { getUndefined<int8_t>(), 41}, {}});
    buildFloatAttribute("array.fval", {{ 110.0}, { 120.0, 121.0 }, { 130.0, 131.0}, { getUndefined<double>(), 141.0 }, {}});
}

AttributeManagerFixture::~AttributeManagerFixture() = default;

template <typename AttributeType, typename ValueType>
void
AttributeManagerFixture::buildAttribute(const vespalib::string &name,
                                        BasicType type,
                                        std::vector<std::vector<ValueType>> values)
{
    Config cfg(type, CollectionType::Type::ARRAY);
    auto attrBase = AttributeFactory::createAttribute(name, cfg);
    EXPECT_TRUE(attrBase);
    auto attr = std::dynamic_pointer_cast<AttributeType>(attrBase);
    EXPECT_TRUE(attr);
    attr->addReservedDoc();
    for (const auto &docValues : values) {
        uint32_t docId = 0;
        EXPECT_TRUE(attr->addDoc(docId));
        EXPECT_NOT_EQUAL(0u, docId);
        for (const auto &value : docValues) {
            attr->append(docId, value, 1);
        }
        attr->commit();
    }
    EXPECT_TRUE(mgr.add(attr));
}

void
AttributeManagerFixture::buildStringAttribute(const vespalib::string &name,
                                              std::vector<std::vector<vespalib::string>> values)
{
    buildAttribute<StringAttribute, vespalib::string>(name, BasicType::Type::STRING, std::move(values));
}

void
AttributeManagerFixture::buildFloatAttribute(const vespalib::string &name,
                                             std::vector<std::vector<double>> values)
{
    buildAttribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, std::move(values));
}

void
AttributeManagerFixture::buildIntegerAttribute(const vespalib::string &name,
                                               BasicType type,
                                               std::vector<std::vector<IAttributeVector::largeint_t>> values)
{
    buildAttribute<IntegerAttribute, IAttributeVector::largeint_t>(name, type, std::move(values));
}


class DummyStateCallback : public GetDocsumsStateCallback
{
public:
    void FillSummaryFeatures(GetDocsumsState *, IDocsumEnvironment *) override { }
    void FillRankFeatures(GetDocsumsState *, IDocsumEnvironment *) override { }
    void ParseLocation(GetDocsumsState *) override { }
    ~DummyStateCallback() override { }
};


struct Fixture
{
    AttributeManagerFixture             attrs;
    std::unique_ptr<IDocsumFieldWriter> writer;
    DummyStateCallback                  stateCallback;
    GetDocsumsState                     state;

    Fixture();
    ~Fixture();
    void assertWritten(const vespalib::string &exp, uint32_t docId);
};

Fixture::Fixture()
    : attrs(),
      writer(AttributeCombinerDFW::create("array", attrs.mgr)),
      stateCallback(),
      state(stateCallback)
{
    EXPECT_TRUE(writer->setFieldWriterStateIndex(0));
    state._attrCtx = attrs.mgr.createContext();
    state._fieldWriterStates.resize(1);
}

Fixture::~Fixture()
{
}

void
Fixture::assertWritten(const vespalib::string &expectedJson, uint32_t docId)
{
    vespalib::Slime target;
    vespalib::slime::SlimeInserter inserter(target);
    writer->insertField(docId, nullptr, &state, search::docsummary::RES_JSONSTRING, inserter);
    search::RawBuf binary(1024);
    vespalib::string json = toCompactJsonString(target);
    search::SlimeOutputRawBufAdapter adapter(binary);
    vespalib::slime::BinaryFormat::encode(target, adapter);
    FieldBlock block(expectedJson);
    if (!EXPECT_EQUAL(block.dataLen(), binary.GetUsedLen()) ||
        !EXPECT_EQUAL(0, memcmp(block.data(), binary.GetDrainPos(), block.dataLen()))) {
        LOG(error, "Expected '%s'", expectedJson.c_str());
        LOG(error, "Expected normalized '%s'", block.json.c_str());
        LOG(error, "Got '%s'", json.c_str());
    }
}

TEST_F("require that attributes combiner dfw generates correct slime output for array of struct", Fixture())
{
    f.assertWritten("[ { fval: 110.0, name: \"n1.1\", val: 10}, { name: \"n1.2\", val: 11}]", 1);
    f.assertWritten("[ { fval: 120.0, name: \"n2\", val: 20}, { fval: 121.0, val: 21 }]", 2);
    f.assertWritten("[ { fval: 130.0, name: \"n3.1\", val: 30}, { fval: 131.0, name: \"n3.2\"} ]", 3);
    f.assertWritten("[ { }, { fval: 141.0, name: \"n4.2\", val:  41} ]", 4);
    f.assertWritten("null", 5);
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
