// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/i_document_weight_attribute.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <random>
#include <chrono>
#include <iostream>

using search::AttributeFactory;
using search::AttributeVector;
using search::DictionaryConfig;
using search::IntegerAttribute;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using std::chrono::steady_clock;

namespace {

Config make_config(bool hash)
{
    Config cfg(BasicType::INT64, CollectionType::WSET);
    cfg.setFastSearch(true);
    cfg.set_dictionary_config(DictionaryConfig(hash ? DictionaryConfig::Type::HASH : DictionaryConfig::Type::BTREE));
    return cfg;
}

class MyKey : public search::IDocumentWeightAttribute::LookupKey
{
    int64_t _key;
public:
    MyKey(int64_t key)
        : _key(key)
    {
    }
    vespalib::stringref asString() const override { return ""; }
    bool asInteger(int64_t &value) const override { value=_key; return true; }
    
};

static constexpr uint32_t num_test_docs = 100000000;
static constexpr int64_t value_multiplier = 10;
static constexpr uint32_t num_lookup_keys = 100000000;
static constexpr uint32_t lookup_loops = 2;

}


class DocumentWeightAttributeLookupStressTest : public ::testing::Test
{
protected:
    std::shared_ptr<AttributeVector> _btree_av;
    std::shared_ptr<AttributeVector> _hash_av;
    std::vector<int64_t> _lookup_keys;

public:
    DocumentWeightAttributeLookupStressTest();
    ~DocumentWeightAttributeLookupStressTest() override;
    void populate(AttributeVector& attr);
    void make_lookup_keys();
    std::pair<uint64_t, uint64_t> lookup_loop(AttributeVector& attr, uint32_t loops);
};

DocumentWeightAttributeLookupStressTest::DocumentWeightAttributeLookupStressTest()
    : _btree_av(AttributeFactory::createAttribute("btree", make_config(false))),
      _hash_av(AttributeFactory::createAttribute("hash", make_config(true))),
      _lookup_keys()
{
    populate(*_btree_av);
    populate(*_hash_av);
    make_lookup_keys();
}

DocumentWeightAttributeLookupStressTest::~DocumentWeightAttributeLookupStressTest() = default;

void
DocumentWeightAttributeLookupStressTest::populate(AttributeVector& attr)
{
    std::cout << "Populate " << attr.getName() << " with " << num_test_docs << " values" << std::endl;
    auto before = steady_clock::now();
    auto& iattr = dynamic_cast<IntegerAttribute&>(attr);
    attr.addReservedDoc();
    attr.addDocs(num_test_docs);
    for (uint32_t lid = 1; lid <= num_test_docs; ++lid) {
        attr.clearDoc(lid);
        iattr.append(lid, lid * value_multiplier, 42);
        if ((lid % 1000) == 0) {
            attr.commit();
        }
    }
    attr.commit();
    std::chrono::duration<double> elapsed = steady_clock::now() - before;
    std::cout << elapsed.count() << " seconds elapsed" << std::endl;
}

void
DocumentWeightAttributeLookupStressTest::make_lookup_keys()
{
    std::cout << "making lookup keys" << std::endl;
    auto before = steady_clock::now();
    std::mt19937_64 mt;
    std::uniform_int_distribution<int64_t> distrib(1, num_test_docs * value_multiplier);
    _lookup_keys.reserve(num_lookup_keys);
    for (uint32_t n = 0; n < num_lookup_keys; ++n) {
        _lookup_keys.emplace_back(distrib(mt));
    }
    std::chrono::duration<double> elapsed = steady_clock::now() - before;
    std::cout << elapsed.count() << " seconds elapsed" << std::endl;
}


std::pair<uint64_t, uint64_t>
DocumentWeightAttributeLookupStressTest::lookup_loop(AttributeVector& attr, uint32_t loops)
{
    size_t lookups = loops * _lookup_keys.size();
    std::cout << "Performing " << lookups << " " << attr.getName() << " lookups" << std::endl;
    auto before = steady_clock::now();
    auto dwa = attr.asDocumentWeightAttribute();
    uint64_t hits = 0;
    uint64_t misses = 0;
    for (uint32_t loop = 0; loop < loops; ++loop) {
        auto root = dwa->get_dictionary_snapshot();
        for (auto key : _lookup_keys) {
            MyKey my_key(key);
            auto result = dwa->lookup(my_key, root);
            if (result.posting_idx.valid()) {
                ++hits;
            } else {
                ++misses;
            }
        }
    }
    std::chrono::duration<double> elapsed = steady_clock::now() - before;
    std::cout.precision(12);
    std::cout << (lookups / elapsed.count()) << " " << attr.getName() << " lookups/s" << std::endl;
    std::cout << hits << " hits, " << misses << " misses" << std::endl;
    std::cout << elapsed.count() << " seconds elapsed" << std::endl;
    return std::make_pair(hits, misses);
}

TEST_F(DocumentWeightAttributeLookupStressTest, lookup)
{
    auto btree_result = lookup_loop(*_btree_av, lookup_loops);
    auto hash_result = lookup_loop(*_hash_av, lookup_loops);
    EXPECT_EQ(btree_result, hash_result);
}

GTEST_MAIN_RUN_ALL_TESTS()
