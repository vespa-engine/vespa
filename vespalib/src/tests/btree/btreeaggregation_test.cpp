// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeaggregator.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/btree/btree_printer.h>
#include <vespa/vespalib/util/rand48.h>

#include <iostream>
#include <map>
#include <set>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("btreeaggregation_test");

using vespalib::GenerationHandler;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

namespace vespalib::btree {

namespace {

int32_t
toVal(uint32_t key)
{
    return key + 1000;
}

int32_t
toHighVal(uint32_t key)
{
    return toVal(key) + 1000;
}

int32_t
toLowVal(uint32_t key)
{
    return toVal(key) - 1000000;
}

}

using MyTraits = BTreeTraits<4, 4, 31, false>;

#define KEYWRAP

#ifdef KEYWRAP

// Force use of functor to compare keys.
class WrapInt
{
public:
    int _val;
    WrapInt(int val) : _val(val) {}
    WrapInt() : _val(0) {}
    bool operator==(const WrapInt & rhs) const { return _val == rhs._val; }
};

std::ostream &
operator<<(std::ostream &s, const WrapInt &i)
{
    s << i._val;
    return s;
}

using MyKey = WrapInt;
class MyComp
{
public:
    bool
    operator()(const WrapInt &a, const WrapInt &b) const
    {
        return a._val < b._val;
    }
};

#define UNWRAP(key) (key._val)
#else
using MyKey = int;
using MyComp = std::less<int>;
#define UNWRAP(key) (key)
#endif

struct KeyMinMaxAggrCalc : MinMaxAggrCalc {
    constexpr static bool aggregate_over_values() { return false; }
    constexpr static int32_t getVal(const MyKey& key) { return key._val; }
};

typedef BTree<MyKey, int32_t,
              btree::MinMaxAggregated,
              MyComp, MyTraits,
              MinMaxAggrCalc> MyTree;
typedef BTreeStore<MyKey, int32_t,
                   btree::MinMaxAggregated,
                   MyComp,
                   BTreeDefaultTraits,
                   MinMaxAggrCalc> MyTreeStore;
using MyTreeBuilder = MyTree::Builder;
using MyLeafNode = MyTree::LeafNodeType;
using MyInternalNode = MyTree::InternalNodeType;
using MyNodeAllocator = MyTree::NodeAllocatorType;
using MyAggregator = MyTree::Builder::Aggregator;
using MyAggrCalc = MyTree::AggrCalcType;
using LeafPair = std::pair<MyKey, int32_t>;
using MyKeyData = MyTreeStore::KeyDataType;
using MyKeyDataRefPair = MyTreeStore::KeyDataTypeRefPair;

using SetTreeB = BTree<int, BTreeNoLeafData, btree::NoAggregated>;

using LSeekTraits = BTreeTraits<16, 16, 10, false>;
typedef BTree<int, BTreeNoLeafData, btree::NoAggregated,
              std::less<int>, LSeekTraits> SetTreeL;

struct LeafPairLess {
    bool operator()(const LeafPair & lhs, const LeafPair & rhs) const {
        return UNWRAP(lhs.first) < UNWRAP(rhs.first);
    }
};

using MyKeyAggrTree = BTree<MyKey, int32_t, btree::MinMaxAggregated, MyComp, MyTraits, KeyMinMaxAggrCalc>;

class MockTree
{
public:
    using MTree = std::map<uint32_t, int32_t>;
    using MRTree = std::map<int32_t, std::set<uint32_t> >;
    MTree _tree;
    MRTree _rtree;

    MockTree();
    ~MockTree();


    void
    erase(uint32_t key)
    {
        MTree::iterator it(_tree.find(key));
        if (it == _tree.end())
            return;
        int32_t oval = it->second;
        MRTree::iterator rit(_rtree.find(oval));
        assert(rit != _rtree.end());
        size_t ecount = rit->second.erase(key);
        assert(ecount == 1);
        (void) ecount;
        if (rit->second.empty()) {
            _rtree.erase(oval);
        }
        _tree.erase(key);
    }

    void
    insert(uint32_t key, int32_t val)
    {
        erase(key);
        _tree[key] = val;
        _rtree[val].insert(key);
    }
};


MockTree::MockTree()
    : _tree(),
      _rtree()
{}
MockTree::~MockTree() {}

template <typename ManagerType>
void
freezeTree(GenerationHandler &g, ManagerType &m)
{
    m.freeze();
    m.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    m.reclaim_memory(g.get_oldest_used_generation());
}

template <typename ManagerType>
void
cleanup(GenerationHandler &g, ManagerType &m)
{
    freezeTree(g, m);
}

template <typename ManagerType, typename NodeType>
void
cleanup(GenerationHandler & g,
        ManagerType & m,
        BTreeNode::Ref n1Ref, NodeType * n1,
        BTreeNode::Ref n2Ref = BTreeNode::Ref(), NodeType * n2 = NULL)
{
    assert(ManagerType::isValidRef(n1Ref));
    m.holdNode(n1Ref, n1);
    if (n2 != NULL) {
        assert(ManagerType::isValidRef(n2Ref));
        m.holdNode(n2Ref, n2);
    } else {
        assert(!ManagerType::isValidRef(n2Ref));
    }
    cleanup(g, m);
}

class BTreeAggregationTest : public ::testing::Test {
protected:
    BTreeAggregationTest();
    ~BTreeAggregationTest() override;
    template <typename Tree>
    bool
    assertTree(const std::string & exp, const Tree &t);

