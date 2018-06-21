// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakememtreeocc.h"
#include "fpfactory.h"
#include <vespa/searchlib/queryeval/iterators.h>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/memoryindex/postingiterator.h>
#include <vespa/searchlib/util/postingpriorityqueue.h>

#include <vespa/log/log.h>
LOG_SETUP(".fakememtreeocc");

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

namespace search::fakedata {

static FPFactoryInit
init(std::make_pair("MemTreeOcc",
                    makeFPFactory<FakeMemTreeOccFactory>));

static FPFactoryInit
init2(std::make_pair("MemTreeOcc2",
                     makeFPFactory<FakeMemTreeOcc2Factory>));

FakeMemTreeOcc::FakeMemTreeOcc(const FakeWord &fw,
                               NodeAllocator &allocator,
                               Tree &tree,
                               uint64_t featureBitSize,
                               const FakeMemTreeOccMgr &mgr)
    : FakePosting(fw.getName() + ".memtreeocc"),
      _allocator(allocator),
      _tree(tree),
      _fieldsParams(fw.getFieldsParams()),
      _packedIndex(fw.getPackedIndex()),
      _featureBitSize(featureBitSize),
      _mgr(mgr),
      _docIdLimit(0),
      _hitDocs(0)
{
    _docIdLimit = fw._docIdLimit;
    _hitDocs = fw._postings.size();
}


FakeMemTreeOcc::FakeMemTreeOcc(const FakeWord &fw,
                               NodeAllocator &allocator,
                               Tree &tree,
                               uint64_t featureBitSize,
                               const FakeMemTreeOccMgr &mgr,
                               const char *suffix)
    : FakePosting(fw.getName() + suffix),
      _allocator(allocator),
      _tree(tree),
      _fieldsParams(fw.getFieldsParams()),
      _packedIndex(fw.getPackedIndex()),
      _featureBitSize(featureBitSize),
      _mgr(mgr),
      _docIdLimit(0),
      _hitDocs(0)
{
    _docIdLimit = fw._docIdLimit;
    _hitDocs = fw._postings.size();
}


FakeMemTreeOcc::~FakeMemTreeOcc()
{
}


void
FakeMemTreeOcc::forceLink()
{
}


size_t
FakeMemTreeOcc::bitSize() const
{
    return _tree.bitSize(_allocator) + _featureBitSize;
}


bool
FakeMemTreeOcc::hasWordPositions() const
{
    return true;
}


int
FakeMemTreeOcc::lowLevelSinglePostingScan() const
{
    return 0;
}


int
FakeMemTreeOcc::lowLevelSinglePostingScanUnpack() const
{
    return 0;
}


int
FakeMemTreeOcc::
lowLevelAndPairPostingScan(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


int
FakeMemTreeOcc::
lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


search::queryeval::SearchIterator *
FakeMemTreeOcc::
createIterator(const fef::TermFieldMatchDataArray &matchData) const
{
    return new search::memoryindex::PostingIterator(_tree.begin(_allocator),
                                                    _mgr._featureStore,
                                                    _packedIndex,
                                                    matchData);
}


FakeMemTreeOccMgr::FakeMemTreeOccMgr(const Schema &schema)
    : _generationHandler(),
      _allocator(),
      _fw2WordIdx(),
      _postingIdxs(),
      _fakeWords(),
      _featureSizes(),
      _featureStore(schema)
{
}


FakeMemTreeOccMgr::~FakeMemTreeOccMgr()
{
    std::vector<std::shared_ptr<PostingIdx> >::iterator
        it(_postingIdxs.begin());
    std::vector<std::shared_ptr<PostingIdx> >::iterator
        ite(_postingIdxs.end());

    for (; it != ite; ++it) {
        (*it)->clear();
    }
    sync();
}


void
FakeMemTreeOccMgr::freeze()
{
    _allocator.freeze();
}


void
FakeMemTreeOccMgr::transferHoldLists()
{
    _allocator.transferHoldLists(_generationHandler.getCurrentGeneration());
}

void
FakeMemTreeOccMgr::incGeneration()
{
    _generationHandler.incGeneration();
}


void
FakeMemTreeOccMgr::trimHoldLists()
{
    _allocator.trimHoldLists(_generationHandler.getFirstUsedGeneration());
}


void
FakeMemTreeOccMgr::sync()
{
    freeze();
    transferHoldLists();
    incGeneration();
    trimHoldLists();
}


void
FakeMemTreeOccMgr::add(uint32_t wordIdx, index::DocIdAndFeatures &features)
{
    typedef FeatureStore::RefType RefType;

    const FakeWord *fw = _fakeWords[wordIdx];

    std::pair<EntryRef, uint64_t> r =
        _featureStore.addFeatures(fw->getPackedIndex(), features);

    _featureSizes[wordIdx] += RefType::align((r.second + 7) / 8) * 8;

    _unflushed.push_back(PendingOp(wordIdx, features._docId, r.first));

    if (_unflushed.size() >= 10000)
        flush();
}


void
FakeMemTreeOccMgr::remove(uint32_t wordIdx, uint32_t docId)
{
    _unflushed.push_back(PendingOp(wordIdx, docId));

    if (_unflushed.size() >= 10000)
        flush();
}


void
FakeMemTreeOccMgr::sortUnflushed()
{
    typedef std::vector<PendingOp>::iterator I;
    uint32_t seq = 0;
    for (I i(_unflushed.begin()), ie(_unflushed.end()); i != ie; ++i) {
        i->setSeq(++seq);
    }
    std::sort(_unflushed.begin(), _unflushed.end());
}


void
FakeMemTreeOccMgr::flush()
{
    typedef FeatureStore::RefType RefType;
    typedef std::vector<PendingOp>::iterator I;

    if (_unflushed.empty())
        return;

    uint32_t lastWord = std::numeric_limits<uint32_t>::max();
    sortUnflushed();
    for (I i(_unflushed.begin()), ie(_unflushed.end()); i != ie; ++i) {
        uint32_t wordIdx = i->getWordIdx();
        uint32_t docId = i->getDocId();
        PostingIdx &pidx(*_postingIdxs[wordIdx].get());
        Tree &tree = pidx._tree;
        Tree::Iterator &itr = pidx._iterator;
        const FakeWord *fw = _fakeWords[wordIdx];
        if (wordIdx != lastWord)
            itr.lower_bound(docId);
        else if (itr.valid() && itr.getKey() < docId) {
            itr.linearSeek(docId);
        }
        lastWord = wordIdx;
        if (i->getRemove()) {
            if (itr.valid() && itr.getKey() == docId) {
                uint64_t bits = _featureStore.bitSize(fw->getPackedIndex(),
                                                      itr.getData());
                _featureSizes[wordIdx] -= RefType::align((bits + 7) / 8) * 8;
                tree.remove(itr);
            }
        } else {
            if (!itr.valid() || docId < itr.getKey()) {
                tree.insert(itr, docId, i->getFeatureRef().ref());
            }
        }
    }
    _unflushed.clear();
    sync();
}

void
FakeMemTreeOccMgr::compactTrees()
{
    // compact full trees by calling incremental compaction methods in a loop

    std::vector<uint32_t> toHold = _allocator.startCompact();
    for (uint32_t wordIdx = 0; wordIdx < _postingIdxs.size(); ++wordIdx) {
        PostingIdx &pidx(*_postingIdxs[wordIdx].get());
        Tree &tree = pidx._tree;
        Tree::Iterator &itr = pidx._iterator;
        itr.begin();
        tree.setRoot(itr.moveFirstLeafNode(tree.getRoot()), _allocator);
        while (itr.valid()) {
            itr.moveNextLeafNode();
        }
    }
    _allocator.finishCompact(toHold);
    sync();
}

void
FakeMemTreeOccMgr::finalize()
{
    flush();
}


FakeMemTreeOccFactory::FakeMemTreeOccFactory(const Schema &schema)
    : _mgr(schema)
{
}


FakeMemTreeOccFactory::~FakeMemTreeOccFactory()
{
}


FakePosting::SP
FakeMemTreeOccFactory::make(const FakeWord &fw)
{
    std::map<const FakeWord *, uint32_t>::const_iterator
        i(_mgr._fw2WordIdx.find(&fw));

    if (i == _mgr._fw2WordIdx.end())
        LOG_ABORT("should not be reached");

    uint32_t wordIdx = i->second;

    assert(_mgr._postingIdxs.size() > wordIdx);

    return FakePosting::SP(new FakeMemTreeOcc(fw, _mgr._allocator,
                                   _mgr._postingIdxs[wordIdx]->_tree,
                                   _mgr._featureSizes[wordIdx],
                                   _mgr));
}


void
FakeMemTreeOccFactory::setup(const std::vector<const FakeWord *> &fws)
{
    typedef FakeMemTreeOccMgr::PostingIdx PostingIdx;
    std::vector<FakeWord::RandomizedReader> r;
    uint32_t wordIdx = 0;
    std::vector<const FakeWord *>::const_iterator fwi(fws.begin());
    std::vector<const FakeWord *>::const_iterator fwe(fws.end());
    while (fwi != fwe) {
        _mgr._fakeWords.push_back(*fwi);
        _mgr._featureSizes.push_back(0);
        _mgr._fw2WordIdx[*fwi] = wordIdx;
        _mgr._postingIdxs.push_back(
                std::shared_ptr<PostingIdx>
                (new PostingIdx(_mgr._allocator)));
        r.push_back(FakeWord::RandomizedReader());
        r.back().setup(*fwi, wordIdx);
        ++fwi;
        ++wordIdx;
    }

    PostingPriorityQueue<FakeWord::RandomizedReader> heap;
    std::vector<FakeWord::RandomizedReader>::iterator i(r.begin());
    std::vector<FakeWord::RandomizedReader>::iterator ie(r.end());
    while (i != ie) {
        i->read();
        if (i->isValid())
            heap.initialAdd(&*i);
#if 0
        heap.merge(_mgr, 4);
#endif
        ++i;
    }
    heap.merge(_mgr, 4);
    assert(heap.empty());
    _mgr.finalize();
}


FakeMemTreeOcc2Factory::FakeMemTreeOcc2Factory(const Schema &schema)
    : FakeMemTreeOccFactory(schema)
{
}


FakeMemTreeOcc2Factory::~FakeMemTreeOcc2Factory()
{
}


FakePosting::SP
FakeMemTreeOcc2Factory::make(const FakeWord &fw)
{
    std::map<const FakeWord *, uint32_t>::const_iterator
        i(_mgr._fw2WordIdx.find(&fw));

    if (i == _mgr._fw2WordIdx.end())
        LOG_ABORT("should not be reached");

    uint32_t wordIdx = i->second;

    assert(_mgr._postingIdxs.size() > wordIdx);

    return FakePosting::SP(new FakeMemTreeOcc(fw, _mgr._allocator,
                                   _mgr._postingIdxs[wordIdx]->_tree,
                                   _mgr._featureSizes[wordIdx],
                                   _mgr,
                                   ".memtreeocc2"));
}


void
FakeMemTreeOcc2Factory::setup(const std::vector<const FakeWord *> &fws)
{
    FakeMemTreeOccFactory::setup(fws);
    LOG(info, "start compacting trees");
    _mgr.compactTrees();
    LOG(info, "done compacting trees");
}

}
