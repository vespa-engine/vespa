// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("extendattribute_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/extendableattributes.h>

namespace search {

class ExtendAttributeTest : public vespalib::TestApp
{
private:
    template <typename Attribute>
    void testExtendInteger(Attribute & attr);
    template <typename Attribute>
    void testExtendFloat(Attribute & attr);
    template <typename Attribute>
    void testExtendString(Attribute & attr);

public:
    int Main() override;
};

template <typename Attribute>
void ExtendAttributeTest::testExtendInteger(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQUAL(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 0u);
    EXPECT_EQUAL(attr.getNumDocs(), 1u);
    attr.add(1, 10);
    EXPECT_EQUAL(attr.getInt(0), 1);
    attr.add(2, 20);
    EXPECT_EQUAL(attr.getInt(0), attr.hasMultiValue() ? 1 : 2);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[2];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQUAL(v[0].getValue(), 1);
        EXPECT_EQUAL(v[1].getValue(), 2);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 10);
            EXPECT_EQUAL(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 1u);
    EXPECT_EQUAL(attr.getNumDocs(), 2u);
    attr.add(3, 30);
    EXPECT_EQUAL(attr.getInt(1), 3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[1];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQUAL(v[0].getValue(), 3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendFloat(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQUAL(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 0u);
    EXPECT_EQUAL(attr.getNumDocs(), 1u);
    attr.add(1.7, 10);
    EXPECT_EQUAL(attr.getInt(0), 1);
    EXPECT_EQUAL(attr.getFloat(0), 1.7);
    attr.add(2.3, 20);
    EXPECT_EQUAL(attr.getFloat(0), attr.hasMultiValue() ? 1.7 : 2.3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[2];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQUAL(v[0].getValue(), 1.7);
        EXPECT_EQUAL(v[1].getValue(), 2.3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 10);
            EXPECT_EQUAL(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 1u);
    EXPECT_EQUAL(attr.getNumDocs(), 2u);
    attr.add(3.6, 30);
    EXPECT_EQUAL(attr.getFloat(1), 3.6);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[1];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQUAL(v[0].getValue(), 3.6);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendString(Attribute & attr)
{
    uint32_t docId(0);
    EXPECT_EQUAL(attr.getNumDocs(), 0u);
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 0u);
    EXPECT_EQUAL(attr.getNumDocs(), 1u);
    attr.add("1.7", 10);
    EXPECT_EQUAL(std::string(attr.getString(0, NULL, 0)), "1.7");
    attr.add("2.3", 20);
    EXPECT_EQUAL(std::string(attr.getString(0, NULL, 0)), attr.hasMultiValue() ? "1.7" : "2.3");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[2];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQUAL(v[0].getValue(), "1.7");
        EXPECT_EQUAL(v[1].getValue(), "2.3");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 10);
            EXPECT_EQUAL(v[1].getWeight(), 20);
        }
    }
    attr.addDoc(docId);
    EXPECT_EQUAL(docId, 1u);
    EXPECT_EQUAL(attr.getNumDocs(), 2u);
    attr.add("3.6", 30);
    EXPECT_EQUAL(std::string(attr.getString(1, NULL, 0)), "3.6");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[1];
        EXPECT_EQUAL((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQUAL(v[0].getValue(), "3.6");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQUAL(v[0].getWeight(), 30);
        }
    }
}

int
ExtendAttributeTest::Main()
{
    TEST_INIT("extendattribute_test");

    SingleIntegerExtAttribute siattr("si1");
    MultiIntegerExtAttribute miattr("mi1");
    WeightedSetIntegerExtAttribute wsiattr("wsi1");
    EXPECT_TRUE( ! siattr.hasMultiValue() );
    EXPECT_TRUE( miattr.hasMultiValue() );
    EXPECT_TRUE( wsiattr.hasWeightedSetType() );
    testExtendInteger(siattr);
    testExtendInteger(miattr);
    testExtendInteger(wsiattr);

    SingleFloatExtAttribute sdattr("sd1");
    MultiFloatExtAttribute mdattr("md1");
    WeightedSetFloatExtAttribute wsdattr("wsd1");
    EXPECT_TRUE( ! sdattr.hasMultiValue() );
    EXPECT_TRUE( mdattr.hasMultiValue() );
    EXPECT_TRUE( wsdattr.hasWeightedSetType() );
    testExtendFloat(sdattr);
    testExtendFloat(mdattr);
    testExtendFloat(wsdattr);

    SingleStringExtAttribute ssattr("ss1");
    MultiStringExtAttribute msattr("ms1");
    WeightedSetStringExtAttribute wssattr("wss1");
    EXPECT_TRUE( ! ssattr.hasMultiValue() );
    EXPECT_TRUE( msattr.hasMultiValue() );
    EXPECT_TRUE( wssattr.hasWeightedSetType() );
    testExtendString(ssattr);
    testExtendString(msattr);
    testExtendString(wssattr);

    TEST_DONE();
}

}

TEST_APPHOOK(search::ExtendAttributeTest);
