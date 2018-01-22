// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/multi_value_mapping.h>
#include <vespa/searchlib/attribute/multi_value_mapping.hpp>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/stllike/hash_set.h>

#include <vespa/log/log.h>
LOG_SETUP("multivaluemapping_test");

using search::datastore::ArrayStoreConfig;

template <typename EntryT>
void
assertArray(const std::vector<EntryT> &exp, vespalib::ConstArrayRef<EntryT> values)
{
    EXPECT_EQUAL(exp, std::vector<EntryT>(values.cbegin(), values.cend()));
}

template <class MvMapping>
class MyAttribute : public search::NotImplementedAttribute
{
    using MultiValueType = typename MvMapping::MultiValueType;
    using ConstArrayRef = vespalib::ConstArrayRef<MultiValueType>;
    MvMapping &_mvMapping;
    virtual void onCommit() override { }
    virtual void onUpdateStat() override { }
    virtual void onShrinkLidSpace() override {
        uint32_t committedDocIdLimit = getCommittedDocIdLimit();
        _mvMapping.shrink(committedDocIdLimit);
        setNumDocs(committedDocIdLimit);
    }
    virtual void removeOldGenerations(generation_t firstUsed) override {
        _mvMapping.trimHoldLists(firstUsed);
    }
    virtual void onGenerationChange(generation_t generation) override {
        _mvMapping.transferHoldLists(generation - 1);
    }

public:
    MyAttribute(MvMapping &mvMapping)
        : NotImplementedAttribute("test", AttributeVector::Config()),
          _mvMapping(mvMapping)
    {}
    virtual bool addDoc(DocId &doc) override {
        _mvMapping.addDoc(doc);
        incNumDocs();
        updateUncommittedDocIdLimit(doc);
        return false;
    }
    virtual uint32_t clearDoc(uint32_t docId) override {
        assert(docId < _mvMapping.size());
        _mvMapping.set(docId, ConstArrayRef());
        return 1u;
    }
};

constexpr float ALLOC_GROW_FACTOR = 0.2;

template <typename EntryT>
class Fixture
{
protected:
    using MvMapping = search::attribute::MultiValueMapping<EntryT>;
    MvMapping _mvMapping;
    MyAttribute<MvMapping> _attr;
    using RefType = typename MvMapping::RefType;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;
    Fixture(uint32_t maxSmallArraySize)
        : _mvMapping(ArrayStoreConfig(maxSmallArraySize,
                                      ArrayStoreConfig::AllocSpec(0, RefType::offsetSize(), 8 * 1024,
                                      ALLOC_GROW_FACTOR))),
          _attr(_mvMapping)
    {
    }
    Fixture(uint32_t maxSmallArraySize, size_t minClusters, size_t maxClusters, size_t numClustersForNewBuffer)
        : _mvMapping(ArrayStoreConfig(maxSmallArraySize,
                                      ArrayStoreConfig::AllocSpec(minClusters, maxClusters, numClustersForNewBuffer,
                                      ALLOC_GROW_FACTOR))),
          _attr(_mvMapping)
    {
    }
    ~Fixture() { }

    void set(uint32_t docId, const std::vector<EntryT> &values) { _mvMapping.set(docId, values); }
    void replace(uint32_t docId, const std::vector<EntryT> &values) { _mvMapping.replace(docId, values); }
    ConstArrayRef get(uint32_t docId) { return _mvMapping.get(docId); }
    void assertGet(uint32_t docId, const std::vector<EntryT> &exp)
    {
        ConstArrayRef act = get(docId);
        EXPECT_EQUAL(exp, std::vector<EntryT>(act.cbegin(), act.cend()));
    }
    void transferHoldLists(generation_t generation) { _mvMapping.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _mvMapping.trimHoldLists(firstUsed); }
    void addDocs(uint32_t numDocs) {
        for (uint32_t i = 0; i < numDocs; ++i) {
            uint32_t doc = 0;
            _attr.addDoc(doc);
        }
        _attr.commit();
        _attr.incGeneration();
    }
    uint32_t size() const { return _mvMapping.size(); }
    void shrink(uint32_t docIdLimit) {
        _attr.setCommittedDocIdLimit(docIdLimit);
        _attr.commit();
        _attr.incGeneration();
        _attr.shrinkLidSpace();
    }
    void clearDocs(uint32_t lidLow, uint32_t lidLimit) {
        _mvMapping.clearDocs(lidLow, lidLimit, [=](uint32_t docId) { _attr.clearDoc(docId); });
    }
    size_t getTotalValueCnt() const { return _mvMapping.getTotalValueCnt(); }

    uint32_t countBuffers() {
        using RefVector = typename MvMapping::RefCopyVector;
        using RefType = typename MvMapping::RefType;
        RefVector refs = _mvMapping.getRefCopy(_mvMapping.size());
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
        _mvMapping.compactWorst(true, false);
        _attr.commit();
        _attr.incGeneration();
    }
};

class IntFixture : public Fixture<int>
{
    search::Rand48 _rnd;
    std::map<uint32_t, std::vector<int>> _refMapping;
    uint32_t _maxSmallArraySize;
public:
    IntFixture(uint32_t maxSmallArraySize)
        : Fixture<int>(maxSmallArraySize),
          _rnd(),
          _refMapping(),
          _maxSmallArraySize(maxSmallArraySize)
    {
        _rnd.srand48(32);
    }

