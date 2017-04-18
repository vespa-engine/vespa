// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("comparator_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/btree/btreeroot.h>

#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>

namespace search {

using namespace btree;

typedef EnumStoreT<NumericEntryType<int32_t> > NumericEnumStore;
typedef EnumStoreComparatorT<NumericEntryType<int32_t> > NumericComparator;

typedef EnumStoreT<NumericEntryType<float> > FloatEnumStore;
typedef EnumStoreComparatorT<NumericEntryType<float> > FloatComparator;

typedef EnumStoreT<StringEntryType> StringEnumStore;
typedef EnumStoreComparatorT<StringEntryType> StringComparator;
typedef EnumStoreFoldedComparatorT<StringEntryType> FoldedStringComparator;

typedef EnumStoreBase::Index EnumIndex;

typedef BTreeRoot<EnumIndex, BTreeNoLeafData,
                  btree::NoAggregated,
                  const EnumStoreComparatorWrapper> TreeType;
typedef TreeType::NodeAllocatorType NodeAllocator;

class Test : public vespalib::TestApp {
private:
    void requireThatNumericComparatorIsWorking();
    void requireThatFloatComparatorIsWorking();
    void requireThatStringComparatorIsWorking();
    void requireThatComparatorWithTreeIsWorking();
    void requireThatFoldedComparatorIsWorking();

public:
    Test() {}
    int Main() override;
};

void
Test::requireThatNumericComparatorIsWorking()
{
    NumericEnumStore es(1024, false);
    EnumIndex e1, e2;
    es.addEnum(10, e1);
    es.addEnum(30, e2);
    NumericComparator cmp1(es);
    EXPECT_TRUE(cmp1(e1, e2));
    EXPECT_TRUE(!cmp1(e2, e1));
    EXPECT_TRUE(!cmp1(e1, e1));
    NumericComparator cmp2(es, 20);
    EXPECT_TRUE(cmp2(EnumIndex(), e2));
    EXPECT_TRUE(!cmp2(e2, EnumIndex()));
}

void
Test::requireThatFloatComparatorIsWorking()
{
    FloatEnumStore es(1024, false);
    EnumIndex e1, e2, e3;
    es.addEnum(10.5, e1);
    es.addEnum(30.5, e2);
    es.addEnum(std::numeric_limits<float>::quiet_NaN(), e3);
    FloatComparator cmp1(es);
    EXPECT_TRUE(cmp1(e1, e2));
    EXPECT_TRUE(!cmp1(e2, e1));
    EXPECT_TRUE(!cmp1(e1, e1));
    EXPECT_TRUE(cmp1(e3, e1));  // nan
    EXPECT_TRUE(!cmp1(e1, e3)); // nan
    EXPECT_TRUE(!cmp1(e3, e3)); // nan
    FloatComparator cmp2(es, 20.5);
    EXPECT_TRUE(cmp2(EnumIndex(), e2));
    EXPECT_TRUE(!cmp2(e2, EnumIndex()));
}

void
Test::requireThatStringComparatorIsWorking()
{
    StringEnumStore es(1024, false);
    EnumIndex e1, e2, e3;
    es.addEnum("Aa", e1);
    es.addEnum("aa", e2);
    es.addEnum("aB", e3);
    StringComparator cmp1(es);
    EXPECT_TRUE(cmp1(e1, e2)); // similar folded, fallback to regular
    EXPECT_TRUE(!cmp1(e2, e1));
    EXPECT_TRUE(!cmp1(e1, e1));
    EXPECT_TRUE(cmp1(e2, e3)); // folded compare
    EXPECT_TRUE(strcmp("aa", "aB") > 0); // regular
    StringComparator cmp2(es, "AB");
    EXPECT_TRUE(cmp2(EnumIndex(), e3));
    EXPECT_TRUE(!cmp2(e3, EnumIndex()));
}

void
Test::requireThatComparatorWithTreeIsWorking()
{
    NumericEnumStore es(2048, false);
    vespalib::GenerationHandler g;
    TreeType t;
    NodeAllocator m;
    EnumIndex ei;
    for (int32_t v = 100; v > 0; --v) {
        NumericComparator cmp(es, v);
        EXPECT_TRUE(!t.find(EnumIndex(), m, cmp).valid());
        es.addEnum(v, ei);
        t.insert(ei, BTreeNoLeafData(), m, cmp);
    }
    EXPECT_EQUAL(100u, t.size(m));
    int32_t exp = 1;
    for (TreeType::Iterator itr = t.begin(m); itr.valid(); ++itr) {
        EXPECT_EQUAL(exp++, es.getValue(itr.getKey()));
    }
    EXPECT_EQUAL(101, exp);
    t.clear(m);
    m.freeze();
    m.transferHoldLists(g.getCurrentGeneration());
    g.incGeneration();
    m.trimHoldLists(g.getFirstUsedGeneration());
}

void
Test::requireThatFoldedComparatorIsWorking()
{
    StringEnumStore es(1024, false);
    EnumIndex e1, e2, e3, e4;
    es.addEnum("Aa", e1);
    es.addEnum("aa", e2);
    es.addEnum("aB", e3);
    es.addEnum("Folded", e4);
    FoldedStringComparator cmp1(es);
    EXPECT_TRUE(!cmp1(e1, e2)); // similar folded
    EXPECT_TRUE(!cmp1(e2, e1)); // similar folded
    EXPECT_TRUE(cmp1(e2, e3)); // folded compare
    EXPECT_TRUE(!cmp1(e3, e2)); // folded compare
    FoldedStringComparator cmp2(es, "fol", false);
    FoldedStringComparator cmp3(es, "fol", true);
    EXPECT_TRUE(cmp2(EnumIndex(), e4));
    EXPECT_TRUE(!cmp2(e4, EnumIndex()));
    EXPECT_TRUE(!cmp3(EnumIndex(), e4)); // similar when prefix
    EXPECT_TRUE(!cmp3(e4, EnumIndex())); // similar when prefix
}

int
Test::Main()
{
    TEST_INIT("comparator_test");

    requireThatNumericComparatorIsWorking();
    requireThatFloatComparatorIsWorking();
    requireThatStringComparatorIsWorking();
    requireThatComparatorWithTreeIsWorking();
    requireThatFoldedComparatorIsWorking();

    TEST_DONE();
}

}

TEST_APPHOOK(search::Test);

