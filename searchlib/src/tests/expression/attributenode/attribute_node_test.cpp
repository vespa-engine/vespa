// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/gtest/gtest.h>

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

std::string stringValue(const ResultNode &result, const IAttributeVector &attr) {
    if (result.inherits(EnumResultNode::classId)) {
        auto enumHandle = result.getEnum();
        return std::string(attr.getStringFromEnum(enumHandle));
    }
    char buf[100];
    BufferRef bref(&buf[0], sizeof(buf));
    auto sbuf = result.getString(bref);
    return std::string(sbuf.c_str(), sbuf.c_str() + sbuf.size());
}

struct AttributeManagerFixture
{
    AttributeManager mgr;

    AttributeManagerFixture();
    ~AttributeManagerFixture();
    template <typename AttributeType, typename ValueType>
    void buildAttribute(const std::string &name, BasicType type, std::vector<ValueType> values);
    void buildStringAttribute(const std::string &name, std::vector<std::string> values);
    void buildBoolAttribute(const std::string &name, std::vector<bool> values);
    void buildFloatAttribute(const std::string &name, std::vector<double> values);
    void buildIntegerAttribute(const std::string &name, BasicType type, std::vector<IAttributeVector::largeint_t> values);
    template <typename AttributeType, typename ValueType>
    void buildArrayAttribute(const std::string &name, BasicType type, std::vector<std::vector<ValueType>> values);
    void buildStringArrayAttribute(const std::string &name,std::vector<std::vector<std::string>> values);
    void buildFloatArrayAttribute(const std::string &name, std::vector<std::vector<double>> values);
    void buildIntegerArrayAttribute(const std::string &name, BasicType type, std::vector<std::vector<IAttributeVector::largeint_t>> values);
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
AttributeManagerFixture::buildAttribute(const std::string &name, BasicType type,
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
        EXPECT_NE(0u, docId);
        attr->update(docId, value);
        attr->commit();
    }
    EXPECT_TRUE(mgr.add(attr));
}

void
AttributeManagerFixture::buildStringAttribute(const std::string &name,
                                              std::vector<std::string> values)
{
    buildAttribute<StringAttribute, std::string>(name, BasicType::Type::STRING, std::move(values));
}

void
AttributeManagerFixture::buildFloatAttribute(const std::string &name,
                                             std::vector<double> values)
{
    buildAttribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, std::move(values));
}

void
AttributeManagerFixture::buildIntegerAttribute(const std::string &name, BasicType type,
                                               std::vector<IAttributeVector::largeint_t> values)
{
    buildAttribute<IntegerAttribute, IAttributeVector::largeint_t>(name, type, std::move(values));
}

void
AttributeManagerFixture::buildBoolAttribute(const std::string &name,
                                            std::vector<bool> values)
{
    buildAttribute<SingleBoolAttribute>(name, BasicType::BOOL, std::move(values));
}

template <typename AttributeType, typename ValueType>
void
AttributeManagerFixture::buildArrayAttribute(const std::string &name, BasicType type,
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
        EXPECT_NE(0u, docId);
        for (const auto &value : docValues) {
            attr->append(docId, value, 1);
        }
        attr->commit();
    }
    EXPECT_TRUE(mgr.add(attr));
}

void
AttributeManagerFixture::buildStringArrayAttribute(const std::string &name,
                                              std::vector<std::vector<std::string>> values)
{
    buildArrayAttribute<StringAttribute, std::string>(name, BasicType::Type::STRING, std::move(values));
}

void
AttributeManagerFixture::buildFloatArrayAttribute(const std::string &name,
                                             std::vector<std::vector<double>> values)
{
    buildArrayAttribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, std::move(values));
}

void
AttributeManagerFixture::buildIntegerArrayAttribute(const std::string &name,
                                                    BasicType type,
                                                    std::vector<std::vector<IAttributeVector::largeint_t>> values)
{
    buildArrayAttribute<IntegerAttribute, IAttributeVector::largeint_t>(name, type, std::move(values));
}

std::string preserve_accurate_types_string(bool preserve_accurate_types) {
    return preserve_accurate_types ? " with preserve accurate types" : " without preserve accurate types";
}

std::string use_enum_opt_string(bool use_enum_optimization) {
    return use_enum_optimization ? " with enum optimization" : " without enum optimization";
}

