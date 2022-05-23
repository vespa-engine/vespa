// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/vespalib/util/jsonstream.h>
#include <vespa/vespalib/util/jsonexception.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace vespalib;

class JSONTest : public vespalib::TestApp
{
private:
    void testJSONWriterValues();
    void testJSONWriterObject();
    void testJSONWriterArray();
    void testJSONWriterComplex();
    void testJsonStream();
    void testJsonStreamErrors();
    void testJsonStreamStateReporting();

public:
    int Main() override;
};

void
JSONTest::testJSONWriterValues()
{
    JSONStringer js;

    { // bool
        js.appendBool(true);
        EXPECT_EQUAL(js.toString(), "true");
        js.clear().appendBool(false);
        EXPECT_EQUAL(js.toString(), "false");
    }
    { // double
        js.clear().appendDouble(1234.5678);
        EXPECT_EQUAL(js.toString(), "1234.5678");
        js.clear().appendDouble(-1234.5678);
        EXPECT_EQUAL(js.toString(), "-1234.5678");
        js.clear().appendDouble(0.0);
        EXPECT_EQUAL(js.toString(), "0.0");
        js.clear().appendDouble(0.00000000012345678912356789123456789);
        EXPECT_EQUAL(js.toString(), "1.234567891235679e-10");
        js.clear().appendDouble(std::numeric_limits<double>::max());
        EXPECT_EQUAL(js.toString(), "1.797693134862316e+308");
        js.clear().appendDouble(std::numeric_limits<double>::min());
        EXPECT_EQUAL(js.toString(), "2.225073858507201e-308");
        js.clear().appendDouble(1.0 * (uint64_t(1) << 53));
        EXPECT_EQUAL(js.toString(), "9007199254740992.0");
        js.clear().appendDouble(1000);
        EXPECT_EQUAL(js.toString(), "1000.0");
    }
    { // float
        js.clear().appendFloat(1234.5678f);
        EXPECT_EQUAL(js.toString(), "1234.5677");
        js.clear().appendFloat(-1234.5678f);
        EXPECT_EQUAL(js.toString(), "-1234.5677");
        js.clear().appendFloat(0.0f);
        EXPECT_EQUAL(js.toString(), "0.0");
        js.clear().appendFloat(0.00000000012345678912356789123456789f);
        EXPECT_EQUAL(js.toString(), "1.2345679e-10");
        js.clear().appendFloat(std::numeric_limits<float>::max());
        EXPECT_EQUAL(js.toString(), "3.4028235e+38");
        js.clear().appendFloat(std::numeric_limits<float>::min());
        EXPECT_EQUAL(js.toString(), "1.1754944e-38");
        js.clear().appendFloat(1.0 * (uint64_t(1) << 24));
        EXPECT_EQUAL(js.toString(), "16777216.0");
        js.clear().appendFloat(1000);
        EXPECT_EQUAL(js.toString(), "1000.0");
    }
    { // long
        js.clear().appendInt64(4294967296ll);
        EXPECT_EQUAL(js.toString(), "4294967296");
        js.clear().appendInt64(-4294967296ll);
        EXPECT_EQUAL(js.toString(), "-4294967296");
    }
    { // string
        js.clear().appendString("string");
        EXPECT_EQUAL(js.toString(), "\"string\"");
    }
    { // NULL
        js.clear().appendNull();
        EXPECT_EQUAL(js.toString(), "null");
    }
    { // quote
        js.clear().appendString("x\"y");
        EXPECT_EQUAL(js.toString(), "\"x\\\"y\"");
        js.clear().appendString("x\\y");
        EXPECT_EQUAL(js.toString(), "\"x\\\\y\"");
        js.clear().appendString("x/y");
        EXPECT_EQUAL(js.toString(), "\"x/y\"");
        js.clear().appendString("x\by");
        EXPECT_EQUAL(js.toString(), "\"x\\by\"");
        js.clear().appendString("x\fy");
        EXPECT_EQUAL(js.toString(), "\"x\\fy\"");
        js.clear().appendString("x\ny");
        EXPECT_EQUAL(js.toString(), "\"x\\ny\"");
        js.clear().appendString("x\ry");
        EXPECT_EQUAL(js.toString(), "\"x\\ry\"");
        js.clear().appendString("x\ty");
        EXPECT_EQUAL(js.toString(), "\"x\\ty\"");
    }
}

