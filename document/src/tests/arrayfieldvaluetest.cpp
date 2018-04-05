// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/repo/documenttyperepo.h>

using vespalib::nbostream;

namespace document {

struct ArrayFieldValueTest : public CppUnit::TestFixture {
    void testArray();

    CPPUNIT_TEST_SUITE(ArrayFieldValueTest);
    CPPUNIT_TEST(testArray);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(ArrayFieldValueTest);

namespace {
template <typename T>
void deserialize(const ByteBuffer &buffer, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    DocumentTypeRepo repo;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}
}  // namespace

void ArrayFieldValueTest::testArray()
{
    ArrayDataType type(*DataType::INT);
    ArrayFieldValue value(type);

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
    CPPUNIT_ASSERT(value.add(IntFieldValue(2)));
    CPPUNIT_ASSERT(value.add(IntFieldValue(3)));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(3), value.size());
    CPPUNIT_ASSERT(!value.isEmpty());
    CPPUNIT_ASSERT_EQUAL(IntFieldValue(1), (IntFieldValue&) value[0]);
    CPPUNIT_ASSERT_EQUAL(IntFieldValue(2), (IntFieldValue&) value[1]);
    CPPUNIT_ASSERT_EQUAL(IntFieldValue(3), (IntFieldValue&) value[2]);

        // Serialize & equality
    std::unique_ptr<ByteBuffer> buffer(value.serialize());
    buffer->flip();
    ArrayFieldValue value2(type);
    CPPUNIT_ASSERT(value != value2);
    deserialize(*buffer, value2);
    CPPUNIT_ASSERT_EQUAL(value, value2);

        // Various ways of removing
    {
            // By index
        buffer->setPos(0);
        deserialize(*buffer, value2);
        value2.remove(1);
        CPPUNIT_ASSERT(!value2.contains(IntFieldValue(2)));
        CPPUNIT_ASSERT_EQUAL(size_t(2), value2.size());

            // By value
        buffer->setPos(0);
        deserialize(*buffer, value2);
        CPPUNIT_ASSERT(value2.remove(IntFieldValue(1)));
        CPPUNIT_ASSERT(!value2.contains(IntFieldValue(1)));
        CPPUNIT_ASSERT_EQUAL(size_t(2), value2.size());

            // By value with multiple present
        buffer->setPos(0);
        deserialize(*buffer, value2);
        value2.add(IntFieldValue(1));
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
    value2[1].assign(IntFieldValue(5));
    CPPUNIT_ASSERT(!value2.contains(IntFieldValue(2)));
    CPPUNIT_ASSERT_EQUAL(IntFieldValue(5), (IntFieldValue&) value2[1]);
    CPPUNIT_ASSERT(value != value2);
    value2.assign(value);
    CPPUNIT_ASSERT_EQUAL(value, value2);
    ArrayFieldValue::UP valuePtr(value2.clone());
    CPPUNIT_ASSERT_EQUAL(value, *valuePtr);

        // Iterating
    const ArrayFieldValue& constVal(value);
    for(ArrayFieldValue::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        const FieldValue& fval1(*it);
        (void) fval1;
        CPPUNIT_ASSERT_EQUAL((uint32_t) IntFieldValue::classId,
                             it->getClass().id());
    }
    value2 = value;
    for(ArrayFieldValue::iterator it = value2.begin(); it != value2.end(); ++it)
    {
        (*it).assign(IntFieldValue(7));
        it->assign(IntFieldValue(7));
    }
    CPPUNIT_ASSERT(value != value2);
    CPPUNIT_ASSERT(value2.contains(IntFieldValue(7)));
    CPPUNIT_ASSERT(value2.remove(IntFieldValue(7)));
    CPPUNIT_ASSERT(value2.isEmpty());

        // Comparison
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(0, value.compare(value2));
    value2.remove(1);
    CPPUNIT_ASSERT(value.compare(value2) > 0);
    CPPUNIT_ASSERT(value2.compare(value) < 0);
    value2 = value;
    value2[1].assign(IntFieldValue(5));
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);

        // Output
    CPPUNIT_ASSERT_EQUAL(std::string("Array(size: 3,\n  1,\n  2,\n  3\n)"),
                         value.toString(false));
    CPPUNIT_ASSERT_EQUAL(std::string("Array(size: 3,\n.  1,\n.  2,\n.  3\n.)"),
                         value.toString(true, "."));
    CPPUNIT_ASSERT_EQUAL(std::string(
                            "<value>\n"
                            "  <item>1</item>\n"
                            "  <item>2</item>\n"
                            "  <item>3</item>\n"
                            "</value>"
                         ),
                         value.toXml("  "));

        // Failure situations.

        // Verify that datatypes are verified
        // Created almost equal types to try to get it to fail
    ArrayDataType type1(*DataType::INT);
    ArrayDataType type2(*DataType::LONG);
    ArrayDataType type3(static_cast<DataType &>(type1));
    ArrayDataType type4(static_cast<DataType &>(type2));
    ArrayFieldValue value3(type3);
    ArrayFieldValue value4(type4);
    try{
        value3 = value4;
        CPPUNIT_FAIL("Failed to check type equality in operator=");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
    try{
        value3.assign(value4);
        CPPUNIT_FAIL("Failed to check type equality in assign()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.add(subValue);
        CPPUNIT_FAIL("Failed to check type equality in add()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot add value of type",e.what());
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.contains(subValue);
        CPPUNIT_FAIL("Failed to check type equality in contains()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("can't possibly be in array of type", e.what());
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.remove(subValue);
        CPPUNIT_FAIL("Failed to check type equality in remove()");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("can't possibly be in array of type", e.what());
    }
        // Verify that compare sees difference
    {
        ArrayFieldValue subValue1(type1);
        ArrayFieldValue subValue2(type2);
        subValue1.add(IntFieldValue(3));
        subValue2.add(LongFieldValue(3));
        value3.clear();
        value4.clear();
        value3.add(subValue1);
        value4.add(subValue2);
        CPPUNIT_ASSERT(value3.compare(value4) != 0);
    }

        // Removing non-existing element
    value2 = value;
    try{
        value2.remove(5);
        CPPUNIT_FAIL("Failed to throw out of bounds exception on remove(int)");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot remove index 5 from an array of size 3",
                               e.what());
    }
    CPPUNIT_ASSERT(!value2.remove(IntFieldValue(15)));
}

} // document