    template <typename Tree>
    bool
    assertAggregated(const MockTree &m, const Tree &t, const vespalib::string& label);

    template <typename TreeStore>
    bool
    assertAggregated(const MockTree &m, const TreeStore &s, EntryRef ref, const vespalib::string& label);

    void
    buildSubTree(const std::vector<LeafPair> &sub,
                 size_t numEntries);
};

BTreeAggregationTest::BTreeAggregationTest() = default;
BTreeAggregationTest::~BTreeAggregationTest() = default;

template<typename Tree>
bool
BTreeAggregationTest::assertTree(const std::string &exp, const Tree &t)
{
    std::stringstream ss;
    test::BTreePrinter<std::stringstream, typename Tree::NodeAllocatorType> printer(ss, t.getAllocator());
    printer.print(t.getRoot());
    bool failed = false;
    EXPECT_EQ(exp, ss.str()) << (failed = true, "");
    return !failed;
}


template <typename Tree>
bool
BTreeAggregationTest::assertAggregated(const MockTree &m, const Tree &t, const vespalib::string& label)
{
    SCOPED_TRACE(label);
    const MinMaxAggregated &ta(t.getAggregated());
    bool failed = false;
    if (t.getRoot().valid()) {
        EXPECT_FALSE(m._rtree.empty()) << (failed = true, "");
        if (failed) {
            return false;
        }
        EXPECT_EQ(m._rtree.rbegin()->first, ta.getMax()) << (failed = true, "");
        EXPECT_EQ(m._rtree.begin()->first, ta.getMin()) << (failed = true, "");
    } else {
        EXPECT_TRUE(m._rtree.empty()) << (failed = true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::min(), ta.getMax()) << (failed = true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::max(), ta.getMin()) << (failed = true, "");
    }
    return !failed;
}

template <typename TreeStore>
bool
BTreeAggregationTest::assertAggregated(const MockTree &m, const TreeStore &s, EntryRef ref, const vespalib::string& label)
{
    SCOPED_TRACE(label);
    typename TreeStore::Iterator i(s.begin(ref));
    MinMaxAggregated sa(s.getAggregated(ref));
    const MinMaxAggregated &ia(i.getAggregated());
    bool failed = false;
    if (ref.valid()) {
        EXPECT_FALSE(m._rtree.empty()) << (failed = true, "");
        if (failed) {
            return  false;
        }
        EXPECT_EQ(m._rtree.rbegin()->first, ia.getMax()) << (failed = true, "");
        EXPECT_EQ(m._rtree.begin()->first, ia.getMin()) << (failed = true, "");
        EXPECT_EQ(m._rtree.rbegin()->first, sa.getMax()) << (failed = true, "");
        EXPECT_EQ(m._rtree.begin()->first, sa.getMin()) << (failed = true, "");
    } else {
        EXPECT_TRUE(m._rtree.empty()) << (failed = true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::min(), ia.getMax()) << (failed = true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::max(), ia.getMin()) << (failed = true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::min(), sa.getMax()) << (failed  =true, "");
        EXPECT_EQ(std::numeric_limits<int32_t>::max(), sa.getMin()) << (failed =true, "");
    }
    return !failed;
}


TEST_F(BTreeAggregationTest, require_that_node_insert_works)
{
    MyTree t;
    t.insert(20, 102);
    EXPECT_TRUE(assertTree("{{20:102[min=102,max=102]}}", t));
    t.insert(10, 101);
    EXPECT_TRUE(assertTree("{{10:101,20:102[min=101,max=102]}}", t));
    t.insert(30, 103);
    t.insert(40, 104);
    EXPECT_TRUE(assertTree("{{10:101,20:102,30:103,40:104[min=101,max=104]}}", t));
}

TEST_F(BTreeAggregationTest, keys_are_aggregated_correctly_on_node_insertions)
{
    MyKeyAggrTree t;
    t.insert(20, 102);
    EXPECT_TRUE(assertTree("{{20:102[min=20,max=20]}}", t));
    t.insert(10, 101);
    EXPECT_TRUE(assertTree("{{10:101,20:102[min=10,max=20]}}", t));
    t.insert(30, 103);
    t.insert(40, 104);
    EXPECT_TRUE(assertTree("{{10:101,20:102,30:103,40:104[min=10,max=40]}}", t));
}

template <typename Tree>
void
populateTree(Tree &t, uint32_t count, uint32_t delta)
{
    uint32_t key = 1;
    int32_t value = 101;
    for (uint32_t i = 0; i < count; ++i) {
        t.insert(key, value);
        key += delta;
        value += delta;
    }
}

template <typename Tree>
void
populateLeafNode(Tree &t)
{
    populateTree(t, 4, 2);
}

TEST_F(BTreeAggregationTest, require_that_node_split_insert_works)
{
    { // new entry in current node
        MyTree t;
        populateLeafNode(t);
        t.insert(4, 104);
        EXPECT_TRUE(assertTree("{{4,7[min=101,max=107]}} -> "
                               "{{1:101,3:103,4:104[min=101,max=104]},"
                               "{5:105,7:107[min=105,max=107]}}", t));
    }
    { // new entry in split node
        MyTree t;
        populateLeafNode(t);
        t.insert(6, 106);
        EXPECT_TRUE(assertTree("{{5,7[min=101,max=107]}} -> "
                               "{{1:101,3:103,5:105[min=101,max=105]},"
                               "{6:106,7:107[min=106,max=107]}}", t));
    }
    { // new entry at end
        MyTree t;
        populateLeafNode(t);
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{5,8[min=101,max=108]}} -> "
                               "{{1:101,3:103,5:105[min=101,max=105]},"
                               "{7:107,8:108[min=107,max=108]}}", t));
    }
}

