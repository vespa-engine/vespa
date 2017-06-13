// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP("postinglist_test");

namespace search {

using vespalib::GenerationHandler;

/*
 * TODO: Make it pass MALLOC_OPTIONS=AJ on freebsd and valgrind on Linux.
 */

class AttributePostingListTest : public vespalib::TestApp
{
private:
    /* Limited STL version for validation of full version */
    typedef std::set<uint32_t> STLPostingList;
    typedef std::map<int, STLPostingList> STLValueTree;

    class RandomValue
    {
    public:
        uint32_t _docId;
        int _value;
        uint32_t _order;

        RandomValue()
            : _docId(0),
              _value(0u),
              _order(0u)
        {
        }

        RandomValue(uint32_t docId, uint32_t value, uint32_t order)
            : _docId(docId),
              _value(value),
              _order(order)
        {
        }

        bool
        operator<(const RandomValue &rhs) const
        {
            return (_value < rhs._value ||
                    (_value == rhs._value &&
                     (_docId < rhs._docId ||
                      (_docId == rhs._docId &&
                       _order < rhs._order))));
        }

        bool
        operator>(const RandomValue &rhs) const
        {
            return (_value > rhs._value ||
                    (_value == rhs._value &&
                     (_docId > rhs._docId ||
                      (_docId == rhs._docId &&
                       _order > rhs._order))));
        }

        bool
        operator==(const RandomValue &rhs) const
        {
            return (_value == rhs._value &&
                    _docId == rhs._docId &&
                    _order == rhs._order);
        }
    };

    class CompareOrder
    {
    public:
        bool
        operator()(const RandomValue &a, const RandomValue &b)
        {
            return (a._order < b._order ||
                    (a._order == b._order &&
                     (a._value < b._value ||
                      (a._value == b._value &&
                       a._docId < b._docId))));
        }
    };
    std::vector<RandomValue> _randomValues;

public:
    typedef datastore::DataStore<int> IntKeyStore;
    typedef btree::BTreeKeyData<uint32_t, btree::BTreeNoLeafData>
    AttributePosting;
    typedef btree::BTreeStore<uint32_t,
                              btree::BTreeNoLeafData,
                              btree::NoAggregated,
                              std::less<uint32_t>,
                              btree::BTreeDefaultTraits>
    PostingList;
    typedef PostingList::NodeAllocatorType PostingListNodeAllocator;
    typedef datastore::EntryRef PostingIdx;
    typedef datastore::EntryRef StoreIndex;

    class IntComp {
    private:
        const IntKeyStore & _store;
        int                 _value;
        int getValue(const StoreIndex & idx) const {
            if (idx.valid()) {
                return _store.getEntry(idx);
            }
            return _value;
        }
    public:
        IntComp(const IntKeyStore & store) : _store(store), _value(0) {}
        IntComp(const IntKeyStore & store, int value) : _store(store), _value(value) {}
        bool operator() (const StoreIndex & lhs, const StoreIndex & rhs) const {
            return getValue(lhs) < getValue(rhs);
        }
    };

    typedef btree::BTreeRoot<StoreIndex, PostingIdx,
                             btree::NoAggregated,
                             const IntComp &> IntEnumTree;
    typedef IntEnumTree::NodeAllocatorType IntEnumNodeAllocator;
    typedef IntEnumTree Tree;
    typedef IntEnumNodeAllocator TreeManager;
    typedef IntKeyStore ValueHandle;
    typedef std::vector<RandomValue> RandomValuesVector;
private:
    GenerationHandler _handler;
    IntKeyStore *_intKeyStore;
    IntEnumNodeAllocator *_intNodeAlloc;
    IntEnumTree *_intTree;
    PostingList *_intPostings;
    STLValueTree *_stlTree;

    Rand48 _randomGenerator;
    uint32_t _generation;

    void
    allocTree();

    void
    freeTree(bool verbose);

    void
    fillRandomValues(unsigned int count,
                     unsigned int mvcount);

    void
    insertRandomValues(Tree &tree,
                       TreeManager &treeMgr,
                       ValueHandle &valueHandle,
                       PostingList &postings,
                       STLValueTree *stlTree,
                       RandomValuesVector &values);

    void
    removeRandomValues(Tree &tree,
                       TreeManager &treeMgr,
                       ValueHandle &valueHandle,
                       PostingList &postings,
                       STLValueTree *stlTree,
                       RandomValuesVector &values);

