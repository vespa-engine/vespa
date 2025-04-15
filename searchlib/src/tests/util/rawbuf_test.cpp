// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("rawbuf_test");

using std::string;
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
    EXPECT_EQ(expected, getString(buf));
}

TEST(RawBufTest, require_that_rawbuf_can_append_data_of_known_length) {
    RawBuf buf(10);
    const string data("foo bar baz qux quux");
    buf.append(data.data(), data.size());
    EXPECT_EQ(data, getString(buf));
}

TEST(RawBufTest, require_that_prealloc_makes_enough_room) {
    RawBuf buf(10);
    buf.append("foo", 3);
    EXPECT_EQ(7u, buf.GetFreeLen());
    buf.preAlloc(100);
    EXPECT_EQ("foo", getString(buf));
    EXPECT_LE(100u, buf.GetFreeLen());
}

TEST(RawBufTest, require_that_rawbuf_can_putToInet_64_bit_numbers) {
    RawBuf buf(1);
    buf.Put64ToInet(0x123456789abcdef0ULL);
    EXPECT_EQ(8ul, buf.GetUsedLen());
    EXPECT_EQ(0x12, (int) buf.GetDrainPos()[0] & 0xff);
    EXPECT_EQ(0x34, (int) buf.GetDrainPos()[1] & 0xff);
    EXPECT_EQ(0x56, (int) buf.GetDrainPos()[2] & 0xff);
    EXPECT_EQ(0x78, (int) buf.GetDrainPos()[3] & 0xff);
    EXPECT_EQ(0x9a, (int) buf.GetDrainPos()[4] & 0xff);
    EXPECT_EQ(0xbc, (int) buf.GetDrainPos()[5] & 0xff);
    EXPECT_EQ(0xde, (int) buf.GetDrainPos()[6] & 0xff);
    EXPECT_EQ(0xf0, (int) buf.GetDrainPos()[7] & 0xff);
}


}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