void
JSONTest::testJSONWriterObject()
{
    JSONStringer js;

    { // single pair
        js.beginObject().appendKey("k1").appendInt64(1l).endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":1}");
    }
    { // multiple pairs
        js.clear().beginObject().appendKey("k1").appendInt64(1l).appendKey("k2").appendInt64(2l).endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":1,\"k2\":2}");
    }
    { // object in object
        js.clear().beginObject().appendKey("k1").beginObject().appendKey("k1.1").appendInt64(11l).endObject().endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":{\"k1.1\":11}}");
    }
    { // object in object (multiple pairs)
        js.clear().beginObject().
            appendKey("k1").
            beginObject().
            appendKey("k1.1").appendInt64(11l).
            appendKey("k1.2").appendInt64(12l).
            endObject().
            appendKey("k2").
            beginObject().
            appendKey("k2.1").appendInt64(21l).
            appendKey("k2.2").appendInt64(22l).
            endObject().
            endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":{\"k1.1\":11,\"k1.2\":12},\"k2\":{\"k2.1\":21,\"k2.2\":22}}");
    }
    { // array in object
        js.clear().beginObject().appendKey("k1").
            beginArray().appendInt64(1l).appendInt64(2l).endArray().endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":[1,2]}");
    }
    { // array in object (multiple pairs)
        js.clear().beginObject().
            appendKey("k1").beginArray().appendInt64(1l).appendInt64(2l).endArray().
            appendKey("k2").beginArray().appendInt64(3l).appendInt64(4l).endArray().
            endObject();
        EXPECT_EQUAL(js.toString(), "{\"k1\":[1,2],\"k2\":[3,4]}");
    }
}


void
JSONTest::testJSONWriterArray()
{
    JSONStringer js;

    { // single element
        js.beginArray().appendInt64(1l).endArray();
        EXPECT_EQUAL(js.toString(), "[1]");
    }
    { // multiple elements
        js.clear().beginArray().appendInt64(1l).appendInt64(2l).endArray();
        EXPECT_EQUAL(js.toString(), "[1,2]");
    }
    { // array in array
        js.clear().beginArray().beginArray().appendInt64(1l).endArray().endArray();
        EXPECT_EQUAL(js.toString(), "[[1]]");
    }
    { // array in array (multiple elements)
        js.clear().beginArray().
            beginArray().appendInt64(1l).appendInt64(2l).endArray().
            beginArray().appendInt64(3l).appendInt64(4l).endArray().
            endArray();
        EXPECT_EQUAL(js.toString(), "[[1,2],[3,4]]");
    }
    { // object in array
        js.clear().beginArray().
            beginObject().appendKey("k1").appendInt64(1l).endObject().
            endArray();
        EXPECT_EQUAL(js.toString(), "[{\"k1\":1}]");
    }
    { // object in array (multiple elements)
        js.clear().beginArray().
            beginObject().appendKey("k1").appendInt64(1l).appendKey("k2").appendInt64(2l).endObject().
            beginObject().appendKey("k3").appendInt64(3l).appendKey("k4").appendInt64(4l).endObject().
            endArray();
        EXPECT_EQUAL(js.toString(), "[{\"k1\":1,\"k2\":2},{\"k3\":3,\"k4\":4}]");
    }
}


void
JSONTest::testJSONWriterComplex()
{
    JSONStringer js;

    js.beginObject();
    { // object
        js.appendKey("k1");
        js.beginObject();
        {
            js.appendKey("k1.1");
            js.appendInt64(1l);
        }
        {
            js.appendKey("k1.2");
            js.beginArray();
            js.appendInt64(2l);
            js.appendInt64(3l);
            js.endArray();
        }
        js.endObject();
    }
    { // object of object
        js.appendKey("k2");
        js.beginObject();
        {
            js.appendKey("k2.1");
            js.beginObject();
            {
                js.appendKey("k2.1.1");
                js.appendInt64(4l);
            }
            {
                js.appendKey("k2.1.2");
                js.beginArray();
                js.appendInt64(5l);
                js.appendInt64(6l);
                js.endArray();
            }
            js.endObject();
        }
        js.endObject();
    }
    { // array of object
        js.appendKey("k3");
        js.beginArray();
        {
            js.beginObject();
            {
                js.appendKey("k3.1");
                js.appendInt64(7l);
            }
            {
                js.appendKey("k3.2");
                js.beginArray();
                js.appendInt64(8l);
                js.appendInt64(9l);
                js.endArray();
            }
            js.endObject();
        }
        {
            js.beginObject();
            {
                js.appendKey("k3.1");
                js.appendInt64(10l);
            }
            {
                js.appendKey("k3.2");
                js.beginArray();
                js.appendInt64(11l);
                js.appendInt64(12l);
                js.endArray();
            }
            js.endObject();
        }
        js.endArray();
    }
    js.endObject();
    EXPECT_EQUAL(js.toString(), "{\"k1\":{\"k1.1\":1,\"k1.2\":[2,3]},\"k2\":{\"k2.1\":{\"k2.1.1\":4,\"k2.1.2\":[5,6]}},\"k3\":[{\"k3.1\":7,\"k3.2\":[8,9]},{\"k3.1\":10,\"k3.2\":[11,12]}]}");
}

