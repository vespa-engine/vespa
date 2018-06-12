// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("frozenbtree_test");

#define DEBUG_FROZENBTREE
#define LOG_FROZENBTREEXX
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchlib/btree/btreeroot.h>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <map>

using search::btree::BTreeRoot;
using search::btree::BTreeNode;
using search::btree::BTreeInternalNode;
using search::btree::BTreeLeafNode;
using search::btree::BTreeDefaultTraits;
using vespalib::GenerationHandler;

namespace search {


class FrozenBTreeTest : public vespalib::TestApp
{
public:
    typedef int KeyType;
private:
    std::vector<KeyType> _randomValues;
    std::vector<KeyType> _sortedRandomValues;

public:
    typedef int DataType;
    typedef BTreeRoot<KeyType, DataType,
                      btree::NoAggregated,
                      std::less<KeyType>,
                      BTreeDefaultTraits> Tree;
    typedef Tree::NodeAllocatorType NodeAllocator;
    typedef Tree::InternalNodeType InternalNodeType;
    typedef Tree::LeafNodeType LeafNodeType;
    typedef Tree::Iterator Iterator;
    typedef Tree::ConstIterator ConstIterator;
private:
    GenerationHandler *_generationHandler;
    NodeAllocator *_allocator;
    Tree *_tree;

    Rand48 _randomGenerator;

    void allocTree();
    void freeTree(bool verbose);
    void fillRandomValues(unsigned int count);
    void insertRandomValues(Tree &tree, NodeAllocator &allocator, const std::vector<KeyType> &values);
    void removeRandomValues(Tree &tree, NodeAllocator &allocator, const std::vector<KeyType> &values);
    void lookupRandomValues(const Tree &tree, NodeAllocator &allocator, const std::vector<KeyType> &values);
    void lookupGoneRandomValues(const Tree &tree, NodeAllocator &allocator, const std::vector<KeyType> &values);
    void lookupFrozenRandomValues(const Tree &tree, NodeAllocator &allocator, const std::vector<KeyType> &values);
    void sortRandomValues();
    void traverseTreeIterator(const Tree &tree, NodeAllocator &allocator,
                              const std::vector<KeyType> &sorted, bool frozen);

    void printSubEnumTree(BTreeNode::Ref node, NodeAllocator &allocator, int indent) const;
    void printEnumTree(const Tree *tree, NodeAllocator &allocator);

    static const char *frozenName(bool frozen) {
        return frozen ? "frozen" : "thawed";
    }
public:
    FrozenBTreeTest();
    ~FrozenBTreeTest();

