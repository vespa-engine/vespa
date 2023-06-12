// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/singleboolattribute.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/test/make_attribute_map_lookup_node.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_node_test");

using search::AttributeContext;
using search::AttributeFactory;
using search::AttributeManager;
using search::AttributeVector;
using search::IntegerAttribute;
using search::FloatingPointAttribute;
using search::StringAttribute;
using search::SingleBoolAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::getUndefined;
using search::expression::AttributeNode;
using search::expression::EnumResultNode;
using search::expression::EnumResultNodeVector;
using search::expression::FloatResultNode;
using search::expression::FloatResultNodeVector;
using search::expression::Int8ResultNode;
using search::expression::BoolResultNode;
using search::expression::Int8ResultNodeVector;
using search::expression::IntegerResultNodeVector;
using search::expression::IntegerResultNode;
using search::expression::ResultNode;
using search::expression::ResultNodeVector;
using search::expression::StringResultNode;
using search::expression::StringResultNodeVector;
using search::expression::test::makeAttributeMapLookupNode;
using vespalib::BufferRef;

namespace {

vespalib::string stringValue(const ResultNode &result, const IAttributeVector &attr) {
    if (result.inherits(EnumResultNode::classId)) {
        auto enumHandle = result.getEnum();
        return vespalib::string(attr.getStringFromEnum(enumHandle));
    }
    char buf[100];
    BufferRef bref(&buf[0], sizeof(buf));
    auto sbuf = result.getString(bref);
    return vespalib::string(sbuf.c_str(), sbuf.c_str() + sbuf.size());
}

struct AttributeManagerFixture
{
    AttributeManager mgr;

    AttributeManagerFixture();
    ~AttributeManagerFixture();
    template <typename AttributeType, typename ValueType>
    void buildAttribute(const vespalib::string &name, BasicType type, std::vector<ValueType> values);
    void buildStringAttribute(const vespalib::string &name, std::vector<vespalib::string> values);
    void buildBoolAttribute(const vespalib::string &name, std::vector<bool> values);
    void buildFloatAttribute(const vespalib::string &name, std::vector<double> values);
    void buildIntegerAttribute(const vespalib::string &name, BasicType type, std::vector<IAttributeVector::largeint_t> values);
    template <typename AttributeType, typename ValueType>
    void buildArrayAttribute(const vespalib::string &name, BasicType type, std::vector<std::vector<ValueType>> values);
    void buildStringArrayAttribute(const vespalib::string &name,std::vector<std::vector<vespalib::string>> values);
    void buildFloatArrayAttribute(const vespalib::string &name, std::vector<std::vector<double>> values);
    void buildIntegerArrayAttribute(const vespalib::string &name, BasicType type, std::vector<std::vector<IAttributeVector::largeint_t>> values);
};

AttributeManagerFixture::AttributeManagerFixture()
    : mgr()
{
    buildStringAttribute("sfield", { "n1", ""});
    buildBoolAttribute("bfield", { true, false,false,true,true,false });
    buildIntegerAttribute("ifield", BasicType::INT8, { 10, getUndefined<int8_t>() });
    buildFloatAttribute("ffield", { 110.0, getUndefined<double>() });
    buildStringArrayAttribute("array.name", {{"n1.1", "n1.2"}, {"n2"}, {}});
    buildIntegerArrayAttribute("array.val", BasicType::INT8, {{ 10, 11}, {20, 21 }, {}});
    buildFloatArrayAttribute("array.fval", {{ 110.0}, { 120.0, 121.0 }, {}});
    buildStringArrayAttribute("smap.key", {{"k1.1", "k1.2"}, {"k2"}, {}});
    buildStringArrayAttribute("smap.value.name", {{"n1.1", "n1.2"}, {"n2"}, {}});
    buildIntegerArrayAttribute("smap.value.val", BasicType::INT8, {{ 10, 11}, {20, 21 }, {}});
    buildFloatArrayAttribute("smap.value.fval", {{ 110.0}, { 120.0, 121.0 }, {}});
    buildStringArrayAttribute("map.key", {{"k1.1", "k1.2"}, {"k2"}, {}});
    buildStringArrayAttribute("map.value", {{"n1.1", "n1.2"}, {"n2"}, {}});
    buildStringAttribute("keyfield1", {"k1.2", "k2", "k3"});
    buildStringAttribute("keyfield2", {"k1.1", "k1", "k1"});
}

AttributeManagerFixture::~AttributeManagerFixture() = default;

template <typename AttributeType, typename ValueType>
void
AttributeManagerFixture::buildAttribute(const vespalib::string &name, BasicType type,
                                        std::vector<ValueType> values)
{
    Config cfg(type, CollectionType::Type::SINGLE);
    auto attrBase = AttributeFactory::createAttribute(name, cfg);
    EXPECT_TRUE(attrBase);
    auto attr = std::dynamic_pointer_cast<AttributeType>(attrBase);
    EXPECT_TRUE(attr);
    attr->addReservedDoc();
    for (const std::conditional_t<std::is_same_v<bool, ValueType>, bool, ValueType&> value : values) {
        uint32_t docId = 0;
        EXPECT_TRUE(attr->addDoc(docId));
        EXPECT_NOT_EQUAL(0u, docId);
        attr->update(docId, value);
        attr->commit();
    }
    EXPECT_TRUE(mgr.add(attr));
}

void
AttributeManagerFixture::buildStringAttribute(const vespalib::string &name,
                                              std::vector<vespalib::string> values)
{
    buildAttribute<StringAttribute, vespalib::string>(name, BasicType::Type::STRING, std::move(values));
}

void
AttributeManagerFixture::buildFloatAttribute(const vespalib::string &name,
                                             std::vector<double> values)
{
    buildAttribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, std::move(values));
}

