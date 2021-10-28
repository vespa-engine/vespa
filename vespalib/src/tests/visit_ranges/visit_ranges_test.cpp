// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <variant>
#include <string>

using namespace vespalib;

TEST(VisitRangeExample, set_intersection) {
    std::vector<int> first({1,3,7});
    std::vector<int> second({2,3,8});
    std::vector<int> result;
    vespalib::visit_ranges(overload{[](visit_ranges_either, int) {},
                                    [&result](visit_ranges_both, int x, int) { result.push_back(x); }},
                           first.begin(), first.end(), second.begin(), second.end());
    EXPECT_EQ(result, std::vector<int>({3}));
}

TEST(VisitRangeExample, set_subtraction) {
    std::vector<int> first({1,3,7});
    std::vector<int> second({2,3,8});
    std::vector<int> result;
    vespalib::visit_ranges(overload{[&result](visit_ranges_first, int a) { result.push_back(a); },
                                    [](visit_ranges_second, int) {},
                                    [](visit_ranges_both, int, int) {}},
                           first.begin(), first.end(), second.begin(), second.end());
    EXPECT_EQ(result, std::vector<int>({1,7}));
}

TEST(VisitRangesTest, empty_ranges_can_be_visited) {
    std::vector<int> a;
    std::vector<int> b;
    std::vector<int> c;
    auto visitor = overload
                   {
                       [&c](visit_ranges_either, int) {
                           c.push_back(42);
                       },
                       [&c](visit_ranges_both, int, int) {
                           c.push_back(42);
                       }
                   };
    vespalib::visit_ranges(visitor, a.begin(), a.end(), b.begin(), b.end());
    EXPECT_EQ(c, std::vector<int>({}));
}

TEST(VisitRangesTest, simple_merge_can_be_implemented) {
    std::vector<int> a({1,3,7});
    std::vector<int> b({2,3,8});
    std::vector<int> c;
    auto visitor = overload
                   {
                       [&c](visit_ranges_either, int x) {
                           c.push_back(x);
                       },
                       [&c](visit_ranges_both, int x, int y) {
                           c.push_back(x);
                           c.push_back(y);
                       }
                   };
    vespalib::visit_ranges(visitor, a.begin(), a.end(), b.begin(), b.end());
    EXPECT_EQ(c, std::vector<int>({1,2,3,3,7,8}));
}

TEST(VisitRangesTest, simple_union_can_be_implemented) {
    std::vector<int> a({1,3,7});
    std::vector<int> b({2,3,8});
    std::vector<int> c;
    auto visitor = overload
                   {
                       [&c](visit_ranges_either, int x) {
                           c.push_back(x);
                       },
                       [&c](visit_ranges_both, int x, int) {
                           c.push_back(x);
                       }
                   };
    vespalib::visit_ranges(visitor, a.begin(), a.end(), b.begin(), b.end());
    EXPECT_EQ(c, std::vector<int>({1,2,3,7,8}));
}

TEST(VisitRangesTest, asymmetric_merge_can_be_implemented) {
    std::vector<int> a({1,3,7});
    std::vector<int> b({2,3,8});
    std::vector<int> c;
    auto visitor = overload
                   {
                       [&c](visit_ranges_first, int x) {
                           c.push_back(x);
                       },
                       [](visit_ranges_second, int) {},
                       [&c](visit_ranges_both, int x, int y) {
                           c.push_back(x * y);
                       }
                   };
    vespalib::visit_ranges(visitor, a.begin(), a.end(), b.begin(), b.end());
    EXPECT_EQ(c, std::vector<int>({1,9,7}));
}

TEST(VisitRangesTest, comparator_can_be_specified) {
    std::vector<int> a({7,3,1});
    std::vector<int> b({8,3,2});
    std::vector<int> c;
    auto visitor = overload
                   {
                       [&c](visit_ranges_either, int x) {
                           c.push_back(x);
                       },
                       [&c](visit_ranges_both, int x, int y) {
                           c.push_back(x);
                           c.push_back(y);
                       }
                   };
    vespalib::visit_ranges(visitor, a.begin(), a.end(), b.begin(), b.end(), std::greater<>());
    EXPECT_EQ(c, std::vector<int>({8,7,3,3,2,1}));
}

GTEST_MAIN_RUN_ALL_TESTS()
