// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using vespalib::nbostream;
using namespace ::testing;

namespace document {

namespace {
template <typename T>
void deserialize(nbostream & stream, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
    DocumentTypeRepo repo;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}

template<typename Type1, typename Type2>
void verifyFailedAssignment(Type1& lval, const Type2& rval)
{
    try{
        lval = rval;
        FAIL() << "Failed to check type equality in operator=";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
    try{
        lval.assign(rval);
        FAIL() << "Failed to check type equality in assign()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
}

template<typename Type>
void verifyFailedUpdate(Type& lval, const FieldValue& rval)
{
    try{
        lval.add(rval);
        FAIL() << "Failed to check type equality in add()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("These types are not compatible"));
    }
    try{
        lval.contains(rval);
        FAIL() << "Failed to check type equality in contains()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("These types are not compatible"));
    }
    try{
        lval.remove(rval);
        FAIL() << "Failed to check type equality in remove()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("These types are not compatible"));
    }
}
}  // namespace

TEST(WeightedSetFieldValueTest, testWeightedSet)
{
    WeightedSetDataType type(*DataType::INT, false, false);
    WeightedSetFieldValue value(type);

        // Initially empty
    EXPECT_EQ(size_t(0), value.size());
    EXPECT_TRUE(value.isEmpty());
    EXPECT_TRUE(!value.contains(IntFieldValue(1)));

    EXPECT_TRUE(value.add(IntFieldValue(1)));

        // Not empty
    EXPECT_EQ(size_t(1), value.size());
    EXPECT_TRUE(!value.isEmpty());
    EXPECT_TRUE(value.contains(IntFieldValue(1)));

        // Adding some more
    EXPECT_TRUE(value.add(IntFieldValue(2), 5));
    EXPECT_TRUE(value.add(IntFieldValue(3), 6));

        // Not empty
    EXPECT_EQ(size_t(3), value.size());
    EXPECT_TRUE(!value.isEmpty());
    EXPECT_EQ(1, value.get(IntFieldValue(1)));
    EXPECT_EQ(5, value.get(IntFieldValue(2)));
    EXPECT_EQ(6, value.get(IntFieldValue(3)));

        // Serialize & equality
    nbostream buffer(value.serialize());
    WeightedSetFieldValue value2(type);
    EXPECT_TRUE(value != value2);
    deserialize(buffer, value2);
    EXPECT_EQ(value, value2);

        // Various ways of removing
    {
            // By value
        buffer.rp(0);
        deserialize(buffer, value2);
        EXPECT_EQ(size_t(3), value2.size());
        EXPECT_TRUE(value2.remove(IntFieldValue(1)));
        EXPECT_TRUE(!value2.contains(IntFieldValue(1)));
        EXPECT_EQ(size_t(2), value2.size());

            // Clearing all
        buffer.rp(0);
        deserialize(buffer, value2);
        value2.clear();
        EXPECT_TRUE(!value2.contains(IntFieldValue(1)));
        EXPECT_EQ(size_t(0), value2.size());
        EXPECT_TRUE(value2.isEmpty());
    }

        // Updating
    value2 = value;
    EXPECT_EQ(value, value2);
    EXPECT_TRUE(!value2.add(IntFieldValue(2), 10)); // false = overwritten
    EXPECT_TRUE(value2.add(IntFieldValue(17), 9)); // true = added new
    EXPECT_EQ(10, value2.get(IntFieldValue(2)));
    EXPECT_TRUE(value != value2);
    value2.assign(value);
    EXPECT_EQ(value, value2);
    WeightedSetFieldValue::UP valuePtr(value2.clone());
    EXPECT_EQ(value, *valuePtr);

        // Iterating
    const WeightedSetFieldValue& constVal(value);
    for(WeightedSetFieldValue::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        const FieldValue& fval1(*it->first);
        (void) fval1;
        EXPECT_TRUE(it->first->isA(FieldValue::Type::INT));
        const IntFieldValue& val = dynamic_cast<const IntFieldValue&>(*it->second);
        (void) val;
    }
    value2 = value;
    for(WeightedSetFieldValue::iterator it = value2.begin();
        it != value2.end(); ++it)
    {
        IntFieldValue& val = dynamic_cast<IntFieldValue&>(*it->second);
        val.setValue(7);
    }
    EXPECT_TRUE(value != value2);
    EXPECT_EQ(7, value2.get(IntFieldValue(2)));

        // Comparison
    value2 = value;
    EXPECT_EQ(0, value.compare(value2));
    value2.remove(IntFieldValue(1));
    EXPECT_TRUE(value.compare(value2) > 0);
    EXPECT_TRUE(value2.compare(value) < 0);
    value2 = value;
    value2.add(IntFieldValue(7));
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);

        // Output
    EXPECT_EQ(
            std::string(
                "WeightedSet<Int>(\n"
                "  1 - weight 1,\n"
                "  2 - weight 5,\n"
                "  3 - weight 6\n"
                ")"),
            value.toString(false));
    EXPECT_EQ(
            std::string(
                "  WeightedSet<Int>(\n"
                "..  1 - weight 1,\n"
                "..  2 - weight 5,\n"
                "..  3 - weight 6\n"
                "..)"),
            "  " + value.toString(true, ".."));
    EXPECT_EQ(
            std::string(
                "<value>\n"
                "  <item weight=\"1\">1</item>\n"
                "  <item weight=\"5\">2</item>\n"
                "  <item weight=\"6\">3</item>\n"
                "</value>"),
            value.toXml("  "));

        // Failure situations.

        // Refuse to accept non-weightedset types
    try{
        ArrayDataType arrayType(*DataType::STRING);
        WeightedSetFieldValue value6(arrayType);
        FAIL() << "Didn't complain about non-weightedset type";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot generate a weighted set value with "
                                        "non-weighted set type"));
    }