void
AttributeManagerFixture::buildIntegerAttribute(const vespalib::string &name, BasicType type,
                                               std::vector<IAttributeVector::largeint_t> values)
{
    buildAttribute<IntegerAttribute, IAttributeVector::largeint_t>(name, type, std::move(values));
}

void
AttributeManagerFixture::buildBoolAttribute(const vespalib::string &name,
                                            std::vector<bool> values)
{
    buildAttribute<SingleBoolAttribute>(name, BasicType::BOOL, std::move(values));
}

template <typename AttributeType, typename ValueType>
void
AttributeManagerFixture::buildArrayAttribute(const vespalib::string &name, BasicType type,
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
AttributeManagerFixture::buildStringArrayAttribute(const vespalib::string &name,
                                              std::vector<std::vector<vespalib::string>> values)
{
    buildArrayAttribute<StringAttribute, vespalib::string>(name, BasicType::Type::STRING, std::move(values));
}

void
AttributeManagerFixture::buildFloatArrayAttribute(const vespalib::string &name,
                                             std::vector<std::vector<double>> values)
{
    buildArrayAttribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, std::move(values));
}

void
AttributeManagerFixture::buildIntegerArrayAttribute(const vespalib::string &name,
                                                    BasicType type,
                                                    std::vector<std::vector<IAttributeVector::largeint_t>> values)
{
    buildArrayAttribute<IntegerAttribute, IAttributeVector::largeint_t>(name, type, std::move(values));
}