class AttributeNodeTest : public ::testing::Test
{
protected:
    AttributeManagerFixture             attrs;
    AttributeContext                    context;
    AttributeNodeTest();
    ~AttributeNodeTest() override;
    std::unique_ptr<AttributeNode> makeNode(const std::string &attributeName, bool useEnumOptimiation = false, bool preserveAccurateTypes = false);
    void assertInts(std::vector<IAttributeVector::largeint_t> expVals, const std::string &attributteName, bool preserveAccurateTypes = false);
    void assertBools(std::vector<bool> expVals, const std::string &attributteName, bool preserveAccurateTypes = false);
    void assertStrings(std::vector<std::string> expVals, const std::string &attributteName);
    void assertFloats(std::vector<double> expVals, const std::string &attributteName);
    void assertIntArrays(std::vector<std::vector<IAttributeVector::largeint_t>> expVals, const std::string &attributteName, bool preserveAccurateTypes = false);
    void assertStringArrays(std::vector<std::vector<std::string>> expVals, const std::string &attributteName, bool useEnumOptimization = false);
    void assertFloatArrays(std::vector<std::vector<double>> expVals, const std::string &attributteName);
private:
    void assertStrings(std::vector<std::string> expVals, const std::string &attributteName, bool useEnumOptimization);
};

AttributeNodeTest::AttributeNodeTest()
    : attrs(),
      context(attrs.mgr)
{
}

AttributeNodeTest::~AttributeNodeTest() = default;

std::unique_ptr<AttributeNode>
AttributeNodeTest::makeNode(const std::string &attributeName, bool useEnumOptimization, bool preserveAccurateTypes)
{
    std::unique_ptr<AttributeNode> node;
    if (attributeName.find('{') == std::string::npos) {
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
AttributeNodeTest::assertInts(std::vector<IAttributeVector::largeint_t> expVals, const std::string &attributeName, bool preserveAccurateTypes)
{
    SCOPED_TRACE("assertInts " + attributeName + preserve_accurate_types_string(preserveAccurateTypes));
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
        EXPECT_EQ(expDocVal, docVal);
    }
}

void
AttributeNodeTest::assertBools(std::vector<bool> expVals, const std::string &attributeName, bool preserveAccurateTypes)
{
    SCOPED_TRACE("assertBools " + attributeName + preserve_accurate_types_string(preserveAccurateTypes));
    auto node = makeNode(attributeName, false, preserveAccurateTypes);
    uint32_t docId = 0;
    for (const auto expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();

        ASSERT_TRUE(result.inherits(BoolResultNode::classId));
        const BoolResultNode & bResult = static_cast<const BoolResultNode &>(result);

        EXPECT_EQ(expDocVal, bResult.getBool());
    }
}

void
AttributeNodeTest::assertStrings(std::vector<std::string> expVals, const std::string &attributeName) {
    assertStrings(expVals, attributeName, false);
    assertStrings(expVals, attributeName, true);
}

void
AttributeNodeTest::assertStrings(std::vector<std::string> expVals, const std::string &attributeName, bool useEnumOptimization)
{
    SCOPED_TRACE("assertStrings " + attributeName + use_enum_opt_string(useEnumOptimization));
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
            EXPECT_EQ(result.getEnum(), enumVal);
        } else {
            ASSERT_TRUE(result.inherits(StringResultNode::classId));
        }
        std::string docVal = stringValue(result, *node->getAttribute());
        EXPECT_EQ(expDocVal, docVal);
    }
}

void
AttributeNodeTest::assertFloats(std::vector<double> expVals, const std::string &attributeName)
{
    SCOPED_TRACE("assertFloats " + attributeName);
    auto node = makeNode(attributeName);
    uint32_t docId = 0;
    for (const auto &expDocVal : expVals) {
        ++docId;
        node->setDocId(docId);
        node->execute();
        const auto &result = *node->getResult();
        ASSERT_TRUE(result.inherits(FloatResultNode::classId));
        double docVal = result.getFloat();
        EXPECT_EQ(std::isnan(expDocVal), std::isnan(docVal));
        if (!std::isnan(expDocVal)) {
            EXPECT_EQ(expDocVal, docVal);
        }
    }
}

void
AttributeNodeTest::assertIntArrays(std::vector<std::vector<IAttributeVector::largeint_t>> expVals, const std::string &attributeName, bool preserveAccurateTypes)
{
    SCOPED_TRACE("assertIntArrays " + attributeName + preserve_accurate_types_string(preserveAccurateTypes));
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
        EXPECT_EQ(expDocVals, docVals);
    }
}

void
AttributeNodeTest::assertStringArrays(std::vector<std::vector<std::string>> expVals, const std::string &attributeName, bool useEnumOptimization)
{
    SCOPED_TRACE("assertStringArrays " + attributeName + use_enum_opt_string(useEnumOptimization));
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
        std::vector<std::string> docVals;
        for (size_t i = 0; i < resultVector.size(); ++i) {
            docVals.push_back(stringValue(resultVector.get(i), *node->getAttribute()));
        }
        EXPECT_EQ(expDocVals, docVals);
    }
}

