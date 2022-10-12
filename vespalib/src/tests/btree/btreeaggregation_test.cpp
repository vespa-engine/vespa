// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/test/btree/btree_printer.h>
#include <vespa/vespalib/testkit/testapp.h>
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

int32_t
toNotVal(uint32_t key)
{
    return key + 2000;
}

}

typedef BTreeTraits<4, 4, 31, false> MyTraits;

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

typedef WrapInt MyKey;
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
typedef int MyKey;
typedef std::less<int> MyComp;
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
typedef MyTree::Builder               MyTreeBuilder;
typedef MyTree::LeafNodeType          MyLeafNode;
typedef MyTree::InternalNodeType      MyInternalNode;
typedef MyTree::NodeAllocatorType     MyNodeAllocator;
typedef MyTree::Builder::Aggregator   MyAggregator;
typedef MyTree::AggrCalcType             MyAggrCalc;
typedef std::pair<MyKey, int32_t> LeafPair;
typedef MyTreeStore::KeyDataType    MyKeyData;
typedef MyTreeStore::KeyDataTypeRefPair MyKeyDataRefPair;

typedef BTree<int, BTreeNoLeafData, btree::NoAggregated> SetTreeB;

typedef BTreeTraits<16, 16, 10, false> LSeekTraits;
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
    typedef std::map<uint32_t, int32_t> MTree;
    typedef std::map<int32_t, std::set<uint32_t> > MRTree;
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

class MyTreeForceApplyStore : public MyTreeStore
{
public:
    typedef MyComp CompareT;

    bool
    insert(EntryRef &ref, const KeyType &key, const DataType &data,
           CompareT comp = CompareT());

    bool
    remove(EntryRef &ref, const KeyType &key, CompareT comp = CompareT());
};


bool
MyTreeForceApplyStore::insert(EntryRef &ref,
                              const KeyType &key, const DataType &data,
                              CompareT comp)
{
    bool retVal = true;
    if (ref.valid()) {
        RefType iRef(ref);
        uint32_t clusterSize = getClusterSize(iRef);
        if (clusterSize == 0) {
            const BTreeType *tree = getTreeEntry(iRef);
            const NodeAllocatorType &allocator = getAllocator();
            Iterator itr = tree->find(key, allocator, comp);
            if (itr.valid())
                retVal = false;
        } else {
            const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
            const KeyDataType *olde = old + clusterSize;
            const KeyDataType *oldi = lower_bound(old, olde, key, comp);
            if (oldi < olde && !comp(key, oldi->_key))
                retVal = false; // key already present
        }
    }
    KeyDataType addition(key, data);
    if (retVal) {
        apply(ref, &addition, &addition+1, NULL, NULL, comp);
    }
    return retVal;
}


bool
MyTreeForceApplyStore::remove(EntryRef &ref, const KeyType &key,
                              CompareT comp)
{
    bool retVal = true;
    if (!ref.valid())
        retVal = false; // not found
    else {
        RefType iRef(ref);
        uint32_t clusterSize = getClusterSize(iRef);
        if (clusterSize == 0) {
            const BTreeType *tree = getTreeEntry(iRef);
            const NodeAllocatorType &allocator = getAllocator();
            Iterator itr = tree->find(key, allocator, comp);
            if (!itr.valid())
                retVal = false;
        } else {
            const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
            const KeyDataType *olde = old + clusterSize;
            const KeyDataType *oldi = lower_bound(old, olde, key, comp);
            if (oldi == olde || comp(key, oldi->_key))
                retVal = false; // not found
        }
    }
    std::vector<KeyDataType> additions;
    std::vector<KeyType> removals;
    removals.push_back(key);
    apply(ref,
          additions.data(), additions.data() + additions.size(),
          removals.data(), removals.data() + removals.size(),
          comp);
    return retVal;
}

    
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

class Test : public vespalib::TestApp {
private:
    template <typename Tree>
    bool
    assertTree(const std::string & exp, const Tree &t);

    template <typename Tree>
    bool
    assertAggregated(const MockTree &m, const Tree &t);

    template <typename TreeStore>
    bool
    assertAggregated(const MockTree &m, const TreeStore &s, EntryRef ref);

    void
    buildSubTree(const std::vector<LeafPair> &sub,
                 size_t numEntries);