    IntFixture(uint32_t maxSmallArraySize, size_t minClusters, size_t maxClusters, size_t numClustersForNewBuffer)
        : Fixture<int>(maxSmallArraySize, minClusters, maxClusters, numClustersForNewBuffer),
          _rnd(),
          _refMapping(),
          _maxSmallArraySize(maxSmallArraySize)
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
        _attr.addDoc(docId);
        std::vector<int> values = makeValues();
        _refMapping[docId] = values;
        set(docId, values);
        _attr.commit();
        _attr.incGeneration();
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
                TEST_DO(assertGet(docId, {}));
                ++docId;
            }
            TEST_DO(assertGet(docId, kv.second));
            ++docId;
        }
        while (docId < size()) {
            TEST_DO(assertGet(docId, {}));
            ++docId;
        }
    }

    void clearDoc(uint32_t docId) {
        set(docId, {});
        _refMapping.erase(docId);
    }
};

TEST_F("Test that set and get works", Fixture<int>(3))
{
    f.set(1, {});
    f.set(2, {4, 7});
    f.set(3, {5});
    f.set(4, {10, 14, 17, 16});
    f.set(5, {3});
    TEST_DO(f.assertGet(1, {}));
    TEST_DO(f.assertGet(2, {4, 7}));
    TEST_DO(f.assertGet(3, {5}));
    TEST_DO(f.assertGet(4, {10, 14, 17, 16}));
    TEST_DO(f.assertGet(5, {3}));
}

TEST_F("Test that old value is not overwritten while held", Fixture<int>(3, 32, 64, 0))
{
    f.set(3, {5});
    typename F1::ConstArrayRef old3 = f.get(3);
    TEST_DO(assertArray({5}, old3));
    f.set(3, {7});
    f.transferHoldLists(10);
    TEST_DO(assertArray({5}, old3));
    TEST_DO(f.assertGet(3, {7}));
    f.trimHoldLists(10);
    TEST_DO(assertArray({5}, old3));
    f.trimHoldLists(11);
    TEST_DO(assertArray({0}, old3));
}

TEST_F("Test that addDoc works", Fixture<int>(3))
{
    EXPECT_EQUAL(0u, f.size());
    f.addDocs(10);
    EXPECT_EQUAL(10u, f.size());
}

TEST_F("Test that shrink works", Fixture<int>(3))
{
    f.addDocs(10);
    EXPECT_EQUAL(10u, f.size());
    f.shrink(5);
    EXPECT_EQUAL(5u, f.size());
}

TEST_F("Test that clearDocs works", Fixture<int>(3))
{
    f.addDocs(10);
    f.set(1, {});
    f.set(2, {4, 7});
    f.set(3, {5});
    f.set(4, {10, 14, 17, 16});
    f.set(5, {3});
    f.clearDocs(3, 5);
    TEST_DO(f.assertGet(1, {}));
    TEST_DO(f.assertGet(2, {4, 7}));
    TEST_DO(f.assertGet(3, {}));
    TEST_DO(f.assertGet(4, {}));
    TEST_DO(f.assertGet(5, {3}));
}

TEST_F("Test that totalValueCnt works", Fixture<int>(3))
{
    f.addDocs(10);
    EXPECT_EQUAL(0u, f.getTotalValueCnt());
    f.set(1, {});
    EXPECT_EQUAL(0u, f.getTotalValueCnt());
    f.set(2, {4, 7});
    EXPECT_EQUAL(2u, f.getTotalValueCnt());
    f.set(3, {5});
    EXPECT_EQUAL(3u, f.getTotalValueCnt());
    f.set(4, {10, 14, 17, 16});
    EXPECT_EQUAL(7u, f.getTotalValueCnt());
    f.set(5, {3});
    EXPECT_EQUAL(8u, f.getTotalValueCnt());
    f.set(4, {10, 16});
    EXPECT_EQUAL(6u, f.getTotalValueCnt());
    f.set(2, {4});
    EXPECT_EQUAL(5u, f.getTotalValueCnt());
}

TEST_F("Test that replace works", Fixture<int>(3))
{
    f.addDocs(10);
    f.set(4, {10, 14, 17, 16});
    typename F1::ConstArrayRef old4 = f.get(4);
    TEST_DO(assertArray({10, 14, 17, 16}, old4));
    EXPECT_EQUAL(4u, f.getTotalValueCnt());
    f.replace(4, {20, 24, 27, 26});
    TEST_DO(assertArray({20, 24, 27, 26}, old4));
    EXPECT_EQUAL(4u, f.getTotalValueCnt());
}

TEST_F("Test that compaction works", IntFixture(3, 64, 512, 129))
{
    uint32_t addDocs = 10;
    uint32_t bufferCountBefore = 0;
    do {
        f.addRandomDocs(addDocs);
        addDocs *= 2;
        bufferCountBefore = f.countBuffers();
    } while (bufferCountBefore < 10);
    uint32_t docIdLimit = f.size();
    uint32_t clearLimit = docIdLimit / 2;
    LOG(info, "Have %u buffers, %u docs, clearing to %u",
        bufferCountBefore, docIdLimit, clearLimit);
    for (uint32_t docId = 0; docId < clearLimit; ++docId) {
        f.clearDoc(docId);
    }
    uint32_t bufferCountAfter = bufferCountBefore;
    for (uint32_t compactIter = 0; compactIter < 10; ++compactIter) {
        f.compactWorst();
        bufferCountAfter = f.countBuffers();
        f.checkRefMapping();
        LOG(info, "Have %u buffers after compacting", bufferCountAfter);
    }
    EXPECT_LESS(bufferCountAfter, bufferCountBefore);
}

TEST_MAIN() { TEST_RUN_ALL(); }
