// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vector>
#include <cinttypes>
#include <format>

using Z = vespalib::geo::ZCurve;

bool inside(int x, int y, const Z::RangeVector &ranges) {
    int64_t z = Z::encode(x, y);
    for (auto range: ranges) {
        if (z >= range.min() && z <= range.max()) {
            return true;
        }
    }
    fprintf(stderr, "FAILED: (%d, %d) -> (%" PRId64 ") not in:\n", x, y, z);
    for (auto range: ranges) {
        fprintf(stderr, "  [%" PRId64 ", %" PRId64 "]\n", range.min(), range.max());
    }
    return false;
}

bool verify_ranges(int min_x, int min_y, int max_x, int max_y) {
    Z::RangeVector ranges = Z::find_ranges(min_x, min_y, max_x, max_y);
    for (int x = min_x; x <= max_x; ++x) {
        for (int y = min_y; y <= max_y; ++y) {
            EXPECT_TRUE(inside(x, y, ranges));
            if (!inside(x, y, ranges)) {
                return false;
            }
        }
    }
    return true;
}

TEST(ZCurveRangesTest, require_that_returned_ranges_contains_bounding_box) {
    std::vector<int> values({-13, -1, 0, 1, 13});
    for (auto min_x: values) {
        for (auto min_y: values) {
            for (auto max_x: values) {
                for (auto max_y: values) {
                    if (max_x >= min_x && max_y >= min_y) {
                        EXPECT_TRUE(verify_ranges(min_x, min_y, max_x, max_y))
                            << std::format("BOX: ({}, {}) -> ({}, {})\n",
                                           min_x, min_y, max_x, max_y);
                    }
                }
            }
        }
    }
}

TEST(ZCurveRangesTest, require_that_silly_bounding_box_does_not_explode) {
    Z::RangeVector ranges = Z::find_ranges(-105, -7000000, 105, 7000000);
    EXPECT_EQ(42u, ranges.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
