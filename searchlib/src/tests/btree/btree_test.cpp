// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("btree_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <string>
#include <vespa/searchlib/btree/btreeroot.h>
#include <vespa/searchlib/btree/btreebuilder.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreestore.h>
#include <vespa/searchlib/util/rand48.h>

#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreebuilder.hpp>
#include <vespa/searchlib/btree/btree.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>
#include <vespa/searchlib/test/btree/btree_printer.h>

using vespalib::GenerationHandler;
using search::datastore::EntryRef;

namespace search {
namespace btree {

namespace {

template <typename T>
std::string
toStr(const T & v)
{
    std::stringstream ss;
    ss << v;
    return ss.str();
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

typedef BTree<MyKey, std::string,
              btree::NoAggregated,
              MyComp, MyTraits> MyTree;
typedef BTreeStore<MyKey, std::string,
                   btree::NoAggregated,
                   MyComp, MyTraits> MyTreeStore;
typedef MyTree::Builder               MyTreeBuilder;
typedef MyTree::LeafNodeType          MyLeafNode;
typedef MyTree::InternalNodeType      MyInternalNode;
typedef MyTree::NodeAllocatorType     MyNodeAllocator;
typedef std::pair<MyKey, std::string> LeafPair;
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

template <typename ManagerType>
void
cleanup(GenerationHandler & g, ManagerType & m)
{
    m.freeze();
    m.transferHoldLists(g.getCurrentGeneration());
    g.incGeneration();
    m.trimHoldLists(g.getFirstUsedGeneration());
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
    if (!EXPECT_EQUAL(exp, ss.str())) return false;
    return true;
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


class Test : public vespalib::TestApp {
private:
    template <typename LeafNodeType>
    bool assertLeafNode(const std::string & exp, const LeafNodeType & n);
    bool assertSeek(int skey, int ekey, const MyTree & tree);
    bool assertSeek(int skey, int ekey, MyTree::Iterator & itr);
    bool assertMemoryUsage(const MemoryUsage & exp, const MemoryUsage & act);

    void
    buildSubTree(const std::vector<LeafPair> &sub,
                 size_t numEntries);

    void requireThatNodeInsertWorks();
    void requireThatTreeInsertWorks();
    void requireThatNodeSplitInsertWorks();
    void requireThatNodeStealWorks();
    void requireThatTreeRemoveStealWorks();
    void requireThatNodeRemoveWorks();
    void requireThatNodeLowerBoundWorks();
    void requireThatWeCanInsertAndRemoveFromTree();
    void requireThatSortedTreeInsertWorks();
    void requireThatCornerCaseTreeFindWorks();
    void requireThatBasicTreeIteratorWorks();
    void requireThatTreeIteratorSeekWorks();
    void requireThatTreeIteratorAssignWorks();
    void requireThatMemoryUsageIsCalculated();
    template <typename TreeType>
    void requireThatLowerBoundWorksT();
    void requireThatLowerBoundWorks();
    template <typename TreeType>
    void requireThatUpperBoundWorksT();
    void requireThatUpperBoundWorks();
    void requireThatUpdateOfKeyWorks();

    void
    requireThatSmallNodesWorks();

    void
    requireThatApplyWorks();

    void
    requireThatIteratorDistanceWorks(int numEntries);

    void
    requireThatIteratorDistanceWorks();
public:
    int Main() override;
};

template <typename LeafNodeType>
bool
Test::assertLeafNode(const std::string & exp, const LeafNodeType & n)
{
    std::stringstream ss;
    ss << "[";
    for (uint32_t i = 0; i < n.validSlots(); ++i) {
        if (i > 0) ss << ",";
        ss << n.getKey(i) << ":" << n.getData(i);
    }
    ss << "]";
    if (!EXPECT_EQUAL(exp, ss.str())) return false;
    return true;
}

bool
Test::assertSeek(int skey, int ekey, const MyTree & tree)
{
    MyTree::Iterator itr = tree.begin();
    return assertSeek(skey, ekey, itr);
}

bool
Test::assertSeek(int skey, int ekey, MyTree::Iterator & itr)
{
    MyTree::Iterator bseekItr = itr;
    MyTree::Iterator lseekItr = itr;
    bseekItr.binarySeek(skey);
    lseekItr.linearSeek(skey);
    if (!EXPECT_EQUAL(ekey, UNWRAP(bseekItr.getKey()))) return false;
    if (!EXPECT_EQUAL(ekey, UNWRAP(lseekItr.getKey()))) return false;
    itr = bseekItr;
    return true;
}

bool
Test::assertMemoryUsage(const MemoryUsage & exp, const MemoryUsage & act)
{
    if (!EXPECT_EQUAL(exp.allocatedBytes(), act.allocatedBytes())) return false;
    if (!EXPECT_EQUAL(exp.usedBytes(), act.usedBytes())) return false;
    if (!EXPECT_EQUAL(exp.deadBytes(), act.deadBytes())) return false;
    if (!EXPECT_EQUAL(exp.allocatedBytesOnHold(), act.allocatedBytesOnHold())) return false;
    return true;
}

void
Test::requireThatNodeInsertWorks()
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = m.allocLeafNode();
    MyLeafNode *n = nPair.data;
    EXPECT_TRUE(n->isLeaf());
    EXPECT_EQUAL(0u, n->validSlots());
    n->insert(0, 20, "b");
    EXPECT_TRUE(!n->isFull());
    EXPECT_TRUE(!n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[20:b]", *n));
    n->insert(0, 10, "a");
    EXPECT_TRUE(!n->isFull());
    EXPECT_TRUE(n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[10:a,20:b]", *n));
    EXPECT_EQUAL(20, UNWRAP(n->getLastKey()));
    EXPECT_EQUAL("b", n->getLastData());
    n->insert(2, 30, "c");
    EXPECT_TRUE(!n->isFull());
    n->insert(3, 40, "d");
    EXPECT_TRUE(n->isFull());
    EXPECT_TRUE(n->isAtLeastHalfFull());
    EXPECT_TRUE(assertLeafNode("[10:a,20:b,30:c,40:d]", *n));
    cleanup(g, m, nPair.ref, n);
}

void
Test::requireThatTreeInsertWorks()
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

void
Test::requireThatNodeSplitInsertWorks()
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

struct BTreeStealTraits
{
    static const size_t LEAF_SLOTS = 6;
    static const size_t INTERNAL_SLOTS = 6;
    static const size_t PATH_SIZE = 20;
    static const bool BINARY_SEEK = true;
};

void
Test::requireThatNodeStealWorks()
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

void
Test::requireThatTreeRemoveStealWorks()
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

void
Test::requireThatNodeRemoveWorks()
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = getLeafNode(m);
    MyLeafNode *n = nPair.data;
    n->remove(1);
    EXPECT_TRUE(assertLeafNode("[1:a,5:e,7:g]", *n));
    cleanup(g, m, nPair.ref, n);
}

void
Test::requireThatNodeLowerBoundWorks()
{
    GenerationHandler g;
    MyNodeAllocator m;
    MyLeafNode::RefPair nPair = getLeafNode(m);
    MyLeafNode *n = nPair.data;
    EXPECT_EQUAL(1u, n->lower_bound(3, MyComp()));
    EXPECT_FALSE(MyComp()(3, n->getKey(1u)));
    EXPECT_EQUAL(0u, n->lower_bound(0, MyComp()));
    EXPECT_TRUE(MyComp()(0, n->getKey(0u)));
    EXPECT_EQUAL(1u, n->lower_bound(2, MyComp()));
    EXPECT_TRUE(MyComp()(2, n->getKey(1u)));
    EXPECT_EQUAL(3u, n->lower_bound(6, MyComp()));
    EXPECT_TRUE(MyComp()(6, n->getKey(3u)));
    EXPECT_EQUAL(4u, n->lower_bound(8, MyComp()));
    cleanup(g, m, nPair.ref, n);
}

void
generateData(std::vector<LeafPair> & data, size_t numEntries)
{
    data.reserve(numEntries);
    Rand48 rnd;
    rnd.srand48(10);
    for (size_t i = 0; i < numEntries; ++i) {
        int num = rnd.lrand48() % 10000000;
        std::string str = toStr(num);
        data.push_back(std::make_pair(num, str));
    }
}


void
Test::buildSubTree(const std::vector<LeafPair> &sub,
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
        MyTree::NodeAllocatorType &manager = tree.getAllocator();
        std::vector<uint32_t> toHold = manager.startCompact();
        MyTree::Iterator itr = tree.begin();
        tree.setRoot(itr.moveFirstLeafNode(tree.getRoot()));
        while (itr.valid()) {
            // LOG(info, "Leaf moved to %d", UNWRAP(itr.getKey()));
            itr.moveNextLeafNode();
        }
        manager.finishCompact(toHold);
        manager.freeze();
        manager.transferHoldLists(g.getCurrentGeneration());
        g.incGeneration();
        manager.trimHoldLists(g.getFirstUsedGeneration());
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
        GenerationHandler g;
        MyTree tree;
        for (int i = 0; i < 1000; ++i) {
            EXPECT_TRUE(tree.insert(i, toStr(i)));
            MyTree::Iterator itr = tree.find(i);
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(toStr(i), itr.getData());
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
            EXPECT_EQUAL(toStr(i), itr.getData());
            EXPECT_TRUE(tree.isValid());
        }
    }
}

void
Test::requireThatCornerCaseTreeFindWorks()
{
    GenerationHandler g;
    MyTree tree;
    for (int i = 1; i < 100; ++i) {
        tree.insert(i, toStr(i));
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
Test::requireThatTreeIteratorSeekWorks()
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
            EXPECT_EQUAL(i, UNWRAP(itr.getKey()));
        }
    }
    {
        MyTree::Iterator itr = tree.begin();
        EXPECT_TRUE(assertSeek(26, 26, itr));
        for (int i = 28; i < 40; i += 2) {
            ++itr;
            EXPECT_EQUAL(i, UNWRAP(itr.getKey()));
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

void
Test::requireThatTreeIteratorAssignWorks()
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
            EXPECT_EQUAL(expNum++, UNWRAP(itr2.getKey()));
        }
        EXPECT_EQUAL(1000, expNum);
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

void
Test::requireThatMemoryUsageIsCalculated()
{
    typedef BTreeNodeAllocator<int32_t, int8_t,
        btree::NoAggregated,
        MyTraits::INTERNAL_SLOTS, MyTraits::LEAF_SLOTS> NodeAllocator;
    typedef NodeAllocator::InternalNodeType        INode;
    typedef NodeAllocator::LeafNodeType            LNode;
    typedef NodeAllocator::InternalNodeTypeRefPair IRef;
    typedef NodeAllocator::LeafNodeTypeRefPair     LRef;
    LOG(info, "sizeof(BTreeNode)=%zu, sizeof(INode)=%zu, sizeof(LNode)=%zu",
        sizeof(BTreeNode), sizeof(INode), sizeof(LNode));
    EXPECT_GREATER(sizeof(INode), sizeof(LNode));
    GenerationHandler gh;
    gh.incGeneration();
    NodeAllocator tm;
    MemoryUsage mu;
    const uint32_t initialInternalNodes = 128u;
    const uint32_t initialLeafNodes = 128u;
    mu.incAllocatedBytes(adjustAllocatedBytes(initialInternalNodes, sizeof(INode)));
    mu.incAllocatedBytes(adjustAllocatedBytes(initialLeafNodes, sizeof(LNode)));
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
    tm.transferHoldLists(gh.getCurrentGeneration());
    gh.incGeneration();
    tm.trimHoldLists(gh.getFirstUsedGeneration());
    mu = MemoryUsage();
    mu.incAllocatedBytes(adjustAllocatedBytes(initialInternalNodes, sizeof(INode)));
    mu.incAllocatedBytes(adjustAllocatedBytes(initialLeafNodes, sizeof(LNode)));
    mu.incUsedBytes(sizeof(INode) * 2);
    mu.incDeadBytes(sizeof(INode) * 2);
    mu.incUsedBytes(sizeof(LNode));
    mu.incDeadBytes(sizeof(LNode));
    EXPECT_TRUE(assertMemoryUsage(mu, tm.getMemoryUsage()));
}

template <typename TreeType>
void
Test::requireThatLowerBoundWorksT()
{
    GenerationHandler g;
    TreeType t;
    EXPECT_TRUE(t.insert(10, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(20, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(30, BTreeNoLeafData()));
    EXPECT_EQUAL(10, t.lowerBound(9).getKey());
    EXPECT_EQUAL(20, t.lowerBound(20).getKey());
    EXPECT_EQUAL(30, t.lowerBound(21).getKey());
    EXPECT_EQUAL(30, t.lowerBound(30).getKey());
    EXPECT_TRUE(!t.lowerBound(31).valid());
    for (int i = 40; i < 1000; i+=10) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData()));
    }
    for (int i = 9; i < 990; i+=10) {
        EXPECT_EQUAL(i + 1, t.lowerBound(i).getKey());
        EXPECT_EQUAL(i + 1, t.lowerBound(i + 1).getKey());
    }
    EXPECT_TRUE(!t.lowerBound(991).valid());
}

void
Test::requireThatLowerBoundWorks()
{
    requireThatLowerBoundWorksT<SetTreeB>();
    requireThatLowerBoundWorksT<SetTreeL>();
}

template <typename TreeType>
void
Test::requireThatUpperBoundWorksT()
{
    GenerationHandler g;
    TreeType t;
    EXPECT_TRUE(t.insert(10, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(20, BTreeNoLeafData()));
    EXPECT_TRUE(t.insert(30, BTreeNoLeafData()));
    EXPECT_EQUAL(10, t.upperBound(9).getKey());
    EXPECT_EQUAL(30, t.upperBound(20).getKey());
    EXPECT_EQUAL(30, t.upperBound(21).getKey());
    EXPECT_TRUE(!t.upperBound(30).valid());
    for (int i = 40; i < 1000; i+=10) {
        EXPECT_TRUE(t.insert(i, BTreeNoLeafData()));
    }
    for (int i = 9; i < 980; i+=10) {
        EXPECT_EQUAL(i + 1, t.upperBound(i).getKey());
        EXPECT_EQUAL(i + 11, t.upperBound(i + 1).getKey());
    }
    EXPECT_TRUE(!t.upperBound(990).valid());
}

void
Test::requireThatUpperBoundWorks()
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

void
Test::requireThatUpdateOfKeyWorks()
{
    typedef BTree<int, BTreeNoLeafData,
        btree::NoAggregated,
        UpdKeyComp &> UpdKeyTree;
    typedef UpdKeyTree::Iterator                       UpdKeyTreeIterator;
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
Test::requireThatSmallNodesWorks()
{
    typedef BTreeStore<MyKey, std::string, btree::NoAggregated, MyComp,
        BTreeDefaultTraits> TreeStore;
    GenerationHandler g;
    TreeStore s;

    EntryRef root;
    EXPECT_EQUAL(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 40, "fourty"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 20, "twenty"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQUAL(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 60, "sixty"));
    EXPECT_TRUE(!s.insert(root, 60, "sixty.not"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQUAL(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));
    EXPECT_TRUE(s.insert(root, 50, "fifty"));
    EXPECT_TRUE(!s.insert(root, 50, "fifty.not"));
    EXPECT_TRUE(!s.insert(root, 60, "sixty.not"));
    EXPECT_TRUE(!s.insert(root, 20, "twenty.not"));
    EXPECT_TRUE(!s.insert(root, 40, "fourty.not"));
    EXPECT_EQUAL(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.insert(root, 1000 + i, "big"));
        if (i > 0) {
            EXPECT_TRUE(!s.insert(root, 1000 + i - 1, "big"));
        }
        EXPECT_EQUAL(5u + i, s.size(root));
        EXPECT_EQUAL(5u + i <= 8u,  s.isSmallArray(root));
    }
    EXPECT_TRUE(s.remove(root, 40));
    EXPECT_TRUE(!s.remove(root, 40));
    EXPECT_EQUAL(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(s.remove(root, 20));
    EXPECT_TRUE(!s.remove(root, 20));
    EXPECT_EQUAL(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    EXPECT_TRUE(s.remove(root, 50));
    EXPECT_TRUE(!s.remove(root, 50));
    EXPECT_EQUAL(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(s.remove(root, 1000 + i));
        if (i > 0) {
            EXPECT_TRUE(!s.remove(root, 1000 + i - 1));
        }
        EXPECT_EQUAL(100 - i, s.size(root));
        EXPECT_EQUAL(100 - i <= 8u,  s.isSmallArray(root));
    }
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    s.clear(root);
    s.clearBuilder();
    s.freeze();
    s.transferHoldLists(g.getCurrentGeneration());
    g.incGeneration();
    s.trimHoldLists(g.getFirstUsedGeneration());
}


void
Test::requireThatApplyWorks()
{
    typedef BTreeStore<MyKey, std::string, btree::NoAggregated, MyComp,
        BTreeDefaultTraits> TreeStore;
    typedef TreeStore::KeyType KeyType;
    typedef TreeStore::KeyDataType KeyDataType;
    GenerationHandler g;
    TreeStore s;
    std::vector<KeyDataType> additions;
    std::vector<KeyType> removals;

    EntryRef root;
    EXPECT_EQUAL(0u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(40, "fourty"));
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(20, "twenty"));
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(2u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(60, "sixty"));
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(3u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    additions.push_back(KeyDataType(50, "fifty"));
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(4u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    for (uint32_t i = 0; i < 100; ++i) {
        additions.clear();
        removals.clear();
        additions.push_back(KeyDataType(1000 + i, "big"));
        s.apply(root, &additions[0], &additions[0] + additions.size(),
                      &removals[0], &removals[0] + removals.size());
        EXPECT_EQUAL(5u + i, s.size(root));
        EXPECT_EQUAL(5u + i <= 8u,  s.isSmallArray(root));
    }

    additions.clear();
    removals.clear();
    removals.push_back(40);
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(103u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    removals.push_back(20);
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(102u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    removals.push_back(50);
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(101u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));
    for (uint32_t i = 0; i < 100; ++i) {
        additions.clear();
        removals.clear();
        removals.push_back(1000 +i);
        s.apply(root, &additions[0], &additions[0] + additions.size(),
                      &removals[0], &removals[0] + removals.size());
        EXPECT_EQUAL(100 - i, s.size(root));
        EXPECT_EQUAL(100 - i <= 8u,  s.isSmallArray(root));
    }
    EXPECT_EQUAL(1u, s.size(root));
    EXPECT_TRUE(s.isSmallArray(root));

    additions.clear();
    removals.clear();
    for (uint32_t i = 0; i < 20; ++i)
        additions.push_back(KeyDataType(1000 + i, "big"));
    removals.push_back(60);
    removals.push_back(1002);
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(20u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(19u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    additions.clear();
    removals.clear();
    for (uint32_t i = 0; i < 20; ++i)
        additions.push_back(KeyDataType(1100 + i, "big"));
    for (uint32_t i = 0; i < 10; ++i)
        removals.push_back(1000 + i);
    s.apply(root, &additions[0], &additions[0] + additions.size(),
                  &removals[0], &removals[0] + removals.size());
    EXPECT_EQUAL(30u, s.size(root));
    EXPECT_TRUE(!s.isSmallArray(root));

    s.clear(root);
    s.clearBuilder();
    s.freeze();
    s.transferHoldLists(g.getCurrentGeneration());
    g.incGeneration();
    s.trimHoldLists(g.getFirstUsedGeneration());
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
Test::requireThatIteratorDistanceWorks(int numEntries)
{
    GenerationHandler g;
    MyTree tree;
    typedef MyTree::Iterator Iterator;
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
        EXPECT_EQUAL(i, static_cast<int>(iit.position()));
        EXPECT_EQUAL(i < numEntries, iit.valid()); 
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
            EXPECT_EQUAL(i + 1, static_cast<int>(iitn.position()));
            EXPECT_EQUAL(i + 1 < numEntries, iitn.valid());
        }
        for (int j = 0; j <= numEntries; ++j) {
            Iterator jit = tree.lowerBound(j);
            EXPECT_EQUAL(j, static_cast<int>(jit.position()));
            EXPECT_EQUAL(j < numEntries, jit.valid()); 
            EXPECT_EQUAL(i - j, iit - jit);
            EXPECT_EQUAL(j - i, jit - iit);

            Iterator jit2 = jit;
            jit2.setupEnd();
            EXPECT_EQUAL(numEntries - j, jit2 - jit);
            EXPECT_EQUAL(numEntries - i, jit2 - iit);
            EXPECT_EQUAL(j - numEntries, jit - jit2);
            EXPECT_EQUAL(i - numEntries, iit - jit2);
        }
    }
}


void
Test::requireThatIteratorDistanceWorks()
{
    requireThatIteratorDistanceWorks(1);
    requireThatIteratorDistanceWorks(3);
    requireThatIteratorDistanceWorks(8);
    requireThatIteratorDistanceWorks(20);
    requireThatIteratorDistanceWorks(100);
    requireThatIteratorDistanceWorks(400);
}


int
Test::Main()
{
    TEST_INIT("btree_test");

    requireThatNodeInsertWorks();
    requireThatTreeInsertWorks();
    requireThatNodeSplitInsertWorks();
    requireThatNodeStealWorks();
    requireThatTreeRemoveStealWorks();
    requireThatNodeRemoveWorks();
    requireThatNodeLowerBoundWorks();
    requireThatWeCanInsertAndRemoveFromTree();
    requireThatSortedTreeInsertWorks();
    requireThatCornerCaseTreeFindWorks();
    requireThatBasicTreeIteratorWorks();
    requireThatTreeIteratorSeekWorks();
    requireThatTreeIteratorAssignWorks();
    requireThatMemoryUsageIsCalculated();
    requireThatLowerBoundWorks();
    requireThatUpperBoundWorks();
    requireThatUpdateOfKeyWorks();
    requireThatSmallNodesWorks();
    requireThatApplyWorks();
    requireThatIteratorDistanceWorks();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::btree::Test);