namespace {
    struct Builder : public vespalib::JsonStreamTypes {
        void build(JsonStream& s) {
            s << Object() << "k1" << Object()
                << "k1.1" << 1
                << "k1.2" << Array()
                    << 2l << 3ll << End()
                << End()
              << "k2" << Object()
                << "k2.1" << Object()
                    << "k2.1.1" << 4u
                    << "k2.1.2" << Array()
                        << 5ul << 6ull << End()
                    << End()
                << End()
              << "k3" << Array()
                << Object()
                    << "k3.1" << -7
                    << "k3.2" << Array()
                        << -8l << -9ll << End()
                    << End()
                << Object()
                    << "k3.1" << 10l
                    << "k3.2" << Array()
                        << 11l << 12l << End()
                    << End()
                << End()
              << End();
        }
    };
}

void
JSONTest::testJsonStream()
{
    vespalib::asciistream as;
    vespalib::JsonStream stream(as);
    Builder b;
    b.build(stream);
    stream.finalize();
    EXPECT_EQUAL(as.str(), "{\"k1\":{\"k1.1\":1,\"k1.2\":[2,3]},\"k2\":{\"k2.1\":{\"k2.1.1\":4,\"k2.1.2\":[5,6]}},\"k3\":[{\"k3.1\":-7,\"k3.2\":[-8,-9]},{\"k3.1\":10,\"k3.2\":[11,12]}]}");
}

void
JSONTest::testJsonStreamErrors()
{
    using namespace vespalib::jsonstream;
        // Unsupported object keys
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << Object();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: An object value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << true;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: A bool value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << 13;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: An int64_t value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << uint64_t(13);
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: A uint64_t value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << 0.5;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: A double value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << jsonstream::Array();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: An array value cannot be an object key ({}(ObjectExpectingKey))", e.getReason());
    }
        // Invalid points to add End()
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << "foo" << End();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Object got key but not value. Cannot end it now ({foo}(ObjectExpectingValue))", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << End();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: No tag to end. At root ((RootExpectingArrayOrObjectStart))", e.getReason());
    }
        // Adding to finalized stream
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << "foo";
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't add a string value. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << false;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't add a bool value. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << 13;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't add a long long value. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << 13u;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't add an unsigned long long value. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << 0.2;
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't add a double value. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << Object();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't start a new object. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << jsonstream::Array();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't start a new array. (Finalized)", e.getReason());
    }
    try{
        vespalib::asciistream as;
        vespalib::JsonStream stream(as);
        stream << Object() << End() << End();
    } catch (vespalib::JsonStreamException& e) {
        EXPECT_EQUAL("Invalid state on call: Stream already finalized. Can't end it. (Finalized)", e.getReason());
    }
}

void
JSONTest::testJsonStreamStateReporting()
{
    using namespace vespalib::jsonstream;
    vespalib::asciistream as;
    vespalib::JsonStream stream(as);
    stream << jsonstream::Array() << 13
                      << "foo"
                      << Object() << "key" << "value" << End()
                      << false
           << End();
    EXPECT_EQUAL("Current: Finalized", stream.getJsonStreamState());
}

int
JSONTest::Main()
{
    TEST_INIT("json_test");

    testJSONWriterValues();
    testJSONWriterObject();
    testJSONWriterArray();
    testJSONWriterComplex();
    testJsonStream();
    testJsonStreamErrors();
    testJsonStreamStateReporting();

    TEST_DONE();
}

TEST_APPHOOK(JSONTest);