    void requireThatNodeInsertWorks();
    void keys_are_aggregated_correctly_on_node_insertions();
    void requireThatNodeSplitInsertWorks();
    void keys_are_aggregated_correctly_when_node_split_on_insert();
    void requireThatTreeInsertWorks();
    void requireThatNodeStealWorks();
    void requireThatNodeRemoveWorks();
    void keys_are_aggregated_correctly_on_node_removal();
    void requireThatWeCanInsertAndRemoveFromTree();
    void requireThatSortedTreeInsertWorks();
    void requireThatCornerCaseTreeFindWorks();
    void requireThatBasicTreeIteratorWorks();
    void requireThatTreeIteratorAssignWorks();
    void requireThatUpdateOfKeyWorks();
    void requireThatUpdateOfDataWorks();
    void requireThatFrozenViewProvidesAggregatedValues();

    template <typename TreeStore>
    void
    requireThatSmallNodesWorks();
public:
    int Main() override;
};


template<typename Tree>
bool
Test::assertTree(const std::string &exp, const Tree &t)
{
    std::stringstream ss;
    test::BTreePrinter<std::stringstream, typename Tree::NodeAllocatorType> printer(ss, t.getAllocator());
    printer.print(t.getRoot());
    if (!EXPECT_EQUAL(exp, ss.str())) return false;
    return true;
}


template <typename Tree>
bool
Test::assertAggregated(const MockTree &m, const Tree &t)
{
    const MinMaxAggregated &ta(t.getAggregated());
    if (t.getRoot().valid()) {
        return
            EXPECT_FALSE(m._rtree.empty()) &&
            EXPECT_EQUAL(m._rtree.rbegin()->first,
                         ta.getMax()) &&
            EXPECT_EQUAL(m._rtree.begin()->first,
                         ta.getMin());
    } else {
        return EXPECT_TRUE(m._rtree.empty()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::min(),
                         ta.getMax()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::max(),
                         ta.getMin());
    }
}

template <typename TreeStore>
bool
Test::assertAggregated(const MockTree &m, const TreeStore &s, EntryRef ref)
{
    typename TreeStore::Iterator i(s.begin(ref));
    MinMaxAggregated sa(s.getAggregated(ref));
    const MinMaxAggregated &ia(i.getAggregated());
    if (ref.valid()) {
        return
            EXPECT_FALSE(m._rtree.empty()) &&
            EXPECT_EQUAL(m._rtree.rbegin()->first,
                         ia.getMax()) &&
            EXPECT_EQUAL(m._rtree.begin()->first,
                         ia.getMin()) &&
            EXPECT_EQUAL(m._rtree.rbegin()->first,
                         sa.getMax()) &&
            EXPECT_EQUAL(m._rtree.begin()->first,
                         sa.getMin());
    } else {
        return EXPECT_TRUE(m._rtree.empty()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::min(),
                         ia.getMax()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::max(),
                         ia.getMin()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::min(),
                         sa.getMax()) &&
            EXPECT_EQUAL(std::numeric_limits<int32_t>::max(),
                         sa.getMin());
    }
}


void
Test::requireThatNodeInsertWorks()
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