    void
    lookupRandomValues(Tree &tree,
                       TreeManager &treeMgr,
                       const ValueHandle &valueHandle,
                       PostingList &postings,
                       STLValueTree *stlTree,
                       RandomValuesVector &values);

    void
    sortRandomValues();

    void
    doCompactEnumStore(Tree &tree,
                       TreeManager &treeMgr,
                       ValueHandle &valueHandle);

    void
    doCompactPostingList(Tree &tree,
                         TreeManager &treeMgr,
                         PostingList &postings,
                         PostingListNodeAllocator &postingsAlloc);

    void
    bumpGeneration(Tree &tree,
                   ValueHandle &valueHandle,
                   PostingList &postings,
                   PostingListNodeAllocator &postingsAlloc);

    void
    removeOldGenerations(Tree &tree,
                         ValueHandle &valueHandle,
                         PostingList &postings,
                         PostingListNodeAllocator &postingsAlloc);

    static const char *
    frozenName(bool frozen)
    {
        return frozen ? "frozen" : "thawed";
    }
public:
    AttributePostingListTest();
    ~AttributePostingListTest();

    int Main() override;
};

AttributePostingListTest::AttributePostingListTest()
    : vespalib::TestApp(),
      _randomValues(),
      _handler(),
      _intKeyStore(NULL),
      _intNodeAlloc(NULL),
      _intTree(NULL),
      _intPostings(NULL),
      _stlTree(NULL),
      _randomGenerator()
{}
AttributePostingListTest::~AttributePostingListTest() {}

void
AttributePostingListTest::allocTree()
{
    _intKeyStore = new IntKeyStore;
    _intNodeAlloc = new IntEnumNodeAllocator();
    _intTree = new IntEnumTree();
    _intPostings = new PostingList();
    _stlTree = new STLValueTree;
}


void
AttributePostingListTest::freeTree(bool verbose)
{
    (void) verbose;
    LOG(info,
        "freeTree before clear: %" PRIu64 " (%" PRIu64 " held)"
        ", %zu leaves",
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytes()),
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytesOnHold()),
        _intTree->size(*_intNodeAlloc));
    _intTree->clear(*_intNodeAlloc);
    LOG(info,
        "freeTree before unhold: %" PRIu64 " (%" PRIu64 " held)",
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytes()),
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytesOnHold()));
    _intNodeAlloc->freeze();
    _intPostings->freeze();
    _intNodeAlloc->transferHoldLists(_handler.getCurrentGeneration());
    _intPostings->clearBuilder();
    _intPostings->transferHoldLists(_handler.getCurrentGeneration());
    _handler.incGeneration();
    _intNodeAlloc->trimHoldLists(_handler.getFirstUsedGeneration());
    _intPostings->trimHoldLists(_handler.getFirstUsedGeneration());
    LOG(info,
        "freeTree after unhold: %" PRIu64 " (%" PRIu64 " held)",
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytes()),
        static_cast<uint64_t>(_intNodeAlloc->getMemoryUsage().allocatedBytesOnHold()));
    delete _stlTree;
    _stlTree = NULL;
    delete _intTree;
    _intTree = NULL;
    delete _intNodeAlloc;
    _intNodeAlloc = NULL;
    delete _intKeyStore;
    _intKeyStore = NULL;
    delete _intPostings;
    _intPostings = NULL;
}