TEST_F(BTreeAggregationTest, keys_are_aggregated_correctly_when_node_split_on_insert)
{
    { // new entry in current node
        MyKeyAggrTree t;
        populateLeafNode(t);
        t.insert(4, 104);
        EXPECT_TRUE(assertTree("{{4,7[min=1,max=7]}} -> "
                               "{{1:101,3:103,4:104[min=1,max=4]},"
                               "{5:105,7:107[min=5,max=7]}}", t));
    }
    { // new entry in split node
        MyKeyAggrTree t;
        populateLeafNode(t);
        t.insert(6, 106);
        EXPECT_TRUE(assertTree("{{5,7[min=1,max=7]}} -> "
                               "{{1:101,3:103,5:105[min=1,max=5]},"
                               "{6:106,7:107[min=6,max=7]}}", t));
    }
    { // new entry at end
        MyKeyAggrTree t;
        populateLeafNode(t);
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{5,8[min=1,max=8]}} -> "
                               "{{1:101,3:103,5:105[min=1,max=5]},"
                               "{7:107,8:108[min=7,max=8]}}", t));
    }
}

TEST_F(BTreeAggregationTest, require_that_tree_insert_works)
{
    { // multi level node split
        MyTree t;
        populateTree(t, 16, 2);
        EXPECT_TRUE(assertTree("{{7,15,23,31[min=101,max=131]}} -> "
                               "{{1:101,3:103,5:105,7:107[min=101,max=107]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]},"
                               "{17:117,19:119,21:121,23:123[min=117,max=123]},"
                               "{25:125,27:127,29:129,31:131[min=125,max=131]}}", t));
        t.insert(33, 133);
        EXPECT_TRUE(assertTree("{{23,33[min=101,max=133]}} -> "
                               "{{7,15,23[min=101,max=123]},{29,33[min=125,max=133]}} -> "
                               "{{1:101,3:103,5:105,7:107[min=101,max=107]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]},"
                               "{17:117,19:119,21:121,23:123[min=117,max=123]},"
                               "{25:125,27:127,29:129[min=125,max=129]},"
                               "{31:131,33:133[min=131,max=133]}}", t));
    }
    { // give to left node to avoid split
        MyTree t;
        populateTree(t, 8, 2);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,7:107[min=101,max=107]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]}}", t));
        t.insert(10, 110);
        EXPECT_TRUE(assertTree("{{9,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,7:107,9:109[min=101,max=109]},"
                               "{10:110,11:111,13:113,15:115[min=110,max=115]}}", t));
    }
    { // give to left node to avoid split, and move to left node
        MyTree t;
        populateTree(t, 8, 2);
        t.remove(3);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15[min=101,max=115]}} -> "
                               "{{1:101,7:107[min=101,max=107]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]}}", t));
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{9,15[min=101,max=115]}} -> "
                               "{{1:101,7:107,8:108,9:109[min=101,max=109]},"
                               "{11:111,13:113,15:115[min=111,max=115]}}", t));
    }
    { // not give to left node to avoid split, but insert at end at left node
        MyTree t;
        populateTree(t, 8, 2);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,7:107[min=101,max=107]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]}}", t));
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{8,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,7:107,8:108[min=101,max=108]},"
                               "{9:109,11:111,13:113,15:115[min=109,max=115]}}", t));
    }
    { // give to right node to avoid split
        MyTree t;
        populateTree(t, 8, 2);
        t.remove(13);
        EXPECT_TRUE(assertTree("{{7,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,5:105,7:107[min=101,max=107]},"
                               "{9:109,11:111,15:115[min=109,max=115]}}", t));
        t.insert(4, 104);
        EXPECT_TRUE(assertTree("{{5,15[min=101,max=115]}} -> "
                               "{{1:101,3:103,4:104,5:105[min=101,max=105]},"
                               "{7:107,9:109,11:111,15:115[min=107,max=115]}}", t));
    }
    { // give to right node to avoid split and move to right node
        using MyTraits6 = BTreeTraits<6, 6, 31, false>;
        using Tree6 = BTree<MyKey, int32_t, btree::MinMaxAggregated, MyComp, MyTraits6, MinMaxAggrCalc>;

        Tree6 t;
        populateTree(t, 12, 2);
        t.remove(19);
        t.remove(21);
        t.remove(23);
        EXPECT_TRUE(assertTree("{{11,17[min=101,max=117]}} -> "
                               "{{1:101,3:103,5:105,7:107,9:109,11:111[min=101,max=111]},"
                               "{13:113,15:115,17:117[min=113,max=117]}}", t));
        t.insert(10, 110);
        EXPECT_TRUE(assertTree("{{7,17[min=101,max=117]}} -> "
                               "{{1:101,3:103,5:105,7:107[min=101,max=107]},"
                               "{9:109,10:110,11:111,13:113,15:115,17:117[min=109,max=117]}}", t));
    }
}