void Test::keys_are_aggregated_correctly_on_node_insertions() {
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

void
Test::requireThatNodeSplitInsertWorks()
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

void Test::keys_are_aggregated_correctly_when_node_split_on_insert() {
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

void
Test::requireThatTreeInsertWorks()
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

void
Test::requireThatNodeStealWorks()
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

void
Test::requireThatNodeRemoveWorks()
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

void Test::keys_are_aggregated_correctly_on_node_removal() {
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
Test::buildSubTree(const std::vector<LeafPair> &sub,
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
    
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
    EXPECT_EQUAL(numEntries, tree.size());
    EXPECT_TRUE(tree.isValid());
    MyTree::Iterator itr = tree.begin();
    MyTree::Iterator ritr = itr;
    if (numEntries > 0) {
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQUAL(0u, ritr.position());
        --ritr;
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQUAL(numEntries, ritr.position());
        --ritr;
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQUAL(numEntries - 1, ritr.position());
    } else {
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQUAL(0u, ritr.position());
        --ritr;
        EXPECT_TRUE(!ritr.valid());
        EXPECT_EQUAL(0u, ritr.position());
    }
    for (size_t i = 0; i < numEntries; ++i) {
        EXPECT_TRUE(itr.valid());
        EXPECT_EQUAL(sorted[i].first, itr.getKey());
        EXPECT_EQUAL(sorted[i].second, itr.getData());
        ++itr;
    }
    EXPECT_TRUE(!itr.valid());
    ritr = itr;
    EXPECT_TRUE(!ritr.valid());
    --ritr;
    for (size_t i = 0; i < numEntries; ++i) {
        EXPECT_TRUE(ritr.valid());
        EXPECT_EQUAL(sorted[numEntries - 1 - i].first, ritr.getKey());
        EXPECT_EQUAL(sorted[numEntries - 1 - i].second, ritr.getData());
        --ritr;
    }
    EXPECT_TRUE(!ritr.valid());
}

void
Test::requireThatWeCanInsertAndRemoveFromTree()
{
    GenerationHandler g;
    MyTree tree;
    MockTree mock;
    std::vector<LeafPair> exp;
    std::vector<LeafPair> sorted;
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
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
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        for (size_t j = 0; j <= i; ++j) {
            //LOG(info, "find[%zu](%d)", j, exp[j].first._val);
            MyTree::Iterator itr = tree.find(exp[j].first);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(exp[j].first, itr.getKey());
            EXPECT_EQUAL(exp[j].second, itr.getData());
        }
        EXPECT_EQUAL(i + 1u, tree.size());
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
            EXPECT_EQUAL(0u, ritr.position());
            --ritr;
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQUAL(numEntries, ritr.position());
            --ritr;
            EXPECT_TRUE(ritr.valid());
            EXPECT_EQUAL(numEntries - 1, ritr.position());
        } else {
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQUAL(0u, ritr.position());
            --ritr;
            EXPECT_TRUE(!ritr.valid());
            EXPECT_EQUAL(0u, ritr.position());
        }
        MyTree::Iterator pitr = itr;
        for (size_t i = 0; i < numEntries; ++i) {
            ssize_t si = i;
            ssize_t sileft = numEntries - i;
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(i, itr.position());
            EXPECT_EQUAL(sileft, itre - itr);
            EXPECT_EQUAL(-sileft, itr - itre);
            EXPECT_EQUAL(sileft, itre2 - itr);
            EXPECT_EQUAL(-sileft, itr - itre2);
            EXPECT_EQUAL(si, itr - tree.begin());
            EXPECT_EQUAL(-si, tree.begin() - itr);
            EXPECT_EQUAL(i != 0, itr - pitr);
            EXPECT_EQUAL(-(i != 0), pitr - itr);
            EXPECT_EQUAL(sorted[i].first, itr.getKey());
            EXPECT_EQUAL(sorted[i].second, itr.getData());
            pitr = itr;
            ++itr;
            ritr = itr;
            --ritr;
            EXPECT_TRUE(ritr.valid());
            EXPECT_TRUE(ritr == pitr);
        }
        EXPECT_TRUE(!itr.valid());
        EXPECT_EQUAL(numEntries, itr.position());
        ssize_t sNumEntries = numEntries;
        EXPECT_EQUAL(sNumEntries, itr - tree.begin());
        EXPECT_EQUAL(-sNumEntries, tree.begin() - itr);
        EXPECT_EQUAL(1, itr - pitr);
        EXPECT_EQUAL(-1, pitr - itr);
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
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        for (size_t j = i + 1; j < numEntries; ++j) {
            MyTree::Iterator itr = tree.find(exp[j].first);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(exp[j].first, itr.getKey());
            EXPECT_EQUAL(exp[j].second, itr.getData());
        }
        EXPECT_EQUAL(numEntries - 1 - i, tree.size());
    }
}

void
Test::requireThatSortedTreeInsertWorks()
{
    {
        MyTree tree;
        MockTree mock;
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        for (int i = 0; i < 1000; ++i) {
            EXPECT_TRUE(tree.insert(i, toVal(i)));
            mock.insert(i, toVal(i));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(toVal(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
            TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        }
    }
    {
        MyTree tree;
        MockTree mock;
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        for (int i = 1000; i > 0; --i) {
            EXPECT_TRUE(tree.insert(i, toVal(i)));
            mock.insert(i, toVal(i));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(toVal(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
            TEST_DO(EXPECT_TRUE(assertAggregated(mock, tree)));
        }
    }
}

void
Test::requireThatCornerCaseTreeFindWorks()
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 1; i < 100; ++i) {
        tree.insert(i, toVal(i));
    }
    EXPECT_TRUE(!tree.find(0).valid()); // lower than lowest
    EXPECT_TRUE(!tree.find(1000).valid()); // higher than highest
}

void
Test::requireThatBasicTreeIteratorWorks()
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
    EXPECT_EQUAL(1000u, itr.size());
    for (; itr.valid(); ++itr) {
        //LOG(info, "itr(%d, %s)", itr.getKey(), itr.getData().c_str());
        EXPECT_EQUAL(UNWRAP(exp[ei].first), UNWRAP(itr.getKey()));
        EXPECT_EQUAL(exp[ei].second, itr.getData());
        ei++;
        ritr = itr;
    }
    EXPECT_EQUAL(numEntries, ei);
    for (; ritr.valid(); --ritr) {
        --ei;
        //LOG(info, "itr(%d, %s)", itr.getKey(), itr.getData().c_str());
        EXPECT_EQUAL(UNWRAP(exp[ei].first), UNWRAP(ritr.getKey()));
        EXPECT_EQUAL(exp[ei].second, ritr.getData());
    }
}



void
Test::requireThatTreeIteratorAssignWorks()
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
            EXPECT_EQUAL(expNum++, UNWRAP(itr2.getKey()));
        }
        EXPECT_EQUAL(1000, expNum);
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

void
Test::requireThatUpdateOfKeyWorks()
{
    using UpdKeyTree = BTree<int, BTreeNoLeafData, btree::NoAggregated, UpdKeyComp &>;
    using UpdKeyTreeIterator = UpdKeyTree::Iterator;
    GenerationHandler g;
    UpdKeyTree t;
    UpdKeyComp cmp1(0);
    for (int i = 0; i < 1000; i+=2) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData(), cmp1));
    }
    EXPECT_EQUAL(0u, cmp1._numErrors);
    for (int i = 0; i < 1000; i+=2) {
        UpdKeyTreeIterator itr = t.find(i, cmp1);
        itr.writeKey(i + 1);
    }
    UpdKeyComp cmp2(1);
    for (int i = 1; i < 1000; i+=2) {
        UpdKeyTreeIterator itr = t.find(i, cmp2);
        EXPECT_TRUE(itr.valid());
    }
    EXPECT_EQUAL(0u, cmp2._numErrors);
}