void
AttributePostingListTest::
fillRandomValues(unsigned int count,
                 unsigned int mvcount)
{
    unsigned int i;
    unsigned int j;
    unsigned int mv;
    unsigned int mvmax;
    unsigned int mvcount2;
    unsigned int mvcount3;

    mvmax = 100;
    mvcount2 = mvcount * (mvmax * (mvmax - 1)) / 2;
    LOG(info,
        "Filling %u+%u random values", count, mvcount2);
    _randomValues.clear();
    _randomValues.reserve(count);
    _randomGenerator.srand48(42);
    for (i = 0; i <count; i++) {
        uint32_t docId = _randomGenerator.lrand48();
        uint32_t val = _randomGenerator.lrand48();
        uint32_t order = _randomGenerator.lrand48();
        _randomValues.push_back(RandomValue(docId, val, order));
    }
    for (mv = 1; mv < mvmax; mv++) {
        for (i = 0; i < mvcount; i++) {
            for (j = 0; j < mv; j++) {
                uint32_t docId = _randomGenerator.lrand48();
                uint32_t val = _randomGenerator.lrand48();
                uint32_t order = _randomGenerator.lrand48();
                _randomValues.push_back(RandomValue(docId, val, order));
            }
        }
    }
    mvcount3 = 0;
    for (mv = 10; mv < 4000; mv = mv * 3)
    {
        mvcount3 += mv * 2;
        for (j = 0; j < mv; j++) {
            uint32_t val = _randomGenerator.lrand48();
            uint32_t docId = _randomGenerator.lrand48();
            uint32_t order = _randomGenerator.lrand48();
            _randomValues.push_back(RandomValue(docId, val, order));
            val = _randomGenerator.lrand48();
            docId = _randomGenerator.lrand48();
            order = _randomGenerator.lrand48();
            _randomValues.push_back(RandomValue(docId, val, order));
        }
    }
    std::sort(_randomValues.begin(),
              _randomValues.end(),
              CompareOrder());

    EXPECT_TRUE(_randomValues.size() == count + mvcount2 + mvcount3);
}


void
AttributePostingListTest::
insertRandomValues(Tree &tree,
                   TreeManager &treeMgr,
                   ValueHandle &valueHandle,
                   PostingList &postings,
                   STLValueTree *stlTree,
                   RandomValuesVector &
                   values)
{
    RandomValuesVector::iterator i;
    RandomValuesVector::iterator ie;

    LOG(info, "insertRandomValues start");
    ie = values.end();
    for (i = values.begin(); i != ie; ++i) {
        Tree::Iterator itr = tree.find(StoreIndex(), treeMgr, IntComp(valueHandle, i->_value));
        if (!itr.valid()) {
#if 0
            if (valueHandle.needResize())
                doCompactEnumStore(tree, treeMgr, valueHandle);
#endif
            StoreIndex idx = valueHandle.addEntry(i->_value);
            if (tree.insert(idx, PostingIdx(), treeMgr, IntComp(valueHandle))) {
                itr = tree.find(idx, treeMgr, IntComp(valueHandle));
            }
        } else {
        }
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(i->_value, valueHandle.getEntry(itr.getKey()));

        /* TODO: Insert docid to postinglist */
        PostingIdx oldIdx = itr.getData();
        PostingIdx newIdx = oldIdx;
        AttributePosting newPosting(i->_docId,
                                    btree::BTreeNoLeafData());
        std::vector<AttributePosting> additions;
        std::vector<uint32_t> removals;
        additions.push_back(newPosting);
        postings.apply(newIdx, &additions[0], &additions[0] + additions.size(),
                               &removals[0], &removals[0] + removals.size());
        std::atomic_thread_fence(std::memory_order_release);
        itr.writeData(newIdx);

        if (stlTree != NULL) {
            STLValueTree::iterator it;
            it = stlTree->find(i->_value);
            if (it == stlTree->end()) {
                std::pair<STLValueTree::iterator,bool> ir =
                    stlTree->insert(std::make_pair(i->_value,
                                    STLPostingList()));
                ASSERT_TRUE(ir.second && ir.first != stlTree->end() &&
                            ir.first->first == i->_value);
                it = ir.first;
            }
            ASSERT_TRUE(it != stlTree->end() && it->first == i->_value);
            it->second.insert(i->_docId);

            if (it->second.empty()) {
                stlTree->erase(it);
                ASSERT_TRUE(!itr.valid());
            } else {
                size_t postingsize;

                ASSERT_TRUE(itr.valid());
                postingsize = postings.size(newIdx);
                ASSERT_TRUE(postingsize > 0 &&
                            postingsize == it->second.size());
                STLPostingList::iterator it3;
                STLPostingList::iterator it3b;
                STLPostingList::iterator it3e;

                PostingList::Iterator it0;

                it3b = it->second.begin();
                it3e = it->second.end();
                it0 = postings.begin(newIdx);
                it3 = it3b;

                while (it3 != it3e) {
                    ASSERT_TRUE(it0.valid());
                    ASSERT_TRUE(*it3 == it0.getKey());
                    ++it3;
                    ++it0;
                }
                ASSERT_TRUE(!it0.valid());
            }
        }
    }
    ASSERT_TRUE(tree.isValid(treeMgr, IntComp(valueHandle)));
    LOG(info, "insertRandomValues done");
}


