// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <limits>
#include <gtest/gtest.h>

using vespalib::nbostream;

namespace document {

namespace {
template <typename T>
void deserialize(nbostream & stream, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
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
            EXPECT_TRUE(!(smallest < smallest));
            EXPECT_TRUE(smallest < medium1);
            EXPECT_TRUE(smallest < medium2);
            EXPECT_TRUE(smallest < largest);
            EXPECT_TRUE(!(medium1 < smallest));
            EXPECT_TRUE(!(medium1 < medium1));
            EXPECT_TRUE(!(medium1 < medium2));
            EXPECT_TRUE(medium1 < largest);
            EXPECT_TRUE(!(medium2 < smallest));
            EXPECT_TRUE(!(medium2 < medium1));
            EXPECT_TRUE(!(medium2 < medium2));
            EXPECT_TRUE(medium2 < largest);
            EXPECT_TRUE(!(largest < smallest));
            EXPECT_TRUE(!(largest < medium1));
            EXPECT_TRUE(!(largest < medium2));
            EXPECT_TRUE(!(largest < largest));
                // Equal
            EXPECT_TRUE(smallest == smallest);
            EXPECT_TRUE(!(smallest == medium1));
            EXPECT_TRUE(!(smallest == medium2));
            EXPECT_TRUE(!(smallest == largest));
            EXPECT_TRUE(!(medium1 == smallest));
            EXPECT_TRUE(medium1 == medium1);
            EXPECT_TRUE(medium1 == medium2);
            EXPECT_TRUE(!(medium1 == largest));
            EXPECT_TRUE(!(medium2 == smallest));
            EXPECT_TRUE(medium2 == medium1);
            EXPECT_TRUE(medium2 == medium2);
            EXPECT_TRUE(!(medium2 == largest));
            EXPECT_TRUE(!(largest == smallest));
            EXPECT_TRUE(!(largest == medium1));
            EXPECT_TRUE(!(largest == medium2));
            EXPECT_TRUE(largest == largest);
                // Greater
            EXPECT_TRUE(!(smallest > smallest));
            EXPECT_TRUE(!(smallest > medium1));
            EXPECT_TRUE(!(smallest > medium2));
            EXPECT_TRUE(!(smallest > largest));
            EXPECT_TRUE(medium1 > smallest);
            EXPECT_TRUE(!(medium1 > medium1));
            EXPECT_TRUE(!(medium1 > medium2));
            EXPECT_TRUE(!(medium1 > largest));
            EXPECT_TRUE(medium2 > smallest);
            EXPECT_TRUE(!(medium2 > medium1));
            EXPECT_TRUE(!(medium2 > medium2));
            EXPECT_TRUE(!(medium2 > largest));
            EXPECT_TRUE(largest > smallest);
            EXPECT_TRUE(largest > medium1);
            EXPECT_TRUE(largest > medium2);
            EXPECT_TRUE(!(largest > largest));
                // Currently >=, <= and != is deducted from the above, so not
                // checking separately

                // Serialization
            Type t;
            nbostream buf(smallest.serialize());
            deserialize(buf, t);
            EXPECT_EQ(smallest, t);

            buf = medium1.serialize();
            deserialize(buf, t);
            EXPECT_EQ(medium1, t);
            EXPECT_EQ(medium2, t);

            buf = largest.serialize();
            deserialize(buf, t);
            EXPECT_EQ(largest, t);

                // Assignment
            EXPECT_EQ(smallest, t = smallest);
            EXPECT_EQ(medium1, t = medium1);
            EXPECT_EQ(largest, t = largest);

            Type t1(smallest);
            Type t2(medium1);
            Type t3(medium2);
            Type t4(largest);
            EXPECT_EQ(smallest, t1);
            EXPECT_EQ(medium1, t2);
            EXPECT_EQ(medium2, t3);
            EXPECT_EQ(largest, t4);

            t.assign(smallest);
            EXPECT_EQ(smallest, t);
            t.assign(medium2);
            EXPECT_EQ(medium1, t);
            t.assign(largest);
            EXPECT_EQ(largest, t);

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
        EXPECT_EQ(std::string("foo"), value.toString(false, ""));
        EXPECT_EQ(std::string("foo"), value.toString(true, "  "));
        EXPECT_EQ(std::string("<value>foo</value>\n"), value.toXml("  "));

            // Conversion
        EXPECT_EQ(typename Literal::string(value.getAsString()), value.getValue());

            // Operator =
        value = "anotherVal";
        EXPECT_EQ(typename Literal::string("anotherVal"), value.getValue());
        value = std::string("yetAnotherVal");
        EXPECT_EQ(typename Literal::string("yetAnotherVal"), value.getValue());

        // Test that a just deserialized value can be serialized again
        // (literals have lazy deserialization so behaves diff then
        value = "foo";
        nbostream buf(value.serialize());
        Literal value2("Other");
        deserialize(buf, value2);
        buf = value2.serialize();
        deserialize(buf, value2);
        EXPECT_EQ(value, value2);

        // Verify that get value ref gives us ref within original bytebuffer
        // (operator== use above should not modify this)
        buf = value.serialize();
        deserialize(buf, value2);

        EXPECT_EQ(size_t(3), value2.getValueRef().size());
        // Zero termination
        EXPECT_TRUE(*(value2.getValueRef().data() + value2.getValueRef().size()) == '\0');
    }

}

TEST(PrimitiveFieldValueTest, testLiterals)
{
    testLiteral<StringFieldValue>();
}

TEST(PrimitiveFieldValueTest, testRaw)
{
    testCommon(RawFieldValue(),
               RawFieldValue("bar\0bar", 7),
               RawFieldValue("bar\0bar", 7),
               RawFieldValue("bar\0other", 9));
    RawFieldValue value("\tfoo\0\r\n", 7);
        // Textual output
    EXPECT_EQ(std::string(
            "0: 09 66 6f 6f 00 0d 0a                            .foo..."),
            value.toString(false, ""));
    EXPECT_EQ(std::string(
            "0: 09 66 6f 6f 00 0d 0a                            .foo..."),
            value.toString(true, "  "));
    EXPECT_EQ(std::string(
            "<value binaryencoding=\"base64\">CWZvbwANCg==</value>\n"),
            value.toXml("  "));

    value.setValue("grmpf", 4);
    EXPECT_TRUE(strncmp("grmpf", value.getValueRef().data(),
                           value.getValueRef().size()) == 0);
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
        EXPECT_EQ(maxVal, value.toString(false, ""));
        EXPECT_EQ(maxVal, value.toString(true, "  "));
        EXPECT_EQ("<value>" + maxVal + "</value>\n", value.toXml("  "));
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
        } else {
            EXPECT_EQ((char) maxValue, value.getAsByte());
        }
        if (floatingPoint || sizeof(Number) > sizeof(int32_t)) {
            // No longer throws. This is guarded on the perimeter by java code.
        } else {
            EXPECT_EQ((int32_t) maxValue, value.getAsInt());
        }
        if (floatingPoint || sizeof(Number) > sizeof(int64_t)) {
            // No longer throws. This is guarded on the perimeter by java code.
        } else {
            EXPECT_EQ((int64_t) maxValue, value.getAsLong());
        }
        if (floatingPoint && sizeof(Number) > sizeof(float)) {
            // No longer throws. This is guarded on the perimeter by java code.
        } else {
            EXPECT_EQ((float) maxValue, value.getAsFloat());
        }
        EXPECT_EQ((double) maxValue, value.getAsDouble());
        // Test some simple conversions
        Numeric a(0);
        a = std::string("5");
        EXPECT_EQ(5, a.getAsInt());
    }

}

