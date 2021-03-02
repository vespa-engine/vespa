// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/brain_float16.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdio.h>
#include <cmath>
#include <cmath>
#include <cfenv>
#include <vector>

using namespace vespalib;

using Limits = std::numeric_limits<BrainFloat16>;

static std::vector<float> simple_values = {
    0.0, 1.0, -1.0, -0.0, 1.75, 0x1.02p20, -0x1.02p-20, 0x3.0p-100, 0x7.0p100
};

TEST(BrainFloat16Test, normal_usage) {
    EXPECT_EQ(sizeof(float), 4);
    EXPECT_EQ(sizeof(BrainFloat16), 2);
    BrainFloat16 answer = 42;
    double fortytwo = answer;
    EXPECT_EQ(fortytwo, 42);
    std::vector<BrainFloat16> vec;
    for (float value : simple_values) {
        BrainFloat16 b = value;
        float recover = b;
        EXPECT_EQ(value, recover);
    }
    BrainFloat16 b1 = 0x101;
    EXPECT_EQ(float(b1), 0x100);
    BrainFloat16 b2 = 0x111;
    EXPECT_EQ(float(b2), 0x110);
}

TEST(BrainFloat16Test, with_nbostream) {
    nbostream buf;
    for (BrainFloat16 value : simple_values) {
        buf << value;
    }
    for (float value : simple_values) {
        BrainFloat16 stored;
        buf >> stored;
        EXPECT_EQ(float(stored), value);
    }
}

TEST(BrainFloat16Test, constants_check) {
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

	BrainFloat16 try_epsilon = 1.0f + eps;
	EXPECT_GT(try_epsilon.to_float(), 1.0f);
	BrainFloat16 try_half_epsilon = 1.0f + (0.5f * eps);
	EXPECT_EQ(try_half_epsilon.to_float(), 1.0f);

	EXPECT_LT(big, std::numeric_limits<float>::max());
	EXPECT_GT(low, std::numeric_limits<float>::lowest());

	printf("bfloat16 epsilon: %.10g (float has %.20g)\n", eps, std::numeric_limits<float>::epsilon());
	printf("bfloat16 norm_min: %.20g (float has %.20g)\n", n_min, std::numeric_limits<float>::min());
	printf("bfloat16 denorm_min: %.20g (float has %.20g)\n", d_min, std::numeric_limits<float>::denorm_min());
	printf("bfloat16 max: %.20g (float has %.20g)\n", big, std::numeric_limits<float>::max());
	printf("bfloat16 lowest: %.20g (float has %.20g)\n", low, std::numeric_limits<float>::lowest());
}

TEST(BrainFloat16Test, traits_check) {
        EXPECT_TRUE(std::is_trivially_constructible<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivially_move_constructible<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivially_default_constructible<BrainFloat16>::value);
        EXPECT_TRUE((std::is_trivially_assignable<BrainFloat16,BrainFloat16>::value));
        EXPECT_TRUE(std::is_trivially_move_assignable<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivially_copy_assignable<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivially_copyable<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivially_destructible<BrainFloat16>::value);
        EXPECT_TRUE(std::is_trivial<BrainFloat16>::value);
        EXPECT_TRUE(std::is_swappable<BrainFloat16>::value);
        EXPECT_TRUE(std::has_unique_object_representations<BrainFloat16>::value);
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

TEST(BrainFloat16Test, check_special_values) {
    // we should not need to support HW without normal float support:
    EXPECT_TRUE(std::numeric_limits<float>::has_quiet_NaN);
    EXPECT_TRUE(std::numeric_limits<float>::has_signaling_NaN);
    EXPECT_TRUE(std::numeric_limits<BrainFloat16>::has_quiet_NaN);
    EXPECT_TRUE(std::numeric_limits<BrainFloat16>::has_signaling_NaN);
    std::feclearexcept(FE_ALL_EXCEPT);
    EXPECT_TRUE(std::fetestexcept(FE_INVALID) == 0);
    float f_inf = std::numeric_limits<float>::infinity();
    float f_neg = -f_inf;
    float f_qnan = std::numeric_limits<float>::quiet_NaN();
    float f_snan = std::numeric_limits<float>::signaling_NaN();
    BrainFloat16 b_inf = std::numeric_limits<BrainFloat16>::infinity();
    BrainFloat16 b_qnan = std::numeric_limits<BrainFloat16>::quiet_NaN();
    BrainFloat16 b_snan = std::numeric_limits<BrainFloat16>::signaling_NaN();
    BrainFloat16 b_from_f_inf = f_inf;
    BrainFloat16 b_from_f_neg = f_neg;
    BrainFloat16 b_from_f_qnan = f_qnan;
    BrainFloat16 b_from_f_snan = f_snan;
    EXPECT_EQ(memcmp(&b_inf, &b_from_f_inf, sizeof(BrainFloat16)), 0);
    EXPECT_EQ(memcmp(&b_qnan, &b_from_f_qnan, sizeof(BrainFloat16)), 0);
    EXPECT_EQ(memcmp(&b_snan, &b_from_f_snan, sizeof(BrainFloat16)), 0);
    printf("+inf float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_inf), HEX_DUMP(b_from_f_inf));
    printf("-inf float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_neg), HEX_DUMP(b_from_f_neg));
    printf("qNaN float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_qnan), HEX_DUMP(b_from_f_qnan));
    printf("sNan float is '%s' / bf16 is '%s'\n", HEX_DUMP(f_snan), HEX_DUMP(b_from_f_snan));
    double d_inf = b_inf;
    double d_neg = b_from_f_neg;
    double d_qnan = b_qnan;
    EXPECT_TRUE(std::fetestexcept(FE_INVALID) == 0);
    // float->double conversion of signaling NaN:
    double d_snan = b_snan;
    EXPECT_TRUE(std::fetestexcept(FE_INVALID) != 0);
    std::feclearexcept(FE_ALL_EXCEPT);
    EXPECT_TRUE(std::fetestexcept(FE_INVALID) == 0);
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
    // none of the BF16 operations should trigger FPE:
    EXPECT_TRUE(std::fetestexcept(FE_INVALID) == 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