class Fixture
{
public:
    AttributeManagerFixture             attrs;
    AttributeContext                    context;
    Fixture();
    ~Fixture();
    std::unique_ptr<AttributeNode> makeNode(const vespalib::string &attributeName, bool useEnumOptimiation = false, bool preserveAccurateTypes = false);
    void assertInts(std::vector<IAttributeVector::largeint_t> expVals, const vespalib::string &attributteName, bool preserveAccurateTypes = false);
    void assertBools(std::vector<bool> expVals, const vespalib::string &attributteName, bool preserveAccurateTypes = false);
    void assertStrings(std::vector<vespalib::string> expVals, const vespalib::string &attributteName);
    void assertFloats(std::vector<double> expVals, const vespalib::string &attributteName);
    void assertIntArrays(std::vector<std::vector<IAttributeVector::largeint_t>> expVals, const vespalib::string &attributteName, bool preserveAccurateTypes = false);
    void assertStringArrays(std::vector<std::vector<vespalib::string>> expVals, const vespalib::string &attributteName, bool useEnumOptimization = false);
    void assertFloatArrays(std::vector<std::vector<double>> expVals, const vespalib::string &attributteName);
private:
    void assertStrings(std::vector<vespalib::string> expVals, const vespalib::string &attributteName, bool useEnumOptimization);
};

Fixture::Fixture()
    : attrs(),
      context(attrs.mgr)
{
}

Fixture::~Fixture() = default;

std::unique_ptr<AttributeNode>
Fixture::makeNode(const vespalib::string &attributeName, bool useEnumOptimization, bool preserveAccurateTypes)
{
    std::unique_ptr<AttributeNode> node;
    if (attributeName.find('{') == vespalib::string::npos) {
        node = std::make_unique<AttributeNode>(attributeName);
    } else {
        node = makeAttributeMapLookupNode(attributeName);
    }
    node->enableEnumOptimization(useEnumOptimization);
    AttributeNode::Configure configure(context);
    node->select(configure, configure);
    node->prepare(preserveAccurateTypes);
    return node;
}


void
Fixture::assertInts(std::vector<IAttributeVector::largeint_t> expVals, const vespalib::string &attributeName, bool preserveAccurateTypes)
{
    auto node = makeNode(attributeName, false, preserveAccurateTypes);
    uint32_t docId = 0;
    for (const auto &expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        if (preserveAccurateTypes) {
            ASSERT_TRUE(result.inherits(Int8ResultNode::classId));
        } else {
            ASSERT_TRUE(result.inherits(IntegerResultNode::classId));
        }
        IAttributeVector::largeint_t docVal = result.getInteger();
        EXPECT_EQUAL(expDocVal, docVal);
    }
}

void
Fixture::assertBools(std::vector<bool> expVals, const vespalib::string &attributeName, bool preserveAccurateTypes)
{
    auto node = makeNode(attributeName, false, preserveAccurateTypes);
    uint32_t docId = 0;
    for (const auto expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();

        ASSERT_TRUE(result.inherits(BoolResultNode::classId));
        const BoolResultNode & bResult = static_cast<const BoolResultNode &>(result);

        EXPECT_EQUAL(expDocVal, bResult.getBool());
    }
}

void
Fixture::assertStrings(std::vector<vespalib::string> expVals, const vespalib::string &attributeName) {
    assertStrings(expVals, attributeName, false);
    assertStrings(expVals, attributeName, true);
}

void
Fixture::assertStrings(std::vector<vespalib::string> expVals, const vespalib::string &attributeName, bool useEnumOptimization)
{
    auto node = makeNode(attributeName, useEnumOptimization);
    uint32_t docId = 0;
    for (const auto &expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        if (useEnumOptimization) {
            ASSERT_TRUE(result.inherits(EnumResultNode::classId));
            search::enumstore::EnumHandle enumVal(0);
            ASSERT_TRUE(node->getAttribute()->findEnum(expDocVal.c_str(), enumVal));
            EXPECT_EQUAL(result.getEnum(), enumVal);
        } else {
            ASSERT_TRUE(result.inherits(StringResultNode::classId));
        }
        vespalib::string docVal = stringValue(result, *node->getAttribute());
        EXPECT_EQUAL(expDocVal, docVal);
    }
}

void
Fixture::assertFloats(std::vector<double> expVals, const vespalib::string &attributeName)
{
    auto node = makeNode(attributeName);
    uint32_t docId = 0;
    for (const auto &expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        ASSERT_TRUE(result.inherits(FloatResultNode::classId));
        double docVal = result.getFloat();
        EXPECT_EQUAL(std::isnan(expDocVal), std::isnan(docVal));
        if (!std::isnan(expDocVal)) {
            EXPECT_EQUAL(expDocVal, docVal);
        }
    }
}

