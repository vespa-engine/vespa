// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/attribute/dfa_string_comparator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

#include <vespa/searchlib/attribute/enumstore.hpp>

using namespace vespalib::btree;

using vespalib::datastore::AtomicEntryRef;
using vespalib::LowerCase;
using vespalib::Utf8ReaderForZTS;

namespace vespalib::datastore {

std::ostream & operator << (std::ostream& os, const EntryRef& ref) {
    return os << "EntryRef(" << ref.ref() << ")";
}

}

namespace {

std::vector<uint32_t> as_utf32(const char* key)
{
    std::vector<uint32_t> result;
    Utf8ReaderForZTS reader(key);
    while (reader.hasMore()) {
        uint32_t code_point = reader.getChar();
        result.push_back(code_point);
    }
    return result;
}

}

namespace search {

using NumericEnumStore = EnumStoreT<int32_t>;
using FloatEnumStore = EnumStoreT<float>;
using StringEnumStore = EnumStoreT<const char*>;

using EnumIndex = IEnumStore::Index;

using TreeType = BTreeRoot<AtomicEntryRef, BTreeNoLeafData,
                           vespalib::btree::NoAggregated,
                           const vespalib::datastore::EntryComparatorWrapper>;
using NodeAllocator = TreeType::NodeAllocatorType;

using attribute::DfaStringComparator;
using vespalib::datastore::EntryComparator;


TEST(EnumComparatorTest, require_that_numeric_less_is_working)
{
    NumericEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert(10);
    EnumIndex e2 = es.insert(30);
    const auto & cmp1 = es.get_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2));
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e1, e1));
    auto cmp2 = es.make_comparator(20);
    EXPECT_TRUE(cmp2.less(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
}

TEST(EnumComparatorTest, require_that_numeric_equal_is_working)
{
    NumericEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert(10);
    EnumIndex e2 = es.insert(30);
    const auto & cmp1 = es.get_comparator();
    EXPECT_FALSE(cmp1.equal(e1, e2));
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e1, e1));
    auto cmp2 = es.make_comparator(20);
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.equal(e2, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
}

TEST(EnumComparatorTest, require_that_float_less_is_working)
{
    FloatEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert(10.5);
    EnumIndex e2 = es.insert(30.5);
    EnumIndex e3 = es.insert(std::numeric_limits<float>::quiet_NaN());
    const auto & cmp1 = es.get_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2));
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e1, e1));
    EXPECT_TRUE(cmp1.less(e3, e1));  // nan
    EXPECT_FALSE(cmp1.less(e1, e3)); // nan
    EXPECT_FALSE(cmp1.less(e3, e3)); // nan
    auto cmp2 = es.make_comparator(20.5);
    EXPECT_TRUE(cmp2.less(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
}

TEST(EnumComparatorTest, require_that_float_equal_is_working)
{
    FloatEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert(10.5);
    EnumIndex e2 = es.insert(30.5);
    EnumIndex e3 = es.insert(std::numeric_limits<float>::quiet_NaN());
    const auto & cmp1 = es.get_comparator();
    EXPECT_FALSE(cmp1.equal(e1, e2));
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e1, e1));
    EXPECT_FALSE(cmp1.equal(e3, e1));  // nan
    EXPECT_FALSE(cmp1.equal(e1, e3)); // nan
    EXPECT_TRUE(cmp1.equal(e3, e3)); // nan
    auto cmp2 = es.make_comparator(20.5);
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.equal(e2, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
}

TEST(EnumComparatorTest, require_that_string_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    const auto & cmp1 = es.get_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2)); // similar folded, fallback to regular
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e1, e1));
    EXPECT_TRUE(cmp1.less(e2, e3)); // folded compare
    EXPECT_TRUE(strcmp("aa", "aB") > 0); // regular
    auto cmp2 = es.make_comparator("AB");
    EXPECT_TRUE(cmp2.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.less(e3, EnumIndex()));
}