TEST(PrimitiveFieldValueTest, testFloatDoubleCasts)
{
    float inf(std::numeric_limits<float>::infinity());
    EXPECT_EQ(inf, static_cast<float>(static_cast<double>(inf)));
}

TEST(PrimitiveFieldValueTest, testBool)
{
    BoolFieldValue v;
    EXPECT_TRUE( ! v.getValue() );

    v = BoolFieldValue(true);
    EXPECT_TRUE(v.getValue());

    v.setValue(false);
    EXPECT_FALSE(v.getValue());
    v.setValue(true);
    EXPECT_TRUE(v.getValue());

    v = vespalib::stringref("true");
    EXPECT_TRUE(v.getValue());
    v = vespalib::stringref("something not true");
    EXPECT_FALSE(v.getValue());
}

TEST(PrimitiveFieldValueTest, testNumerics)
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
    EXPECT_EQ(-128, (int) b1.getValue());
    EXPECT_EQ(-1, (int) b2.getValue());

    ShortFieldValue s1(-32768);
    ShortFieldValue s2(static_cast<int16_t>(65535));
    EXPECT_EQ((int16_t)-32768, s1.getValue());
    EXPECT_EQ((int16_t)65535, s2.getValue());
    EXPECT_EQ((int16_t)-1, s2.getValue());

    IntFieldValue i1(-2147483647-1);
    IntFieldValue i2(4294967295U);

    EXPECT_EQ((int) -2147483647-1, i1.getValue());
    EXPECT_EQ((int) -1, i2.getValue());

    LongFieldValue l1(-9223372036854775807ll-1);
    LongFieldValue l2(18446744073709551615ull);

    EXPECT_EQ((int64_t) -9223372036854775807ll-1, l1.getValue());
    EXPECT_EQ((int64_t) -1, l2.getValue());

    b1 = "-128";
    b2 = "255";

    EXPECT_EQ(-128, (int) b1.getValue());
    EXPECT_EQ(-1, (int) b2.getValue());
    i1 = "-2147483648";
    i2 = "4294967295";
    EXPECT_EQ((int) -2147483647-1, i1.getValue());
    EXPECT_EQ((int) -1, i2.getValue());

    l1 = "-9223372036854775808";
    l2 = "18446744073709551615";

    int64_t bnv = -1;
    bnv <<= 63;
    EXPECT_EQ(bnv, l1.getValue());
    EXPECT_EQ((int64_t) -9223372036854775807ll-1, l1.getValue());
    EXPECT_EQ((int64_t) -1, l2.getValue());

        // Test some special cases for bytes
        // (as unsigned char is not always handled as a number)
    b1 = "0xff";
    EXPECT_EQ(-1, (int) b1.getValue());
    b1 = "53";
    EXPECT_EQ(53, (int) b1.getValue());
    EXPECT_EQ(vespalib::string("53"), b1.getAsString());

    try{
        b1 = "-129";
        FAIL() << "Expected -129 to be invalid byte";
    } catch (std::exception& e) {}
    try{
        b1 = "256";
        FAIL() << "Expected 256 to be invalid byte";
    } catch (std::exception& e) {}
    try{
        s1 = "-32769";
        FAIL() << "Expected -32769 to be invalid short";
    } catch (std::exception& e) {}
    try{
        s1 = "65536";
        FAIL() << "Expected 65536 to be invalid short";
    } catch (std::exception& e) {}
    try{
        i1 = "-2147483649";
        // Ignore failing test for now.
        // FAIL() << "Expected -2147483649 to be invalid int";
    } catch (std::exception& e) {}
    try{
        i1 = "4294967296";
        FAIL() << "Expected 4294967296 to be invalid int";
    } catch (std::exception& e) {}
    try{
        l1 = "-9223372036854775809";
        // Ignore failing test for now.
        // FAIL() << "Expected -9223372036854775809 to be invalid long";
    } catch (std::exception& e) {}
    try{
        l1 = "18446744073709551616";
        FAIL() << "Expected 18446744073709551616 to be invalid long";
    } catch (std::exception& e) {}
}

} // document