void
Test::requireThatUpdateOfDataWorks()
{
    // typedef MyTree::Iterator Iterator;
    GenerationHandler g;
    MyTree t;
    MockTree mock;
    MyAggrCalc ac;
    MyTree::NodeAllocatorType &manager = t.getAllocator();
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, t)));
    for (int i = 0; i < 1000; i+=2) {
        EXPECT_TRUE(t.insert(i, toVal(i)));
        mock.insert(i, toVal(i));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, t)));
    }
    freezeTree(g, manager);
    for (int i = 0; i < 1000; i+=2) {
        MyTree::Iterator itr = t.find(i);
        MyTree::Iterator itr2 = itr;
        t.thaw(itr);
        itr.updateData(toHighVal(i), ac);
        EXPECT_EQUAL(toHighVal(i), itr.getData());
        EXPECT_EQUAL(toVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toHighVal(i));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, t)));
        freezeTree(g, manager);
        itr = t.find(i);
        itr2 = itr;
        t.thaw(itr);
        itr.updateData(toLowVal(i), ac);
        EXPECT_EQUAL(toLowVal(i), itr.getData());
        EXPECT_EQUAL(toHighVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toLowVal(i));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, t)));
        freezeTree(g, manager);
        itr = t.find(i);
        itr2 = itr;
        t.thaw(itr);
        itr.updateData(toVal(i), ac);
        EXPECT_EQUAL(toVal(i), itr.getData());
        EXPECT_EQUAL(toLowVal(i), itr2.getData());
        mock.erase(i);
        mock.insert(i, toVal(i));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, t)));
        freezeTree(g, manager);
    }
}


