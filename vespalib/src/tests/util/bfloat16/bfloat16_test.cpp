// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/bfloat16.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdio.h>
#include <cmath>
#include <cmath>
#include <vector>

using namespace vespalib;

using Limits = std::numeric_limits<BFloat16>;

static std::vector<float> simple_values = {
    0.0, 1.0, -1.0, -0.0, 1.75, 0x1.02p20, -0x1.02p-20, 0x3.0p-100, 0x7.0p100
};

TEST(BFloat16Test, normal_usage) {
    EXPECT_EQ(sizeof(float), 4);
    EXPECT_EQ(sizeof(BFloat16), 2);
    BFloat16 answer = 42;
    double fortytwo = answer;
    EXPECT_EQ(fortytwo, 42);
    std::vector<BFloat16> vec;
    for (float value : simple_values) {
        BFloat16 b = value;
        float recover = b;
        EXPECT_EQ(value, recover);
    }
    BFloat16 b1 = 0x101;
    EXPECT_EQ(float(b1), 0x100);
    BFloat16 b2 = 0x111;
    EXPECT_EQ(float(b2), 0x110);
}

TEST(BFloat16Test, has_range_of_int_8) {
    for (int i = -128; i < 128; ++i) {
        int8_t byte = i;
        float flt = byte;
        EXPECT_EQ(byte, i);
        EXPECT_EQ(flt, i);
        BFloat16 value = flt;
        float recover = value;
        EXPECT_EQ(recover, flt);
    }
}

TEST(BFloat16Test, with_nbostream) {
    nbostream buf;
    for (BFloat16 value : simple_values) {
        buf << value;
    }
    for (float value : simple_values) {
        BFloat16 stored;
        buf >> stored;
        EXPECT_EQ(float(stored), value);
    }
}

TEST(BFloat16Test, constants_check) {
	EXPECT_EQ(0x1.0p-7, (1.0/128.0));

	float n_min = Limits::min();
	float d_min = Limits::denorm_min();
	float eps = Limits::epsilon();
	float big = Limits::max();
	float low = Limits::lowest();

	EXPECT_EQ(n_min, 0x1.0p-126);
	EXPECT_EQ(d_min, 0x1.0p-133);
	EXPECT_EQ(eps, 0x1.0p-7);
	EXPECT_EQ(big, 0x1.FEp127);
	EXPECT_EQ(low, -big);

	EXPECT_EQ(n_min, std::numeric_limits<float>::min());
	EXPECT_EQ(d_min, n_min / 128.0);
	EXPECT_GT(eps, std::numeric_limits<float>::epsilon());

	BFloat16 try_epsilon = 1.0f + eps;
	EXPECT_GT(try_epsilon.to_float(), 1.0f);
	BFloat16 try_half_epsilon = 1.0f + (0.5f * eps);
	EXPECT_EQ(try_half_epsilon.to_float(), 1.0f);

	EXPECT_LT(big, std::numeric_limits<float>::max());
	EXPECT_GT(low, std::numeric_limits<float>::lowest());

	printf("bfloat16 epsilon: %.10g (float has %.20g)\n", eps, std::numeric_limits<float>::epsilon());
	printf("bfloat16 norm_min: %.20g (float has %.20g)\n", n_min, std::numeric_limits<float>::min());
	printf("bfloat16 denorm_min: %.20g (float has %.20g)\n", d_min, std::numeric_limits<float>::denorm_min());
	printf("bfloat16 max: %.20g (float has %.20g)\n", big, std::numeric_limits<float>::max());
	printf("bfloat16 lowest: %.20g (float has %.20g)\n", low, std::numeric_limits<float>::lowest());
}

