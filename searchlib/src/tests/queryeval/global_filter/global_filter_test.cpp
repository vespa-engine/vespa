// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <gmock/gmock.h>
#include <vector>

using namespace testing;
using namespace search::queryeval;
using namespace search::engine;

using search::BitVector;
using vespalib::RequireFailedException;
using vespalib::SimpleThreadBundle;
using vespalib::ThreadBundle;

TEST(GlobalFilterTest, create_can_make_inactive_filter) {
    auto filter = GlobalFilter::create();
    EXPECT_FALSE(filter->is_active());
}

void verify(const GlobalFilter &filter, uint32_t nth = 11, uint32_t limit = 100) {
    EXPECT_TRUE(filter.is_active());
    EXPECT_EQ(filter.size(), limit);
    uint32_t my_count = 0;
    for (size_t i = 1; i < limit; ++i) {
        if ((i % nth) == 0) {
            ++my_count;
            EXPECT_TRUE(filter.check(i));
        } else {
            EXPECT_FALSE(filter.check(i));
        }
    }
    EXPECT_EQ(filter.count(), my_count);
}

TEST(GlobalFilterTest, create_can_make_test_filter) {
    std::vector<uint32_t> docs;
    for (uint32_t docid = 11; docid < 100; docid += 11) {
        docs.push_back(docid);
    }
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
    for (uint32_t docid = 11; docid < 100; docid += 11) {
        bits->setBit(docid);
    }
    bits->invalidateCachedCount();
    EXPECT_EQ(bits->countTrueBits(), 9);
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
    for (uint32_t docid = 11; docid < 100; docid += 11) {
        size_t idx = 0;
        while (docid >= bits[idx]->size()) {
            ++idx;
        }
        bits[idx]->setBit(docid);
    }
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
    for (uint32_t docid = 11; docid < 100; docid += 11) {
        size_t idx = 0;
        while (docid >= bits[idx]->size()) {
            ++idx;
        }
        bits[idx]->setBit(docid);
    }
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
    EXPECT_EQ(filter->size(), 1);
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

Blueprint::UP create_blueprint(uint32_t nth = 11, uint32_t limit = 100) {
    std::vector<SimpleResult> results;
    results.resize(5);
    for (size_t i = 1; i < limit; ++i) {
        if ((i % nth) == 0) {
            results[i % results.size()].addHit(i);
        }
    }
    auto root = std::make_unique<OrBlueprint>();
    for (size_t i = 0; i < results.size(); ++i) {
        root->addChild(std::make_unique<SimpleBlueprint>(results[i]));
    }
    root->setDocIdLimit(limit);
    return root;
}

TEST(GlobalFilterTest, global_filter_can_be_created_with_blueprint) {
    auto blueprint = create_blueprint();
    auto filter = GlobalFilter::create(*blueprint, 100, ThreadBundle::trivial());
    verify(*filter);
}

TEST(GlobalFilterTest, global_filter_can_be_created_with_blueprint_using_multiple_threads) {
    SimpleThreadBundle thread_bundle(7);
    auto blueprint = create_blueprint();
    auto filter = GlobalFilter::create(*blueprint, 100, thread_bundle);
    verify(*filter);
}

TEST(GlobalFilterTest, multi_threaded_global_filter_works_with_few_documents) {
    SimpleThreadBundle thread_bundle(7);
    for (uint32_t limit = 1; limit < 20; ++limit) {
        auto blueprint = create_blueprint(2, limit);
        auto filter = GlobalFilter::create(*blueprint, limit, thread_bundle);
        verify(*filter, 2, limit);
    }
}

TEST(GlobalFilterTest, multi_threaded_global_filter_works_with_docid_limit_0) {
    SimpleThreadBundle thread_bundle(7);
    auto blueprint = create_blueprint(2, 100);
    auto filter = GlobalFilter::create(*blueprint, 0, thread_bundle);
    verify(*filter, 2, 1);
}

TEST(GlobalFilterTest, global_filter_matching_any_document_becomes_invalid) {
    SimpleThreadBundle thread_bundle(7);
    AlwaysTrueBlueprint blueprint;
    auto filter = GlobalFilter::create(blueprint, 100, thread_bundle);
    EXPECT_FALSE(filter->is_active());
}

TEST(GlobalFilterTest, global_filter_not_matching_any_document_becomes_empty) {
    SimpleThreadBundle thread_bundle(7);
    EmptyBlueprint blueprint;
    auto filter = GlobalFilter::create(blueprint, 100, thread_bundle);
    auto class_name = vespalib::getClassName(*filter);
    fprintf(stderr, "empty global filter class name: %s\n", class_name.c_str());
    EXPECT_TRUE(class_name.find("EmptyFilter") < class_name.size());
    verify(*filter, 1000, 100);
}

TEST(GlobalFilterTest, global_filter_with_profiling_and_tracing) {
    SimpleThreadBundle thread_bundle(4);
    auto blueprint = create_blueprint();
    RelativeTime my_time(std::make_unique<SteadyClock>());
    Trace trace(my_time, 7);
    trace.match_profile_depth(64);
    auto filter = GlobalFilter::create(*blueprint, 100, thread_bundle, &trace);
    verify(*filter);
    fprintf(stderr, "trace: %s\n", trace.getSlime().toString().c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()