void
AttributePostingListTest::
removeRandomValues(Tree &tree,
                   TreeManager &treeMgr,
                   ValueHandle &valueHandle,
                   PostingList &postings,
                   STLValueTree *stlTree,
                   RandomValuesVector &values)
{
    RandomValuesVector::iterator i;
    RandomValuesVector::iterator ie;

    LOG(info, "removeRandomValues start");
    ie = values.end();
    for (i = values.begin(); i != ie; ++i) {
        Tree::Iterator itr = tree.find(StoreIndex(), treeMgr, IntComp(valueHandle, i->_value));
        PostingIdx newIdx;
        /*
         * TODO: Remove docid from postinglist, and only remove
         *       value from tree if postinglist is empty
         */
        if (itr.valid()) {
            PostingIdx oldIdx = itr.getData();
            newIdx = oldIdx;
            std::vector<AttributePosting> additions;
            std::vector<uint32_t> removals;
            removals.push_back(i->_docId);
            postings.apply(newIdx, &additions[0], &additions[0]+additions.size(),
                                   &removals[0], &removals[0] + removals.size());
            if (newIdx != oldIdx) {
                std::atomic_thread_fence(std::memory_order_release);
                itr.writeData(newIdx);
            }
            if (!newIdx.valid()) {
                if (tree.remove(StoreIndex(), treeMgr, IntComp(valueHandle, i->_value))) {
                    itr = tree.find(StoreIndex(), treeMgr, IntComp(valueHandle, i->_value));
                }
            }
        }
        if (stlTree != NULL) {
            STLValueTree::iterator it;
            it = stlTree->find(i->_value);
            ASSERT_TRUE(it != stlTree->end() && it->first == i->_value);
            STLPostingList::iterator it2;
            it2 = it->second.find(i->_docId);
            ASSERT_TRUE(it2 != it->second.end() &&
                        *it2 == i->_docId);
            it->second.erase(it2);

            if (it->second.empty()) {
                stlTree->erase(it);
                ASSERT_TRUE(!itr.valid());
            } else {
                size_t postingsize;

                ASSERT_TRUE(itr.valid());
                postingsize = postings.size(newIdx);
                ASSERT_TRUE(postingsize > 0 &&
                            postingsize == it->second.size());
                STLPostingList::iterator it3;
                STLPostingList::iterator it3b;
                STLPostingList::iterator it3e;

                PostingList::Iterator it0;

                it3b = it->second.begin();
                it3e = it->second.end();
                it0 = postings.begin(newIdx);
                it3 = it3b;

                while (it3 != it3e) {
                    ASSERT_TRUE(it0.valid());
                    ASSERT_TRUE(*it3 == it0.getKey());
                    ++it3;
                    ++it0;
                }
                ASSERT_TRUE(!it0.valid());
            }
        }
    }
    ASSERT_TRUE(tree.isValid(treeMgr, IntComp(valueHandle)));
    LOG(info, "removeRandomValues done");
}


void
AttributePostingListTest::
lookupRandomValues(Tree &tree,
                   TreeManager &treeMgr,
                   const ValueHandle &valueHandle,
                   PostingList &postings,
                   STLValueTree *stlTree,
                   RandomValuesVector &values)
{
    RandomValuesVector::iterator i;
    RandomValuesVector::iterator ie;

    LOG(info, "lookupRandomValues start");
    ie = values.end();
    for (i = values.begin(); i != ie; ++i) {
        Tree::Iterator itr = tree.find(StoreIndex(), treeMgr, IntComp(valueHandle, i->_value));
        ASSERT_TRUE(itr.valid() &&
                    valueHandle.getEntry(itr.getKey()) == i->_value);
        if (stlTree != NULL) {
            STLValueTree::iterator it;
            it = stlTree->find(i->_value);
            ASSERT_TRUE(it != stlTree->end() && it->first == i->_value);

            if (it->second.empty()) {
                stlTree->erase(it);
                ASSERT_TRUE(!itr.valid());
            } else {
                size_t postingsize;

                ASSERT_TRUE(itr.valid());
                postingsize = postings.size(itr.getData());
                ASSERT_TRUE(postingsize > 0 &&
                            postingsize == it->second.size());
                STLPostingList::iterator it3;
                STLPostingList::iterator it3b;
                STLPostingList::iterator it3e;

                PostingList::Iterator it0;

                it3b = it->second.begin();
                it3e = it->second.end();
                it0 = postings.begin(itr.getData());
                it3 = it3b;

                while (it3 != it3e) {
                    ASSERT_TRUE(it0.valid());
                    ASSERT_TRUE(*it3 == it0.getKey());
                    ++it3;
                    ++it0;
                }
                ASSERT_TRUE(!it0.valid());
            }
        }
    }
    LOG(info, "lookupRandomValues done");
}


