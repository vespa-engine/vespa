// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/quant/multi_bit_packer.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <gmock/gmock.h>

#include <algorithm>
#include <format>
#include <random>
#include <span>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

template <uint8_t Bits>
void do_test_bit_packing_of_all_representable_values() {
    SCOPED_TRACE("bit packing of all representable values");
    using BP = MultiBitPacker<Bits>;

    // Ensure we can pack/unpack all representable values for Bits
    for (uint8_t v = 0; v < (1u << Bits); ++v) {
        // For a range of sizes that includes multiple full blocks with and without remainders
        for (size_t n = 0; n < 32; ++n) {
            // Test with destination arrays (both for packing and unpacking) that
            // are all zeroes and all ones to ensure we do not accidentally leave
            // dirty bits behind from the destination.
            for (const uint8_t packed_init_value : {0x00, 0xff}) {
                for (const uint8_t unpacked_init_value : {0x00, 0xff}) {
                    std::vector<uint8_t> in(n);
                    std::ranges::fill(in, v);
                    // Input bits > Bits should be masked away to avoid tainting the output
                    std::vector tainted_in = in;
                    for (uint8_t& b : tainted_in) {
                        b |= (0xff << Bits);
                    }
                    std::vector<uint8_t> packed(BP::packed_bytes(n), packed_init_value);
                    BP::pack(packed.data(), tainted_in.data(), n);

                    std::vector<uint8_t> unpacked(n, unpacked_init_value);
                    BP::unpack(unpacked.data(), packed.data(), n);

                    ASSERT_THAT(unpacked, ElementsAreArray(in))
                        << "n=" << n << ", v=" << int(v) << "packed_init_value=" << int(packed_init_value)
                        << ", unpacked_init_value=" << int(unpacked_init_value);
                }
            }
        }
    }
}

// Just testing with the same value for all packed/unpacked elements hides
// possible permutation errors, so also test with random element values.
template <uint8_t Bits>
void do_test_bit_packing_of_randomized_values() {
    SCOPED_TRACE("bit packing of randomized values");
    using BP = MultiBitPacker<Bits>;
    const uint64_t         seed = std::random_device{}();
    Xoshiro256PlusPlusPrng prng(seed);

    auto gen_rand_value = [&prng]() noexcept { return prng() & ((1u << Bits) - 1); };

    for (size_t n = 1; n < 32; ++n) {
        // Reuse vectors across rounds to help catch any dirty bit reuse
        std::vector<uint8_t> in(n);
        std::vector<uint8_t> packed(BP::packed_bytes(n));
        std::vector<uint8_t> unpacked(n);

        for (size_t i = 0; i < 1'000; ++i) {
            std::ranges::generate(in, gen_rand_value);
            BP::pack(packed.data(), in.data(), n);
            BP::unpack(unpacked.data(), packed.data(), n);
            ASSERT_THAT(unpacked, ElementsAreArray(in)) << "n=" << n << ", seed=" << std::hex << seed;
        }
    }
}

template <uint8_t Bits>
void do_test_remainder_bits_are_zeroed() {
    SCOPED_TRACE("remainder bits are zeroed");
    using BP = MultiBitPacker<Bits>;
    static_assert(BP::packed_bytes(1) == 1);
    std::array<uint8_t, 1> dst{};
    std::array<uint8_t, 1> src = {(1u << Bits) - 1};
    BP::pack(dst.data(), src.data(), 1);
    // Every 1-element packing fits within a byte and should live in its LSBs.
    // The MSBs should then consequently be zero. This is not an exhaustive test.
    constexpr uint8_t msb_mask = static_cast<uint8_t>(0xff << Bits);
    EXPECT_EQ(dst[0] & msb_mask, 0);
}

template <uint8_t Bits>
void do_run_bit_pack_unpack_tests() {
    ASSERT_NO_FATAL_FAILURE(do_test_bit_packing_of_all_representable_values<Bits>());
    ASSERT_NO_FATAL_FAILURE(do_test_bit_packing_of_randomized_values<Bits>());
    ASSERT_NO_FATAL_FAILURE(do_test_remainder_bits_are_zeroed<Bits>());
}

TEST(MultiBitPackerTest, packed_byte_count_is_calculated_for_1_bit) {
    using BP = MultiBitPacker<1>;
    EXPECT_EQ(BP::packed_bytes(0), 0);
    for (size_t i = 1; i <= 8; ++i) {
        EXPECT_EQ(BP::packed_bytes(i), 1);
    }
    EXPECT_EQ(BP::packed_bytes(9), 2);
    EXPECT_EQ(BP::packed_bytes(16), 2);
    EXPECT_EQ(BP::packed_bytes(17), 3);
}

TEST(MultiBitPackerTest, can_pack_and_unpack_1_bit_values) {
    do_run_bit_pack_unpack_tests<1>();
}

TEST(MultiBitPackerTest, packed_byte_count_is_calculated_for_2_bits) {
    using BP = MultiBitPacker<2>;
    EXPECT_EQ(BP::packed_bytes(0), 0);
    for (size_t i = 1; i <= 4; ++i) {
        EXPECT_EQ(BP::packed_bytes(i), 1);
    }
    EXPECT_EQ(BP::packed_bytes(5), 2);
    EXPECT_EQ(BP::packed_bytes(8), 2);
    EXPECT_EQ(BP::packed_bytes(9), 3);
}

TEST(MultiBitPackerTest, can_pack_and_unpack_2_bit_values) {
    do_run_bit_pack_unpack_tests<2>();
}

TEST(MultiBitPackerTest, packed_byte_count_is_calculated_for_3_bits) {
    using BP = MultiBitPacker<3>;
    EXPECT_EQ(BP::packed_bytes(0), 0);
    EXPECT_EQ(BP::packed_bytes(1), 1);
    EXPECT_EQ(BP::packed_bytes(2), 1);
    EXPECT_EQ(BP::packed_bytes(3), 2); // 3rd value is split across 2 bytes
    EXPECT_EQ(BP::packed_bytes(4), 2);
    EXPECT_EQ(BP::packed_bytes(5), 2);
    EXPECT_EQ(BP::packed_bytes(6), 3); // Also split across 2 bytes
    EXPECT_EQ(BP::packed_bytes(7), 3);
    EXPECT_EQ(BP::packed_bytes(8), 3);
    EXPECT_EQ(BP::packed_bytes(9), 4);
}

TEST(MultiBitPackerTest, can_pack_and_unpack_3_bit_values) {
    do_run_bit_pack_unpack_tests<3>();
}

TEST(MultiBitPackerTest, packed_byte_count_is_calculated_for_4_bits) {
    using BP = MultiBitPacker<4>;
    EXPECT_EQ(BP::packed_bytes(0), 0);
    EXPECT_EQ(BP::packed_bytes(1), 1);
    EXPECT_EQ(BP::packed_bytes(2), 1);
    EXPECT_EQ(BP::packed_bytes(3), 2);
    EXPECT_EQ(BP::packed_bytes(4), 2);
    EXPECT_EQ(BP::packed_bytes(5), 3);
}

TEST(MultiBitPackerTest, can_pack_and_unpack_4_bit_values) {
    do_run_bit_pack_unpack_tests<4>();
}

TEST(MultiBitPackerTest, bit_packer_function_dispatch_chooses_correct_packer_type) {
    for (const uint8_t b : {1, 2, 3, 4}) {
        const size_t bytes = with_packer_for_bit_count(b, [](auto bp) { return decltype(bp)::packed_bytes(8); });
        ASSERT_EQ(bytes, b); // 1-1 for 8 element packing
    }
}

} // namespace vespalib::quant
