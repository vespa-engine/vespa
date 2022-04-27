// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/attribute/extendableattributes.h>

namespace search {

class ExtendAttributeTest : public ::testing::Test
{
protected:
    ExtendAttributeTest() = default;
    ~ExtendAttributeTest() override = default;
    template <typename Attribute>
    void testExtendInteger(Attribute & attr);
    template <typename Attribute>
    void testExtendFloat(Attribute & attr);
    template <typename Attribute>
    void testExtendString(Attribute & attr);
};

template <typename Attribute>
void ExtendAttributeTest::testExtendInteger(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQ(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQ(docId, 0u);
    EXPECT_EQ(attr.getNumDocs(), 1u);
    attr.add(1, 10);
    EXPECT_EQ(attr.getInt(0), 1);
    attr.add(2, 20);
    EXPECT_EQ(attr.getInt(0), attr.hasMultiValue() ? 1 : 2);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), 1);
        EXPECT_EQ(v[1].getValue(), 2);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQ(docId, 1u);
    EXPECT_EQ(attr.getNumDocs(), 2u);
    attr.add(3, 30);
    EXPECT_EQ(attr.getInt(1), 3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), 3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendFloat(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQ(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQ(docId, 0u);
    EXPECT_EQ(attr.getNumDocs(), 1u);
    attr.add(1.7, 10);
    EXPECT_EQ(attr.getInt(0), 1);
    EXPECT_EQ(attr.getFloat(0), 1.7);
    attr.add(2.3, 20);
    EXPECT_EQ(attr.getFloat(0), attr.hasMultiValue() ? 1.7 : 2.3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), 1.7);
        EXPECT_EQ(v[1].getValue(), 2.3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQ(docId, 1u);
    EXPECT_EQ(attr.getNumDocs(), 2u);
    attr.add(3.6, 30);
    EXPECT_EQ(attr.getFloat(1), 3.6);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), 3.6);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendString(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQ(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQ(docId, 0u);
    EXPECT_EQ(attr.getNumDocs(), 1u);
    attr.add("1.7", 10);
    EXPECT_EQ(std::string(attr.getString(0, NULL, 0)), "1.7");
    attr.add("2.3", 20);
    EXPECT_EQ(std::string(attr.getString(0, NULL, 0)), attr.hasMultiValue() ? "1.7" : "2.3");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), "1.7");
        EXPECT_EQ(v[1].getValue(), "2.3");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQ(docId, 1u);
    EXPECT_EQ(attr.getNumDocs(), 2u);
    attr.add("3.6", 30);
    EXPECT_EQ(std::string(attr.getString(1, NULL, 0)), "3.6");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), "3.6");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}


TEST_F(ExtendAttributeTest, single_integer_ext_attribute)
{
    SingleIntegerExtAttribute siattr("si1");
    EXPECT_TRUE( ! siattr.hasMultiValue() );
    testExtendInteger(siattr);
}

TEST_F(ExtendAttributeTest, array_integer_ext_attribute)
{
    MultiIntegerExtAttribute miattr("mi1");
    EXPECT_TRUE( miattr.hasMultiValue() );
    testExtendInteger(miattr);
}

TEST_F(ExtendAttributeTest, weighted_set_integer_ext_attribute)
{
    WeightedSetIntegerExtAttribute wsiattr("wsi1");
    EXPECT_TRUE( wsiattr.hasWeightedSetType() );
    testExtendInteger(wsiattr);
}

TEST_F(ExtendAttributeTest, single_float_ext_attribute)
{
    SingleFloatExtAttribute sdattr("sd1");
    EXPECT_TRUE( ! sdattr.hasMultiValue() );
    testExtendFloat(sdattr);
}

TEST_F(ExtendAttributeTest, array_float_ext_attribute)
{
    MultiFloatExtAttribute mdattr("md1");
    EXPECT_TRUE( mdattr.hasMultiValue() );
    testExtendFloat(mdattr);
}

TEST_F(ExtendAttributeTest, weighted_set_float_ext_attribute)
{
    WeightedSetFloatExtAttribute wsdattr("wsd1");
    EXPECT_TRUE( wsdattr.hasWeightedSetType() );
    testExtendFloat(wsdattr);
}

TEST_F(ExtendAttributeTest, single_string_ext_attribute)
{
    SingleStringExtAttribute ssattr("ss1");
    EXPECT_TRUE( ! ssattr.hasMultiValue() );
    testExtendString(ssattr);
}

TEST_F(ExtendAttributeTest, array_string_ext_attribute)
{
    MultiStringExtAttribute msattr("ms1");
    EXPECT_TRUE( msattr.hasMultiValue() );
    testExtendString(msattr);
}

TEST_F(ExtendAttributeTest, weighted_set_string_ext_attribute)
{
    WeightedSetStringExtAttribute wssattr("wss1");
    EXPECT_TRUE( wssattr.hasWeightedSetType() );
    testExtendString(wssattr);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
