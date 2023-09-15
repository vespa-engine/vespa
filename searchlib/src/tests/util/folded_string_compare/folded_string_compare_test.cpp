// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/string.h>

using search::FoldedStringCompare;

using IntVec = std::vector<int>;
using StringVec = std::vector<vespalib::string>;

class FoldedStringCompareTest : public ::testing::Test
{
protected:
    FoldedStringCompareTest();
    ~FoldedStringCompareTest() override;

    static int normalize_ret(int ret) {
        return (ret == 0) ? 0 : ((ret < 0) ? -1 : 1);
    }

    template <bool fold_lhs, bool fold_rhs>
    int
    compare_folded_helper(const vespalib::string& lhs, const vespalib::string& rhs)
    {
        int ret = FoldedStringCompare::compareFolded<fold_lhs, fold_rhs>(lhs.c_str(), rhs.c_str());
        EXPECT_EQ(-ret, (FoldedStringCompare::compareFolded<fold_rhs, fold_lhs>(rhs.c_str(), lhs.c_str())));
        return ret;
    }

    IntVec
    compare_folded(const vespalib::string& lhs, const vespalib::string& rhs)
    {
        IntVec result;
        result.emplace_back(compare_folded_helper<false, false>(lhs, rhs));
        result.emplace_back(compare_folded_helper<false, true>(lhs, rhs));
        result.emplace_back(compare_folded_helper<true, false>(lhs, rhs));
        result.emplace_back(compare_folded_helper<true, true>(lhs, rhs));
        return result;
    }

    template <bool fold_lhs, bool fold_rhs>
    int
    compare_folded_prefix_helper(const vespalib::string& lhs, const vespalib::string& rhs, size_t prefix_len)
    {
        int ret = FoldedStringCompare::compareFoldedPrefix<fold_lhs, fold_rhs>(lhs.c_str(), rhs.c_str(), prefix_len);
        EXPECT_EQ(-ret, (FoldedStringCompare::compareFoldedPrefix<fold_rhs, fold_lhs>(rhs.c_str(), lhs.c_str(), prefix_len)));
        return ret;
    }

    IntVec
    compare_folded_prefix(const vespalib::string& lhs, const vespalib::string& rhs, size_t prefix_len)
    {
        IntVec result;
        result.emplace_back(compare_folded_prefix_helper<false, false>(lhs, rhs, prefix_len));
        result.emplace_back(compare_folded_prefix_helper<false, true>(lhs, rhs, prefix_len));
        result.emplace_back(compare_folded_prefix_helper<true, false>(lhs, rhs, prefix_len));
        result.emplace_back(compare_folded_prefix_helper<true, true>(lhs, rhs, prefix_len));
        return result;
    }

    int
    compare(const vespalib::string& lhs, const vespalib::string& rhs) {
        int ret = normalize_ret(FoldedStringCompare::compare(lhs.c_str(), rhs.c_str()));
        EXPECT_EQ(-ret, normalize_ret(FoldedStringCompare::compare(rhs.c_str(), lhs.c_str())));
        return ret;
    }

    int
    compare_prefix(const vespalib::string& lhs, const vespalib::string& rhs, size_t prefix_len) {
        int ret = normalize_ret(FoldedStringCompare::comparePrefix(lhs.c_str(), rhs.c_str(), prefix_len));
        EXPECT_EQ(-ret, normalize_ret(FoldedStringCompare::comparePrefix(rhs.c_str(), lhs.c_str(), prefix_len)));
        return ret;
    }
};

FoldedStringCompareTest::FoldedStringCompareTest()
    : ::testing::Test()
{
}

FoldedStringCompareTest::~FoldedStringCompareTest() = default;

TEST_F(FoldedStringCompareTest, compare_folded)
{
    EXPECT_EQ((IntVec{0, 0, 0, 0}), compare_folded("bar", "bar"));
    EXPECT_EQ((IntVec{1, 0, 1, 0}), compare_folded("bar", "BAR"));
    EXPECT_EQ((IntVec{-1, -1, 0, 0}), compare_folded("BAR", "bar"));
    EXPECT_EQ((IntVec{0, -1, 1, 0}), compare_folded("BAR", "BAR"));
    EXPECT_EQ((IntVec{1, -1, 1, -1}), compare_folded("bar", "FOO"));
    EXPECT_EQ((IntVec{-1, -1, -1, -1}), compare_folded("BAR", "foo"));
}

TEST_F(FoldedStringCompareTest, compare_folded_prefix)
{
    EXPECT_EQ((IntVec{0, 0, 0, 0}), compare_folded_prefix("bar", "bar", 100));
    EXPECT_EQ((IntVec{1, 0, 1, 0}), compare_folded_prefix("bar", "BAR", 100));
    EXPECT_EQ((IntVec{-1, -1, 0, 0}), compare_folded_prefix("BAR", "bar", 100));
    EXPECT_EQ((IntVec{0, -1, 1, 0}), compare_folded_prefix("BAR", "BAR", 100));
    EXPECT_EQ((IntVec{1, -1, 1, -1}), compare_folded_prefix("bar", "FOO", 100));
    EXPECT_EQ((IntVec{-1, -1, -1, -1}), compare_folded_prefix("BAR", "foo", 100));
    EXPECT_EQ((IntVec{1, 0, 1, 0}), compare_folded_prefix("ba", "BAR", 2));
    EXPECT_EQ((IntVec{-1, -1, 0, 0}), compare_folded_prefix("BA", "bar", 2));
    EXPECT_EQ((IntVec{1, -1, 1, -1}), compare_folded_prefix("ba", "FOO", 2));
    EXPECT_EQ((IntVec{-1, -1, -1, -1}), compare_folded_prefix("BA", "foo", 2));
}

TEST_F(FoldedStringCompareTest, compare)
{
    EXPECT_EQ(0, compare("bar", "bar"));
    EXPECT_EQ(1, compare("bar", "BAR"));
    EXPECT_EQ(0, compare("BAR", "BAR"));
    EXPECT_EQ(1, compare("FOO", "bar"));
    EXPECT_EQ(-1, compare("BAR", "foo"));
    StringVec words{"foo", "FOO", "bar", "BAR"};
    std::sort(words.begin(), words.end(), [this](auto& lhs, auto& rhs) { return compare(lhs, rhs) < 0; });
    EXPECT_EQ((StringVec{"BAR", "bar", "FOO", "foo"}), words);
}

TEST_F(FoldedStringCompareTest, compare_prefix)
{
    EXPECT_EQ(1, compare_prefix("ba", "BAR", 2));
    EXPECT_EQ(-1, compare_prefix("BA", "bar", 2));
    EXPECT_EQ(-1, compare_prefix("ba", "FOO", 2));
    EXPECT_EQ(-1, compare_prefix("BA", "foo", 2));
    // Verify that we don't mix number of bytes versus number of code points
    EXPECT_EQ(1, compare_prefix("å", "Å", 1));
}

GTEST_MAIN_RUN_ALL_TESTS()
