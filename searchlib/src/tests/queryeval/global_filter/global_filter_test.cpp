// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/searchlib/common/bitvector.h>

#include <gmock/gmock.h>
#include <vector>

using namespace testing;

using search::BitVector;
using search::queryeval::GlobalFilter;
using vespalib::RequireFailedException;

TEST(GlobalFilterTest, create_can_make_inactive_filter) {
    auto filter = GlobalFilter::create();
    EXPECT_FALSE(filter->is_active());
}

void verify(const GlobalFilter &filter) {
    EXPECT_TRUE(filter.is_active());
    EXPECT_EQ(filter.size(), 100);
    EXPECT_EQ(filter.count(), 3);
    for (size_t i = 1; i < 100; ++i) {
        if (i == 11 || i == 22 || i == 33) {
            EXPECT_TRUE(filter.check(i));
        } else {
            EXPECT_FALSE(filter.check(i));
        }
    }
}

TEST(GlobalFilterTest, create_can_make_test_filter) {
    auto docs = std::vector<uint32_t>({11,22,33});
    auto filter = GlobalFilter::create(docs, 100);
    verify(*filter);
}

TEST(GlobalFilterTest, test_filter_requires_docs_in_order) {
    auto docs = std::vector<uint32_t>({11,33,22});
    EXPECT_THAT([&](){ GlobalFilter::create(docs, 100); }, Throws<RequireFailedException>());
}

TEST(GlobalFilterTest, test_filter_requires_docs_in_range) {
    auto docs = std::vector<uint32_t>({11,22,133});
    EXPECT_THAT([&](){ GlobalFilter::create(docs, 100); }, Throws<RequireFailedException>());
}

TEST(GlobalFilterTest, test_filter_docid_0_not_allowed) {
    auto docs = std::vector<uint32_t>({0,22,33});
    EXPECT_THAT([&](){ GlobalFilter::create(docs, 100); }, Throws<RequireFailedException>());
}

TEST(GlobalFilterTest, create_can_make_single_bitvector_filter) {
    auto bits = BitVector::create(1, 100);
    bits->setBit(11);
    bits->setBit(22);
    bits->setBit(33);
    bits->invalidateCachedCount();
    EXPECT_EQ(bits->countTrueBits(), 3);
    auto filter = GlobalFilter::create(std::move(bits));
    verify(*filter);
}

TEST(GlobalFilterTest, global_filter_pointer_guard) {
    auto inactive = GlobalFilter::create();
    auto active = GlobalFilter::create(BitVector::create(1,100));
    EXPECT_TRUE(active->is_active());
    EXPECT_FALSE(inactive->is_active());
    EXPECT_TRUE(active->ptr_if_active() == active.get());
    EXPECT_TRUE(inactive->ptr_if_active() == nullptr);
}

TEST(GlobalFilterTest, create_can_make_multi_bitvector_filter) {
    std::vector<std::unique_ptr<BitVector>> bits;
    bits.push_back(BitVector::create(1, 11));
    bits.push_back(BitVector::create(11, 23));
    bits.push_back(BitVector::create(23, 25));
    bits.push_back(BitVector::create(25, 100));
    bits[1]->setBit(11);
    bits[1]->setBit(22);
    bits[3]->setBit(33);
    for (const auto &v: bits) {
        v->invalidateCachedCount();
    }
    auto filter = GlobalFilter::create(std::move(bits));
    verify(*filter);
}

TEST(GlobalFilterTest, multi_bitvector_filter_with_empty_vectors) {
    std::vector<std::unique_ptr<BitVector>> bits;
    bits.push_back(BitVector::create(1, 11));
    bits.push_back(BitVector::create(11, 23));
    bits.push_back(BitVector::create(23, 23));
    bits.push_back(BitVector::create(23, 23));
    bits.push_back(BitVector::create(23, 25));
    bits.push_back(BitVector::create(25, 100));
    bits[1]->setBit(11);
    bits[1]->setBit(22);
    bits[5]->setBit(33);
    for (const auto &v: bits) {
        v->invalidateCachedCount();
    }
    auto filter = GlobalFilter::create(std::move(bits));
    verify(*filter);
}

TEST(GlobalFilterTest, multi_bitvector_filter_with_no_vectors) {
    std::vector<std::unique_ptr<BitVector>> bits;
    auto filter = GlobalFilter::create(std::move(bits));
    EXPECT_TRUE(filter->is_active());
    EXPECT_EQ(filter->size(), 0);
    EXPECT_EQ(filter->count(), 0);
}

TEST(GlobalFilterTest, multi_bitvector_filter_requires_no_gaps) {
    std::vector<std::unique_ptr<BitVector>> bits;
    bits.push_back(BitVector::create(1, 11));
    bits.push_back(BitVector::create(12, 100));
    EXPECT_THAT([&](){ GlobalFilter::create(std::move(bits)); }, Throws<RequireFailedException>());
}

TEST(GlobalFilterTest, multi_bitvector_filter_requires_no_overlap) {
    std::vector<std::unique_ptr<BitVector>> bits;
    bits.push_back(BitVector::create(1, 11));
    bits.push_back(BitVector::create(10, 100));
    EXPECT_THAT([&](){ GlobalFilter::create(std::move(bits)); }, Throws<RequireFailedException>());
}

TEST(GlobalFilterTest, multi_bitvector_filter_requires_correct_order) {
    std::vector<std::unique_ptr<BitVector>> bits;
    bits.push_back(BitVector::create(11, 100));
    bits.push_back(BitVector::create(1, 11));
    EXPECT_THAT([&](){ GlobalFilter::create(std::move(bits)); }, Throws<RequireFailedException>());
}

GTEST_MAIN_RUN_ALL_TESTS()
