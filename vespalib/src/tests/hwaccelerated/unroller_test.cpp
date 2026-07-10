// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/hwaccelerated/unroller.h>

#include <gmock/gmock.h>

#include <format>

using namespace ::testing;

namespace vespalib::hwaccelerated {

namespace {

template <size_t UnrollCount>
std::string unroll_simple() {
    std::string out;

    auto fn = [&out](auto idx) { std::format_to(std::back_inserter(out), "{} ", decltype(idx)::value); };
    Unroller::unroll<UnrollCount>(fn);
    return out;
}

template <size_t UnrollCount>
std::string unroll_with_args() {
    std::string out;
    const char  c = ':';
    int         mut_v = 1;

    auto fn = [&out](auto idx, char my_c, int& my_mut_v) {
        std::format_to(std::back_inserter(out), "{}{}{} ", decltype(idx)::value, my_c, my_mut_v);
        my_mut_v += 2;
    };
    Unroller::unroll<UnrollCount>(fn, c, mut_v);
    return out;
}

} // namespace

TEST(ForceUnrollTest, unrolled_fn_invocations_have_well_defined_index_order) {
    EXPECT_EQ(unroll_simple<1>(), "0 ");
    EXPECT_EQ(unroll_simple<2>(), "0 1 ");
    EXPECT_EQ(unroll_simple<3>(), "0 1 2 ");
    EXPECT_EQ(unroll_simple<4>(), "0 1 2 3 ");
    EXPECT_EQ(unroll_simple<5>(), "0 1 2 3 4 ");
    EXPECT_EQ(unroll_simple<6>(), "0 1 2 3 4 5 ");
    EXPECT_EQ(unroll_simple<7>(), "0 1 2 3 4 5 6 ");
    EXPECT_EQ(unroll_simple<8>(), "0 1 2 3 4 5 6 7 ");
    EXPECT_EQ(unroll_simple<9>(), "0 1 2 3 4 5 6 7 8 ");
    EXPECT_EQ(unroll_simple<10>(), "0 1 2 3 4 5 6 7 8 9 ");
}

TEST(ForceUnrollTest, can_pass_mutable_and_immutable_fn_args) {
    EXPECT_EQ(unroll_with_args<1>(), "0:1 ");
    EXPECT_EQ(unroll_with_args<2>(), "0:1 1:3 ");
    EXPECT_EQ(unroll_with_args<3>(), "0:1 1:3 2:5 ");
    EXPECT_EQ(unroll_with_args<4>(), "0:1 1:3 2:5 3:7 ");
    EXPECT_EQ(unroll_with_args<5>(), "0:1 1:3 2:5 3:7 4:9 ");
}

} // namespace vespalib::hwaccelerated
