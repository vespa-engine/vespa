// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"
#include "fpfactory.h"
#include <vespa/searchlib/memoryindex/feature_store.h>
#include <vespa/searchlib/memoryindex/field_index.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search::fakedata {

class FakeMemTreeOccMgr : public FakeWord::RandomizedWriter {
public:
    // TODO: Create implementation for "interleaved features" posting list as well.
    using Tree = memoryindex::FieldIndex<false>::PostingList;
    using PostingListEntryType = memoryindex::FieldIndex<false>::PostingListEntryType;
    using NodeAllocator = Tree::NodeAllocatorType;
    using FeatureStore = memoryindex::FeatureStore;
    using EntryRef = vespalib::datastore::EntryRef;
    using Schema = index::Schema;
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;

    vespalib::GenerationHandler _generationHandler;
    NodeAllocator _allocator;

    std::map<const FakeWord *, uint32_t> _fw2WordIdx;
    class PostingIdx
    {
    public:
        Tree _tree;
        Tree::Iterator _iterator;

        PostingIdx(NodeAllocator &allocator)
            : _tree(),
              _iterator(_tree.getRoot(), allocator)
        {}

        void clear() {
            _tree.clear(_iterator.getAllocator());
            _iterator = _tree.begin(_iterator.getAllocator());
        }
    };

    class PendingOp
    {
        uint32_t _wordIdx;
        uint32_t _docId;
        EntryRef _features;
        bool     _removal;
        uint32_t _seq;

    public:
        PendingOp(uint32_t wordIdx, uint32_t docId)
            : _wordIdx(wordIdx),
              _docId(docId),
              _features(),
              _removal(true),
              _seq(0)
        {}

        PendingOp(uint32_t wordIdx, uint32_t docId, EntryRef features)
            : _wordIdx(wordIdx),
              _docId(docId),
              _features(features),
              _removal(false),
              _seq(0)
        {}

        void setSeq(uint32_t seq) { _seq = seq; }
        uint32_t getWordIdx() const { return _wordIdx; }
        uint32_t getDocId() const { return _docId; }
        EntryRef getFeatureRef() const { return _features; }
        bool getRemove() const { return _removal; }

        bool operator<(const PendingOp &rhs) const {
            if (_wordIdx != rhs._wordIdx)
                return _wordIdx < rhs._wordIdx;
            if (_docId != rhs.getDocId())
                return _docId < rhs.getDocId();
            return _seq < rhs._seq;
        } 
    };
 
    std::vector<std::shared_ptr<PostingIdx> > _postingIdxs;
    std::vector<const FakeWord *> _fakeWords;
    std::vector<uint64_t> _featureSizes;
    std::vector<PendingOp> _unflushed;

    FeatureStore _featureStore;

    FakeMemTreeOccMgr(const Schema &schema);
    ~FakeMemTreeOccMgr();

    void freeze();
    void assign_generation();
    void incGeneration();
    void reclaim_memory();
    void sync();
    void add(uint32_t wordIdx, index::DocIdAndFeatures &features) override;
    void remove(uint32_t wordIdx, uint32_t docId) override;
    void sortUnflushed();
    void flush();
    void compactTrees();
    void finalize();
};


class FakeMemTreeOccFactory : public FPFactory
{
public:
    typedef FakeMemTreeOccMgr::Tree Tree;
    typedef FakeMemTreeOccMgr::NodeAllocator NodeAllocator;
    typedef index::Schema Schema;

    FakeMemTreeOccMgr _mgr;

    FakeMemTreeOccFactory(const Schema &schema);
    ~FakeMemTreeOccFactory();

    FakePosting::SP make(const FakeWord &fw) override;
    void setup(const std::vector<const FakeWord *> &fws) override;
};

class FakeMemTreeOcc2Factory : public FakeMemTreeOccFactory
{
public:
    FakeMemTreeOcc2Factory(const Schema &schema);
    ~FakeMemTreeOcc2Factory();

    FakePosting::SP make(const FakeWord &fw) override;
    void setup(const std::vector<const FakeWord *> &fws) override;
};

/*
 * Updateable memory tree format.
 */
class FakeMemTreeOcc : public FakePosting
{
public:
    typedef FakeMemTreeOccMgr::Tree Tree;
    typedef FakeMemTreeOccMgr::NodeAllocator NodeAllocator;
    typedef FakeMemTreeOccMgr::PosOccFieldsParams PosOccFieldsParams;


private:
    NodeAllocator &_allocator;
    Tree &_tree;
    const PosOccFieldsParams &_fieldsParams;
    uint32_t _packedIndex;
    uint64_t _featureBitSize;
    const FakeMemTreeOccMgr &_mgr;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
public:
    FakeMemTreeOcc(const FakeWord &fakeword,
                   NodeAllocator &allocator,
                   Tree &tree,
                   uint64_t featureBitSize,
                   const FakeMemTreeOccMgr &mgr);

    FakeMemTreeOcc(const FakeWord &fakeword,
                   NodeAllocator &allocator,
                   Tree &tree,
                   uint64_t featureBitSize,
                   const FakeMemTreeOccMgr &mgr,
                   const char *suffix);

    ~FakeMemTreeOcc();

    static void forceLink();
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    int lowLevelSinglePostingScan() const override;
    int lowLevelSinglePostingScanUnpack() const override;
    int lowLevelAndPairPostingScan(const FakePosting &rhs) const override;
    int lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const override;
    queryeval::SearchIterator *createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

}