namespace {

struct BTreeStealTraits
{
    static constexpr size_t LEAF_SLOTS = 6;
    static constexpr size_t INTERNAL_SLOTS = 6;
    static constexpr size_t PATH_SIZE = 20;
    [[maybe_unused]] static constexpr bool BINARY_SEEK = true;
};

}

TEST_F(BTreeAggregationTest, require_that_node_steal_works)
{
    typedef BTree<MyKey, int32_t,
        btree::MinMaxAggregated,
        MyComp, BTreeStealTraits,
        MinMaxAggrCalc> MyStealTree;
    { // steal all from left
        MyStealTree t;
        t.insert(10, 110);
        t.insert(20, 120);
        t.insert(30, 130);
        t.insert(40, 140);
        t.insert(50, 150);
        t.insert(60, 160);
        t.insert(35, 135);
        t.remove(35);
        EXPECT_TRUE(assertTree("{{30,60[min=110,max=160]}} -> "
                               "{{10:110,20:120,30:130[min=110,max=130]},"
                               "{40:140,50:150,60:160[min=140,max=160]}}", t));
        t.remove(50);
        EXPECT_TRUE(assertTree("{{10:110,20:120,30:130,40:140,60:160[min=110,max=160]}}", t));
    }
    { // steal all from right
        MyStealTree t;
        t.insert(10, 110);
        t.insert(20, 120);
        t.insert(30, 130);
        t.insert(40, 140);
        t.insert(50, 150);
        t.insert(60, 160);
        t.insert(35, 135);
        t.remove(35);
        EXPECT_TRUE(assertTree("{{30,60[min=110,max=160]}} -> "
                               "{{10:110,20:120,30:130[min=110,max=130]},"
                               "{40:140,50:150,60:160[min=140,max=160]}}", t));
        t.remove(20);
        EXPECT_TRUE(assertTree("{{10:110,30:130,40:140,50:150,60:160[min=110,max=160]}}", t));
    }
    { // steal some from left
        MyStealTree t;
        t.insert(10, 110);
        t.insert(20, 120);
        t.insert(30, 130);
        t.insert(60, 160);
        t.insert(70, 170);
        t.insert(80, 180);
        t.insert(50, 150);
        t.insert(40, 140);
        EXPECT_TRUE(assertTree("{{50,80[min=110,max=180]}} -> "
                               "{{10:110,20:120,30:130,40:140,50:150[min=110,max=150]},"
                               "{60:160,70:170,80:180[min=160,max=180]}}", t));
        t.remove(60);
        EXPECT_TRUE(assertTree("{{30,80[min=110,max=180]}} -> "
                               "{{10:110,20:120,30:130[min=110,max=130]},"
                               "{40:140,50:150,70:170,80:180[min=140,max=180]}}", t));
    }
    { // steal some from right
        MyStealTree t;
        t.insert(10, 110);
        t.insert(20, 120);
        t.insert(30, 130);
        t.insert(40, 140);
        t.insert(50, 150);
        t.insert(60, 160);
        t.insert(70, 170);
        t.insert(80, 180);
        t.insert(90, 190);
        t.remove(40);
        EXPECT_TRUE(assertTree("{{30,90[min=110,max=190]}} -> "
                               "{{10:110,20:120,30:130[min=110,max=130]},"
                               "{50:150,60:160,70:170,80:180,90:190[min=150,max=190]}}", t));
        t.remove(20);
        EXPECT_TRUE(assertTree("{{60,90[min=110,max=190]}} -> "
                               "{{10:110,30:130,50:150,60:160[min=110,max=160]},"
                               "{70:170,80:180,90:190[min=170,max=190]}}", t));
    }
}