void
Fixture::assertIntArrays(std::vector<std::vector<IAttributeVector::largeint_t>> expVals, const vespalib::string &attributeName, bool preserveAccurateTypes)
{
    auto node = makeNode(attributeName, false, preserveAccurateTypes);
    uint32_t docId = 0;
    for (const auto &expDocVals : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        ASSERT_TRUE(result.inherits(ResultNodeVector::classId));
        const auto &resultVector = static_cast<const ResultNodeVector &>(result);
        if (preserveAccurateTypes) {
            ASSERT_TRUE(result.inherits(Int8ResultNodeVector::classId));
        } else {
            ASSERT_TRUE(result.inherits(IntegerResultNodeVector::classId));
        }
        std::vector<IAttributeVector::largeint_t> docVals;
        for (size_t i = 0; i < resultVector.size(); ++i) {
            docVals.push_back(resultVector.get(i).getInteger());
        }
        EXPECT_EQUAL(expDocVals, docVals);
    }
}

void
Fixture::assertStringArrays(std::vector<std::vector<vespalib::string>> expVals, const vespalib::string &attributeName, bool useEnumOptimization)
{
    auto node = makeNode(attributeName, useEnumOptimization);
    uint32_t docId = 0;
    for (const auto &expDocVals : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        ASSERT_TRUE(result.inherits(ResultNodeVector::classId));
        const auto &resultVector = static_cast<const ResultNodeVector &>(result);
        if (useEnumOptimization) {
            ASSERT_TRUE(result.inherits(EnumResultNodeVector::classId));
        } else {
            ASSERT_TRUE(result.inherits(StringResultNodeVector::classId));
        }
        std::vector<vespalib::string> docVals;
        for (size_t i = 0; i < resultVector.size(); ++i) {
            docVals.push_back(stringValue(resultVector.get(i), *node->getAttribute()));
        }
        EXPECT_EQUAL(expDocVals, docVals);
    }
}

void
Fixture::assertFloatArrays(std::vector<std::vector<double>> expVals, const vespalib::string &attributeName)
{
    auto node = makeNode(attributeName);
    uint32_t docId = 0;
    for (const auto &expDocVals : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        ASSERT_TRUE(result.inherits(ResultNodeVector::classId));
        const auto &resultVector = static_cast<const ResultNodeVector &>(result);
        ASSERT_TRUE(result.inherits(FloatResultNodeVector::classId));
        std::vector<double> docVals;
        for (size_t i = 0; i < resultVector.size(); ++i) {
            docVals.push_back(resultVector.get(i).getFloat());
        }
        EXPECT_EQUAL(expDocVals.size(), docVals.size());
        for (size_t i = 0; i < expDocVals.size(); ++i) {
            EXPECT_EQUAL(std::isnan(expDocVals[i]), std::isnan(docVals[i]));
            if (!std::isnan(expDocVals[i])) {
                EXPECT_EQUAL(expDocVals[i], docVals[i]);
            }
        }
    }
}

TEST_F("test single values", Fixture)
{
    TEST_DO(f.assertBools({ true, false,false,true,true,false }, "bfield"));
    TEST_DO(f.assertBools({ true, false,false,true,true,false }, "bfield", true));
    TEST_DO(f.assertInts({ 10, getUndefined<int8_t>()}, "ifield"));
    TEST_DO(f.assertInts({ 10, getUndefined<int8_t>()}, "ifield", true));
    TEST_DO(f.assertStrings({ "n1", "" }, "sfield"));
    TEST_DO(f.assertFloats({ 110.0, getUndefined<double>() }, "ffield"));
}

