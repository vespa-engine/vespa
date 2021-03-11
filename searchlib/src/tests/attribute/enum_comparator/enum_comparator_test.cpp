// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/attribute/enumstore.hpp>

#include <vespa/log/log.h>
LOG_SETUP("enum_comparator_test");

using namespace vespalib::btree;

namespace search {

using NumericEnumStore = EnumStoreT<int32_t>;
using FloatEnumStore = EnumStoreT<float>;
using StringEnumStore = EnumStoreT<const char*>;

using EnumIndex = IEnumStore::Index;

using TreeType = BTreeRoot<EnumIndex, BTreeNoLeafData,
                           vespalib::btree::NoAggregated,
                           const vespalib::datastore::EntryComparatorWrapper>;
using NodeAllocator = TreeType::NodeAllocatorType;


TEST("requireThatNumericLessIsWorking")
{
    NumericEnumStore es(false);
    EnumIndex e1 = es.insert(10);
    EnumIndex e2 = es.insert(30);
    auto cmp1 = es.make_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2));
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e1, e1));
    auto cmp2 = es.make_comparator(20);
    EXPECT_TRUE(cmp2.less(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.less(e2, EnumIndex()));
}

TEST("requireThatNumericEqualIsWorking")
{
    NumericEnumStore es(false);
    EnumIndex e1 = es.insert(10);
    EnumIndex e2 = es.insert(30);
    auto cmp1 = es.make_comparator();
    EXPECT_FALSE(cmp1.equal(e1, e2));
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e1, e1));
    auto cmp2 = es.make_comparator(20);
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e2));
    EXPECT_FALSE(cmp2.equal(e2, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
}

TEST("requireThatFloatLessIsWorking")
{
    FloatEnumStore es(false);
    EnumIndex e1 = es.insert(10.5);
    EnumIndex e2 = es.insert(30.5);
    EnumIndex e3 = es.insert(std::numeric_limits<float>::quiet_NaN());
    auto cmp1 = es.make_comparator();
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

TEST("requireThatFloatEqualIsWorking")
{
    FloatEnumStore es(false);
    EnumIndex e1 = es.insert(10.5);
    EnumIndex e2 = es.insert(30.5);
    EnumIndex e3 = es.insert(std::numeric_limits<float>::quiet_NaN());
    auto cmp1 = es.make_comparator();
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

TEST("requireThatStringLessIsWorking")
{
    StringEnumStore es(false);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    auto cmp1 = es.make_comparator();
    EXPECT_TRUE(cmp1.less(e1, e2)); // similar folded, fallback to regular
    EXPECT_FALSE(cmp1.less(e2, e1));
    EXPECT_FALSE(cmp1.less(e1, e1));
    EXPECT_TRUE(cmp1.less(e2, e3)); // folded compare
    EXPECT_TRUE(strcmp("aa", "aB") > 0); // regular
    auto cmp2 = es.make_comparator("AB");
    EXPECT_TRUE(cmp2.less(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.less(e3, EnumIndex()));
}

TEST("requireThatStringEqualIsWorking")
{
    StringEnumStore es(false);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    auto cmp1 = es.make_comparator();
    EXPECT_FALSE(cmp1.equal(e1, e2)); // similar folded, fallback to regular
    EXPECT_FALSE(cmp1.equal(e2, e1));
    EXPECT_TRUE(cmp1.equal(e1, e1));
    EXPECT_FALSE(cmp1.equal(e2, e3)); // folded compare
    auto cmp2 = es.make_comparator("AB");
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e3));
    EXPECT_FALSE(cmp2.equal(e3, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
}

TEST("requireThatComparatorWithTreeIsWorking")
{
    NumericEnumStore es(false);
    vespalib::GenerationHandler g;
    TreeType t;
    NodeAllocator m;
    for (int32_t v = 100; v > 0; --v) {
        auto cmp = es.make_comparator(v);
        EXPECT_FALSE(t.find(EnumIndex(), m, cmp).valid());
        EnumIndex idx = es.insert(v);
        t.insert(idx, BTreeNoLeafData(), m, cmp);
    }
    EXPECT_EQUAL(100u, t.size(m));
    int32_t exp = 1;
    for (TreeType::Iterator itr = t.begin(m); itr.valid(); ++itr) {
        EXPECT_EQUAL(exp++, es.get_value(itr.getKey()));
    }
    EXPECT_EQUAL(101, exp);
    t.clear(m);
    m.freeze();
    m.transferHoldLists(g.getCurrentGeneration());
    g.incGeneration();
    m.trimHoldLists(g.getFirstUsedGeneration());
}

TEST("requireThatFoldedLessIsWorking")
{
    StringEnumStore es(false);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    EnumIndex e4 = es.insert("Folded");
    auto cmp1 = es.make_folded_comparator();
    EXPECT_FALSE(cmp1.less(e1, e2)); // similar folded
    EXPECT_FALSE(cmp1.less(e2, e1)); // similar folded
    EXPECT_TRUE(cmp1.less(e2, e3)); // folded compare
    EXPECT_FALSE(cmp1.less(e3, e2)); // folded compare
    auto cmp2 = es.make_folded_comparator("fol", false);
    auto cmp3 = es.make_folded_comparator("fol", true);
    EXPECT_TRUE(cmp2.less(EnumIndex(), e4));
    EXPECT_FALSE(cmp2.less(e4, EnumIndex()));
    EXPECT_FALSE(cmp3.less(EnumIndex(), e4)); // similar when prefix
    EXPECT_FALSE(cmp3.less(e4, EnumIndex())); // similar when prefix
}

TEST("requireThatFoldedEqualIsWorking")
{
    StringEnumStore es(false);
    EnumIndex e1 = es.insert("Aa");
    EnumIndex e2 = es.insert("aa");
    EnumIndex e3 = es.insert("aB");
    EnumIndex e4 = es.insert("Folded");
    auto cmp1 = es.make_folded_comparator();
    EXPECT_TRUE(cmp1.equal(e1, e1)); // similar folded
    EXPECT_TRUE(cmp1.equal(e2, e1)); // similar folded
    EXPECT_TRUE(cmp1.equal(e2, e1));
    EXPECT_FALSE(cmp1.equal(e2, e3)); // folded compare
    EXPECT_FALSE(cmp1.equal(e3, e2)); // folded compare
    auto cmp2 = es.make_folded_comparator("fol", false);
    auto cmp3 = es.make_folded_comparator("fol", true);
    EXPECT_FALSE(cmp2.equal(EnumIndex(), e4));
    EXPECT_FALSE(cmp2.equal(e4, EnumIndex()));
    EXPECT_TRUE(cmp2.equal(EnumIndex(), EnumIndex()));
    EXPECT_FALSE(cmp3.equal(EnumIndex(), e4)); // similar when prefix
    EXPECT_FALSE(cmp3.equal(e4, EnumIndex())); // similar when prefix
    EXPECT_TRUE(cmp3.equal(EnumIndex(), EnumIndex())); // similar when prefix

}

}

TEST_MAIN() { TEST_RUN_ALL(); }
