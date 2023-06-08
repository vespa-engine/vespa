// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <string>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/util/rand48.h>

#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/test/btree/btree_printer.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("btree_test");

using vespalib::GenerationHandler;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

namespace vespalib::btree {

namespace {

template <typename T>
std::string
toStr(const T & v)
{
    std::stringstream ss;
    ss << v;
    return ss.str();
}

class SequenceValidator
{
    int _wanted_count;
    int _prev_key;
    int _count;
    bool _failed;

public:
    SequenceValidator(int start, int wanted_count)
        : _wanted_count(wanted_count),
          _prev_key(start - 1),
          _count(0),
          _failed(false)
    {
    }

    bool failed() const {
        return _failed || _wanted_count != _count;
    }
    
    void operator()(int key) {
        if (key != _prev_key + 1) {
            _failed = true;
        }
        _prev_key = key;
        ++_count;
    }
};

class ForeachKeyValidator
{
    SequenceValidator & _validator;
public:
    ForeachKeyValidator(SequenceValidator &validator)
        : _validator(validator)
    {
    }
    void operator()(int key) {
        _validator(key);
    }
};

template <typename Iterator>
void validate_subrange(Iterator &start, Iterator &end, SequenceValidator &validator) {
    start.foreach_key_range(end, ForeachKeyValidator(validator));
    EXPECT_FALSE(validator.failed());
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

typedef BTree<MyKey, std::string,
              btree::NoAggregated,
              MyComp, MyTraits> MyTree;
typedef BTreeStore<MyKey, std::string,
                   btree::NoAggregated,
                   MyComp, MyTraits> MyTreeStore;
using MyTreeBuilder = MyTree::Builder;
using MyLeafNode = MyTree::LeafNodeType;
using MyInternalNode = MyTree::InternalNodeType;
using MyNodeAllocator = MyTree::NodeAllocatorType;
using LeafPair = std::pair<MyKey, std::string>;
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

template <typename ManagerType>
void
cleanup(GenerationHandler & g, ManagerType & m)
{
    m.freeze();
    m.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    m.reclaim_memory(g.get_oldest_used_generation());
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

template<typename Tree>
bool
assertTree(const std::string &exp, const Tree &t)
{
    std::stringstream ss;
    test::BTreePrinter<std::stringstream, typename Tree::NodeAllocatorType> printer(ss, t.getAllocator());
    printer.print(t.getRoot());
    bool result = true;
    EXPECT_EQ(exp, ss.str()) << (result = false, "");
    return result;
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


class BTreeTest : public ::testing::Test {
protected:
    template <typename LeafNodeType>
    bool assertLeafNode(const std::string & exp, const LeafNodeType & n);
    bool assertSeek(int skey, int ekey, const MyTree & tree);
    bool assertSeek(int skey, int ekey, MyTree::Iterator & itr);
    bool assertMemoryUsage(const vespalib::MemoryUsage & exp, const vespalib::MemoryUsage & act);
    void buildSubTree(const std::vector<LeafPair> &sub, size_t numEntries);
    template <typename TreeType>
    void requireThatLowerBoundWorksT();
    template <typename TreeType>
    void requireThatUpperBoundWorksT();
    void requireThatIteratorDistanceWorks(int numEntries);
};

template <typename LeafNodeType>
bool
BTreeTest::assertLeafNode(const std::string & exp, const LeafNodeType & n)
{
    std::stringstream ss;
    ss << "[";
    for (uint32_t i = 0; i < n.validSlots(); ++i) {
        if (i > 0) ss << ",";
        ss << n.getKey(i) << ":" << n.getData(i);
    }
    ss << "]";
    bool result = true;
    EXPECT_EQ(exp, ss.str()) << (result = false, "");
    return result;
}

bool
BTreeTest::assertSeek(int skey, int ekey, const MyTree & tree)
{
    MyTree::Iterator itr = tree.begin();
    return assertSeek(skey, ekey, itr);
}

bool
BTreeTest::assertSeek(int skey, int ekey, MyTree::Iterator & itr)
{
    MyTree::Iterator bseekItr = itr;
    MyTree::Iterator lseekItr = itr;
    bseekItr.binarySeek(skey);
    lseekItr.linearSeek(skey);
    bool result = true;
    EXPECT_EQ(ekey, UNWRAP(bseekItr.getKey())) << (result = false, "");
    if (!result) {
        return false;
    }
    EXPECT_EQ(ekey, UNWRAP(lseekItr.getKey())) << (result = false, "");
    if (!result) {
        return false;
    }
    itr = bseekItr;
    return result;
}

bool
BTreeTest::assertMemoryUsage(const vespalib::MemoryUsage & exp, const vespalib::MemoryUsage & act)
{
    bool result = true;
    EXPECT_EQ(exp.allocatedBytes(), act.allocatedBytes()) << (result = false, "");
    if (!result) {
        return false;
    }
    EXPECT_EQ(exp.usedBytes(), act.usedBytes()) << (result = false, "");
    if (!result) {
        return false;
    }
    EXPECT_EQ(exp.deadBytes(), act.deadBytes()) << (result = false, "");
    if (!result) {
        return result;
    }
    EXPECT_EQ(exp.allocatedBytesOnHold(), act.allocatedBytesOnHold()) << (result = false, "");
    return result;
}

TEST_F(BTreeTest, control_iterator_size) {
    EXPECT_EQ(120u,  sizeof(BTreeIteratorBase<uint32_t, uint32_t, NoAggregated>));
    EXPECT_EQ(120u,  sizeof(BTreeIteratorBase<uint32_t, BTreeNoLeafData, NoAggregated>));
    EXPECT_EQ(288u, sizeof(MyTree::Iterator));
}

TEST_F(BTreeTest, require_that_node_insert_works)
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = m.allocLeafNode();
    MyLeafNode *n = nPair.data;
    EXPECT_TRUE(n->isLeaf());
    EXPECT_EQ(0u, n->validSlots());
    n->insert(0, 20, "b");
    EXPECT_TRUE(!n->isFull());
    EXPECT_TRUE(!n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[20:b]", *n));
    n->insert(0, 10, "a");
    EXPECT_TRUE(!n->isFull());
    EXPECT_TRUE(n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[10:a,20:b]", *n));
    EXPECT_EQ(20, UNWRAP(n->getLastKey()));
    EXPECT_EQ("b", n->getLastData());
    n->insert(2, 30, "c");
    EXPECT_TRUE(!n->isFull());
    n->insert(3, 40, "d");
    EXPECT_TRUE(n->isFull());
    EXPECT_TRUE(n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[10:a,20:b,30:c,40:d]", *n));
    cleanup(g, m, nPair.ref, n);
}


TEST_F(BTreeTest, require_that_tree_insert_works)
{
    using Tree = BTree<MyKey, int32_t, btree::NoAggregated, MyComp, MyTraits>;
    {
        Tree t;
        EXPECT_TRUE(assertTree("{}", t));
        t.insert(20, 102);
        EXPECT_TRUE(assertTree("{{20:102}}", t));
        t.insert(10, 101);
        EXPECT_TRUE(assertTree("{{10:101,20:102}}", t));
        t.insert(30, 103);
        t.insert(40, 104);
        EXPECT_TRUE(assertTree("{{10:101,20:102,30:103,40:104}}", t));
    }
    { // new entry in current node
        Tree t;
        populateLeafNode(t);
        t.insert(4, 104);
        EXPECT_TRUE(assertTree("{{4,7}} -> "
                               "{{1:101,3:103,4:104},"
                               "{5:105,7:107}}", t));
    }
    { // new entry in split node
        Tree t;
        populateLeafNode(t);
        t.insert(6, 106);
        EXPECT_TRUE(assertTree("{{5,7}} -> "
                               "{{1:101,3:103,5:105},"
                               "{6:106,7:107}}", t));
    }
    { // new entry at end
        Tree t;
        populateLeafNode(t);
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{5,8}} -> "
                               "{{1:101,3:103,5:105},"
                               "{7:107,8:108}}", t));
    }
    { // multi level node split
        Tree t;
        populateTree(t, 16, 2);
        EXPECT_TRUE(assertTree("{{7,15,23,31}} -> "
                               "{{1:101,3:103,5:105,7:107},"
                               "{9:109,11:111,13:113,15:115},"
                               "{17:117,19:119,21:121,23:123},"
                               "{25:125,27:127,29:129,31:131}}", t));
        t.insert(33, 133);
        EXPECT_TRUE(assertTree("{{23,33}} -> "
                               "{{7,15,23},{29,33}} -> "
                               "{{1:101,3:103,5:105,7:107},"
                               "{9:109,11:111,13:113,15:115},"
                               "{17:117,19:119,21:121,23:123},"
                               "{25:125,27:127,29:129},"
                               "{31:131,33:133}}", t));
    }
    { // give to left node to avoid split
        Tree t;
        populateTree(t, 8, 2);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15}} -> "
                               "{{1:101,3:103,7:107},"
                               "{9:109,11:111,13:113,15:115}}", t));
        t.insert(10, 110);
        EXPECT_TRUE(assertTree("{{9,15}} -> "
                               "{{1:101,3:103,7:107,9:109},"
                               "{10:110,11:111,13:113,15:115}}", t));
    }
    { // give to left node to avoid split, and move to left node
        Tree t;
        populateTree(t, 8, 2);
        t.remove(3);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15}} -> "
                               "{{1:101,7:107},"
                               "{9:109,11:111,13:113,15:115}}", t));
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{9,15}} -> "
                               "{{1:101,7:107,8:108,9:109},"
                               "{11:111,13:113,15:115}}", t));
    }
    { // not give to left node to avoid split, but insert at end at left node
        Tree t;
        populateTree(t, 8, 2);
        t.remove(5);
        EXPECT_TRUE(assertTree("{{7,15}} -> "
                               "{{1:101,3:103,7:107},"
                               "{9:109,11:111,13:113,15:115}}", t));
        t.insert(8, 108);
        EXPECT_TRUE(assertTree("{{8,15}} -> "
                               "{{1:101,3:103,7:107,8:108},"
                               "{9:109,11:111,13:113,15:115}}", t));
    }
    { // give to right node to avoid split
        Tree t;
        populateTree(t, 8, 2);
        t.remove(13);
        EXPECT_TRUE(assertTree("{{7,15}} -> "
                               "{{1:101,3:103,5:105,7:107},"
                               "{9:109,11:111,15:115}}", t));
        t.insert(4, 104);
        EXPECT_TRUE(assertTree("{{5,15}} -> "
                               "{{1:101,3:103,4:104,5:105},"
                               "{7:107,9:109,11:111,15:115}}", t));
    }
    { // give to right node to avoid split and move to right node
        using MyTraits6 = BTreeTraits<6, 6, 31, false>;
        using Tree6 = BTree<MyKey, int32_t, btree::NoAggregated, MyComp, MyTraits6>;

        Tree6 t;
        populateTree(t, 12, 2);
        t.remove(19);
        t.remove(21);
        t.remove(23);
        EXPECT_TRUE(assertTree("{{11,17}} -> "
                               "{{1:101,3:103,5:105,7:107,9:109,11:111},"
                               "{13:113,15:115,17:117}}", t));
        t.insert(10, 110);
        EXPECT_TRUE(assertTree("{{7,17}} -> "
                               "{{1:101,3:103,5:105,7:107},"
                               "{9:109,10:110,11:111,13:113,15:115,17:117}}", t));
    }
}