        // Verify that datatypes are verified
        // Created almost equal types to try to get it to fail
    WeightedSetDataType type1(*DataType::INT, false, false);
    WeightedSetDataType type2(*DataType::LONG, false, false);
    WeightedSetDataType type3(type1, false, false);
    WeightedSetDataType type4(type2, false, false);
    WeightedSetDataType type5(type2, false, true);
    WeightedSetDataType type6(type2, true, false);

        // Type differs in nested of nested type (verify recursivity)
    {
        WeightedSetFieldValue value3(type3);
        WeightedSetFieldValue value4(type4);
        verifyFailedAssignment(value3, value4);
    }
        // Type arguments differ
    {
        WeightedSetFieldValue value4(type4);
        WeightedSetFieldValue value5(type5);
        WeightedSetFieldValue value6(type6);
        verifyFailedAssignment(value4, value5);
        verifyFailedAssignment(value4, value6);
        verifyFailedAssignment(value5, value4);
        verifyFailedAssignment(value5, value6);
        verifyFailedAssignment(value6, value4);
        verifyFailedAssignment(value6, value5);
    }
        // Updates are checked too
    {
        WeightedSetFieldValue value3(type3);
        WeightedSetFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        verifyFailedUpdate(value3, subValue);
    }

        // Compare see difference even of close types.
    {
        WeightedSetFieldValue subValue2(type2);
        subValue2.add(LongFieldValue(3));
        WeightedSetFieldValue value3(type3);
        WeightedSetFieldValue value4(type4);
        value4.add(subValue2);
        EXPECT_TRUE(value3.compare(value4) != 0);
    }

        // Test createIfNonExisting and removeIfZero
    {
        WeightedSetDataType mytype1(*DataType::STRING, false, false);
        WeightedSetDataType mytype2(*DataType::STRING, true, true);
        EXPECT_EQ(*DataType::TAG, static_cast<DataType &>(mytype2));

        WeightedSetFieldValue wsval1(mytype1);
        WSetHelper val1(wsval1);
        val1.add("foo", 4);
        try{
            val1.increment("bar", 2);
            FAIL() << "Expected exception incrementing with "
                "createIfNonExistent set false";
        } catch (std::exception& e) {}
        try{
            val1.decrement("bar", 2);
            FAIL() << "Expected exception incrementing with "
                "createIfNonExistent set false";
        } catch (std::exception& e) {}
        val1.increment("foo", 6);
        EXPECT_EQ(10, val1.get("foo"));
        val1.decrement("foo", 3);
        EXPECT_EQ(7, val1.get("foo"));
        val1.decrement("foo", 7);
        EXPECT_TRUE(CollectionHelper(wsval1).contains("foo"));

        WeightedSetFieldValue wsval2(mytype2);
        WSetHelper val2(wsval2);
        val2.add("foo", 4);
        val2.increment("bar", 2);
        EXPECT_EQ(2, val2.get("bar"));
        val2.decrement("bar", 4);
        EXPECT_EQ(-2, val2.get("bar"));
        val2.increment("bar", 2);
        EXPECT_TRUE(!CollectionHelper(wsval2).contains("bar"));

        val2.decrement("foo", 4);
        EXPECT_TRUE(!CollectionHelper(wsval2).contains("foo"));

        val2.decrement("foo", 4);
        EXPECT_EQ(-4, val2.get("foo"));

        val2.add("foo", 0);
        EXPECT_TRUE(!CollectionHelper(wsval2).contains("foo"));
    }
}

TEST(WeightedSetFieldValueTest, testAddIgnoreZeroWeight)
{
    // Data type with auto-create and remove-if-zero set.
    WeightedSetDataType wsetType(*DataType::STRING, true, true);
    WeightedSetFieldValue ws(wsetType);

    ws.addIgnoreZeroWeight(StringFieldValue("yarn"), 0);
    EXPECT_TRUE(CollectionHelper(ws).contains("yarn"));
    EXPECT_EQ(0, WSetHelper(ws).get("yarn"));

    ws.addIgnoreZeroWeight(StringFieldValue("flarn"), 1);
    EXPECT_TRUE(CollectionHelper(ws).contains("flarn"));
    EXPECT_EQ(1, WSetHelper(ws).get("flarn"));
}

} // document