TEST_F(BTreeAggregationTest, require_that_node_remove_works)
{
    MyTree t;
    populateLeafNode(t);
    t.remove(3);
    EXPECT_TRUE(assertTree("{{1:101,5:105,7:107[min=101,max=107]}}", t));
    t.remove(1);
    EXPECT_TRUE(assertTree("{{5:105,7:107[min=105,max=107]}}", t));
    t.remove(7);
    EXPECT_TRUE(assertTree("{{5:105[min=105,max=105]}}", t));
}

TEST_F(BTreeAggregationTest, keys_are_aggregated_correctly_on_node_removal)
{
    MyKeyAggrTree t;
    populateLeafNode(t);
    t.remove(3);
    EXPECT_TRUE(assertTree("{{1:101,5:105,7:107[min=1,max=7]}}", t));
    t.remove(1);
    EXPECT_TRUE(assertTree("{{5:105,7:107[min=5,max=7]}}", t));
    t.remove(7);
    EXPECT_TRUE(assertTree("{{5:105[min=5,max=5]}}", t));
}

void
generateData(std::vector<LeafPair> & data, size_t numEntries)
{
    data.reserve(numEntries);
    vespalib::Rand48 rnd;
    rnd.srand48(10);
    for (size_t i = 0; i < numEntries; ++i) {
        int num = rnd.lrand48() % 10000000;
        uint32_t val = toVal(num);
        data.push_back(std::make_pair(num, val));
    }
}

void
BTreeAggregationTest::buildSubTree(const std::vector<LeafPair> &sub,
                   size_t numEntries)
{
    GenerationHandler g;
    MyTree tree;
    MyTreeBuilder builder(tree.getAllocator());
    MockTree mock;

    std::vector<LeafPair> sorted(sub.begin(), sub.begin() + numEntries);
    std::sort(sorted.begin(), sorted.end(), LeafPairLess());
    for (size_t i = 0; i < numEntries; ++i) {
        int num = UNWRAP(sorted[i].first);
        const uint32_t & val = sorted[i].second;
        builder.insert(num, val);
        mock.insert(num, val);
    }
    tree.assign(builder);
    assert(numEntries == tree.size());
    assert(tree.isValid());
    
    EXPECT_TRUE(assertAggregated(mock, tree, "build_sub_tree"));
    EXPECT_EQ(numEntries, tree.size());
    EXPECT_TRUE(tree.isValid());
    MyTree::Iterator itr = tree.begin();
    MyTree::Iterator ritr = itr;
    if (numEntries > 0) {
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQ(0u, ritr.position());
        --ritr;
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQ(numEntries, ritr.position());
        --ritr;
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQ(numEntries - 1, ritr.position());
    } else {
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQ(0u, ritr.position());
        --ritr;
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQ(0u, ritr.position());
    }
    for (size_t i = 0; i < numEntries; ++i) {
        EXPECT_TRUE(itr.valid());
        EXPECT_EQ(sorted[i].first, itr.getKey());
        EXPECT_EQ(sorted[i].second, itr.getData());
        ++itr;
    }
    EXPECT_TRUE(!itr.valid());
    ritr = itr;
    EXPECT_TRUE(!ritr.valid());
    --ritr;
    for (size_t i = 0; i < numEntries; ++i) {
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQ(sorted[numEntries - 1 - i].first, ritr.getKey());
        EXPECT_EQ(sorted[numEntries - 1 - i].second, ritr.getData());
        --ritr;
    }
    EXPECT_TRUE(!ritr.valid());
}