    int Main() override;
};

FrozenBTreeTest::FrozenBTreeTest()
    : vespalib::TestApp(),
      _randomValues(),
      _sortedRandomValues(),
      _generationHandler(NULL),
      _allocator(NULL),
      _tree(NULL),
      _randomGenerator()
{}
FrozenBTreeTest::~FrozenBTreeTest() {}

void
FrozenBTreeTest::allocTree()
{
    assert(_generationHandler == NULL);
    assert(_allocator == NULL);
    assert(_tree == NULL);
    _generationHandler = new GenerationHandler;
    _allocator = new NodeAllocator();
    _tree = new Tree;
}


void
FrozenBTreeTest::freeTree(bool verbose)
{
#if 0
    LOG(info,
        "freeTree before clear: %" PRIu64 " (%" PRIu64 " held)"
        ", %" PRIu32 " leaves",
        static_cast<uint64_t>(_intTree->getUsedMemory()),
        static_cast<uint64_t>(_intTree->getHeldMemory()),
        _intTree->validLeaves());
    _intTree->clear();
    LOG(info,
        "freeTree before unhold: %" PRIu64 " (%" PRIu64 " held)",
        static_cast<uint64_t>(_intTree->getUsedMemory()),
        static_cast<uint64_t>(_intTree->getHeldMemory()));
    _intTree->dropFrozen();
    _intTree->removeOldGenerations(_intTree->getGeneration() + 1);
    LOG(info,
        "freeTree after unhold: %" PRIu64 " (%" PRIu64 " held)",
        static_cast<uint64_t>(_intTree->getUsedMemory()),
        static_cast<uint64_t>(_intTree->getHeldMemory()));
    if (verbose)
        LOG(info,
            "%d+%d leftover tree nodes",
            _intTree->getNumInternalNodes(),
            _intTree->getNumLeafNodes());
    EXPECT_TRUE(_intTree->getNumInternalNodes() == 0 &&
               _intTree->getNumLeafNodes() == 0);
    delete _intTree;
    _intTree = NULL;
    delete _intKeyStore;
    _intKeyStore = NULL;
#endif
    (void) verbose;
    _tree->clear(*_allocator);
    _allocator->freeze();
    _allocator->transferHoldLists(_generationHandler->getCurrentGeneration());
    _generationHandler->incGeneration();
    _allocator->trimHoldLists(_generationHandler->getFirstUsedGeneration());
    delete _tree;
    _tree = NULL;
    delete _allocator;
    _allocator = NULL;
    delete _generationHandler;
    _generationHandler = NULL;
}


void
FrozenBTreeTest::fillRandomValues(unsigned int count)
{
    unsigned int i;

    LOG(info, "Filling %u random values", count);
    _randomValues.clear();
    _randomValues.reserve(count);
    _randomGenerator.srand48(42);
    for (i = 0; i <count; i++)
        _randomValues.push_back(_randomGenerator.lrand48());

    EXPECT_TRUE(_randomValues.size() == count);
}


void
FrozenBTreeTest::
insertRandomValues(Tree &tree,
                   NodeAllocator &allocator,
                   const std::vector<KeyType> &values)
{
    std::vector<KeyType>::const_iterator i(values.begin());
    std::vector<KeyType>::const_iterator ie(values.end());
    Iterator p;

    LOG(info, "insertRandomValues start");
    for (; i != ie; ++i) {
#ifdef LOG_FROZENBTREE
        LOG(info, "Try lookup %d before insert", *i);
#endif
        p = tree.find(*i, allocator);
        if (!p.valid()) {
            DataType val = *i + 42;
            if (tree.insert(*i, val, allocator))
                p = tree.find(*i, allocator);
        }
        ASSERT_TRUE(p.valid() && p.getKey() == *i && p.getData() == *i + 42);
#ifdef DEBUG_FROZENBTREEX
        printEnumTree(&tree);
#endif
    }
    ASSERT_TRUE(tree.isValid(allocator));
    ASSERT_TRUE(tree.isValidFrozen(allocator));
    LOG(info, "insertRandomValues done");
}


void
FrozenBTreeTest::
removeRandomValues(Tree &tree,
                   NodeAllocator &allocator,
                   const std::vector<KeyType> & values)
{
    std::vector<KeyType>::const_iterator i(values.begin());
    std::vector<KeyType>::const_iterator ie(values.end());
    Iterator p;

    LOG(info, "removeRandomValues start");
    for (; i != ie; ++i) {
#ifdef LOG_FROZENBTREE
        LOG(info, "Try lookup %d before remove", *i);
#endif
        p = tree.find(*i, allocator);
        if (p.valid()) {
            if (tree.remove(*i, allocator))
                p = tree.find(*i, allocator);
        }
        ASSERT_TRUE(!p.valid());
#ifdef DEBUG_FROZENBTREEX
        tree.printTree();
#endif
    }
    ASSERT_TRUE(tree.isValid(allocator));
    ASSERT_TRUE(tree.isValidFrozen(allocator));
    LOG(info, "removeRandomValues done");
}


void
FrozenBTreeTest::
lookupRandomValues(const Tree &tree,
                   NodeAllocator &allocator,
                   const std::vector<KeyType> &values)
{
    std::vector<KeyType>::const_iterator i(values.begin());
    std::vector<KeyType>::const_iterator ie(values.end());
    Iterator p;

    LOG(info, "lookupRandomValues start");
    for (; i != ie; ++i) {
        p = tree.find(*i, allocator);
        ASSERT_TRUE(p.valid() && p.getKey() == *i);
    }
    LOG(info, "lookupRandomValues done");
}


void
FrozenBTreeTest::
lookupGoneRandomValues(const Tree &tree,
                       NodeAllocator &allocator,
                       const std::vector<KeyType> &values)
{
    std::vector<KeyType>::const_iterator i(values.begin());
    std::vector<KeyType>::const_iterator ie(values.end());
    Iterator p;

    LOG(info, "lookupGoneRandomValues start");
    for (; i != ie; ++i) {
        p = tree.find(*i, allocator);
        ASSERT_TRUE(!p.valid());
    }
    LOG(info, "lookupGoneRandomValues done");
}


void
FrozenBTreeTest::
lookupFrozenRandomValues(const Tree &tree,
                         NodeAllocator &allocator,
                         const std::vector<KeyType> &values)
{
    std::vector<KeyType>::const_iterator i(values.begin());
    std::vector<KeyType>::const_iterator ie(values.end());
    ConstIterator p;

    LOG(info, "lookupFrozenRandomValues start");
    for (; i != ie; ++i) {
        p = tree.getFrozenView(allocator).find(*i, std::less<int>());
        ASSERT_TRUE(p.valid() && p.getKey() == *i && p.getData() == *i + 42);
    }
    LOG(info, "lookupFrozenRandomValues done");
}


void
FrozenBTreeTest::sortRandomValues()
{
    std::vector<KeyType>::iterator i;
    std::vector<KeyType>::iterator ie;
    uint32_t okcnt;
    int prevVal;
    std::vector<KeyType> sorted;

    LOG(info, "sortRandomValues start");
    sorted = _randomValues;
    std::sort(sorted.begin(), sorted.end());
    _sortedRandomValues.clear();
    _sortedRandomValues.reserve(sorted.size());

    okcnt = 0;
    prevVal = 0;
    ie = sorted.end();
    for (i = sorted.begin(); i != ie; ++i) {
        if (i == _sortedRandomValues.begin() || *i > prevVal) {
            okcnt++;
            _sortedRandomValues.push_back(*i);
        } else if (*i == prevVal)
            okcnt++;
        else
            LOG_ABORT("should not be reached");
        prevVal = *i;
    }
    EXPECT_TRUE(okcnt == sorted.size());
    LOG(info, "sortRandomValues done");
}


void
FrozenBTreeTest::
traverseTreeIterator(const Tree &tree,
                     NodeAllocator &allocator,
                     const std::vector<KeyType> &sorted,
                     bool frozen)
{
   LOG(info,
       "traverseTreeIterator %s start",
       frozenName(frozen));

   std::vector<KeyType>::const_iterator i;

   i = sorted.begin();
   if (frozen) {
       ConstIterator ai;
       ai = tree.getFrozenView(allocator).begin();
       for (;ai.valid(); ++ai, ++i)
       {
           ASSERT_TRUE(ai.getKey() == *i);
       }
   } else {
       Iterator ai;
       ai = tree.begin(allocator);
       for (;ai.valid(); ++ai, ++i)
       {
           ASSERT_TRUE(ai.getKey() == *i);
       }
   }


   ASSERT_TRUE(i == sorted.end());

   LOG(info,
       "traverseTreeIterator %s done",
       frozenName(frozen));
}


void
FrozenBTreeTest::
printSubEnumTree(BTreeNode::Ref node,
                 NodeAllocator &allocator,
                 int indent) const
{
    // typedef BTreeNode Node;
    typedef LeafNodeType LeafNode;
    typedef InternalNodeType InternalNode;
    BTreeNode::Ref subNode;
    unsigned int i;

    if (allocator.isLeafRef(node)) {
        const LeafNode *lnode = allocator.mapLeafRef(node);
        printf("%*s LeafNode %s valid=%d\n",
               indent, "",
               lnode->getFrozen() ? "frozen" : "thawed",
               lnode->validSlots());
        for (i = 0; i < lnode->validSlots(); i++) {

            KeyType k = lnode->getKey(i);
            DataType d = lnode->getData(i);
            printf("leaf value %3d %d %d\n",
                   (int) i,
                   (int) k,
                   (int) d);
        }
        return;
    }
    const InternalNode *inode = allocator.mapInternalRef(node);
    printf("%*s IntermediteNode %s valid=%d\n",
           indent, "",
           inode->getFrozen() ? "frozen" : "thawed",
           inode->validSlots());
    for (i = 0; i < inode->validSlots(); i++) {
        subNode = inode->getChild(i);
        assert(subNode != BTreeNode::Ref());
        printSubEnumTree(subNode, allocator, indent + 4);
    }
}


void
FrozenBTreeTest::printEnumTree(const Tree *tree,
                               NodeAllocator &allocator)
{
    printf("Tree Dump start\n");
    if (!NodeAllocator::isValidRef(tree->getRoot())) {
        printf("EMPTY\n");
    } else {
        printSubEnumTree(tree->getRoot(), allocator, 0);
    }
    printf("Tree Dump done\n");
}



int
FrozenBTreeTest::Main()
{
    TEST_INIT("frozenbtree_test");

    fillRandomValues(1000);
    sortRandomValues();

    allocTree();
    insertRandomValues(*_tree, *_allocator, _randomValues);
    lookupRandomValues(*_tree, *_allocator, _randomValues);
    _allocator->freeze();
    _allocator->transferHoldLists(_generationHandler->getCurrentGeneration());
    lookupFrozenRandomValues(*_tree, *_allocator, _randomValues);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         false);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         true);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         false);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         true);
    removeRandomValues(*_tree, *_allocator, _randomValues);
    lookupGoneRandomValues(*_tree, *_allocator, _randomValues);
    lookupFrozenRandomValues(*_tree, *_allocator,_randomValues);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         true);
    insertRandomValues(*_tree, *_allocator, _randomValues);
    freeTree(true);

    fillRandomValues(1000000);
    sortRandomValues();

    allocTree();
    insertRandomValues(*_tree, *_allocator, _randomValues);
    traverseTreeIterator(*_tree,
                         *_allocator,
                         _sortedRandomValues,
                         false);
    freeTree(false);

    TEST_DONE();
}

}

TEST_APPHOOK(search::FrozenBTreeTest);
