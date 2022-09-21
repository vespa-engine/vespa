// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("rawbuf_test");

using vespalib::string;
using namespace search;

namespace {

string getString(const RawBuf &buf) {
    return {buf.GetDrainPos(), buf.GetUsedLen()};
}

template <typename T>
void checkAddNum(void (RawBuf::*addNum)(T, size_t, char), size_t num,
                 size_t fieldw, char fill, const string &expected) {
    RawBuf buf(10);
    (buf.*addNum)(num, fieldw, fill);
    EXPECT_EQUAL(expected, getString(buf));
}

TEST("require that rawbuf can append data of known length") {
    RawBuf buf(10);
    const string data("foo bar baz qux quux");
    buf.append(data.data(), data.size());
    EXPECT_EQUAL(data, getString(buf));
}

TEST("require that prealloc makes enough room") {
    RawBuf buf(10);
    buf.append("foo", 3);
    EXPECT_EQUAL(7u, buf.GetFreeLen());
    buf.preAlloc(100);
    EXPECT_EQUAL("foo", getString(buf));
    EXPECT_LESS_EQUAL(100u, buf.GetFreeLen());
}

TEST("require that rawbuf can 'putToInet' 64-bit numbers") {
    RawBuf buf(1);
    buf.Put64ToInet(0x123456789abcdef0ULL);
    EXPECT_EQUAL(8ul, buf.GetUsedLen());
    EXPECT_EQUAL(0x12, (int) buf.GetDrainPos()[0] & 0xff);
    EXPECT_EQUAL(0x34, (int) buf.GetDrainPos()[1] & 0xff);
    EXPECT_EQUAL(0x56, (int) buf.GetDrainPos()[2] & 0xff);
    EXPECT_EQUAL(0x78, (int) buf.GetDrainPos()[3] & 0xff);
    EXPECT_EQUAL(0x9a, (int) buf.GetDrainPos()[4] & 0xff);
    EXPECT_EQUAL(0xbc, (int) buf.GetDrainPos()[5] & 0xff);
    EXPECT_EQUAL(0xde, (int) buf.GetDrainPos()[6] & 0xff);
    EXPECT_EQUAL(0xf0, (int) buf.GetDrainPos()[7] & 0xff);
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