MyLeafNode::RefPair
getLeafNode(MyNodeAllocator &allocator)
{
    MyLeafNode::RefPair nPair = allocator.allocLeafNode();
    MyLeafNode *n = nPair.data;
    n->insert(0, 1, "a");
    n->insert(1, 3, "c");
    n->insert(2, 5, "e");
    n->insert(3, 7, "g");
    return nPair;
}

TEST_F(BTreeTest, require_that_node_split_insert_works)
{
    { // new entry in current node
        GenerationHandler g;
        MyNodeAllocator m;
        MyLeafNode::RefPair nPair = getLeafNode(m);
        MyLeafNode *n = nPair.data;
        MyLeafNode::RefPair sPair = m.allocLeafNode();
        MyLeafNode *s = sPair.data;
        n->splitInsert(s, 2, 4, "d");
        EXPECT_TRUE(assertLeafNode("[1:a,3:c,4:d]", *n));
        EXPECT_TRUE(assertLeafNode("[5:e,7:g]", *s));
        cleanup(g, m, nPair.ref, n, sPair.ref, s);
    }
    { // new entry in split node
        GenerationHandler g;
        MyNodeAllocator m;
        MyLeafNode::RefPair nPair = getLeafNode(m);
        MyLeafNode *n = nPair.data;
        MyLeafNode::RefPair sPair = m.allocLeafNode();
        MyLeafNode *s = sPair.data;
        n->splitInsert(s, 3, 6, "f");
        EXPECT_TRUE(assertLeafNode("[1:a,3:c,5:e]", *n));
        EXPECT_TRUE(assertLeafNode("[6:f,7:g]", *s));
        cleanup(g, m, nPair.ref, n, sPair.ref, s);
    }
    { // new entry at end
        GenerationHandler g;
        MyNodeAllocator m;
        MyLeafNode::RefPair nPair = getLeafNode(m);
        MyLeafNode *n = nPair.data;
        MyLeafNode::RefPair sPair = m.allocLeafNode();
        MyLeafNode *s = sPair.data;
        n->splitInsert(s, 4, 8, "h");
        EXPECT_TRUE(assertLeafNode("[1:a,3:c,5:e]", *n));
        EXPECT_TRUE(assertLeafNode("[7:g,8:h]", *s));
        cleanup(g, m, nPair.ref, n, sPair.ref, s);
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

TEST_F(BTreeTest, require_that_node_steal_works)
{
    typedef BTreeLeafNode<int, std::string,
        btree::NoAggregated, 6> MyStealNode;
    typedef BTreeNodeAllocator<int, std::string,
        btree::NoAggregated,
        BTreeStealTraits::INTERNAL_SLOTS, BTreeStealTraits::LEAF_SLOTS>
        MyStealManager;
    { // steal all from left
        GenerationHandler g;
        MyStealManager m;
        MyStealNode::RefPair nPair = m.allocLeafNode();
        MyStealNode *n = nPair.data;
        n->insert(0, 4, "d");
        n->insert(1, 5, "e");
        EXPECT_TRUE(!n->isAtLeastHalfFull());
        MyStealNode::RefPair vPair = m.allocLeafNode();
        MyStealNode *v = vPair.data;
        v->insert(0, 1, "a");
        v->insert(1, 2, "b");
        v->insert(2, 3, "c");
        n->stealAllFromLeftNode(v);
        EXPECT_TRUE(n->isAtLeastHalfFull());
        EXPECT_TRUE(assertLeafNode("[1:a,2:b,3:c,4:d,5:e]", *n));
        cleanup(g, m, nPair.ref, n, vPair.ref, v);
    }
    { // steal all from right
        GenerationHandler g;
        MyStealManager m;
        MyStealNode::RefPair nPair = m.allocLeafNode();
        MyStealNode *n = nPair.data;
        n->insert(0, 1, "a");
        n->insert(1, 2, "b");
        EXPECT_TRUE(!n->isAtLeastHalfFull());
        MyStealNode::RefPair vPair = m.allocLeafNode();
        MyStealNode *v = vPair.data;
        v->insert(0, 3, "c");
        v->insert(1, 4, "d");
        v->insert(2, 5, "e");
        n->stealAllFromRightNode(v);
        EXPECT_TRUE(n->isAtLeastHalfFull());
        EXPECT_TRUE(assertLeafNode("[1:a,2:b,3:c,4:d,5:e]", *n));
        cleanup(g, m, nPair.ref, n, vPair.ref, v);
    }
    { // steal some from left
        GenerationHandler g;
        MyStealManager m;
        MyStealNode::RefPair nPair = m.allocLeafNode();
        MyStealNode *n = nPair.data;
        n->insert(0, 5, "e");
        n->insert(1, 6, "f");
        EXPECT_TRUE(!n->isAtLeastHalfFull());
        MyStealNode::RefPair vPair = m.allocLeafNode();
        MyStealNode *v = vPair.data;
        v->insert(0, 1, "a");
        v->insert(1, 2, "b");
        v->insert(2, 3, "c");
        v->insert(3, 4, "d");
        n->stealSomeFromLeftNode(v);
        EXPECT_TRUE(n->isAtLeastHalfFull());
        EXPECT_TRUE(v->isAtLeastHalfFull());
        EXPECT_TRUE(assertLeafNode("[4:d,5:e,6:f]", *n));
        EXPECT_TRUE(assertLeafNode("[1:a,2:b,3:c]", *v));
        cleanup(g, m, nPair.ref, n, vPair.ref, v);
    }
    { // steal some from right
        GenerationHandler g;
        MyStealManager m;
        MyStealNode::RefPair nPair = m.allocLeafNode();
        MyStealNode *n = nPair.data;
        n->insert(0, 1, "a");
        n->insert(1, 2, "b");
        EXPECT_TRUE(!n->isAtLeastHalfFull());
        MyStealNode::RefPair vPair = m.allocLeafNode();
        MyStealNode *v = vPair.data;
        v->insert(0, 3, "c");
        v->insert(1, 4, "d");
        v->insert(2, 5, "e");
        v->insert(3, 6, "f");
        n->stealSomeFromRightNode(v);
        EXPECT_TRUE(n->isAtLeastHalfFull());
        EXPECT_TRUE(v->isAtLeastHalfFull());
        EXPECT_TRUE(assertLeafNode("[1:a,2:b,3:c]", *n));
        EXPECT_TRUE(assertLeafNode("[4:d,5:e,6:f]", *v));
        cleanup(g, m, nPair.ref, n, vPair.ref, v);
    }
}

TEST_F(BTreeTest, require_that_tree_remove_steal_works)
{
    using MyStealTree = BTree<MyKey, int32_t,btree::NoAggregated, MyComp, BTreeStealTraits, NoAggrCalc>;
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
        EXPECT_TRUE(assertTree("{{30,60}} -> "
                               "{{10:110,20:120,30:130},"
                               "{40:140,50:150,60:160}}", t));
        t.remove(50);
        EXPECT_TRUE(assertTree("{{10:110,20:120,30:130,40:140,60:160}}", t));
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
        EXPECT_TRUE(assertTree("{{30,60}} -> "
                               "{{10:110,20:120,30:130},"
                               "{40:140,50:150,60:160}}", t));
        t.remove(20);
        EXPECT_TRUE(assertTree("{{10:110,30:130,40:140,50:150,60:160}}", t));
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
        EXPECT_TRUE(assertTree("{{50,80}} -> "
                               "{{10:110,20:120,30:130,40:140,50:150},"
                               "{60:160,70:170,80:180}}", t));
        t.remove(60);
        EXPECT_TRUE(assertTree("{{30,80}} -> "
                               "{{10:110,20:120,30:130},"
                               "{40:140,50:150,70:170,80:180}}", t));
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
        EXPECT_TRUE(assertTree("{{30,90}} -> "
                               "{{10:110,20:120,30:130},"
                               "{50:150,60:160,70:170,80:180,90:190}}", t));
        t.remove(20);
        EXPECT_TRUE(assertTree("{{60,90}} -> "
                               "{{10:110,30:130,50:150,60:160},"
                               "{70:170,80:180,90:190}}", t));
    }
}

TEST_F(BTreeTest, require_that_node_remove_works)
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = getLeafNode(m);
    MyLeafNode *n = nPair.data;
    n->remove(1);
    EXPECT_TRUE(assertLeafNode("[1:a,5:e,7:g]", *n));
    cleanup(g, m, nPair.ref, n);
}