TEST_F(BTreeAggregationTest, require_that_we_can_insert_and_remove_from_tree)
{
    GenerationHandler g;
    MyTree tree;
    MockTree mock;
    std::vector<LeafPair> exp;
    std::vector<LeafPair> sorted;
    EXPECT_TRUE(assertAggregated(mock, tree, "insert_and_remove_1"));
    size_t numEntries = 1000;
    generateData(exp, numEntries);
    sorted = exp;
    std::sort(sorted.begin(), sorted.end(), LeafPairLess());
    // insert entries
    for (size_t i = 0; i < numEntries; ++i) {
        int num = UNWRAP(exp[i].first);
        const uint32_t & val = exp[i].second;
        EXPECT_TRUE(!tree.find(num).valid());
        //LOG(info, "insert[%zu](%d, %s)", i, num, str.c_str());
        EXPECT_TRUE(tree.insert(num, val));
        EXPECT_TRUE(!tree.insert(num, val));
        mock.insert(num, val);
        EXPECT_TRUE(assertAggregated(mock, tree, "insert_and_remove_2"));
        for (size_t j = 0; j <= i; ++j) {
            //LOG(info, "find[%zu](%d)", j, exp[j].first._val);
            MyTree::Iterator itr = tree.find(exp[j].first);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(exp[j].first, itr.getKey());
            EXPECT_EQ(exp[j].second, itr.getData());
        }
        EXPECT_EQ(i + 1u, tree.size());
        EXPECT_TRUE(tree.isValid());
        buildSubTree(exp, i + 1);
    }
    //std::cout << "tree: " << tree.toString() << std::endl;

    {
        MyTree::Iterator itr = tree.begin();
        MyTree::Iterator itre = itr;
        MyTree::Iterator itre2;
        MyTree::Iterator ritr = itr;
        while (itre.valid())
            ++itre;
        if (numEntries > 0) {
            EXPECT_TRUE(ritr.valid());
            EXPECT_EQ(0u, ritr.position());
            --ritr;
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQ(numEntries, ritr.position());
            --ritr;
            EXPECT_TRUE(ritr.valid());
            EXPECT_EQ(numEntries - 1, ritr.position());
        } else {
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQ(0u, ritr.position());
            --ritr;
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQ(0u, ritr.position());
        }
        MyTree::Iterator pitr = itr;
        for (size_t i = 0; i < numEntries; ++i) {
            ssize_t si = i;
            ssize_t sileft = numEntries - i;
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(i, itr.position());
            EXPECT_EQ(sileft, itre - itr);
            EXPECT_EQ(-sileft, itr - itre);
            EXPECT_EQ(sileft, itre2 - itr);
            EXPECT_EQ(-sileft, itr - itre2);
            EXPECT_EQ(si, itr - tree.begin());
            EXPECT_EQ(-si, tree.begin() - itr);
            EXPECT_EQ(i != 0, itr - pitr);
            EXPECT_EQ(-(i != 0), pitr - itr);
            EXPECT_EQ(sorted[i].first, itr.getKey());
            EXPECT_EQ(sorted[i].second, itr.getData());
            pitr = itr;
            ++itr;
            ritr = itr;
            --ritr;
            EXPECT_TRUE(ritr.valid());
            EXPECT_TRUE(ritr == pitr);
        }
        EXPECT_TRUE(!itr.valid());
        EXPECT_EQ(numEntries, itr.position());
        ssize_t sNumEntries = numEntries;
        EXPECT_EQ(sNumEntries, itr - tree.begin());
        EXPECT_EQ(-sNumEntries, tree.begin() - itr);
        EXPECT_EQ(1, itr - pitr);
        EXPECT_EQ(-1, pitr - itr);
    }
    // compact full tree by calling incremental compaction methods in a loop
    {
        // Use a compaction strategy that will compact all active buffers
        auto compaction_strategy = CompactionStrategy::make_compact_all_active_buffers_strategy();
        MyTree::NodeAllocatorType &manager = tree.getAllocator();
        auto compacting_buffers = manager.start_compact_worst(compaction_strategy);
        MyTree::Iterator itr = tree.begin();
        tree.setRoot(itr.moveFirstLeafNode(tree.getRoot()));
        while (itr.valid()) {
            // LOG(info, "Leaf moved to %d", UNWRAP(itr.getKey()));
            itr.moveNextLeafNode();
        }
        compacting_buffers->finish();
        manager.freeze();
        manager.assign_generation(g.getCurrentGeneration());
        g.incGeneration();
        manager.reclaim_memory(g.get_oldest_used_generation());
    }
    // remove entries
    for (size_t i = 0; i < numEntries; ++i) {
        int num = UNWRAP(exp[i].first);
        //LOG(info, "remove[%zu](%d)", i, num);
        //std::cout << "tree: " << tree.toString() << std::endl;
        EXPECT_TRUE(tree.remove(num));
        EXPECT_TRUE(!tree.find(num).valid());
        EXPECT_TRUE(!tree.remove(num));
        EXPECT_TRUE(tree.isValid());
        mock.erase(num);
        EXPECT_TRUE(assertAggregated(mock, tree, "insert_and_remove3"));
        for (size_t j = i + 1; j < numEntries; ++j) {
            MyTree::Iterator itr = tree.find(exp[j].first);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(exp[j].first, itr.getKey());
            EXPECT_EQ(exp[j].second, itr.getData());
        }
        EXPECT_EQ(numEntries - 1 - i, tree.size());
    }
}

