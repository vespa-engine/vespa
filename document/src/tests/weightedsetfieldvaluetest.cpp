// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/repo/documenttyperepo.h>

using vespalib::nbostream;

namespace document {

struct WeightedSetFieldValueTest : public CppUnit::TestFixture {

    void testWeightedSet();
    void testAddIgnoreZeroWeight();

    CPPUNIT_TEST_SUITE(WeightedSetFieldValueTest);
    CPPUNIT_TEST(testWeightedSet);
    CPPUNIT_TEST(testAddIgnoreZeroWeight);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(WeightedSetFieldValueTest);

namespace {
template <typename T>
void deserialize(const ByteBuffer &buffer, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    DocumentTypeRepo repo;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}

template<typename Type1, typename Type2>
void verifyFailedAssignment(Type1& lval, const Type2& rval)
{
    try{
        lval = rval;
        CPPUNIT_FAIL("Failed to check type equality in operator=");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
    try{
        lval.assign(rval);
        CPPUNIT_FAIL("Failed to check type equality in assign()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
}

template<typename Type>
void verifyFailedUpdate(Type& lval, const FieldValue& rval)
{
    try{
        lval.add(rval);
        CPPUNIT_FAIL("Failed to check type equality in add()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("These types are not compatible", e.what());
    }
    try{
        lval.contains(rval);
        CPPUNIT_FAIL("Failed to check type equality in contains()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("These types are not compatible", e.what());
    }
    try{
        lval.remove(rval);
        CPPUNIT_FAIL("Failed to check type equality in remove()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("These types are not compatible", e.what());
    }
}
}  // namespace

void WeightedSetFieldValueTest::testWeightedSet()
{
    WeightedSetDataType type(*DataType::INT, false, false);
    WeightedSetFieldValue value(type);

        // Initially empty
    CPPUNIT_ASSERT_EQUAL(size_t(0), value.size());
    CPPUNIT_ASSERT(value.isEmpty());
    CPPUNIT_ASSERT(!value.contains(IntFieldValue(1)));

    CPPUNIT_ASSERT(value.add(IntFieldValue(1)));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(1), value.size());
    CPPUNIT_ASSERT(!value.isEmpty());
    CPPUNIT_ASSERT(value.contains(IntFieldValue(1)));

        // Adding some more
    CPPUNIT_ASSERT(value.add(IntFieldValue(2), 5));
    CPPUNIT_ASSERT(value.add(IntFieldValue(3), 6));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(3), value.size());
    CPPUNIT_ASSERT(!value.isEmpty());
    CPPUNIT_ASSERT_EQUAL(1, value.get(IntFieldValue(1)));
    CPPUNIT_ASSERT_EQUAL(5, value.get(IntFieldValue(2)));
    CPPUNIT_ASSERT_EQUAL(6, value.get(IntFieldValue(3)));

        // Serialize & equality
    std::unique_ptr<ByteBuffer> buffer(value.serialize());
    buffer->flip();
    WeightedSetFieldValue value2(type);
    CPPUNIT_ASSERT(value != value2);
    deserialize(*buffer, value2);
    CPPUNIT_ASSERT_EQUAL(value, value2);

        // Various ways of removing
    {
            // By value
        buffer->setPos(0);
        deserialize(*buffer, value2);
        CPPUNIT_ASSERT_EQUAL(size_t(3), value2.size());
        CPPUNIT_ASSERT(value2.remove(IntFieldValue(1)));
        CPPUNIT_ASSERT(!value2.contains(IntFieldValue(1)));
        CPPUNIT_ASSERT_EQUAL(size_t(2), value2.size());

            // Clearing all
        buffer->setPos(0);
        deserialize(*buffer, value2);
        value2.clear();
        CPPUNIT_ASSERT(!value2.contains(IntFieldValue(1)));
        CPPUNIT_ASSERT_EQUAL(size_t(0), value2.size());
        CPPUNIT_ASSERT(value2.isEmpty());
    }

        // Updating
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(value, value2);
    CPPUNIT_ASSERT(!value2.add(IntFieldValue(2), 10)); // false = overwritten
    CPPUNIT_ASSERT(value2.add(IntFieldValue(17), 9)); // true = added new
    CPPUNIT_ASSERT_EQUAL(10, value2.get(IntFieldValue(2)));
    CPPUNIT_ASSERT(value != value2);
    value2.assign(value);
    CPPUNIT_ASSERT_EQUAL(value, value2);
    WeightedSetFieldValue::UP valuePtr(value2.clone());
    CPPUNIT_ASSERT_EQUAL(value, *valuePtr);

        // Iterating
    const WeightedSetFieldValue& constVal(value);
    for(WeightedSetFieldValue::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        const FieldValue& fval1(*it->first);
        (void) fval1;
        CPPUNIT_ASSERT_EQUAL((uint32_t) IntFieldValue::classId,
                             it->first->getClass().id());
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
    CPPUNIT_ASSERT(value != value2);
    CPPUNIT_ASSERT_EQUAL(7, value2.get(IntFieldValue(2)));

        // Comparison
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(0, value.compare(value2));
    value2.remove(IntFieldValue(1));
    CPPUNIT_ASSERT(value.compare(value2) > 0);
    CPPUNIT_ASSERT(value2.compare(value) < 0);
    value2 = value;
    value2.add(IntFieldValue(7));
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);

        // Output
    CPPUNIT_ASSERT_EQUAL(
            std::string(
                "WeightedSet<Int>(\n"
                "  1 - weight 1,\n"
                "  2 - weight 5,\n"
                "  3 - weight 6\n"
                ")"),
            value.toString(false));
    CPPUNIT_ASSERT_EQUAL(
            std::string(
                "  WeightedSet<Int>(\n"
                "..  1 - weight 1,\n"
                "..  2 - weight 5,\n"
                "..  3 - weight 6\n"
                "..)"),
            "  " + value.toString(true, ".."));
    CPPUNIT_ASSERT_EQUAL(
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
        CPPUNIT_FAIL("Didn't complain about non-weightedset type");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot generate a weighted set value with "
                               "non-weighted set type", e.what());
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
        CPPUNIT_ASSERT(value3.compare(value4) != 0);
    }

        // Test createIfNonExisting and removeIfZero
    {
        WeightedSetDataType mytype1(*DataType::STRING, false, false);
        WeightedSetDataType mytype2(*DataType::STRING, true, true);
        CPPUNIT_ASSERT_EQUAL(*DataType::TAG, static_cast<DataType &>(mytype2));

        WeightedSetFieldValue val1(mytype1);
        val1.add("foo", 4);
        try{
            val1.increment("bar", 2);
            CPPUNIT_FAIL("Expected exception incrementing with "
                         "createIfNonExistent set false");
        } catch (std::exception& e) {}
        try{
            val1.decrement("bar", 2);
            CPPUNIT_FAIL("Expected exception incrementing with "
                         "createIfNonExistent set false");
        } catch (std::exception& e) {}
        val1.increment("foo", 6);
        CPPUNIT_ASSERT_EQUAL(10, val1.get("foo"));
        val1.decrement("foo", 3);
        CPPUNIT_ASSERT_EQUAL(7, val1.get("foo"));
        val1.decrement("foo", 7);
        CPPUNIT_ASSERT(val1.contains("foo"));

        WeightedSetFieldValue val2(mytype2);
        val2.add("foo", 4);
        val2.increment("bar", 2);
        CPPUNIT_ASSERT_EQUAL(2, val2.get("bar"));
        val2.decrement("bar", 4);
        CPPUNIT_ASSERT_EQUAL(-2, val2.get("bar"));
        val2.increment("bar", 2);
        CPPUNIT_ASSERT(!val2.contains("bar"));

        val2.decrement("foo", 4);
        CPPUNIT_ASSERT(!val2.contains("foo"));

        val2.decrement("foo", 4);
        CPPUNIT_ASSERT_EQUAL(-4, val2.get("foo"));

        val2.add("foo", 0);
        CPPUNIT_ASSERT(!val2.contains("foo"));
    }
}

void
WeightedSetFieldValueTest::testAddIgnoreZeroWeight()
{
    // Data type with auto-create and remove-if-zero set.
    WeightedSetDataType wsetType(*DataType::STRING, true, true);
    WeightedSetFieldValue ws(wsetType);

    ws.addIgnoreZeroWeight(StringFieldValue("yarn"), 0);
    CPPUNIT_ASSERT(ws.contains("yarn"));
    CPPUNIT_ASSERT_EQUAL(0, ws.get("yarn"));

    ws.addIgnoreZeroWeight(StringFieldValue("flarn"), 1);
    CPPUNIT_ASSERT(ws.contains("flarn"));
    CPPUNIT_ASSERT_EQUAL(1, ws.get("flarn"));
}

} // document