template <typename TreeStore>
void
Test::requireThatSmallNodesWorks()
{
    GenerationHandler g;
    TreeStore s;
    MockTree mock;

    EntryRef root;
    EXPECT_EQUAL(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.insert(root, 40, toVal(40)));
    mock.insert(40, toVal(40));
    EXPECT_TRUE(!s.insert(root, 40, toNotVal(40)));
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.insert(root, 20, toVal(20)));
    mock.insert(20, toVal(20));
    EXPECT_TRUE(!s.insert(root, 20, toNotVal(20)));
    EXPECT_TRUE(!s.insert(root, 40, toNotVal(40)));
    EXPECT_EQUAL(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.insert(root, 60, toVal(60)));
    mock.insert(60, toVal(60));
    EXPECT_TRUE(!s.insert(root, 60, toNotVal(60)));
    EXPECT_TRUE(!s.insert(root, 20, toNotVal(20)));
    EXPECT_TRUE(!s.insert(root, 40, toNotVal(40)));
    EXPECT_EQUAL(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.insert(root, 50, toVal(50)));
    mock.insert(50, toVal(50));
    EXPECT_TRUE(!s.insert(root, 50, toNotVal(50)));
    EXPECT_TRUE(!s.insert(root, 60, toNotVal(60)));
    EXPECT_TRUE(!s.insert(root, 20, toNotVal(20)));
    EXPECT_TRUE(!s.insert(root, 40, toNotVal(40)));
    EXPECT_EQUAL(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));

    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.insert(root, 1000 + i, 42));
        mock.insert(1000 + i, 42);
        if (i > 0) {
            EXPECT_TRUE(!s.insert(root, 1000 + i - 1, 42));
        }
        EXPECT_EQUAL(5u + i, s.size(root));
        EXPECT_EQUAL(5u + i <= 8u,  s.isSmallArray(root));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    }
    EXPECT_TRUE(s.remove(root, 40));
    mock.erase(40);
    EXPECT_TRUE(!s.remove(root, 40));
    EXPECT_EQUAL(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.remove(root, 20));
    mock.erase(20);
    EXPECT_TRUE(!s.remove(root, 20));
    EXPECT_EQUAL(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    EXPECT_TRUE(s.remove(root, 50));
    mock.erase(50);
    EXPECT_TRUE(!s.remove(root, 50));
    EXPECT_EQUAL(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.remove(root, 1000 + i));
        mock.erase(1000 + i);
        if (i > 0) {
            EXPECT_TRUE(!s.remove(root, 1000 + i - 1));
        }
        EXPECT_EQUAL(100 - i, s.size(root));
        EXPECT_EQUAL(100 - i <= 8u,  s.isSmallArray(root));
        TEST_DO(EXPECT_TRUE(assertAggregated(mock, s, root)));
    }
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    s.clear(root);
    s.clearBuilder();
    s.freeze();
    s.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    s.reclaim_memory(g.get_oldest_used_generation());
}

void
Test::requireThatFrozenViewProvidesAggregatedValues()
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
    EXPECT_EQUAL(new_aggregated.getMin(), 101);
    EXPECT_EQUAL(new_aggregated.getMax(), 104);
    auto old_aggregated = old_view.getAggregated();
    EXPECT_EQUAL(old_aggregated.getMin(), std::numeric_limits<int32_t>::max());
    EXPECT_EQUAL(old_aggregated.getMax(), std::numeric_limits<int32_t>::min());
}

int
Test::Main()
{
    TEST_INIT("btreeaggregation_test");

    requireThatNodeInsertWorks();
    keys_are_aggregated_correctly_on_node_insertions();
    requireThatNodeSplitInsertWorks();
    keys_are_aggregated_correctly_when_node_split_on_insert();
    requireThatTreeInsertWorks();
    requireThatNodeStealWorks();
    requireThatNodeRemoveWorks();
    keys_are_aggregated_correctly_on_node_removal();
    requireThatWeCanInsertAndRemoveFromTree();
    requireThatSortedTreeInsertWorks();
    requireThatCornerCaseTreeFindWorks();
    requireThatBasicTreeIteratorWorks();
    requireThatTreeIteratorAssignWorks();
    requireThatUpdateOfKeyWorks();
    requireThatUpdateOfDataWorks();
    TEST_DO(requireThatSmallNodesWorks<MyTreeStore>());
    TEST_DO(requireThatSmallNodesWorks<MyTreeForceApplyStore>());
    TEST_DO(requireThatFrozenViewProvidesAggregatedValues());

    TEST_DONE();
}

}

TEST_APPHOOK(vespalib::btree::Test);
