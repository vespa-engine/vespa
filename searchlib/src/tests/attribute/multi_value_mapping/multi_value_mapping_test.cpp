// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/multi_value_mapping.h>
#include <vespa/searchlib/attribute/multi_value_mapping.hpp>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchlib/attribute/save_utils.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP("multivaluemapping_test");

using vespalib::datastore::ArrayStoreConfig;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MemoryAllocatorObserver::Stats;

template <typename ElemT>
void
assertArray(const std::vector<ElemT> &exp, std::span<const ElemT> values)
{
    EXPECT_EQ(exp, std::vector<ElemT>(values.begin(), values.end()));
}

template <class MvMapping>
class MyAttribute : public search::NotImplementedAttribute
{
    using MultiValueType = typename MvMapping::MultiValueType;
    using ConstArrayRef = std::span<const MultiValueType>;
    MvMapping &_mvMapping;
    void onCommit() override { }
    void onUpdateStat() override { }
    void onShrinkLidSpace() override {
        uint32_t committedDocIdLimit = getCommittedDocIdLimit();
        _mvMapping.shrink(committedDocIdLimit);
        setNumDocs(committedDocIdLimit);
    }
    void reclaim_memory(generation_t oldest_used_gen) override {
        _mvMapping.reclaim_memory(oldest_used_gen);
    }
    void before_inc_generation(generation_t current_gen) override {
        _mvMapping.assign_generation(current_gen);
    }

public:
    MyAttribute(MvMapping &mvMapping)
        : NotImplementedAttribute("test"),
          _mvMapping(mvMapping)
    {}
    bool addDoc(DocId &doc) override {
        _mvMapping.addDoc(doc);
        incNumDocs();
        updateUncommittedDocIdLimit(doc);
        return false;
    }
    uint32_t clearDoc(uint32_t docId) override {
        assert(docId < _mvMapping.size());
        _mvMapping.set(docId, ConstArrayRef());
        return 1u;
    }
};

constexpr float ALLOC_GROW_FACTOR = 0.2;

template <typename ElemT>
class MappingTestBase : public ::testing::Test {
protected:
    using MvMapping = search::attribute::MultiValueMapping<ElemT>;
    using AttributeType = MyAttribute<MvMapping>;
    AllocStats _stats;
    std::unique_ptr<MvMapping> _mvMapping;
    std::unique_ptr<AttributeType> _attr;
    uint32_t _maxSmallArraySize;
    using RefType = typename MvMapping::RefType;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    using ArrayRef = std::span<ElemT>;
    using ConstArrayRef = std::span<const ElemT>;
    MappingTestBase()
        : _stats(),
          _mvMapping(),
          _attr(),
          _maxSmallArraySize()
    {
    }
    void setup(uint32_t max_array_store_type_id, bool enable_free_lists = true) {
        ArrayStoreConfig config(max_array_store_type_id,
                                ArrayStoreConfig::AllocSpec(0, RefType::offsetSize(), 8_Ki, ALLOC_GROW_FACTOR));
        config.enable_free_lists(enable_free_lists);
        _mvMapping = std::make_unique<MvMapping>(config, ArrayStoreConfig::default_max_buffer_size, vespalib::GrowStrategy(), std::make_unique<MemoryAllocatorObserver>(_stats));
        _attr = std::make_unique<AttributeType>(*_mvMapping);
        _maxSmallArraySize = _mvMapping->get_mapper().get_array_size(max_array_store_type_id);
    }
    void setup(uint32_t max_array_store_type_id, size_t min_entries, size_t max_entries, size_t num_entries_for_new_buffer, bool enable_free_lists = true) {
        ArrayStoreConfig config(max_array_store_type_id,
                                ArrayStoreConfig::AllocSpec(min_entries, max_entries, num_entries_for_new_buffer, ALLOC_GROW_FACTOR));
        config.enable_free_lists(enable_free_lists);
        _mvMapping = std::make_unique<MvMapping>(config, ArrayStoreConfig::default_max_buffer_size, vespalib::GrowStrategy(), std::make_unique<MemoryAllocatorObserver>(_stats));
        _attr = std::make_unique<AttributeType>(*_mvMapping);
        _maxSmallArraySize = _mvMapping->get_mapper().get_array_size(max_array_store_type_id);
    }
    ~MappingTestBase() { }