TEST_F(BTreeAggregationTest, require_that_sorted_tree_insert_works)
{
    {
        MyTree tree;
        MockTree mock;
        EXPECT_TRUE(assertAggregated(mock, tree, "sorted_tree_insert1"));
        for (int i = 0; i < 1000; ++i) {
            EXPECT_TRUE(tree.insert(i, toVal(i)));
            mock.insert(i, toVal(i));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(toVal(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
            EXPECT_TRUE(assertAggregated(mock, tree, "sorted_tree_insert2"));
        }
    }
    {
        MyTree tree;
        MockTree mock;
        EXPECT_TRUE(assertAggregated(mock, tree, "sorted_tree_insert3"));
        for (int i = 1000; i > 0; --i) {
            EXPECT_TRUE(tree.insert(i, toVal(i)));
            mock.insert(i, toVal(i));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(toVal(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
            EXPECT_TRUE(assertAggregated(mock, tree, "sorted_tree_insert4"));
        }
    }
}

TEST_F(BTreeAggregationTest, require_that_corner_case_tree_find_works)
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 1; i < 100; ++i) {
        tree.insert(i, toVal(i));
    }
    EXPECT_TRUE(!tree.find(0).valid()); // lower than lowest
    EXPECT_TRUE(!tree.find(1000).valid()); // higher than highest
}

TEST_F(BTreeAggregationTest, require_that_basic_tree_iterator_works)
{
    GenerationHandler g;
    MyTree tree;
    EXPECT_TRUE(!tree.begin().valid());
    std::vector<LeafPair> exp;
    size_t numEntries = 1000;
    generateData(exp, numEntries);
    for (size_t i = 0; i < numEntries; ++i) {
        tree.insert(exp[i].first, exp[i].second);
    }
    std::sort(exp.begin(), exp.end(), LeafPairLess());
    size_t ei = 0;
    MyTree::Iterator itr = tree.begin();
    MyTree::Iterator ritr;
    EXPECT_EQ(1000u, itr.size());
    for (; itr.valid(); ++itr) {
        //LOG(info, "itr(%d, %s)", itr.getKey(), itr.getData().c_str());
        EXPECT_EQ(UNWRAP(exp[ei].first), UNWRAP(itr.getKey()));
        EXPECT_EQ(exp[ei].second, itr.getData());
        ei++;
        ritr = itr;
    }
    EXPECT_EQ(numEntries, ei);
    for (; ritr.valid(); --ritr) {
        --ei;
        //LOG(info, "itr(%d, %s)", itr.getKey(), itr.getData().c_str());
        EXPECT_EQ(UNWRAP(exp[ei].first), UNWRAP(ritr.getKey()));
        EXPECT_EQ(exp[ei].second, ritr.getData());
    }
}

TEST_F(BTreeAggregationTest, require_that_tree_iterator_assign_works)
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 0; i < 1000; ++i) {
        tree.insert(i, toVal(i));
    }
    for (int i = 0; i < 1000; ++i) {
        MyTree::Iterator itr = tree.find(i);
        MyTree::Iterator itr2 = itr;
        EXPECT_TRUE(itr == itr2);
        int expNum = i;
        for (; itr2.valid(); ++itr2) {
            EXPECT_EQ(expNum++, UNWRAP(itr2.getKey()));
        }
        EXPECT_EQ(1000, expNum);
    }
}

struct UpdKeyComp {
    int _remainder;
    mutable size_t _numErrors;
    UpdKeyComp(int remainder) : _remainder(remainder), _numErrors(0) {}
    bool operator() (const int & lhs, const int & rhs) const {
        if (lhs % 2 != _remainder) ++_numErrors;
        if (rhs % 2 != _remainder) ++_numErrors;
        return lhs < rhs;
    }
};

TEST_F(BTreeAggregationTest, require_that_update_of_key_works)
{
    using UpdKeyTree = BTree<int, BTreeNoLeafData, btree::NoAggregated, UpdKeyComp &>;
    using UpdKeyTreeIterator = UpdKeyTree::Iterator;
    GenerationHandler g;
    UpdKeyTree t;
    UpdKeyComp cmp1(0);
    for (int i = 0; i < 1000; i+=2) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData(), cmp1));
    }
    EXPECT_EQ(0u, cmp1._numErrors);
    for (int i = 0; i < 1000; i+=2) {
        UpdKeyTreeIterator itr = t.find(i, cmp1);
        itr.writeKey(i + 1);
    }
    UpdKeyComp cmp2(1);
    for (int i = 1; i < 1000; i+=2) {
        UpdKeyTreeIterator itr = t.find(i, cmp2);
        EXPECT_TRUE(itr.valid());
    }
    EXPECT_EQ(0u, cmp2._numErrors);
}