TEST(BFloat16Test, traits_check) {
        EXPECT_TRUE(std::is_trivially_constructible<BFloat16>::value);
        EXPECT_TRUE(std::is_trivially_move_constructible<BFloat16>::value);
        EXPECT_TRUE(std::is_trivially_default_constructible<BFloat16>::value);
        EXPECT_TRUE((std::is_trivially_assignable<BFloat16,BFloat16>::value));
        EXPECT_TRUE(std::is_trivially_move_assignable<BFloat16>::value);
        EXPECT_TRUE(std::is_trivially_copy_assignable<BFloat16>::value);
        EXPECT_TRUE(std::is_trivially_copyable<BFloat16>::value);
        EXPECT_TRUE(std::is_trivially_destructible<BFloat16>::value);
        EXPECT_TRUE(std::is_trivial<BFloat16>::value);
        EXPECT_TRUE(std::is_swappable<BFloat16>::value);
        EXPECT_TRUE(std::has_unique_object_representations<BFloat16>::value);
}

static std::string hexdump(const void *p, size_t sz) {
    char tmpbuf[10];
    if (sz == 2) {
        uint16_t bits;
        memcpy(&bits, p, sz);
        snprintf(tmpbuf, 10, "%04x", bits);
    } else if (sz == 4) {
        uint32_t bits;
        memcpy(&bits, p, sz);
        snprintf(tmpbuf, 10, "%08x", bits);
    } else {
        abort();
    }
    return tmpbuf;
}
#define HEX_DUMP(arg) hexdump(&arg, sizeof(arg)).c_str()

TEST(BFloat16Test, check_special_values) {
    // we should not need to support HW without normal float support:
    EXPECT_TRUE(std::numeric_limits<float>::has_quiet_NaN);
    EXPECT_TRUE(std::numeric_limits<float>::has_signaling_NaN);
    EXPECT_TRUE(std::numeric_limits<BFloat16>::has_quiet_NaN);
    EXPECT_TRUE(std::numeric_limits<BFloat16>::has_signaling_NaN);
    float f_inf = std::numeric_limits<float>::infinity();
    float f_neg = -f_inf;
    float f_qnan = std::numeric_limits<float>::quiet_NaN();
    float f_snan = std::numeric_limits<float>::signaling_NaN();
    BFloat16 b_inf = std::numeric_limits<BFloat16>::infinity();
    BFloat16 b_qnan = std::numeric_limits<BFloat16>::quiet_NaN();
    BFloat16 b_snan = std::numeric_limits<BFloat16>::signaling_NaN();
    BFloat16 b_from_f_inf = f_inf;
    BFloat16 b_from_f_neg = f_neg;
    BFloat16 b_from_f_qnan = f_qnan;
    BFloat16 b_from_f_snan = f_snan;
    EXPECT_EQ(memcmp(&b_inf, &b_from_f_inf, sizeof(BFloat16)), 0);
    EXPECT_EQ(memcmp(&b_qnan, &b_from_f_qnan, sizeof(BFloat16)), 0);
    EXPECT_EQ(memcmp(&b_snan, &b_from_f_snan, sizeof(BFloat16)), 0);
    printf("+inf float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_inf), HEX_DUMP(b_from_f_inf));
    printf("-inf float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_neg), HEX_DUMP(b_from_f_neg));
    printf("qNaN float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_qnan), HEX_DUMP(b_from_f_qnan));
    printf("sNan float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_snan), HEX_DUMP(b_from_f_snan));
    double d_inf = b_inf;
    double d_neg = b_from_f_neg;
    double d_qnan = b_qnan;
    double d_snan = b_snan;
    EXPECT_EQ(d_inf, std::numeric_limits<double>::infinity());
    EXPECT_EQ(d_neg, -std::numeric_limits<double>::infinity());
    EXPECT_TRUE(std::isnan(d_qnan));
    EXPECT_TRUE(std::isnan(d_snan));
    float f_from_b_inf = b_inf;
    float f_from_b_neg = b_from_f_neg;
    float f_from_b_qnan = b_qnan;
    float f_from_b_snan = b_snan;
    EXPECT_EQ(memcmp(&f_inf, &f_from_b_inf, sizeof(float)), 0);
    EXPECT_EQ(memcmp(&f_neg, &f_from_b_neg, sizeof(float)), 0);
    EXPECT_EQ(memcmp(&f_qnan, &f_from_b_qnan, sizeof(float)), 0);
    EXPECT_EQ(memcmp(&f_snan, &f_from_b_snan, sizeof(float)), 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
