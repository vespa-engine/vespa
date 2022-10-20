// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/documentmetastore/lid_allocator.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/vespalib/util/generationholder.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

using search::queryeval::Blueprint;
using search::queryeval::SimpleResult;
using vespalib::GenerationHolder;
using vespalib::Timer;
using vespalib::Trinary;

namespace proton {

using documentmetastore::LidAllocator;

class LidAllocatorTest : public ::testing::Test
{
protected:
    GenerationHolder _gen_hold;
    LidAllocator     _allocator;

    LidAllocatorTest()
        : ::testing::Test(),
          _gen_hold(),
          _allocator(100, 100, _gen_hold)
    {
    }

    ~LidAllocatorTest()
    {
        _gen_hold.reclaim_all();
    }

    uint32_t get_size() { return _allocator.getActiveLids().size(); }

    void construct_free_list() {
        _allocator.constructFreeList(_allocator.getActiveLids().size());
        _allocator.setFreeListConstructed();
    }

    void register_lids(const std::vector<uint32_t>& lids) {
        for (uint32_t lid : lids) {
            _allocator.registerLid(lid);
        }
    }

    std::vector<uint32_t> alloc_lids(uint32_t count) {
        std::vector<uint32_t> result;
        for (uint32_t i = 0; i < count; ++i) {
            result.emplace_back(_allocator.getFreeLid(get_size()));
        }
        return result;
    }

    void activate_lids(const std::vector<uint32_t>& lids, bool active) {
        for (uint32_t lid : lids) {
            _allocator.updateActiveLids(lid, active);
        }
    }
    
    void unregister_lids(const std::vector<uint32_t>& lids) {
         _allocator.unregister_lids(lids);
    }

    void hold_lids(const std::vector<uint32_t>& lids) {
        _allocator.holdLids(lids, get_size(), 0);
    }

    void reclaim_memory() {
        _allocator.reclaim_memory(1);
    }
    
    std::vector<uint32_t> get_valid_lids() {
        std::vector<uint32_t> result;
        auto size = get_size();
        for (uint32_t lid = 1; lid < size; ++lid) {
            if (_allocator.validLid(lid)) {
                result.emplace_back(lid);
            }
        }
        return result;
    }

    std::vector<uint32_t> get_active_lids() {
        std::vector<uint32_t> result;
        const auto &active_lids = _allocator.getActiveLids();
        uint32_t lid = active_lids.getNextTrueBit(1);
        while (lid < active_lids.size()) {
            if (active_lids.testBit(lid)) {
                result.emplace_back(lid);
            }
            lid = active_lids.getNextTrueBit(lid + 1);
        }
        return result;
    }

    SimpleResult get_active_lids_in_search_iterator(uint32_t docid_limit) {
        auto blueprint = _allocator.createWhiteListBlueprint();
        blueprint->setDocIdLimit(docid_limit);
        auto iterator = blueprint->createFilterSearch(true, search::queryeval::Blueprint::FilterConstraint::UPPER_BOUND);
        SimpleResult res;
        res.search(*iterator, docid_limit);
        return res;
    }

    Trinary search_iterator_matches_any(uint32_t docid_limit) {
        auto blueprint = _allocator.createWhiteListBlueprint();
        blueprint->setDocIdLimit(docid_limit);
        auto iterator = blueprint->createFilterSearch(true, search::queryeval::Blueprint::FilterConstraint::UPPER_BOUND);
        return iterator->matches_any();
    }

    void
    assert_valid_lids(const std::vector<uint32_t>& exp_lids) {
        EXPECT_EQ(exp_lids, get_valid_lids());
    }

    void
    assert_active_lids(const std::vector<uint32_t>& exp_lids) {
        EXPECT_EQ(exp_lids, get_active_lids());
    }

};

TEST_F(LidAllocatorTest, unregister_lids)
{
    register_lids({ 1, 2, 3, 4, 5, 6 });
    activate_lids({ 4, 5, 6 }, true);
    assert_valid_lids({1, 2, 3, 4, 5, 6});
    assert_active_lids({4, 5, 6});
    construct_free_list();
    unregister_lids({1, 3, 5});
    assert_valid_lids({2, 4, 6});
    assert_active_lids({4, 6});
    hold_lids({1, 3, 5});
    reclaim_memory();
    EXPECT_EQ((std::vector<uint32_t>{1, 3, 5, 7, 8}), alloc_lids(5));
}

TEST_F(LidAllocatorTest, active_lids_are_available_in_search_iterator)
{
    register_lids({ 1, 2, 3, 4 });
    activate_lids({ 1, 2, 4 }, true);
    EXPECT_EQ(Trinary::Undefined, search_iterator_matches_any(5));
    EXPECT_EQ(SimpleResult({1, 2, 4}), get_active_lids_in_search_iterator(5));
}

TEST_F(LidAllocatorTest, search_iterator_matches_all_when_all_lids_are_active)
{
    register_lids({ 1, 2, 3, 4 });
    activate_lids({ 1, 2, 3, 4 }, true);
    EXPECT_EQ(Trinary::True, search_iterator_matches_any(5));
    EXPECT_EQ(SimpleResult({1, 2, 3, 4}), get_active_lids_in_search_iterator(5));
}

class LidAllocatorPerformanceTest : public LidAllocatorTest,
                                    public testing::WithParamInterface<bool>
{
};

TEST_P(LidAllocatorPerformanceTest, unregister_lids_performance)
{
    constexpr uint32_t test_size = 1000000;
    _allocator.ensureSpace(test_size + 1, test_size + 1);
    std::vector<std::vector<uint32_t>> buckets;
    buckets.resize(1000);
    auto reserve_size = (test_size + (buckets.size() - 1)) / buckets.size(); 
for (auto& bucket : buckets) {
    bucket.reserve(reserve_size);
}
    for (uint32_t i = 0; i < test_size; ++i) {
        _allocator.registerLid(i + 1);
        buckets[i % buckets.size()].emplace_back(i + 1);
    }
    construct_free_list();
    Timer timer;
    for (auto& bucket: buckets) {
        if (GetParam()) {
            unregister_lids(bucket);
        } else {
            for (auto lid : bucket) {
                _allocator.unregisterLid(lid);
            }
        }
    }
    auto rate = test_size / vespalib::to_s(timer.elapsed());
    std::cout << "Unregister rate: " << std::fixed << rate << std::endl;
}

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(LidAllocatorParameterizedPerformanceTest, LidAllocatorPerformanceTest, testing::Values(false, true));

}

GTEST_MAIN_RUN_ALL_TESTS()
