// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <limits>

using vespalib::nbostream;

namespace document {

struct PrimitiveFieldValueTest : public CppUnit::TestFixture {

    void testLiterals();
    void testRaw();
    void testNumerics();
    void testFloatDoubleCasts();

    CPPUNIT_TEST_SUITE(PrimitiveFieldValueTest);
    CPPUNIT_TEST(testLiterals);
    CPPUNIT_TEST(testRaw);
    CPPUNIT_TEST(testNumerics);
    CPPUNIT_TEST(testFloatDoubleCasts);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PrimitiveFieldValueTest);

namespace {
template <typename T>
void deserialize(const ByteBuffer &buffer, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    DocumentTypeRepo repo;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}

    /**
     * Test common functionality, such as serialization, comparisons, and
     * assignment. medium1 and medium2 should be equal, but not the same
     * instance.
     */
    template<typename Type>
    void
    testCommon(const Type& smallest, const Type& medium1,
               const Type& medium2, const Type& largest)
    {
        try{
                // Less
            CPPUNIT_ASSERT(!(smallest < smallest));
            CPPUNIT_ASSERT(smallest < medium1);
            CPPUNIT_ASSERT(smallest < medium2);
            CPPUNIT_ASSERT(smallest < largest);
            CPPUNIT_ASSERT(!(medium1 < smallest));
            CPPUNIT_ASSERT(!(medium1 < medium1));
            CPPUNIT_ASSERT(!(medium1 < medium2));
            CPPUNIT_ASSERT(medium1 < largest);
            CPPUNIT_ASSERT(!(medium2 < smallest));
            CPPUNIT_ASSERT(!(medium2 < medium1));
            CPPUNIT_ASSERT(!(medium2 < medium2));
            CPPUNIT_ASSERT(medium2 < largest);
            CPPUNIT_ASSERT(!(largest < smallest));
            CPPUNIT_ASSERT(!(largest < medium1));
            CPPUNIT_ASSERT(!(largest < medium2));
            CPPUNIT_ASSERT(!(largest < largest));
                // Equal
            CPPUNIT_ASSERT(smallest == smallest);
            CPPUNIT_ASSERT(!(smallest == medium1));
            CPPUNIT_ASSERT(!(smallest == medium2));
            CPPUNIT_ASSERT(!(smallest == largest));
            CPPUNIT_ASSERT(!(medium1 == smallest));
            CPPUNIT_ASSERT(medium1 == medium1);
            CPPUNIT_ASSERT(medium1 == medium2);
            CPPUNIT_ASSERT(!(medium1 == largest));
            CPPUNIT_ASSERT(!(medium2 == smallest));
            CPPUNIT_ASSERT(medium2 == medium1);
            CPPUNIT_ASSERT(medium2 == medium2);
            CPPUNIT_ASSERT(!(medium2 == largest));
            CPPUNIT_ASSERT(!(largest == smallest));
            CPPUNIT_ASSERT(!(largest == medium1));
            CPPUNIT_ASSERT(!(largest == medium2));
            CPPUNIT_ASSERT(largest == largest);
                // Greater
            CPPUNIT_ASSERT(!(smallest > smallest));
            CPPUNIT_ASSERT(!(smallest > medium1));
            CPPUNIT_ASSERT(!(smallest > medium2));
            CPPUNIT_ASSERT(!(smallest > largest));
            CPPUNIT_ASSERT(medium1 > smallest);
            CPPUNIT_ASSERT(!(medium1 > medium1));
            CPPUNIT_ASSERT(!(medium1 > medium2));
            CPPUNIT_ASSERT(!(medium1 > largest));
            CPPUNIT_ASSERT(medium2 > smallest);
            CPPUNIT_ASSERT(!(medium2 > medium1));
            CPPUNIT_ASSERT(!(medium2 > medium2));
            CPPUNIT_ASSERT(!(medium2 > largest));
            CPPUNIT_ASSERT(largest > smallest);
            CPPUNIT_ASSERT(largest > medium1);
            CPPUNIT_ASSERT(largest > medium2);
            CPPUNIT_ASSERT(!(largest > largest));
                // Currently >=, <= and != is deducted from the above, so not
                // checking separately

                // Serialization
            Type t;
            std::unique_ptr<ByteBuffer> buf(smallest.serialize());
            buf->flip();
            deserialize(*buf, t);
            CPPUNIT_ASSERT_EQUAL(smallest, t);

            buf = medium1.serialize();
            buf->flip();
            deserialize(*buf, t);
            CPPUNIT_ASSERT_EQUAL(medium1, t);
            CPPUNIT_ASSERT_EQUAL(medium2, t);

            buf = largest.serialize();
            buf->flip();
            deserialize(*buf, t);
            CPPUNIT_ASSERT_EQUAL(largest, t);

                // Assignment
            CPPUNIT_ASSERT_EQUAL(smallest, t = smallest);
            CPPUNIT_ASSERT_EQUAL(medium1, t = medium1);
            CPPUNIT_ASSERT_EQUAL(largest, t = largest);

            Type t1(smallest);
            Type t2(medium1);
            Type t3(medium2);
            Type t4(largest);
            CPPUNIT_ASSERT_EQUAL(smallest, t1);
            CPPUNIT_ASSERT_EQUAL(medium1, t2);
            CPPUNIT_ASSERT_EQUAL(medium2, t3);
            CPPUNIT_ASSERT_EQUAL(largest, t4);

            t.assign(smallest);
            CPPUNIT_ASSERT_EQUAL(smallest, t);
            t.assign(medium2);
            CPPUNIT_ASSERT_EQUAL(medium1, t);
            t.assign(largest);
            CPPUNIT_ASSERT_EQUAL(largest, t);

            // Catch errors and say what type there were trouble with.
        } catch (std::exception& e) {
            std::cerr << "\nFailed for type " << *smallest.getDataType()
                      << "\n";
            throw;
        }
    }