    void set(uint32_t docId, const std::vector<ElemT> &values) { _mvMapping->set(docId, values); }
    ConstArrayRef get(uint32_t docId) { return _mvMapping->get(docId); }
    ArrayRef get_writable(uint32_t docId) { return _mvMapping->get_writable(docId); }
    void assertGet(uint32_t docId, const std::vector<ElemT> &exp) {
        ConstArrayRef act = get(docId);
        EXPECT_EQ(exp, std::vector<ElemT>(act.begin(), act.end()));
    }
    void assign_generation(generation_t current_gen) { _mvMapping->assign_generation(current_gen); }
    void reclaim_memory(generation_t oldest_used_gen) { _mvMapping->reclaim_memory(oldest_used_gen); }
    void addDocs(uint32_t numDocs) {
        for (uint32_t i = 0; i < numDocs; ++i) {
            uint32_t doc = 0;
            _attr->addDoc(doc);
        }
        _attr->commit();
        _attr->incGeneration();
    }
    uint32_t size() const { return _mvMapping->size(); }
    void shrink(uint32_t docIdLimit) {
        _attr->setCommittedDocIdLimit(docIdLimit);
        _attr->commit();
        _attr->incGeneration();
        _attr->shrinkLidSpace();
    }
    void clearDocs(uint32_t lidLow, uint32_t lidLimit) {
        _mvMapping->clearDocs(lidLow, lidLimit, [this](uint32_t docId) { _attr->clearDoc(docId); });
    }
    size_t getTotalValueCnt() const { return _mvMapping->getTotalValueCnt(); }
    const AllocStats &get_stats() const noexcept { return _stats; }

    uint32_t countBuffers() {
        auto refs = search::attribute::make_entry_ref_vector_snapshot(_mvMapping->get_ref_vector(), _mvMapping->size());
        vespalib::hash_set<uint32_t> buffers;
        for (const auto &ref : refs) {
            if (ref.valid()) {
                RefType iRef = ref;
                buffers.insert(iRef.bufferId());
            }
        }
        return buffers.size();
    }

    void compactWorst() {
        CompactionSpec compaction_spec(true, false);
        CompactionStrategy compaction_strategy;
        _mvMapping->set_compaction_spec(compaction_spec);
        _mvMapping->compact_worst(compaction_strategy);
        _attr->commit();
        _attr->incGeneration();
    }
};

using IntMappingTest = MappingTestBase<int>;

class CompactionIntMappingTest : public MappingTestBase<int>
{
    vespalib::Rand48 _rnd;
    std::map<uint32_t, std::vector<int>> _refMapping;
public:
    CompactionIntMappingTest()
        : MappingTestBase<int>(),
          _rnd(),
          _refMapping()
    {
        _rnd.srand48(32);
    }

    std::vector<int> makeValues() {
        std::vector<int> result;
        uint32_t numValues = _rnd.lrand48() % (_maxSmallArraySize + 2);
        for (uint32_t i = 0; i < numValues; ++i)
        {
            result.emplace_back(_rnd.lrand48());
        }
        return result;
    }

    void addRandomDoc() {
        uint32_t docId = 0;
        _attr->addDoc(docId);
        std::vector<int> values = makeValues();
        _refMapping[docId] = values;
        set(docId, values);
        _attr->commit();
        _attr->incGeneration();
    }

    void addRandomDocs(uint32_t count) {
        for (uint32_t i = 0; i < count; ++i) {
            addRandomDoc();
        }
    }

    void checkRefMapping() {
        uint32_t docId = 0;
        for (const auto &kv : _refMapping) {
            while (docId < kv.first) {
                assertGet(docId, {});
                ++docId;
            }
            assertGet(docId, kv.second);
            ++docId;
        }
        while (docId < size()) {
            assertGet(docId, {});
            ++docId;
        }
    }

    void clearDoc(uint32_t docId) {
        set(docId, {});
        _refMapping.erase(docId);
    }
};

TEST_F(IntMappingTest, test_that_set_and_get_works)
{
    setup(3);
    set(1, {});
    set(2, {4, 7});
    set(3, {5});
    set(4, {10, 14, 17, 16});
    set(5, {3});
    assertGet(1, {});
    assertGet(2, {4, 7});
    assertGet(3, {5});
    assertGet(4, {10, 14, 17, 16});
    assertGet(5, {3});
}