TEST(EnumComparatorTest, require_that_string_equal_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    const auto & cmp1 = es.get_comparator();
    EXPECT_FALSE(cmp1.equal(e1, e2)); // similar folded, fallback to regular
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e1, e1));
    EXPECT_FALSE(cmp1.equal(e2, e3)); // folded compare
    auto cmp2 = es.make_comparator("AB");
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.equal(e3, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
}

TEST(EnumComparatorTest, require_that_comparator_with_tree_is_working)
{
    NumericEnumStore es(false, DictionaryConfig::Type::BTREE);
    vespalib::GenerationHandler g;
    TreeType t;
    NodeAllocator m;
    for (int32_t v = 100; v > 0; --v) {
        auto cmp = es.make_comparator(v);
        EXPECT_FALSE(t.find(AtomicEntryRef(), m, cmp).valid());
        EnumIndex idx = es.insert(v);
        t.insert(AtomicEntryRef(idx), BTreeNoLeafData(), m, cmp);
    }
    EXPECT_EQ(100u, t.size(m));
    int32_t exp = 1;
    for (TreeType::Iterator itr = t.begin(m); itr.valid(); ++itr) {
        EXPECT_EQ(exp++, es.get_value(itr.getKey().load_relaxed()));
    }
    EXPECT_EQ(101, exp);
    t.clear(m);
    m.freeze();
    m.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    m.reclaim_memory(g.get_oldest_used_generation());
}

using EnumIndexVector = std::vector<EnumIndex>;

void sort_enum_indexes(EnumIndexVector &vec, const EntryComparator &compare)
{
    std::stable_sort(vec.begin(), vec.end(), [&compare](auto& lhs, auto& rhs) { return compare.less(lhs, rhs); });
}

TEST(EnumComparatorTest, require_that_folded_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    EnumIndex e4 = es.insert("Folded");
    const auto & cmp1 = es.get_folded_comparator();
    EXPECT_FALSE(cmp1.less(e1, e2)); // similar folded
    EXPECT_FALSE(cmp1.less(e2, e1)); // similar folded
    EXPECT_TRUE(cmp1.less(e2, e3)); // folded compare
    EXPECT_FALSE(cmp1.less(e3, e2)); // folded compare
    auto cmp2 = es.make_folded_comparator("fol");
    auto cmp3 = es.make_folded_comparator_prefix("fol");
    EXPECT_TRUE(cmp2.less(EnumIndex(), e4));
    EXPECT_FALSE(cmp2.less(e4, EnumIndex()));
    EXPECT_FALSE(cmp3.less(EnumIndex(), e4)); // similar when prefix
    EXPECT_FALSE(cmp3.less(e4, EnumIndex())); // similar when prefix
    // Full sort, CompareStrategy::UNCASED_THEN_CASED
    EnumIndexVector vec{e4, e3, e2, e1};
    sort_enum_indexes(vec, es.get_comparator());
    EXPECT_EQ((EnumIndexVector{e1, e2, e3, e4}), vec);
    // Partial sort, CompareStrategy::UNCASED
    EnumIndexVector vec2{e4, e3, e2, e1};
    sort_enum_indexes(vec2, cmp1);
    EXPECT_EQ((EnumIndexVector{e2, e1, e3, e4}), vec2);
    // Partial sort, CompareStrategy::UNCASED
    EnumIndexVector vec3{e4, e3, e1, e2};
    sort_enum_indexes(vec3, cmp1);
    EXPECT_EQ((EnumIndexVector{e1, e2, e3, e4}), vec3);
}

TEST(EnumComparatorTest, require_that_equal_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    const auto & cmp1 = es.get_comparator();
    EXPECT_TRUE(cmp1.equal(e1, e1));
    EXPECT_FALSE(cmp1.equal(e1, e2));
    EXPECT_FALSE(cmp1.equal(e1, e3));
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e2, e2));
    EXPECT_FALSE(cmp1.equal(e2, e3));
    EXPECT_FALSE(cmp1.equal(e3, e1));
    EXPECT_FALSE(cmp1.equal(e3, e2));
    EXPECT_TRUE(cmp1.equal(e3, e3));
}

