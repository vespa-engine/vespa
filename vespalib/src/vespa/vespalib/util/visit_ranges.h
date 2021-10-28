// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>

namespace vespalib {

struct visit_ranges_either {};
struct visit_ranges_first : visit_ranges_either {};
struct visit_ranges_second : visit_ranges_either {};
struct visit_ranges_both {};

/**
 * Visit elements from two distinct ranges in the order defined by the
 * given comparator. The comparator must define a strict-weak ordering
 * across all elements from both ranges and each range must already be
 * sorted according to the comparator before calling this
 * function. Pairs of elements from the two ranges (one from each)
 * that are equal according to the comparator will be visited by a
 * single callback. The different cases ('from the first range', 'from
 * the second range' and 'from both ranges') are indicated by using
 * tagged dispatch in the visitation callback.
 *
 * An example treating both inputs equally:
 * <pre>
 *   TEST(VisitRangeExample, set_intersection) {
 *       std::vector<int> first({1,3,7});
 *       std::vector<int> second({2,3,8});
 *       std::vector<int> result;
 *       vespalib::visit_ranges(overload{[](visit_ranges_either, int) {},
 *                                       [&result](visit_ranges_both, int x, int) { result.push_back(x); }},
 *                              first.begin(), first.end(), second.begin(), second.end());
 *       EXPECT_EQ(result, std::vector<int>({3}));
 *   }
 * </pre>
 *
 * An example treating the inputs differently:
 * <pre>
 *   TEST(VisitRangeExample, set_subtraction) {
 *       std::vector<int> first({1,3,7});
 *       std::vector<int> second({2,3,8});
 *       std::vector<int> result;
 *       vespalib::visit_ranges(overload{[&result](visit_ranges_first, int a) { result.push_back(a); },
 *                                       [](visit_ranges_second, int) {},
 *                                       [](visit_ranges_both, int, int) {}},
 *                              first.begin(), first.end(), second.begin(), second.end());
 *       EXPECT_EQ(result, std::vector<int>({1,7}));
 *   }
 * </pre>
 *
 * The intention of this function is to simplify the implementation of
 * merge-like operations.
 **/

template <typename V, typename ItA, typename ItB, typename Cmp = std::less<> >
void visit_ranges(V &&visitor, ItA pos_a, ItA end_a, ItB pos_b, ItB end_b, Cmp cmp = Cmp()) {
    while ((pos_a != end_a) && (pos_b != end_b)) {
        if (cmp(*pos_a, *pos_b)) {
            visitor(visit_ranges_first(), *pos_a);
            ++pos_a;
        } else if (cmp(*pos_b, *pos_a)) {
            visitor(visit_ranges_second(), *pos_b);
            ++pos_b;
        } else {
            visitor(visit_ranges_both(), *pos_a, *pos_b);
            ++pos_a;
            ++pos_b;
        }
    }
    while (pos_a != end_a) {
        visitor(visit_ranges_first(), *pos_a);
        ++pos_a;
    }
    while (pos_b != end_b) {
        visitor(visit_ranges_second(), *pos_b);
        ++pos_b;
    }
}

}