TEST_F(BTreeTest, require_that_node_lower_bound_works)
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = getLeafNode(m);
    MyLeafNode *n = nPair.data;
    EXPECT_EQ(1u, n->lower_bound(3, MyComp()));
    EXPECT_FALSE(MyComp()(3, n->getKey(1u)));
    EXPECT_EQ(0u, n->lower_bound(0, MyComp()));
    EXPECT_TRUE(MyComp()(0, n->getKey(0u)));
    EXPECT_EQ(1u, n->lower_bound(2, MyComp()));
    EXPECT_TRUE(MyComp()(2, n->getKey(1u)));
    EXPECT_EQ(3u, n->lower_bound(6, MyComp()));
    EXPECT_TRUE(MyComp()(6, n->getKey(3u)));
    EXPECT_EQ(4u, n->lower_bound(8, MyComp()));
    cleanup(g, m, nPair.ref, n);
}

void
generateData(std::vector<LeafPair> & data, size_t numEntries)
{
    data.reserve(numEntries);
    vespalib::Rand48 rnd;
    rnd.srand48(10);
    for (size_t i = 0; i < numEntries; ++i) {
        int num = rnd.lrand48() % 10000000;
        std::string str = toStr(num);
        data.push_back(std::make_pair(num, str));
    }
}


