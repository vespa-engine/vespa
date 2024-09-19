// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/zc_decoder_validator.h>
#include <vespa/searchlib/diskindex/zcbuf.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <limits>

using search::diskindex::ZcBuf;
using search::diskindex::ZcDecoderValidator;

class ZcTest : public ::testing::Test
{
protected:
    static constexpr uint32_t fill_size = 1000;
    static constexpr uint32_t timing_loops = 10000000;

    ZcBuf _zc_buf;
    ZcTest();
    ~ZcTest() override;
    uint32_t encode_used_bytes(uint64_t value);
    void check_encoding(uint32_t bytes, uint64_t min, uint64_t max);
    void fill();
    bool verify_decoder();
};

ZcTest::ZcTest()
: ::testing::Test(),
  _zc_buf()
{
}

ZcTest::~ZcTest() = default;

uint32_t
ZcTest::encode_used_bytes(uint64_t value)
{
    _zc_buf.clear();
    _zc_buf.encode42(value);
    auto view = _zc_buf.view();
    bool failed = false;
    ZcDecoderValidator zc_decoder(view);
    EXPECT_EQ(value, zc_decoder.decode42()) << (failed = true, "");
    EXPECT_EQ(view.size(), zc_decoder.pos()) << (failed = true, "");
    if (value <= std::numeric_limits<uint32_t>::max()) {
        zc_decoder = ZcDecoderValidator(view);
        EXPECT_EQ(value, zc_decoder.decode32()) << (failed = true, "");
        EXPECT_EQ(view.size(), zc_decoder.pos()) << (failed = true, "");
    }
    return failed ? 0 : view.size();
}

void
ZcTest::check_encoding(uint32_t bytes, uint64_t min, uint64_t max)
{
    SCOPED_TRACE(std::string("check_encoding, bytes=") + std::to_string(bytes) +
                                  ", min=" + std::to_string(min) + ", max=" + std::to_string(max));
    // Test boundary values
    EXPECT_EQ(bytes, encode_used_bytes(min));
    EXPECT_EQ(bytes, encode_used_bytes(max));
    // Try values between boundaries
    for (uint32_t shift = 0; shift < 64; ++shift) {
        SCOPED_TRACE(std::string("shift=") + std::to_string(shift));
        uint64_t toggle = UINT64_C(1) << shift;
        if (toggle >= min && toggle <= max) {
            EXPECT_EQ(bytes, encode_used_bytes(toggle));
        }
        auto toggled_min = min ^ toggle;
        if (toggled_min >= min && toggled_min <= max) {
            EXPECT_EQ(bytes, encode_used_bytes(toggled_min));
        }
        auto toggled_max = max ^ toggle;
        if (toggled_max >= min && toggled_max <= max) {
            EXPECT_EQ(bytes, encode_used_bytes(toggled_max));
        }
    }
}

void
ZcTest::fill()
{
    for (uint32_t i = 0; i < fill_size; ++i) {
        _zc_buf.encode32(i);
    }
}

bool
ZcTest::verify_decoder()
{
    bool failed = false;
    ZcDecoderValidator zc_decoder(_zc_buf.view());
    for (uint32_t i = 0; i < fill_size; ++i) {
        if (zc_decoder.decode32() != i) [[unlikely]] {
            failed = true;
        }
    }
    return !failed;
}

TEST_F(ZcTest, encode_then_decode_should_give_original_result)
{
    constexpr uint64_t one = 1;
    check_encoding(1, 0, (one << 7) - 1);
    check_encoding(2, one << 7, (one << 14) - 1);
    check_encoding(3, one << 14, (one << 21) - 1);
    check_encoding(4, one << 21, (one << 28) - 1);
    check_encoding(5, one << 28, (one << 35) - 1);
    check_encoding(6, one << 35, (one << 42) - 1);
    EXPECT_EQ(5, encode_used_bytes(std::numeric_limits<uint32_t>::max()));
}

TEST_F(ZcTest, DISABLED_decode_speed_decoder)
{
    fill();
    for (uint32_t outer = 0; outer < timing_loops; ++outer) {
        EXPECT_TRUE(verify_decoder());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