void
AttributePostingListTest::doCompactEnumStore(Tree &tree,
                                             TreeManager &treeMgr,
                                             ValueHandle &valueHandle)
{
    LOG(info,
        "doCompactEnumStore start");

    Tree::Iterator i = tree.begin(treeMgr);

    uint32_t numBuffers = valueHandle.getNumBuffers();
    std::vector<uint32_t> toHold;

    for (uint32_t bufferId = 0; bufferId < numBuffers; ++bufferId) {
        datastore::BufferState &state = valueHandle.getBufferState(bufferId);
        if (state.isActive()) {
            toHold.push_back(bufferId);
            // Freelists already disabled due to variable sized data
        }
    }
    valueHandle.switchActiveBuffer(0, 0u);

    for (; i.valid(); ++i)
    {
        StoreIndex ov = i.getKey();
        StoreIndex nv = valueHandle.addEntry(valueHandle.getEntry(ov));

        std::atomic_thread_fence(std::memory_order_release);
        i.writeKey(nv);
    }
    typedef GenerationHandler::generation_t generation_t;
    for (std::vector<uint32_t>::const_iterator
             it = toHold.begin(), ite = toHold.end(); it != ite; ++it) {
        valueHandle.holdBuffer(*it);
    }
    generation_t generation = _handler.getCurrentGeneration();
    valueHandle.transferHoldLists(generation);
    _handler.incGeneration();
    valueHandle.trimHoldLists(_handler.getFirstUsedGeneration());

    LOG(info,
        "doCompactEnumStore done");
}


void
AttributePostingListTest::
doCompactPostingList(Tree &tree,
                     TreeManager &treeMgr,
                     PostingList &postings,
                     PostingListNodeAllocator &postingsAlloc)
{
    LOG(info,
        "doCompactPostingList start");

#if 0
    Tree::Iterator i(tree.begin(treeMgr));

    postings.performCompaction(i, capacityNeeded);
#else
    (void) tree;
    (void) treeMgr;
    (void) postings;
    (void) postingsAlloc;
#endif

    LOG(info,
        "doCompactPostingList done");
}


void
AttributePostingListTest::
bumpGeneration(Tree &tree,
               ValueHandle &valueHandle,
               PostingList &postings,
               PostingListNodeAllocator &postingsAlloc)
{
    (void) tree;
    (void) valueHandle;
    postingsAlloc.freeze();
    postingsAlloc.transferHoldLists(_handler.getCurrentGeneration());
    postings.transferHoldLists(_handler.getCurrentGeneration());
    _handler.incGeneration();
}

void
AttributePostingListTest::
removeOldGenerations(Tree &tree,
                     ValueHandle &valueHandle,
                     PostingList &postings,
                     PostingListNodeAllocator &postingsAlloc)
{
    (void) tree;
    (void) valueHandle;
    postingsAlloc.trimHoldLists(_handler.getFirstUsedGeneration());
    postings.trimHoldLists(_handler.getFirstUsedGeneration());
}

int
AttributePostingListTest::Main()
{
    TEST_INIT("postinglist_test");

    fillRandomValues(1000, 10);

    allocTree();
    insertRandomValues(*_intTree, *_intNodeAlloc, *_intKeyStore, *_intPostings,
                       _stlTree, _randomValues);
    lookupRandomValues(*_intTree, *_intNodeAlloc, *_intKeyStore, *_intPostings,
                       _stlTree, _randomValues);
    _intNodeAlloc->freeze();
    _intNodeAlloc->transferHoldLists(_handler.getCurrentGeneration());
    doCompactEnumStore(*_intTree, *_intNodeAlloc, *_intKeyStore);
    removeRandomValues(*_intTree, *_intNodeAlloc, *_intKeyStore, *_intPostings,
                       _stlTree, _randomValues);
    insertRandomValues(*_intTree, *_intNodeAlloc, *_intKeyStore, *_intPostings,
                       _stlTree, _randomValues);
    freeTree(true);

    TEST_DONE();
}

}

TEST_APPHOOK(search::AttributePostingListTest);