void
BTreeTest::buildSubTree(const std::vector<LeafPair> &sub,
                   size_t numEntries)
{
    GenerationHandler g;
    MyTree tree;
    MyTreeBuilder builder(tree.getAllocator());

    std::vector<LeafPair> sorted(sub.begin(), sub.begin() + numEntries);
    std::sort(sorted.begin(), sorted.end(), LeafPairLess());
    for (size_t i = 0; i < numEntries; ++i) {
        int num = UNWRAP(sorted[i].first);
        const std::string & str = sorted[i].second;
        builder.insert(num, str);
    }
    tree.assign(builder);
    assert(numEntries == tree.size());
    assert(tree.isValid());
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

TEST_F(BTreeTest, require_that_we_can_insert_and_remove_from_tree)
{
    GenerationHandler g;
    MyTree tree;
    std::vector<LeafPair> exp;
    std::vector<LeafPair> sorted;
    size_t numEntries = 1000;
    generateData(exp, numEntries);
    sorted = exp;
    std::sort(sorted.begin(), sorted.end(), LeafPairLess());
    // insert entries
    for (size_t i = 0; i < numEntries; ++i) {
        int num = UNWRAP(exp[i].first);
        const std::string & str = exp[i].second;
        EXPECT_TRUE(!tree.find(num).valid());
        //LOG(info, "insert[%zu](%d, %s)", i, num, str.c_str());
        EXPECT_TRUE(tree.insert(num, str));
        EXPECT_TRUE(!tree.insert(num, str));
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
        for (size_t j = i + 1; j < numEntries; ++j) {
            MyTree::Iterator itr = tree.find(exp[j].first);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(exp[j].first, itr.getKey());
            EXPECT_EQ(exp[j].second, itr.getData());
        }
        EXPECT_EQ(numEntries - 1 - i, tree.size());
    }
}

TEST_F(BTreeTest, require_that_sorted_tree_insert_works)
{
    {
        GenerationHandler g;
        MyTree tree;
        for (int i = 0; i < 1000; ++i) {
            EXPECT_TRUE(tree.insert(i, toStr(i)));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(toStr(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
        }
    }
    {
        GenerationHandler g;
        MyTree tree;
        for (int i = 1000; i > 0; --i) {
            EXPECT_TRUE(tree.insert(i, toStr(i)));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQ(toStr(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
        }
    }
}

TEST_F(BTreeTest, require_that_corner_case_tree_find_works)
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 1; i < 100; ++i) {
        tree.insert(i, toStr(i));
    }
    EXPECT_TRUE(!tree.find(0).valid()); // lower than lowest
    EXPECT_TRUE(!tree.find(1000).valid()); // higher than highest
}

TEST_F(BTreeTest, require_that_basic_tree_iterator_works)
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

TEST_F(BTreeTest, require_that_tree_iterator_seek_works)
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 0; i < 40; i += 2) {
        tree.insert(i, toStr(i));
    }
    //std::cout << tree.toString() << std::endl;
    EXPECT_TRUE(assertSeek(2, 2, tree)); // next key
    EXPECT_TRUE(assertSeek(10, 10, tree)); // skip to existing
    EXPECT_TRUE(assertSeek(26, 26, tree)); // skip to existing
    EXPECT_TRUE(assertSeek(11, 12, tree)); // skip to non-existing
    EXPECT_TRUE(assertSeek(23, 24, tree)); // skip to non-existing
    {
        MyTree::Iterator itr = tree.begin();
        EXPECT_TRUE(assertSeek(4, 4, itr));
        EXPECT_TRUE(assertSeek(14, 14, itr));
        EXPECT_TRUE(assertSeek(18, 18, itr));
        EXPECT_TRUE(assertSeek(36, 36, itr));
    }
    {
        MyTree::Iterator itr = tree.begin();
        EXPECT_TRUE(assertSeek(3, 4, itr));
        EXPECT_TRUE(assertSeek(13, 14, itr));
        EXPECT_TRUE(assertSeek(17, 18, itr));
        EXPECT_TRUE(assertSeek(35, 36, itr));
    }
    {
        MyTree::Iterator itr = tree.begin();
        MyTree::Iterator itr2 = tree.begin();
        itr.binarySeek(40); // outside
        itr2.linearSeek(40); // outside
        EXPECT_TRUE(!itr.valid());
        EXPECT_TRUE(!itr2.valid());
    }
    {
        MyTree::Iterator itr = tree.begin();
        EXPECT_TRUE(assertSeek(8, 8, itr));
        for (int i = 10; i < 40; i += 2) {
            ++itr;
            EXPECT_EQ(i, UNWRAP(itr.getKey()));
        }
    }
    {
        MyTree::Iterator itr = tree.begin();
        EXPECT_TRUE(assertSeek(26, 26, itr));
        for (int i = 28; i < 40; i += 2) {
            ++itr;
            EXPECT_EQ(i, UNWRAP(itr.getKey()));
        }
    }
    GenerationHandler g2;
    MyTree tree2; // only leaf node
    tree2.insert(0, "0");
    tree2.insert(2, "2");
    tree2.insert(4, "4");
    EXPECT_TRUE(assertSeek(1, 2, tree2));
    EXPECT_TRUE(assertSeek(2, 2, tree2));
    {
        MyTree::Iterator itr = tree2.begin();
        MyTree::Iterator itr2 = tree2.begin();
        itr.binarySeek(5); // outside
        itr2.linearSeek(5); // outside
        EXPECT_TRUE(!itr.valid());
        EXPECT_TRUE(!itr2.valid());
    }
}

TEST_F(BTreeTest, require_that_tree_iterator_assign_works)
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 0; i < 1000; ++i) {
        tree.insert(i, toStr(i));
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

size_t
adjustAllocatedBytes(size_t nodeCount, size_t nodeSize)
{
    // Note: Sizes of underlying data store buffers are power of 2.
    size_t allocatedBytes = vespalib::roundUp2inN(nodeCount * nodeSize);
    size_t adjustedNodeCount = allocatedBytes / nodeSize;
    return adjustedNodeCount * nodeSize;
}

TEST_F(BTreeTest, require_that_memory_usage_is_calculated)
{
    constexpr size_t BASE_ALLOCATED = 28744u;
    constexpr size_t BASE_USED = 24936;
    typedef BTreeNodeAllocator<int32_t, int8_t,
        btree::NoAggregated,
        MyTraits::INTERNAL_SLOTS, MyTraits::LEAF_SLOTS> NodeAllocator;
    using INode = NodeAllocator::InternalNodeType;
    using LNode = NodeAllocator::LeafNodeType;
    using IRef = NodeAllocator::InternalNodeTypeRefPair;
    using LRef = NodeAllocator::LeafNodeTypeRefPair;
    LOG(info, "sizeof(BTreeNode)=%zu, sizeof(INode)=%zu, sizeof(LNode)=%zu",
        sizeof(BTreeNode), sizeof(INode), sizeof(LNode));
    EXPECT_GT(sizeof(INode), sizeof(LNode));
    GenerationHandler gh;
    gh.incGeneration();
    NodeAllocator tm;
    vespalib::MemoryUsage mu;
    const uint32_t initialInternalNodes = 128u;
    const uint32_t initialLeafNodes = 128u;
    mu.incAllocatedBytes(adjustAllocatedBytes(initialInternalNodes, sizeof(INode)));
    mu.incAllocatedBytes(adjustAllocatedBytes(initialLeafNodes, sizeof(LNode)));
    mu.incAllocatedBytes(BASE_ALLOCATED);
    mu.incUsedBytes(BASE_USED);
    mu.incUsedBytes(sizeof(INode));
    mu.incDeadBytes(sizeof(INode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));

    // add internal node
    IRef ir = tm.allocInternalNode(1);
    mu.incUsedBytes(sizeof(INode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));

    // add leaf node
    LRef lr = tm.allocLeafNode();
    mu.incUsedBytes(sizeof(LNode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));

    // move nodes to hold list
    tm.freeze(); // mark allocated nodes as frozen so we can hold them later on
    tm.holdNode(ir.ref, ir.data);
    mu.incAllocatedBytesOnHold(sizeof(INode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));
    tm.holdNode(lr.ref, lr.data);
    mu.incAllocatedBytesOnHold(sizeof(LNode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));

    // trim hold lists
    tm.assign_generation(gh.getCurrentGeneration());
    gh.incGeneration();
    tm.reclaim_memory(gh.get_oldest_used_generation());
    mu = vespalib::MemoryUsage();
    mu.incAllocatedBytes(adjustAllocatedBytes(initialInternalNodes, sizeof(INode)));
    mu.incAllocatedBytes(adjustAllocatedBytes(initialLeafNodes, sizeof(LNode)));
    mu.incAllocatedBytes(BASE_ALLOCATED);
    mu.incUsedBytes(BASE_USED);
    mu.incUsedBytes(sizeof(INode) * 2);
    mu.incDeadBytes(sizeof(INode) * 2);
    mu.incUsedBytes(sizeof(LNode));
    mu.incDeadBytes(sizeof(LNode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));
}

template <typename TreeType>
void
BTreeTest::requireThatLowerBoundWorksT()
{
    GenerationHandler g;
    TreeType t;
    EXPECT_TRUE(t.insert(10, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(20, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(30, BTreeNoLeafData()));
    EXPECT_EQ(10, t.lowerBound(9).getKey());
    EXPECT_EQ(20, t.lowerBound(20).getKey());
    EXPECT_EQ(30, t.lowerBound(21).getKey());
    EXPECT_EQ(30, t.lowerBound(30).getKey());
    EXPECT_TRUE(!t.lowerBound(31).valid());
    for (int i = 40; i < 1000; i+=10) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData()));
    }
    for (int i = 9; i < 990; i+=10) {
        EXPECT_EQ(i + 1, t.lowerBound(i).getKey());
        EXPECT_EQ(i + 1, t.lowerBound(i + 1).getKey());
    }
    EXPECT_TRUE(!t.lowerBound(991).valid());
}