    template<typename Literal>
    void
    testLiteral()
    {
        testCommon(Literal(),
                   Literal("bar"),
                   Literal("bar"),
                   Literal("foo"));
        Literal value("foo");
            // Textual output
        CPPUNIT_ASSERT_EQUAL(std::string("foo"), value.toString(false, ""));
        CPPUNIT_ASSERT_EQUAL(std::string("foo"), value.toString(true, "  "));
        CPPUNIT_ASSERT_EQUAL(std::string("<value>foo</value>\n"),
                             value.toXml("  "));

            // Conversion
        CPPUNIT_ASSERT_EQUAL(typename Literal::string(value.getAsString()), value.getValue());

            // Operator =
        value = "anotherVal";
        CPPUNIT_ASSERT_EQUAL(typename Literal::string("anotherVal"), value.getValue());
        value = std::string("yetAnotherVal");
        CPPUNIT_ASSERT_EQUAL(typename Literal::string("yetAnotherVal"), value.getValue());

        // Test that a just deserialized value can be serialized again
        // (literals have lazy deserialization so behaves diff then
        value = "foo";
        std::unique_ptr<ByteBuffer> buf(value.serialize());
        buf->flip();
        Literal value2("Other");
        deserialize(*buf, value2);
        buf = value2.serialize();
        buf->flip();
        deserialize(*buf, value2);
        CPPUNIT_ASSERT_EQUAL(value, value2);

        // Verify that get value ref gives us ref within original bytebuffer
        // (operator== use above should not modify this)
        buf = value.serialize();
        buf->flip();
        deserialize(*buf, value2);

        CPPUNIT_ASSERT_EQUAL(size_t(3), value2.getValueRef().size());
        // Zero termination
        CPPUNIT_ASSERT(*(value2.getValueRef().c_str() + value2.getValueRef().size()) == '\0');
    }

}

void
PrimitiveFieldValueTest::testLiterals()
{
    testLiteral<StringFieldValue>();
}

void
PrimitiveFieldValueTest::testRaw()
{
    testCommon(RawFieldValue(),
               RawFieldValue("bar\0bar", 7),
               RawFieldValue("bar\0bar", 7),
               RawFieldValue("bar\0other", 9));
    RawFieldValue value("\tfoo\0\r\n", 7);
        // Textual output
    CPPUNIT_ASSERT_EQUAL(std::string(
            "0: 09 66 6f 6f 00 0d 0a                            .foo..."),
            value.toString(false, ""));
    CPPUNIT_ASSERT_EQUAL(std::string(
            "0: 09 66 6f 6f 00 0d 0a                            .foo..."),
            value.toString(true, "  "));
    CPPUNIT_ASSERT_EQUAL(std::string(
            "<value binaryencoding=\"base64\">CWZvbwANCg==</value>\n"),
            value.toXml("  "));

    value.setValue("grmpf", 4);
    CPPUNIT_ASSERT(strncmp("grmpf", value.getValueRef().c_str(),
                           value.getValueRef().size()) == 0);
}

#define ASSERT_FAILED_CONV(getter, totype, floating) \
{ \
    totype toType; \
    FieldValue::UP copy(value.clone()); \
    try{ \
        getter; \
        std::ostringstream ost; \
        ost << "Conversion unexpectedly worked from max value of " \
            << *value.getDataType() << " to " << *toType.getDataType(); \
        CPPUNIT_FAIL(ost.str().c_str()); \
    } catch (std::exception& e) { \
        CPPUNIT_ASSERT_EQUAL( \
                std::string("bad numeric conversion: positive overflow"), \
                std::string(e.what())); \
    } \
        /* Verify that we can convert to smaller type if value is within \
           range. Only tests integer to integer. No floating point. */ \
    if (!floating) { \
        totype::Number maxV = std::numeric_limits<totype::Number>::max(); \
        value.setValue((Number) maxV); \
        getter; \
    } \
    value.assign(*copy); \
}

namespace {

