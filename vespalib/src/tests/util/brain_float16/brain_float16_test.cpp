// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/brain_float16.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdio.h>
#include <cmath>

using namespace vespalib;

using Limits = std::numeric_limits<BrainFloat16>;

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

TEST(BrainFloat16Test, check_special_values) {
    float f_inf = std::numeric_limits<float>::infinity();
    float f_neg = -f_inf;
    float f_nan = std::numeric_limits<float>::quiet_NaN();
    BrainFloat16 b_inf = f_inf;
    BrainFloat16 b_neg = f_neg;
    BrainFloat16 b_nan = f_nan;
    double d_inf = b_inf;
    double d_neg = b_neg;
    double d_nan = b_nan;
    EXPECT_EQ(d_inf, std::numeric_limits<double>::infinity());
    EXPECT_EQ(d_neg, -std::numeric_limits<double>::infinity());
    EXPECT_TRUE(std::isnan(d_nan));
    float f_from_b_inf = b_inf;
    float f_from_b_neg = b_neg;
    float f_from_b_nan = b_nan;
    EXPECT_EQ(memcmp(&f_inf, &f_from_b_inf, sizeof(float)), 0);
    EXPECT_EQ(memcmp(&f_neg, &f_from_b_neg, sizeof(float)), 0);
    EXPECT_EQ(memcmp(&f_nan, &f_from_b_nan, sizeof(float)), 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