TEST_F("Test array values", Fixture)
{
    TEST_DO(f.assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "array.val"));
    TEST_DO(f.assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "array.val", true));
    TEST_DO(f.assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "array.name"));
    TEST_DO(f.assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "array.name", true));
    TEST_DO(f.assertFloatArrays({{ 110.0}, { 120.0, 121.0 }, {}}, "array.fval"));
    TEST_DO(f.assertStringArrays({{"k1.1", "k1.2"}, {"k2"}, {}}, "smap.key"));
    TEST_DO(f.assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "smap.value.name"));
    TEST_DO(f.assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "smap.value.val"));
    TEST_DO(f.assertFloatArrays({{ 110.0}, { 120.0, 121.0 }, {}}, "smap.value.fval"));
    TEST_DO(f.assertStringArrays({{"k1.1", "k1.2"}, {"k2"}, {}}, "map.key"));
    TEST_DO(f.assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "map.value"));
}

TEST_F("test keyed values", Fixture)
{
    TEST_DO(f.assertStrings({"n1.1", "", ""}, "smap{\"k1.1\"}.name"));
    TEST_DO(f.assertStrings({"n1.2", "", ""}, "smap{\"k1.2\"}.name"));
    TEST_DO(f.assertStrings({"", "n2", ""}, "smap{\"k2\"}.name"));
    TEST_DO(f.assertStrings({"", "", ""}, "smap{\"k5\"}.name"));
    TEST_DO(f.assertFloats({ 110.0, getUndefined<double>(), getUndefined<double>()}, "smap{\"k1.1\"}.fval"));
    TEST_DO(f.assertFloats({ getUndefined<double>(), getUndefined<double>(), getUndefined<double>()}, "smap{\"k1.2\"}.fval"));
    TEST_DO(f.assertFloats({ getUndefined<double>(), 120.0, getUndefined<double>()}, "smap{\"k2\"}.fval"));
    TEST_DO(f.assertFloats({ getUndefined<double>(), getUndefined<double>(), getUndefined<double>()}, "smap{\"k5\"}.fval"));
    TEST_DO(f.assertInts({ 10, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k1.1\"}.val"));
    TEST_DO(f.assertInts({ 11, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k1.2\"}.val"));
    TEST_DO(f.assertInts({ getUndefined<int8_t>(), 20, getUndefined<int8_t>()}, "smap{\"k2\"}.val"));
    TEST_DO(f.assertInts({ getUndefined<int8_t>(), getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k5\"}.val"));
    TEST_DO(f.assertStrings({"n1.1", "", ""}, "map{\"k1.1\"}"));
    TEST_DO(f.assertStrings({"n1.2", "", ""}, "map{\"k1.2\"}"));
    TEST_DO(f.assertStrings({"", "n2", ""}, "map{\"k2\"}"));
    TEST_DO(f.assertStrings({"", "", ""}, "map{\"k5\"}"));
}

TEST_F("test indirectly keyed values", Fixture)
{
    TEST_DO(f.assertStrings({"n1.2", "n2", ""}, "map{attribute(keyfield1)}"));
    TEST_DO(f.assertStrings({"n1.1", "", ""}, "map{attribute(keyfield2)}"));
    TEST_DO(f.assertStrings({"n1.2", "n2", ""}, "smap{attribute(keyfield1)}.name"));
    TEST_DO(f.assertStrings({"n1.1", "", ""}, "smap{attribute(keyfield2)}.name"));
    TEST_DO(f.assertFloats({ getUndefined<double>(), 120.0, getUndefined<double>()}, "smap{attribute(keyfield1)}.fval"));
    TEST_DO(f.assertFloats({ 110.0, getUndefined<double>(), getUndefined<double>()}, "smap{attribute(keyfield2)}.fval"));
    TEST_DO(f.assertInts({ 11, 20, getUndefined<int8_t>()}, "smap{attribute(keyfield1)}.val"));
    TEST_DO(f.assertInts({ 10, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{attribute(keyfield2)}.val"));
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
