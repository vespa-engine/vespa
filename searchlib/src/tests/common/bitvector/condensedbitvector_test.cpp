// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/common/condensedbitvectors.h>
#include <vespa/searchlib/common/bitvectorcache.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/log/log.h>

LOG_SETUP("condensedbitvector_test");

using search::CondensedBitVector;
using search::BitVectorCache;
using search::PopulateInterface;
using vespalib::GenerationHolder;

TEST("Verify state after init")
{
    GenerationHolder genHolder;
    CondensedBitVector::UP cbv(CondensedBitVector::create(8, genHolder));
    EXPECT_EQUAL(32u, cbv->getKeyCapacity());
    EXPECT_EQUAL(8u, cbv->getCapacity());
    EXPECT_EQUAL(8u, cbv->getSize());
}


TEST("Verify set/get")
{
    GenerationHolder genHolder;
    CondensedBitVector::UP cbv(CondensedBitVector::create(8, genHolder));
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_FALSE(cbv->get(i,j));
        }
    }
    cbv->set(23,5, false);
    EXPECT_FALSE(cbv->get(23, 5));
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_FALSE(cbv->get(i,j));
        }
    }
    cbv->set(23,5, true);
    EXPECT_TRUE(cbv->get(23, 5));
    size_t sum(0);
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            sum += cbv->get(i,j) ? 1 : 0;
        }
    }
    EXPECT_EQUAL(1u, sum);
}

namespace {
using DocIds = std::vector<int32_t>;
using KeyDocIdsMap = vespalib::hash_map<uint64_t, DocIds>;
struct DocIdIterator : public PopulateInterface::Iterator {
    explicit DocIdIterator(const DocIds & docs) : _docs(docs), _index(0) {}
    int32_t getNext() override {
        return (_index < _docs.size()) ? _docs[_index++] : -1;
    }

    const DocIds & _docs;
    uint32_t       _index;
};
class Populater : public PopulateInterface {
public:
    explicit Populater(const KeyDocIdsMap & m)
        : _map(m),
          _empty()
    {}
    ~Populater() override;
    Iterator::UP lookup(uint64_t key) const override {
        return _map.contains(key)
               ? std::make_unique<DocIdIterator>(_map[key])
               : std::make_unique<DocIdIterator>(_empty);
    }
private:
    const KeyDocIdsMap & _map;
    DocIds _empty;
};

Populater::~Populater() = default;

KeyDocIdsMap
create(uint32_t numDocs, uint32_t numKeys, uint32_t seed) {
    KeyDocIdsMap m;
    std::srand(seed);
    for (uint32_t k = 0; k < numKeys; k++) {
        DocIds & docIds = m[k];
        for (uint32_t d=0, count=(std::rand()%numDocs); d < count; d++) {
            docIds.push_back(std::rand()%numDocs);
        }
    }
    return m;
}

}

TEST("Test repopulation of bitvector cache") {
    GenerationHolder genHolder;
    BitVectorCache cache(genHolder);
    constexpr uint32_t numDocs = 100;
    std::vector<uint8_t> countVector(numDocs);
    EXPECT_TRUE(cache.lookupCachedSet({{0,5}}).empty());
    cache.populate(numDocs, Populater(create(numDocs, 1, 1)));
    EXPECT_TRUE(cache.lookupCachedSet({{0,5}}).empty());
    cache.requirePopulation();
    cache.populate(numDocs, Populater(create(numDocs, 1, 1)));
    auto keySet = cache.lookupCachedSet({{0,5}, {1,10}});
    EXPECT_EQUAL(1u, keySet.size());
    EXPECT_TRUE(keySet.contains(0));
    cache.computeCountVector(keySet, countVector);

    BitVectorCache::KeyAndCountSet keys;
    for (uint64_t i=0; i < 10; i++) {
        keys.emplace_back(i, 10+i);
    }
    cache.lookupCachedSet(keys);
    for (size_t i = 2; i < keys.size(); i++) {
        cache.requirePopulation();
        cache.populate(numDocs, Populater(create(numDocs, i, i)));
        keySet = cache.lookupCachedSet(keys);
        EXPECT_EQUAL(keys.size(), keySet.size());
        cache.computeCountVector(keySet, countVector);
    }

    keySet = cache.lookupCachedSet(keys);
    cache.computeCountVector(keySet, countVector);
    cache.set(1, 7, false);
    cache.computeCountVector(keySet, countVector);
    uint8_t prev_7 = countVector[7];
    cache.set(1, 7, true);
    cache.computeCountVector(keySet, countVector);
    EXPECT_EQUAL(prev_7 + 1, countVector[7]);
    cache.set(1, 7, false);
    cache.computeCountVector(keySet, countVector);
    EXPECT_EQUAL(prev_7, countVector[7]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