TEST_F(BTreeTest, require_that_lower_bound_works)
{
    requireThatLowerBoundWorksT<SetTreeB>();
    requireThatLowerBoundWorksT<SetTreeL>();
}

template <typename TreeType>
void
BTreeTest::requireThatUpperBoundWorksT()
{
    GenerationHandler g;
    TreeType t;
    EXPECT_TRUE(t.insert(10, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(20, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(30, BTreeNoLeafData()));
    EXPECT_EQ(10, t.upperBound(9).getKey());
    EXPECT_EQ(30, t.upperBound(20).getKey());
    EXPECT_EQ(30, t.upperBound(21).getKey());
    EXPECT_TRUE(!t.upperBound(30).valid());
    for (int i = 40; i < 1000; i+=10) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData()));
    }
    for (int i = 9; i < 980; i+=10) {
        EXPECT_EQ(i + 1, t.upperBound(i).getKey());
        EXPECT_EQ(i + 11, t.upperBound(i + 1).getKey());
    }
    EXPECT_TRUE(!t.upperBound(990).valid());
}

TEST_F(BTreeTest, require_that_upper_bound_works)
{
    requireThatUpperBoundWorksT<SetTreeB>();
    requireThatUpperBoundWorksT<SetTreeL>();
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

TEST_F(BTreeTest, require_that_update_of_key_works)
{
    typedef BTree<int, BTreeNoLeafData,
        btree::NoAggregated,
        UpdKeyComp &> UpdKeyTree;
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


TEST_F(BTreeTest, require_that_small_nodes_works)
{
    typedef BTreeStore<MyKey, std::string, btree::NoAggregated, MyComp,
        BTreeDefaultTraits> TreeStore;
    GenerationHandler g;
    TreeStore s;

    EntryRef root;
    EXPECT_EQ(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 40, "fourty"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQ(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 20, "twenty"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQ(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 60, "sixty"));
    EXPECT_TRUE(!s.insert(root, 60, "sixty.not"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQ(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 50, "fifty"));
    EXPECT_TRUE(!s.insert(root, 50, "fifty.not"));
    EXPECT_TRUE(!s.insert(root, 60, "sixty.not"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQ(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.insert(root, 1000 + i, "big"));
        if (i > 0) {
            EXPECT_TRUE(!s.insert(root, 1000 + i - 1, "big"));
        }
        EXPECT_EQ(5u + i, s.size(root));
        EXPECT_EQ(5u + i <= 8u,  s.isSmallArray(root));
    }
    EXPECT_TRUE(s.remove(root, 40));
    EXPECT_TRUE(!s.remove(root, 40));
    EXPECT_EQ(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(s.remove(root, 20));
    EXPECT_TRUE(!s.remove(root, 20));
    EXPECT_EQ(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(s.remove(root, 50));
    EXPECT_TRUE(!s.remove(root, 50));
    EXPECT_EQ(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.remove(root, 1000 + i));
        if (i > 0) {
            EXPECT_TRUE(!s.remove(root, 1000 + i - 1));
        }
        EXPECT_EQ(100 - i, s.size(root));
        EXPECT_EQ(100 - i <= 8u,  s.isSmallArray(root));
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

namespace {

template <typename TreeStore, typename AdditionsVector, typename RemovalsVector>
void
apply_tree_mutations(TreeStore& s,
                     EntryRef& root,
                     const AdditionsVector& additions,
                     const RemovalsVector& removals)
{
    s.apply(root, additions.empty() ? nullptr : &additions[0],
                  additions.empty() ? nullptr : &additions[0] + additions.size(),
                  removals.empty()  ? nullptr : &removals[0],
                  removals.empty()  ? nullptr : &removals[0] + removals.size());
}

}


TEST_F(BTreeTest, require_that_apply_works)
{
    typedef BTreeStore<MyKey, std::string, btree::NoAggregated, MyComp,
        BTreeDefaultTraits> TreeStore;
    using KeyType = TreeStore::KeyType;
    using KeyDataType = TreeStore::KeyDataType;
    GenerationHandler g;
    TreeStore s;
    std::vector<KeyDataType> additions;
    std::vector<KeyType> removals;

    EntryRef root;
    EXPECT_EQ(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(40, "fourty"));
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(20, "twenty"));
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(60, "sixty"));
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(50, "fifty"));
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    for (uint32_t i = 0; i < 100; ++i) {
        additions.clear();
        removals.clear();
        additions.push_back(KeyDataType(1000 + i, "big"));
        apply_tree_mutations(s, root, additions, removals);
        EXPECT_EQ(5u + i, s.size(root));
        EXPECT_EQ(5u + i <= 8u,  s.isSmallArray(root));
    }

    additions.clear();
    removals.clear();
    removals.push_back(40);
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    removals.push_back(20);
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    removals.push_back(50);
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    for (uint32_t i = 0; i < 100; ++i) {
        additions.clear();
        removals.clear();
        removals.push_back(1000 +i);
        apply_tree_mutations(s, root, additions, removals);
        EXPECT_EQ(100 - i, s.size(root));
        EXPECT_EQ(100 - i <= 8u,  s.isSmallArray(root));
    }
    EXPECT_EQ(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    for (uint32_t i = 0; i < 20; ++i)
        additions.push_back(KeyDataType(1000 + i, "big"));
    removals.push_back(60);
    removals.push_back(1002);
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(20u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(19u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    for (uint32_t i = 0; i < 20; ++i)
        additions.push_back(KeyDataType(1100 + i, "big"));
    for (uint32_t i = 0; i < 10; ++i)
        removals.push_back(1000 + i);
    apply_tree_mutations(s, root, additions, removals);
    EXPECT_EQ(30u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    s.clear(root);
    s.clearBuilder();
    s.freeze();
    s.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    s.reclaim_memory(g.get_oldest_used_generation());
}

class MyTreeTestIterator : public MyTree::Iterator
{
public:
    MyTreeTestIterator(const MyTree::Iterator &rhs)
        : MyTree::Iterator(rhs)
    {
    }

    int getPathSize() const { return _pathSize; }
};


void
BTreeTest::requireThatIteratorDistanceWorks(int numEntries)
{
    GenerationHandler g;
    MyTree tree;
    using Iterator = MyTree::Iterator;
    for (int i = 0; i < numEntries; ++i) {
        tree.insert(i, toStr(i));
    }
    MyTreeTestIterator tit = tree.begin();
    LOG(info,
        "numEntries=%d, iterator pathSize=%d",
        numEntries, tit.getPathSize());
    Iterator it = tree.begin();
    for (int i = 0; i <= numEntries; ++i) {
        Iterator iit = tree.lowerBound(i);
        Iterator iitn = tree.lowerBound(i + 1);
        Iterator iitu = tree.upperBound(i);
        Iterator iitls = tree.begin();
        Iterator iitbs = tree.begin();
        Iterator iitlsp = tree.begin();
        Iterator iitbsp = tree.begin();
        Iterator iitlb(tree.getRoot(), tree.getAllocator());
        iitlb.lower_bound(i);
        Iterator iitlb2(BTreeNode::Ref(), tree.getAllocator());
        iitlb2.lower_bound(tree.getRoot(), i);
        if (i > 0) {
            iitls.linearSeek(i);
            iitbs.binarySeek(i);
            ++it;
        }
        iitlsp.linearSeekPast(i);
        iitbsp.binarySeekPast(i);
        Iterator iitlsp2 = iitls;
        Iterator iitbsp2 = iitbs;
        Iterator iitnr = i < numEntries ? iitn : tree.begin();
        --iitnr;
        if (i < numEntries) {
            iitlsp2.linearSeekPast(i);
            iitbsp2.binarySeekPast(i);
        }
        EXPECT_EQ(i, static_cast<int>(iit.position()));
        EXPECT_EQ(i < numEntries, iit.valid()); 
        EXPECT_TRUE(iit.identical(it));
        EXPECT_TRUE(iit.identical(iitls));
        EXPECT_TRUE(iit.identical(iitbs));
        EXPECT_TRUE(iit.identical(iitnr));
        EXPECT_TRUE(iit.identical(iitlb));
        EXPECT_TRUE(iit.identical(iitlb2));
        EXPECT_TRUE(iitn.identical(iitu));
        EXPECT_TRUE(iitn.identical(iitlsp));
        EXPECT_TRUE(iitn.identical(iitbsp));
        EXPECT_TRUE(iitn.identical(iitlsp2));
        EXPECT_TRUE(iitn.identical(iitbsp2));
        if (i < numEntries) {
            EXPECT_EQ(i + 1, static_cast<int>(iitn.position()));
            EXPECT_EQ(i + 1 < numEntries, iitn.valid());
        }
        for (int j = 0; j <= numEntries; ++j) {
            Iterator jit = tree.lowerBound(j);
            EXPECT_EQ(j, static_cast<int>(jit.position()));
            EXPECT_EQ(j < numEntries, jit.valid()); 
            EXPECT_EQ(i - j, iit - jit);
            EXPECT_EQ(j - i, jit - iit);

            Iterator jit2 = jit;
            jit2.setupEnd();
            EXPECT_EQ(numEntries - j, jit2 - jit);
            EXPECT_EQ(numEntries - i, jit2 - iit);
            EXPECT_EQ(j - numEntries, jit - jit2);
            EXPECT_EQ(i - numEntries, iit - jit2);
        }
    }
}


TEST_F(BTreeTest, require_that_iterator_distance_works)
{
    requireThatIteratorDistanceWorks(1);
    requireThatIteratorDistanceWorks(3);
    requireThatIteratorDistanceWorks(8);
    requireThatIteratorDistanceWorks(20);
    requireThatIteratorDistanceWorks(100);
    requireThatIteratorDistanceWorks(400);
}

TEST_F(BTreeTest, require_that_foreach_key_works)
{
    using Tree = BTree<int, int, btree::NoAggregated, MyComp, MyTraits>;
    using Iterator = typename Tree::ConstIterator;
    Tree t;
    populateTree(t, 256, 1);

    {
        // Whole range
        SequenceValidator validator(1, 256);
        t.foreach_key(ForeachKeyValidator(validator));
        EXPECT_FALSE(validator.failed());
    }
    {
        // Subranges
        for (int startval = 1; startval < 259; ++startval) {
            for (int endval = 1; endval < 259; ++endval) {
                SequenceValidator validator(startval, std::max(0, std::min(endval,257) - std::min(startval, 257)));
                Iterator start = t.lowerBound(startval);
                Iterator end = t.lowerBound(endval);
                validate_subrange(start, end, validator);
            }
        }
    }
}

namespace {

template <typename Tree>
void
inc_generation(GenerationHandler &g, Tree &t)
{
    auto &s = t.getAllocator();
    s.freeze();
    s.assign_generation(g.getCurrentGeneration());
    g.incGeneration();
    s.reclaim_memory(g.get_oldest_used_generation());
}

template <typename Tree>
void
make_iterators(Tree& t, std::vector<int>& list, std::vector<typename Tree::ConstIterator>& iterators)
{
    for (auto key : list) {
        iterators.emplace_back(t.lowerBound(key));
    }
    iterators.emplace_back(t.lowerBound(300));
}

class KeyRangeValidator
{
    std::vector<int> &_list;
    size_t _curr_pos;
public:
    KeyRangeValidator(std::vector<int> &list, size_t start_pos)
        : _list(list),
          _curr_pos(start_pos)
    {
    }
    void operator()(int key) {
        assert(_curr_pos < _list.size());
        EXPECT_EQ(key, _list[_curr_pos]);
        ++_curr_pos;
    }
    size_t curr_pos() const noexcept { return _curr_pos; }
};

}

TEST_F(BTreeTest, require_that_compaction_works)
{
    using Tree = BTree<int, int, btree::NoAggregated, MyComp, MyTraits>;
    GenerationHandler g;
    Tree t;
    std::vector<int> before_list;
    std::vector<typename Tree::ConstIterator> before_iterators;
    std::vector<int> after_list;
    std::vector<typename Tree::ConstIterator> after_iterators;
    for (uint32_t i = 1; i < 256; ++i) {
        t.insert(i, 101);
    }
    for (uint32_t i = 50; i < 100; ++i) {
        t.remove(i);
    }
    inc_generation(g, t);
    auto guard = g.takeGuard();
    auto memory_usage_before = t.getAllocator().getMemoryUsage();
    t.foreach_key([&before_list](int key) { before_list.emplace_back(key); });
    make_iterators(t, before_list, before_iterators);
    CompactionStrategy compaction_strategy;
    for (int i = 0; i < 15; ++i) {
        t.compact_worst(compaction_strategy);
    }
    inc_generation(g, t);
    auto memory_usage_after = t.getAllocator().getMemoryUsage();
    t.foreach_key([&after_list](int key) { after_list.emplace_back(key); });
    make_iterators(t, after_list, after_iterators);
    EXPECT_LT(memory_usage_after.deadBytes(), memory_usage_before.deadBytes());
    EXPECT_EQ(before_list, after_list);
    EXPECT_EQ(before_iterators.size(), after_iterators.size());
    for (size_t i = 0; i < before_iterators.size(); ++i) {
        for (size_t j = 0; j < after_iterators.size(); ++j) {
            EXPECT_EQ(before_iterators[i] == after_iterators[j], i == j);
            EXPECT_EQ(before_iterators[i] - after_iterators[j], static_cast<ssize_t>(i - j));
            EXPECT_EQ(after_iterators[j] - before_iterators[i], static_cast<ssize_t>(j - i));
            if (i <= j) {
                KeyRangeValidator validate_keys(before_list, i);
                EXPECT_EQ(i, validate_keys.curr_pos());
                before_iterators[i].foreach_key_range(after_iterators[j], [&validate_keys](int key) { validate_keys(key); });
                EXPECT_EQ(j, validate_keys.curr_pos());
            }
            if (j <= i) {
                KeyRangeValidator validate_keys(before_list, j);
                EXPECT_EQ(j, validate_keys.curr_pos());
                after_iterators[j].foreach_key_range(before_iterators[i], [&validate_keys](int key) { validate_keys(key); });
                EXPECT_EQ(i, validate_keys.curr_pos());
            }
        }
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
