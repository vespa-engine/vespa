// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/xoshiro.h>

#include <gmock/gmock.h>

#include <limits>
#include <random>

using namespace ::testing;

namespace vespalib {

using XoPrng = Xoshiro256PlusPlusPrng;

// Meta-test: Xoshiro256PlusPlusPrng must satisfy the uniform_random_bit_generator concept
static_assert(std::uniform_random_bit_generator<XoPrng>);

TEST(Xoshiro256PlusPlusTest, min_max_covers_u64_range) {
    EXPECT_EQ(XoPrng::min(), 0);
    EXPECT_EQ(XoPrng::max(), std::numeric_limits<uint64_t>::max());
}

TEST(Xoshiro256PlusPlusTest, default_seed_state_is_deterministic) {
    XoPrng rng;
    EXPECT_EQ(rng(), 0x1ddd89fc9b318a42);
    EXPECT_EQ(rng(), 0xe71414201009afd5);
    EXPECT_EQ(rng(), 0xa515d7518379891a);
    EXPECT_EQ(rng(), 0xdcdbfe869406dfb);
}

TEST(Xoshiro256PlusPlusTest, u64_seeded_state_is_deterministic) {
    {
        XoPrng rng(0x1337cafe);
        EXPECT_EQ(rng(), 0xee5d31f96adfacd);
        EXPECT_EQ(rng(), 0x7746dc1f69fffcfc);
        EXPECT_EQ(rng(), 0x7cf56044c4cebacd);
        EXPECT_EQ(rng(), 0xdf65fd6b42bbaccc);
    }
    {
        XoPrng rng(0x1337caff);
        EXPECT_EQ(rng(), 0x422bd9d0409b5bca);
        EXPECT_EQ(rng(), 0x5565e1fe9014ec7f);
        EXPECT_EQ(rng(), 0xe4d29fb55f9d6b72);
        EXPECT_EQ(rng(), 0x99a2d5acc2c23efe);
    }
}

TEST(Xoshiro256PlusPlusTest, u256_seeded_state_is_deterministic) {
    // The below initially highly regular PRNG outputs show how important it
    // is to seed with something that isn't completely bogus...!
    // It's probably a lot better to seed with a single low quality u64 that is
    // then splitmix64'ed into something OK than to seed with 256 bits of crap.
    XoPrng rng(0x1111'1111'1111'1111, 0x2222'2222'2222'2222, 0x3333'3333'3333'3333, 0x4444'4444'4444'4444);
    EXPECT_EQ(rng(), 0xbbbbbbbbbbbbbbbb);
    EXPECT_EQ(rng(), 0x9999999999199999);
    EXPECT_EQ(rng(), 0x6666666665e66665);
    EXPECT_EQ(rng(), 0x555555777759ddd9);
    EXPECT_EQ(rng(), 0x80009133330d1113);
    // Output entropy quickly increases with subsequent rounds.
    // The world is healing, etc.
}

TEST(Xoshiro256PlusPlusTest, can_be_used_with_stl_prng_statistical_distributions) {
    XoPrng rng(0xcafef00d12345678);
    // It's not guaranteed that the output of std::uniform_int_distribution is stable
    // across compiler/library versions, so this doesn't check any explicit output values.
    std::uniform_int_distribution<> dist(1, 10);
    for (int i = 0; i < 100; ++i) {
        const int v = dist(rng);
        EXPECT_TRUE(v >= 0 && v <= 10) << v;
    }
}

TEST(Xoshiro256PlusPlusTest, instances_can_be_deep_copied_and_compared) {
    XoPrng rng1(0x1337cafe), rng2(0x1337caff);

    XoPrng rng1_copy(rng1);
    auto   rng2_copy = rng2;

    EXPECT_TRUE(rng1 == rng1);
    EXPECT_TRUE(rng1_copy == rng1);
    EXPECT_FALSE(rng1 == rng2);
    EXPECT_TRUE(rng2_copy == rng2);

    EXPECT_EQ(rng1_copy(), 0xee5d31f96adfacd);
    EXPECT_EQ(rng2_copy(), 0x422bd9d0409b5bca);
    // Internal state has changed
    EXPECT_FALSE(rng1_copy == rng1);
    EXPECT_FALSE(rng2_copy == rng2);
}

} // namespace vespalib