TEST_F(BTreeAggregationTest, require_that_update_of_data_works)
{
    GenerationHandler g;
    MyTree t;
    MockTree mock;
    MyAggrCalc ac;
    MyTree::NodeAllocatorType &manager = t.getAllocator();
    EXPECT_TRUE(assertAggregated(mock, t, "update_data1"));
    for (int i = 0; i < 1000; i+=2) {
        EXPECT_TRUE(t.insert(i, toVal(i)));
        mock.insert(i, toVal(i));
        EXPECT_TRUE(assertAggregated(mock, t, "udate_data2"));
    }
    freezeTree(g, manager);
    for (int i = 0; i < 1000; i+=2) {
        MyTree::Iterator itr = t.find(i);
        MyTree::Iterator itr2 = itr;
        t.thaw(itr);
        itr.updateData(toHighVal(i), ac);
        EXPECT_EQ(toHighVal(i), itr.getData());
        EXPECT_EQ(toVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toHighVal(i));
        EXPECT_TRUE(assertAggregated(mock, t, "update_data3"));
        freezeTree(g, manager);
        itr = t.find(i);
        itr2 = itr;
        t.thaw(itr);
        itr.updateData(toLowVal(i), ac);
        EXPECT_EQ(toLowVal(i), itr.getData());
        EXPECT_EQ(toHighVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toLowVal(i));
        EXPECT_TRUE(assertAggregated(mock, t, "update_data4"));
        freezeTree(g, manager);
        itr = t.find(i);
        itr2 = itr;
        t.thaw(itr);
        itr.updateData(toVal(i), ac);
        EXPECT_EQ(toVal(i), itr.getData());
        EXPECT_EQ(toLowVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toVal(i));
        EXPECT_TRUE(assertAggregated(mock, t, "update_data5"));
        freezeTree(g, manager);
    }
}

namespace {

void
insert(MyTreeStore& s, EntryRef& root, MyTreeStore::KeyDataType addition)
{
    s.apply(root, &addition, &addition + 1, nullptr, nullptr);
}

void
remove(MyTreeStore& s, EntryRef& root, MyTreeStore::KeyType removal)
{
    s.apply(root, nullptr, nullptr, &removal, &removal + 1);
}

}

TEST_F(BTreeAggregationTest, require_that_small_nodes_works)
{
    GenerationHandler g;
    MyTreeStore s;
    MockTree mock;

    EntryRef root;
    EXPECT_EQ(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small1"));
    insert(s, root, {40, toVal(40)});
    mock.insert(40, toVal(40));
    EXPECT_EQ(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small2"));
    insert(s, root, {20, toVal(20)});
    mock.insert(20, toVal(20));
    EXPECT_EQ(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small3"));
    insert(s, root, {60, toVal(60)});
    mock.insert(60, toVal(60));
    EXPECT_EQ(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small4"));
    insert(s, root, {50, toVal(50)});
    mock.insert(50, toVal(50));
    EXPECT_EQ(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small5"));

    for (uint32_t i = 0; i < 100; ++i) {
        insert(s, root, {int(1000 + i), 42});
        mock.insert(1000 + i, 42);
        EXPECT_EQ(5u + i, s.size(root));
        EXPECT_EQ(5u + i <= MyTreeStore::clusterLimit,  s.isSmallArray(root));
        EXPECT_TRUE(assertAggregated(mock, s, root, "small6"));
    }
    remove(s, root, 40);
    mock.erase(40);
    EXPECT_EQ(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small7"));
    remove(s, root, 20);
    mock.erase(20);
    EXPECT_EQ(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small8"));
    remove(s, root, 50);
    mock.erase(50);
    EXPECT_EQ(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(assertAggregated(mock, s, root, "small9"));
    for (uint32_t i = 0; i < 100; ++i) {
        remove(s, root, 1000 + i);
        mock.erase(1000 + i);
        EXPECT_EQ(100 - i, s.size(root));
        EXPECT_EQ(100 - i <= MyTreeStore::clusterLimit,  s.isSmallArray(root));
        EXPECT_TRUE(assertAggregated(mock, s, root, "small10"));
    }
    EXPECT_EQ(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    s.clear(root);
    s.clearBuilder();
    s.freeze();
    s.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    s.reclaim_memory(g.get_oldest_used_generation());
}

TEST_F(BTreeAggregationTest, require_that_frozen_view_provides_aggregated_values)
{
    MyTree t;
    t.insert(20, 102);
    t.insert(10, 101);
    t.insert(30, 103);
    t.insert(40, 104);
    auto old_view = t.getFrozenView();
    t.getAllocator().freeze();
    auto new_view = t.getFrozenView();
    auto new_aggregated = new_view.getAggregated();
    EXPECT_EQ(new_aggregated.getMin(), 101);
    EXPECT_EQ(new_aggregated.getMax(), 104);
    auto old_aggregated = old_view.getAggregated();
    EXPECT_EQ(old_aggregated.getMin(), std::numeric_limits<int32_t>::max());
    EXPECT_EQ(old_aggregated.getMax(), std::numeric_limits<int32_t>::min());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