TEST(EnumComparatorTest, require_that_cased_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig(DictionaryConfig::Type::BTREE, DictionaryConfig::Match::CASED));
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    EnumIndex e4 = es.insert("Folded");
    const auto & cmp1 = es.get_folded_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2));
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e2, e3));
    EXPECT_TRUE(cmp1.less(e3, e2));
    auto cmp2 = es.make_folded_comparator("fol");
    auto cmp3 = es.make_folded_comparator_prefix("fol");
    EXPECT_FALSE(cmp2.less(EnumIndex(), e4)); // case mismatch
    EXPECT_TRUE(cmp2.less(e4, EnumIndex()));  // case mismatch
    EXPECT_FALSE(cmp3.less(EnumIndex(), e4)); // case mismatch
    EXPECT_TRUE(cmp3.less(e4, EnumIndex()));  // case mismatch
    auto cmp4 = es.make_folded_comparator("Fol");
    auto cmp5 = es.make_folded_comparator_prefix("Fol");
    EXPECT_TRUE(cmp4.less(EnumIndex(), e4));  // no match
    EXPECT_FALSE(cmp4.less(e4, EnumIndex())); // no match
    EXPECT_FALSE(cmp5.less(EnumIndex(), e4)); // prefix match
    EXPECT_FALSE(cmp5.less(e4, EnumIndex())); // prefix match
    // Full sort, CompareStrategy::CASED
    EnumIndexVector vec{e4, e3, e2, e1};
    sort_enum_indexes(vec, es.get_comparator());
    EXPECT_EQ((EnumIndexVector{e1, e4, e3, e2}), vec);
}

TEST(DfaStringComparatorTest, require_that_folded_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    auto aa_utf32 = as_utf32("aa");
    DfaStringComparator cmp1(es.get_data_store(), aa_utf32, false);
    EXPECT_FALSE(cmp1.less(EnumIndex(), e1));
    EXPECT_FALSE(cmp1.less(EnumIndex(), e2));
    EXPECT_TRUE(cmp1.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp1.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e2, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e3, EnumIndex()));
    auto Aa_utf32 = as_utf32("Aa");
    DfaStringComparator cmp2(es.get_data_store(), Aa_utf32, false);
    EXPECT_TRUE(cmp2.less(EnumIndex(), e1));
    EXPECT_TRUE(cmp2.less(EnumIndex(), e2));
    EXPECT_TRUE(cmp2.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
    EXPECT_FALSE(cmp2.less(e3, EnumIndex()));
}

TEST(DfaStringComparatorTest, require_that_cased_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig(DictionaryConfig::Type::BTREE, DictionaryConfig::Match::CASED));
    auto e1 = es.insert("Aa");
    auto e2 = es.insert("aa");
    auto e3 = es.insert("aB");
    auto uaa_utf32 = as_utf32("Aa");
    auto aa_utf32 = as_utf32("aa");
    DfaStringComparator cmp1(es.get_data_store(), uaa_utf32, true);
    DfaStringComparator cmp2(es.get_data_store(), aa_utf32, true);
    EXPECT_FALSE(cmp1.less(e1, e1));
    EXPECT_TRUE(cmp1.less(e1, e2));
    EXPECT_TRUE(cmp1.less(e1, e3));
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e2, e2));
    EXPECT_FALSE(cmp1.less(e2, e3));
    EXPECT_FALSE(cmp1.less(e3, e1));
    EXPECT_TRUE(cmp1.less(e3, e2));
    EXPECT_FALSE(cmp1.less(e3, e3));
    EXPECT_FALSE(cmp1.less(EnumIndex(), e1));
    EXPECT_TRUE(cmp1.less(EnumIndex(), e2));
    EXPECT_TRUE(cmp1.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.less(EnumIndex(), e1));
    EXPECT_FALSE(cmp2.less(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp1.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e2, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e3, EnumIndex()));
    EXPECT_TRUE(cmp2.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
    EXPECT_TRUE(cmp2.less(e3, EnumIndex()));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