    template<typename Numeric>
    void
    testNumeric(const std::string& maxVal, bool floatingPoint)
    {
        typedef typename Numeric::Number Number;
        Number maxValue(std::numeric_limits<Number>::max());
            // Test common fieldvalue stuff
        testCommon(Numeric(),
                   Numeric(Number(1)),
                   Numeric(Number(1)),
                   Numeric(Number(maxValue)));
        Numeric value;
        value.setValue(maxValue);
            // Test textual output
        CPPUNIT_ASSERT_EQUAL(maxVal, value.toString(false, ""));
        CPPUNIT_ASSERT_EQUAL(maxVal, value.toString(true, "  "));
        CPPUNIT_ASSERT_EQUAL("<value>" + maxVal + "</value>\n",
                             value.toXml("  "));
            // Test numeric conversions
            //
            // Currently, all safe conversion works. For instance, a byte can be
            // converted to a long, a long can be converted to a byte, given
            // that it has a value in the range -128 to 127.
            //
            // All integers will also convert automatically to floating point
            // numbers. No checks is done as to how precise floating point
            // representation can keep the value.
        if (floatingPoint || sizeof(Number) > sizeof(unsigned char)) {
            // No longer throws. This is guarded on the perimeter by java code.
            // ASSERT_FAILED_CONV(value.getAsByte(), ByteFieldValue, floatingPoint);
        } else {
            CPPUNIT_ASSERT_EQUAL((char) maxValue, value.getAsByte());
        }
        if (floatingPoint || sizeof(Number) > sizeof(int32_t)) {
            // No longer throws. This is guarded on the perimeter by java code.
            // ASSERT_FAILED_CONV(value.getAsInt(), IntFieldValue, floatingPoint);
        } else {
            CPPUNIT_ASSERT_EQUAL((int32_t) maxValue, value.getAsInt());
        }
        if (floatingPoint || sizeof(Number) > sizeof(int64_t)) {
            // No longer throws. This is guarded on the perimeter by java code.
            // ASSERT_FAILED_CONV(value.getAsLong(), LongFieldValue, floatingPoint);
        } else {
            CPPUNIT_ASSERT_EQUAL((int64_t) maxValue, value.getAsLong());
        }
        if (floatingPoint && sizeof(Number) > sizeof(float)) {
            // No longer throws. This is guarded on the perimeter by java code.
            // ASSERT_FAILED_CONV(value.getAsFloat(), FloatFieldValue, true);
        } else {
            CPPUNIT_ASSERT_EQUAL((float) maxValue, value.getAsFloat());
        }
        CPPUNIT_ASSERT_EQUAL((double) maxValue, value.getAsDouble());
        // Test some simple conversions
        Numeric a(0);
        a = std::string("5");
        CPPUNIT_ASSERT_EQUAL(5, a.getAsInt());
    }

}

void
PrimitiveFieldValueTest::testFloatDoubleCasts()
{
    float inf(std::numeric_limits<float>::infinity());
    CPPUNIT_ASSERT_EQUAL(inf, static_cast<float>(static_cast<double>(inf)));
}

void
PrimitiveFieldValueTest::testNumerics()
{
    testNumeric<ByteFieldValue>("127", false);
    testNumeric<ShortFieldValue>("32767", false);
    testNumeric<IntFieldValue>("2147483647", false);
    testNumeric<LongFieldValue>("9223372036854775807", false);
    testNumeric<FloatFieldValue>("3.40282e+38", true);
    testNumeric<DoubleFieldValue>("1.79769e+308", true);

        // Test range
    ByteFieldValue b1(-128);
    ByteFieldValue b2(-1);
    CPPUNIT_ASSERT_EQUAL(-128, (int) b1.getValue());
    CPPUNIT_ASSERT_EQUAL(-1, (int) b2.getValue());

    ShortFieldValue s1(-32768);
    ShortFieldValue s2(65535);
    CPPUNIT_ASSERT_EQUAL((int16_t)-32768, s1.getValue());
    CPPUNIT_ASSERT_EQUAL((int16_t)65535, s2.getValue());
    CPPUNIT_ASSERT_EQUAL((int16_t)-1, s2.getValue());

    IntFieldValue i1(-2147483647-1);
    IntFieldValue i2(4294967295U);

    CPPUNIT_ASSERT_EQUAL((int) -2147483647-1, i1.getValue());
    CPPUNIT_ASSERT_EQUAL((int) -1, i2.getValue());

    LongFieldValue l1(-9223372036854775807ll-1);
    LongFieldValue l2(18446744073709551615ull);

    CPPUNIT_ASSERT_EQUAL((int64_t) -9223372036854775807ll-1, l1.getValue());
    CPPUNIT_ASSERT_EQUAL((int64_t) -1, l2.getValue());

    b1 = "-128";
    b2 = "255";

    CPPUNIT_ASSERT_EQUAL(-128, (int) b1.getValue());
    CPPUNIT_ASSERT_EQUAL(-1, (int) b2.getValue());
    i1 = "-2147483648";
    i2 = "4294967295";
    CPPUNIT_ASSERT_EQUAL((int) -2147483647-1, i1.getValue());
    CPPUNIT_ASSERT_EQUAL((int) -1, i2.getValue());

    l1 = "-9223372036854775808";
    l2 = "18446744073709551615";

    int64_t bnv = -1;
    bnv <<= 63;
    CPPUNIT_ASSERT_EQUAL(bnv, l1.getValue());
    CPPUNIT_ASSERT_EQUAL((int64_t) -9223372036854775807ll-1, l1.getValue());
    CPPUNIT_ASSERT_EQUAL((int64_t) -1, l2.getValue());

        // Test some special cases for bytes
        // (as unsigned char is not always handled as a number)
    b1 = "0xff";
    CPPUNIT_ASSERT_EQUAL(-1, (int) b1.getValue());
    b1 = "53";
    CPPUNIT_ASSERT_EQUAL(53, (int) b1.getValue());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("53"), b1.getAsString());

    try{
        b1 = "-129";
        CPPUNIT_FAIL("Expected -129 to be invalid byte");
    } catch (std::exception& e) {}
    try{
        b1 = "256";
        CPPUNIT_FAIL("Expected -129 to be invalid byte");
    } catch (std::exception& e) {}
    try{
        s1 = "-32769";
        CPPUNIT_FAIL("Expected -32769 to be invalid int");
    } catch (std::exception& e) {}
    try{
        s1 = "65536";
        CPPUNIT_FAIL("Expected 65536 to be invalid int");
    } catch (std::exception& e) {}
    try{
        i1 = "-2147483649";
        CPPUNIT_FAIL("Expected -2147483649 to be invalid int");
    } catch (std::exception& e) {}
    try{
        i1 = "4294967296";
        CPPUNIT_FAIL("Expected 4294967296 to be invalid int");
    } catch (std::exception& e) {}
    try{
        l1 = "-9223372036854775809";
        CPPUNIT_FAIL("Expected -9223372036854775809 to be invalid long");
    } catch (std::exception& e) {}
    try{
        l1 = "18446744073709551616";
        CPPUNIT_FAIL("Expected 18446744073709551616 to be invalid long");
    } catch (std::exception& e) {}
}

} // document