TEST_F(IntMappingTest, test_that_old_value_is_not_overwritten_while_held)
{
    setup(3, 32, 64, 0);
    set(3, {5});
    auto old3 = get(3);
    assertArray({5}, old3);
    set(3, {7});
    assign_generation(10);
    assertArray({5}, old3);
    assertGet(3, {7});
    reclaim_memory(10);
    assertArray({5}, old3);
    reclaim_memory(11);
    assertArray({0}, old3);
}

TEST_F(IntMappingTest, test_that_addDoc_works)
{
    setup(3);
    EXPECT_EQ(0u, size());
    addDocs(10);
    EXPECT_EQ(10u, size());
}

TEST_F(IntMappingTest, test_that_shrink_works)
{
    setup(3);
    addDocs(10);
    EXPECT_EQ(10u, size());
    shrink(5);
    EXPECT_EQ(5u, size());
}

TEST_F(IntMappingTest, test_that_clearDocs_works)
{
    setup(3);
    addDocs(10);
    set(1, {});
    set(2, {4, 7});
    set(3, {5});
    set(4, {10, 14, 17, 16});
    set(5, {3});
    clearDocs(3, 5);
    assertGet(1, {});
    assertGet(2, {4, 7});
    assertGet(3, {});
    assertGet(4, {});
    assertGet(5, {3});
}

TEST_F(IntMappingTest, test_that_totalValueCnt_works)
{
    setup(3);
    addDocs(10);
    EXPECT_EQ(0u, getTotalValueCnt());
    set(1, {});
    EXPECT_EQ(0u, getTotalValueCnt());
    set(2, {4, 7});
    EXPECT_EQ(2u, getTotalValueCnt());
    set(3, {5});
    EXPECT_EQ(3u, getTotalValueCnt());
    set(4, {10, 14, 17, 16});
    EXPECT_EQ(7u, getTotalValueCnt());
    set(5, {3});
    EXPECT_EQ(8u, getTotalValueCnt());
    set(4, {10, 16});
    EXPECT_EQ(6u, getTotalValueCnt());
    set(2, {4});
    EXPECT_EQ(5u, getTotalValueCnt());
}

TEST_F(IntMappingTest, test_that_get_writable_works)
{
    setup(3);
    addDocs(10);
    set(4, {10, 14, 17, 16});
    auto old4 = get(4);
    assertArray({10, 14, 17, 16}, old4);
    EXPECT_EQ(4u, getTotalValueCnt());
    {
        auto array = get_writable(4);
        for (auto& elem : array) {
            elem += 10;
        }
    }
    assertArray({20, 24, 27, 26}, old4);
    EXPECT_EQ(4u, getTotalValueCnt());
}

TEST_F(IntMappingTest, test_that_free_lists_can_be_enabled)
{
    setup(3, true);
    EXPECT_TRUE(_mvMapping->has_free_lists_enabled());
}

TEST_F(IntMappingTest, test_that_free_lists_can_be_disabled)
{
    setup(3, false);
    EXPECT_FALSE(_mvMapping->has_free_lists_enabled());
}

TEST_F(IntMappingTest, provided_memory_allocator_is_used)
{
    setup(3, 64, 512, 129, true);
    EXPECT_EQ(AllocStats(5, 0), get_stats());
}

TEST_F(CompactionIntMappingTest, test_that_compaction_works)
{
    setup(3, 64, 512, 129);
    uint32_t addDocs = 10;
    uint32_t bufferCountBefore = 0;
    do {
        addRandomDocs(addDocs);
        addDocs *= 2;
        bufferCountBefore = countBuffers();
    } while (bufferCountBefore < 10);
    uint32_t docIdLimit = size();
    uint32_t clearLimit = docIdLimit / 2;
    LOG(info, "Have %u buffers, %u docs, clearing to %u",
        bufferCountBefore, docIdLimit, clearLimit);
    for (uint32_t docId = 0; docId < clearLimit; ++docId) {
        clearDoc(docId);
    }
    uint32_t bufferCountAfter = bufferCountBefore;
    for (uint32_t compactIter = 0; compactIter < 10; ++compactIter) {
        compactWorst();
        bufferCountAfter = countBuffers();
        checkRefMapping();
        LOG(info, "Have %u buffers after compacting", bufferCountAfter);
    }
    EXPECT_LT(bufferCountAfter, bufferCountBefore);
}

GTEST_MAIN_RUN_ALL_TESTS()