void
AttributeNodeTest::assertFloatArrays(std::vector<std::vector<double>> expVals, const std::string &attributeName)
{
    SCOPED_TRACE("assertFloatArrays " + attributeName);
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
        EXPECT_EQ(expDocVals.size(), docVals.size());
        for (size_t i = 0; i < expDocVals.size(); ++i) {
            EXPECT_EQ(std::isnan(expDocVals[i]), std::isnan(docVals[i]));
            if (!std::isnan(expDocVals[i])) {
                EXPECT_EQ(expDocVals[i], docVals[i]);
            }
        }
    }
}

TEST_F(AttributeNodeTest, test_single_values)
{
    assertBools({ true, false,false,true,true,false }, "bfield");
    assertBools({ true, false,false,true,true,false }, "bfield", true);
    assertInts({ 10, getUndefined<int8_t>()}, "ifield");
    assertInts({ 10, getUndefined<int8_t>()}, "ifield", true);
    assertStrings({ "n1", "" }, "sfield");
    assertFloats({ 110.0, getUndefined<double>() }, "ffield");
}

TEST_F(AttributeNodeTest, Test_array_values)
{
    assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "array.val");
    assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "array.val", true);
    assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "array.name");
    assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "array.name", true);
    assertFloatArrays({{ 110.0}, { 120.0, 121.0 }, {}}, "array.fval");
    assertStringArrays({{"k1.1", "k1.2"}, {"k2"}, {}}, "smap.key");
    assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "smap.value.name");
    assertIntArrays({{ 10, 11}, {20, 21 }, {}}, "smap.value.val");
    assertFloatArrays({{ 110.0}, { 120.0, 121.0 }, {}}, "smap.value.fval");
    assertStringArrays({{"k1.1", "k1.2"}, {"k2"}, {}}, "map.key");
    assertStringArrays({{"n1.1", "n1.2"}, {"n2"}, {}}, "map.value");
}

TEST_F(AttributeNodeTest, test_keyed_values)
{
    assertStrings({"n1.1", "", ""}, "smap{\"k1.1\"}.name");
    assertStrings({"n1.2", "", ""}, "smap{\"k1.2\"}.name");
    assertStrings({"", "n2", ""}, "smap{\"k2\"}.name");
    assertStrings({"", "", ""}, "smap{\"k5\"}.name");
    assertFloats({ 110.0, getUndefined<double>(), getUndefined<double>()}, "smap{\"k1.1\"}.fval");
    assertFloats({ getUndefined<double>(), getUndefined<double>(), getUndefined<double>()}, "smap{\"k1.2\"}.fval");
    assertFloats({ getUndefined<double>(), 120.0, getUndefined<double>()}, "smap{\"k2\"}.fval");
    assertFloats({ getUndefined<double>(), getUndefined<double>(), getUndefined<double>()}, "smap{\"k5\"}.fval");
    assertInts({ 10, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k1.1\"}.val");
    assertInts({ 11, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k1.2\"}.val");
    assertInts({ getUndefined<int8_t>(), 20, getUndefined<int8_t>()}, "smap{\"k2\"}.val");
    assertInts({ getUndefined<int8_t>(), getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{\"k5\"}.val");
    assertStrings({"n1.1", "", ""}, "map{\"k1.1\"}");
    assertStrings({"n1.2", "", ""}, "map{\"k1.2\"}");
    assertStrings({"", "n2", ""}, "map{\"k2\"}");
    assertStrings({"", "", ""}, "map{\"k5\"}");
}

TEST_F(AttributeNodeTest, test_indirectly_keyed_values)
{
    assertStrings({"n1.2", "n2", ""}, "map{attribute(keyfield1)}");
    assertStrings({"n1.1", "", ""}, "map{attribute(keyfield2)}");
    assertStrings({"n1.2", "n2", ""}, "smap{attribute(keyfield1)}.name");
    assertStrings({"n1.1", "", ""}, "smap{attribute(keyfield2)}.name");
    assertFloats({ getUndefined<double>(), 120.0, getUndefined<double>()}, "smap{attribute(keyfield1)}.fval");
    assertFloats({ 110.0, getUndefined<double>(), getUndefined<double>()}, "smap{attribute(keyfield2)}.fval");
    assertInts({ 11, 20, getUndefined<int8_t>()}, "smap{attribute(keyfield1)}.val");
    assertInts({ 10, getUndefined<int8_t>(), getUndefined<int8_t>()}, "smap{attribute(keyfield2)}.val");
}

}

GTEST_MAIN_RUN_ALL_TESTS()
