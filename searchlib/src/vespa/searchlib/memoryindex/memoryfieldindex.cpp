// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryfieldindex.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btree.hpp>
#include "ordereddocumentinserter.h"
#include <vespa/vespalib/util/array.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.memory_field_index");

namespace search {

using index::DocIdAndFeatures;
using index::WordDocElementFeatures;
using index::Schema;

namespace memoryindex {

vespalib::asciistream &
operator<<(vespalib::asciistream & os, const MemoryFieldIndex::WordKey & rhs)
{
    os << "wr(" << rhs._wordRef.ref() << ")";
    return os;
}

MemoryFieldIndex::MemoryFieldIndex(const Schema & schema, uint32_t fieldId)
    : _wordStore(),
      _numUniqueWords(0),
      _generationHandler(),
      _dict(),
      _postingListStore(),
      _featureStore(schema),
      _fieldId(fieldId),
      _remover(_wordStore),
      _inserter(std::make_unique<OrderedDocumentInserter>(*this))
{ }

MemoryFieldIndex::~MemoryFieldIndex()
{
    _postingListStore.disableFreeLists();
    _postingListStore.disableElemHoldList();
    _dict.disableFreeLists();
    _dict.disableElemHoldList();
    // XXX: Kludge
    for (DictionaryTree::Iterator it = _dict.begin();
         it.valid(); ++it) {
        datastore::EntryRef pidx(it.getData());
        if (pidx.valid()) {
            _postingListStore.clear(pidx);
            // Before updating ref
            std::atomic_thread_fence(std::memory_order_release);
            it.writeData(datastore::EntryRef().ref());
        }
    }
    _postingListStore.clearBuilder();
    freeze();   // Flush all pending posting list tree freezes
    transferHoldLists();
    _dict.clear();  // Clear dictionary
    freeze();   // Flush pending freeze for dictionary tree.
    transferHoldLists();
    incGeneration();
    trimHoldLists();
}

MemoryFieldIndex::PostingList::Iterator
MemoryFieldIndex::find(const vespalib::stringref word) const
{
    DictionaryTree::Iterator itr =
        _dict.find(WordKey(datastore::EntryRef()),
                  KeyComp(_wordStore, word));
    if (itr.valid()) {
        return _postingListStore.begin(itr.getData());
    }
    return PostingList::Iterator();
}

MemoryFieldIndex::PostingList::ConstIterator
MemoryFieldIndex::findFrozen(const vespalib::stringref word) const
{
    DictionaryTree::ConstIterator itr =
        _dict.getFrozenView().find(WordKey(datastore::EntryRef()),
                                   KeyComp(_wordStore, word));
    if (itr.valid()) {
        return _postingListStore.beginFrozen(itr.getData());
    }
    return PostingList::Iterator();
}


void
MemoryFieldIndex::compactFeatures()
{
    std::vector<uint32_t> toHold;

    toHold = _featureStore.startCompact();
    DictionaryTree::Iterator itr(_dict.begin());
    uint32_t packedIndex = _fieldId;
    for (; itr.valid(); ++itr) {
        PostingListStore::RefType pidx(itr.getData());
        if (!pidx.valid())
            continue;
        uint32_t clusterSize = _postingListStore.getClusterSize(pidx);
        if (clusterSize == 0) {
            const PostingList *tree =
                _postingListStore.getTreeEntry(pidx);
            PostingList::Iterator
                it(tree->begin(_postingListStore.getAllocator()));
            for (; it.valid(); ++it) {
                datastore::EntryRef oldFeatures = it.getData();

                // Filter on which buffers to move features from when
                // performing incremental compaction.

                datastore::EntryRef newFeatures = _featureStore.moveFeatures(packedIndex, oldFeatures);

                // Features must be written before reference is updated.
                std::atomic_thread_fence(std::memory_order_release);

                // Ugly, ugly due to const_cast in iterator
                it.writeData(newFeatures.ref());
            }
        } else {
            const PostingListKeyDataType *shortArray =
                _postingListStore.getKeyDataEntry(pidx, clusterSize);
            const PostingListKeyDataType *ite = shortArray + clusterSize;
            for (const PostingListKeyDataType *it = shortArray; it < ite;
                 ++it) {
                datastore::EntryRef oldFeatures = it->getData();

                // Filter on which buffers to move features from when
                // performing incremental compaction.

                datastore::EntryRef newFeatures = _featureStore.moveFeatures(packedIndex, oldFeatures);

                // Features must be written before reference is updated.
                std::atomic_thread_fence(std::memory_order_release);

                // Ugly, ugly due to const_cast, but new data is
                // semantically equal to old data
                const_cast<PostingListKeyDataType *>(it)->
                    setData(newFeatures.ref());
            }
        }
    }
    typedef GenerationHandler::generation_t generation_t;
    _featureStore.finishCompact(toHold);
    generation_t generation = _generationHandler.getCurrentGeneration();
    _featureStore.transferHoldLists(generation);
}

void
MemoryFieldIndex::dump(search::index::IndexBuilder & indexBuilder)
{
    vespalib::stringref word;
    FeatureStore::DecodeContextCooked decoder(NULL);
    DocIdAndFeatures features;
    vespalib::Array<uint32_t> wordMap(_numUniqueWords + 1, 0);
    _featureStore.setupForField(_fieldId, decoder);
    for (DictionaryTree::Iterator itr = _dict.begin(); itr.valid(); ++itr) {
        const WordKey & wk = itr.getKey();
        PostingListStore::RefType plist(itr.getData());
        word = _wordStore.getWord(wk._wordRef);
        if (!plist.valid())
            continue;
        indexBuilder.startWord(word);
        uint32_t clusterSize = _postingListStore.getClusterSize(plist);
        if (clusterSize == 0) {
            const PostingList *tree =
                _postingListStore.getTreeEntry(plist);
            PostingList::Iterator pitr = tree->begin(_postingListStore.getAllocator());
            assert(pitr.valid());
            for (; pitr.valid(); ++pitr) {
                uint32_t docId = pitr.getKey();
                datastore::EntryRef featureRef = pitr.getData();
                indexBuilder.startDocument(docId);
                _featureStore.setupForReadFeatures(featureRef, decoder);
                decoder.readFeatures(features);
                size_t poff = 0;
                uint32_t wpIdx = 0u;
                size_t numElements = features._elements.size();
                for (size_t i = 0; i < numElements; ++i) {
                    const WordDocElementFeatures & fef = features._elements[i];
                    indexBuilder.startElement(fef.getElementId(), fef.getWeight(), fef.getElementLen());
                    for (size_t j = 0; j < fef.getNumOccs(); ++j, ++wpIdx) {
                        assert(wpIdx == poff + j);
                        indexBuilder.addOcc(features._wordPositions[poff + j]);
                    }
                    poff += fef.getNumOccs();
                    indexBuilder.endElement();
                }
                indexBuilder.endDocument();
            }
        } else {
            const PostingListKeyDataType *kd =
                _postingListStore.getKeyDataEntry(plist, clusterSize);
            const PostingListKeyDataType *kde = kd + clusterSize;
            for (; kd != kde; ++kd) {
                uint32_t docId = kd->_key;
                datastore::EntryRef featureRef = kd->getData();
                indexBuilder.startDocument(docId);
                _featureStore.setupForReadFeatures(featureRef, decoder);
                decoder.readFeatures(features);
                size_t poff = 0;
                uint32_t wpIdx = 0u;
                size_t numElements = features._elements.size();
                for (size_t i = 0; i < numElements; ++i) {
                    const WordDocElementFeatures & fef = features._elements[i];
                    indexBuilder.startElement(fef.getElementId(), fef.getWeight(), fef.getElementLen());
                    for (size_t j = 0; j < fef.getNumOccs(); ++j, ++wpIdx) {
                        assert(wpIdx == poff + j);
                        indexBuilder.addOcc(features.
                                            _wordPositions[poff + j]);
                    }
                    poff += fef.getNumOccs();
                    indexBuilder.endElement();
                }
                indexBuilder.endDocument();
            }
        }
        indexBuilder.endWord();
    }
}


MemoryUsage
MemoryFieldIndex::getMemoryUsage() const
{
    MemoryUsage usage;
    usage.merge(_wordStore.getMemoryUsage());
    usage.merge(_dict.getMemoryUsage());
    usage.merge(_postingListStore.getMemoryUsage());
    usage.merge(_featureStore.getMemoryUsage());
    usage.merge(_remover.getStore().getMemoryUsage());
    return usage;
}


} // namespace search::memoryindex

namespace btree {

template
class BTreeNodeDataWrap<memoryindex::MemoryFieldIndex::WordKey,
                        BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeT<memoryindex::MemoryFieldIndex::WordKey,
                 BTreeDefaultTraits::INTERNAL_SLOTS>;

#if 0
template
class BTreeNodeT<memoryindex::MemoryFieldIndex::WordKey,
                 BTreeDefaultTraits::LEAF_SLOTS>;
#endif

template
class BTreeNodeTT<memoryindex::MemoryFieldIndex::WordKey,
                  datastore::EntryRef,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::INTERNAL_SLOTS>;

template
class BTreeNodeTT<memoryindex::MemoryFieldIndex::WordKey,
                  memoryindex::MemoryFieldIndex::PostingListPtr,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeInternalNode<memoryindex::MemoryFieldIndex::WordKey,
                        search::btree::NoAggregated,
                        BTreeDefaultTraits::INTERNAL_SLOTS>;

template
class BTreeLeafNode<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeStore<memoryindex::MemoryFieldIndex::WordKey,
                     memoryindex::MemoryFieldIndex::PostingListPtr,
                     search::btree::NoAggregated,
                     BTreeDefaultTraits::INTERNAL_SLOTS,
                     BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeIterator<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    const memoryindex::MemoryFieldIndex::KeyComp,
                    BTreeDefaultTraits>;

template
class BTree<memoryindex::MemoryFieldIndex::WordKey,
                   memoryindex::MemoryFieldIndex::PostingListPtr,
            search::btree::NoAggregated,
                   const memoryindex::MemoryFieldIndex::KeyComp,
                   BTreeDefaultTraits>;

template
class BTreeRoot<memoryindex::MemoryFieldIndex::WordKey,
                memoryindex::MemoryFieldIndex::PostingListPtr,
                search::btree::NoAggregated,
                const memoryindex::MemoryFieldIndex::KeyComp,
                BTreeDefaultTraits>;

template
class BTreeRootBase<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::INTERNAL_SLOTS,
                    BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeAllocator<memoryindex::MemoryFieldIndex::WordKey,
                         memoryindex::MemoryFieldIndex::PostingListPtr,
                         search::btree::NoAggregated,
                         BTreeDefaultTraits::INTERNAL_SLOTS,
                         BTreeDefaultTraits::LEAF_SLOTS>;


} // namespace btree
} // namespace search
