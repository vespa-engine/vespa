// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/attribute/dfa_string_comparator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/searchlib/attribute/enumstore.hpp>

using namespace vespalib::btree;

using vespalib::datastore::AtomicEntryRef;

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
}

TEST(EnumComparatorTest, require_that_folded_equal_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    EnumIndex e4 = es.insert("Folded");
    const auto & cmp1 = es.get_folded_comparator();
    EXPECT_TRUE(cmp1.equal(e1, e1)); // similar folded
    EXPECT_TRUE(cmp1.equal(e2, e1)); // similar folded
    EXPECT_TRUE(cmp1.equal(e2, e1));
    EXPECT_FALSE(cmp1.equal(e2, e3)); // folded compare
    EXPECT_FALSE(cmp1.equal(e3, e2)); // folded compare
    auto cmp2 = es.make_folded_comparator("fol");
    auto cmp3 = es.make_folded_comparator_prefix("fol");
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e4));
    EXPECT_FALSE(cmp2.equal(e4, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
    EXPECT_FALSE(cmp3.equal(EnumIndex(), e4)); // similar when prefix
    EXPECT_FALSE(cmp3.equal(e4, EnumIndex())); // similar when prefix
    EXPECT_TRUE(cmp3.equal(EnumIndex(), EnumIndex())); // similar when prefix
}

TEST(DfaStringComparatorTest, require_that_less_is_working)
{
    StringEnumStore es(false, DictionaryConfig::Type::BTREE);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    DfaStringComparator cmp1(es.get_data_store(), "aa");
    EXPECT_FALSE(cmp1.less(EnumIndex(), e1));
    EXPECT_FALSE(cmp1.less(EnumIndex(), e2));
    EXPECT_TRUE(cmp1.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp1.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e2, EnumIndex()));
    EXPECT_FALSE(cmp1.less(e3, EnumIndex()));
    DfaStringComparator cmp2(es.get_data_store(), "Aa");
    EXPECT_TRUE(cmp2.less(EnumIndex(), e1));
    EXPECT_TRUE(cmp2.less(EnumIndex(), e2));
    EXPECT_TRUE(cmp2.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.less(e1, EnumIndex()));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
    EXPECT_FALSE(cmp2.less(e3, EnumIndex()));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
