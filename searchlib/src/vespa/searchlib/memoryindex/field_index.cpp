// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index.h"
#include "ordered_field_index_inserter.h"
#include "posting_iterator.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.field_index");

using search::fef::TermFieldMatchDataArray;
using search::index::DocIdAndFeatures;
using search::index::Schema;
using search::index::WordDocElementFeatures;
using search::queryeval::BooleanMatchIteratorWrapper;
using search::queryeval::FieldSpecBase;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleLeafBlueprint;
using vespalib::GenerationHandler;

namespace search::memoryindex {

namespace {

void set_interleaved_features(DocIdAndFeatures &features)
{
    // Set cheap features based on normal features.
    // TODO: Update when proper cheap features are present in memory index.
    assert(!features.elements().empty());
    const auto &element = features.elements().front();
    features.set_field_length(element.getElementLen());
    features.set_num_occs(element.getNumOccs());
}

}

using datastore::EntryRef;

template <bool interleaved_features>
FieldIndex<interleaved_features>::FieldIndex(const index::Schema& schema, uint32_t fieldId)
    : FieldIndex(schema, fieldId, index::FieldLengthInfo())
{
}

template <bool interleaved_features>
FieldIndex<interleaved_features>::FieldIndex(const index::Schema& schema, uint32_t fieldId,
                                             const index::FieldLengthInfo& info)
    : FieldIndexBase(schema, fieldId, info),
      _postingListStore()
{
    using InserterType = OrderedFieldIndexInserter<interleaved_features>;
    _inserter = std::make_unique<InserterType>(*this);
}

template <bool interleaved_features>
FieldIndex<interleaved_features>::~FieldIndex()
{
    _postingListStore.disableFreeLists();
    _postingListStore.disableElemHoldList();
    _dict.disableFreeLists();
    _dict.disableElemHoldList();
    // XXX: Kludge
    for (DictionaryTree::Iterator it = _dict.begin();
         it.valid(); ++it) {
        EntryRef pidx(it.getData());
        if (pidx.valid()) {
            _postingListStore.clear(pidx);
            // Before updating ref
            std::atomic_thread_fence(std::memory_order_release);
            it.writeData(EntryRef().ref());
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

template <bool interleaved_features>
typename FieldIndex<interleaved_features>::PostingList::Iterator
FieldIndex<interleaved_features>::find(const vespalib::stringref word) const
{
    DictionaryTree::Iterator itr = _dict.find(WordKey(EntryRef()), KeyComp(_wordStore, word));
    if (itr.valid()) {
        return _postingListStore.begin(EntryRef(itr.getData()));
    }
    return typename PostingList::Iterator();
}

template <bool interleaved_features>
typename FieldIndex<interleaved_features>::PostingList::ConstIterator
FieldIndex<interleaved_features>::findFrozen(const vespalib::stringref word) const
{
    auto itr = _dict.getFrozenView().find(WordKey(EntryRef()), KeyComp(_wordStore, word));
    if (itr.valid()) {
        return _postingListStore.beginFrozen(EntryRef(itr.getData()));
    }
    return typename PostingList::Iterator();
}

template <bool interleaved_features>
void
FieldIndex<interleaved_features>::compactFeatures()
{
    std::vector<uint32_t> toHold;

    toHold = _featureStore.startCompact();
    auto itr = _dict.begin();
    uint32_t packedIndex = _fieldId;
    for (; itr.valid(); ++itr) {
        typename PostingListStore::RefType pidx(EntryRef(itr.getData()));
        if (!pidx.valid()) {
            continue;
        }
        uint32_t clusterSize = _postingListStore.getClusterSize(pidx);
        if (clusterSize == 0) {
            const PostingList *tree = _postingListStore.getTreeEntry(pidx);
            auto pitr = tree->begin(_postingListStore.getAllocator());
            for (; pitr.valid(); ++pitr) {
                const PostingListEntryType& posting_entry(pitr.getData());

                // Filter on which buffers to move features from when
                // performing incremental compaction.

                EntryRef newFeatures = _featureStore.moveFeatures(packedIndex, posting_entry.get_features());

                // Features must be written before reference is updated.
                std::atomic_thread_fence(std::memory_order_release);

                // Reference the moved data
                posting_entry.update_features(newFeatures);
            }
        } else {
            const PostingListKeyDataType *shortArray = _postingListStore.getKeyDataEntry(pidx, clusterSize);
            const PostingListKeyDataType *ite = shortArray + clusterSize;
            for (const PostingListKeyDataType *it = shortArray; it < ite; ++it) {
                const PostingListEntryType& posting_entry(it->getData());

                // Filter on which buffers to move features from when
                // performing incremental compaction.

                EntryRef newFeatures = _featureStore.moveFeatures(packedIndex, posting_entry.get_features());

                // Features must be written before reference is updated.
                std::atomic_thread_fence(std::memory_order_release);

                // Reference the moved data
                posting_entry.update_features(newFeatures);
            }
        }
    }
    using generation_t = GenerationHandler::generation_t;
    _featureStore.finishCompact(toHold);
    generation_t generation = _generationHandler.getCurrentGeneration();
    _featureStore.transferHoldLists(generation);
}

template <bool interleaved_features>
void
FieldIndex<interleaved_features>::dump(search::index::IndexBuilder & indexBuilder)
{
    vespalib::stringref word;
    FeatureStore::DecodeContextCooked decoder(nullptr);
    DocIdAndFeatures features;
    vespalib::Array<uint32_t> wordMap(_numUniqueWords + 1, 0);
    _featureStore.setupForField(_fieldId, decoder);
    for (auto itr = _dict.begin(); itr.valid(); ++itr) {
        const WordKey & wk = itr.getKey();
        typename PostingListStore::RefType plist(EntryRef(itr.getData()));
        word = _wordStore.getWord(wk._wordRef);
        if (!plist.valid()) {
            continue;
        }
        indexBuilder.startWord(word);
        uint32_t clusterSize = _postingListStore.getClusterSize(plist);
        if (clusterSize == 0) {
            const PostingList *tree = _postingListStore.getTreeEntry(plist);
            auto pitr = tree->begin(_postingListStore.getAllocator());
            assert(pitr.valid());
            for (; pitr.valid(); ++pitr) {
                uint32_t docId = pitr.getKey();
                EntryRef featureRef(pitr.getData().get_features());
                _featureStore.setupForReadFeatures(featureRef, decoder);
                decoder.readFeatures(features);
                features.set_doc_id(docId);
                set_interleaved_features(features);
                indexBuilder.add_document(features);
            }
        } else {
            const PostingListKeyDataType *kd =
                _postingListStore.getKeyDataEntry(plist, clusterSize);
            const PostingListKeyDataType *kde = kd + clusterSize;
            for (; kd != kde; ++kd) {
                uint32_t docId = kd->_key;
                EntryRef featureRef(kd->getData().get_features());
                _featureStore.setupForReadFeatures(featureRef, decoder);
                decoder.readFeatures(features);
                features.set_doc_id(docId);
                set_interleaved_features(features);
                indexBuilder.add_document(features);
            }
        }
        indexBuilder.endWord();
    }
}

template <bool interleaved_features>
vespalib::MemoryUsage
FieldIndex<interleaved_features>::getMemoryUsage() const
{
    vespalib::MemoryUsage usage;
    usage.merge(_wordStore.getMemoryUsage());
    usage.merge(_dict.getMemoryUsage());
    usage.merge(_postingListStore.getMemoryUsage());
    usage.merge(_featureStore.getMemoryUsage());
    usage.merge(_remover.getStore().getMemoryUsage());
    return usage;
}

namespace {

template <bool interleaved_features>
class MemoryTermBlueprint : public SimpleLeafBlueprint {
private:
    using FieldIndexType = FieldIndex<interleaved_features>;
    using PostingListIteratorType = typename FieldIndexType::PostingList::ConstIterator;
    GenerationHandler::Guard _guard;
    PostingListIteratorType _posting_itr;
    const FeatureStore& _feature_store;
    const uint32_t _field_id;
    const bool _use_bit_vector;

public:
    MemoryTermBlueprint(GenerationHandler::Guard&& guard,
                        PostingListIteratorType posting_itr,
                        const FeatureStore& feature_store,
                        const FieldSpecBase& field,
                        uint32_t field_id,
                        bool use_bit_vector)
        : SimpleLeafBlueprint(field),
          _guard(),
          _posting_itr(posting_itr),
          _feature_store(feature_store),
          _field_id(field_id),
          _use_bit_vector(use_bit_vector)
    {
        _guard = std::move(guard);
        HitEstimate estimate(_posting_itr.size(), !_posting_itr.valid());
        setEstimate(estimate);
    }

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray& tfmda, bool) const override {
        using PostingIteratorType = PostingIterator<interleaved_features>;
        auto result = std::make_unique<PostingIteratorType>(_posting_itr, _feature_store, _field_id, tfmda);
        if (_use_bit_vector) {
            LOG(debug, "Return BooleanMatchIteratorWrapper: field_id(%u), doc_count(%zu)",
                _field_id, _posting_itr.size());
            return std::make_unique<BooleanMatchIteratorWrapper>(std::move(result), tfmda);
        }
        LOG(debug, "Return PostingIterator: field_id(%u), doc_count(%zu)",
            _field_id, _posting_itr.size());
        return result;
    }
};

}

template <bool interleaved_features>
std::unique_ptr<queryeval::SimpleLeafBlueprint>
FieldIndex<interleaved_features>::make_term_blueprint(const vespalib::string& term,
                                                      const queryeval::FieldSpecBase& field,
                                                      uint32_t field_id)
{
    auto guard = takeGenerationGuard();
    auto posting_itr = findFrozen(term);
    bool use_bit_vector = field.isFilter();
    return std::make_unique<MemoryTermBlueprint<interleaved_features>>
            (std::move(guard), posting_itr, getFeatureStore(), field, field_id, use_bit_vector);
}

template
class FieldIndex<false>;

}

namespace search::btree {

template
class BTreeNodeDataWrap<memoryindex::FieldIndexBase::WordKey, BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeT<memoryindex::FieldIndexBase::WordKey, BTreeDefaultTraits::INTERNAL_SLOTS>;

#if 0
template
class BTreeNodeT<memoryindex::FieldIndexBase::WordKey,
                 BTreeDefaultTraits::LEAF_SLOTS>;
#endif

template
class BTreeNodeTT<memoryindex::FieldIndexBase::WordKey,
                  datastore::EntryRef,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::INTERNAL_SLOTS>;

template
class BTreeNodeTT<memoryindex::FieldIndexBase::WordKey,
                  memoryindex::FieldIndexBase::PostingListPtr,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeInternalNode<memoryindex::FieldIndexBase::WordKey,
                        search::btree::NoAggregated,
                        BTreeDefaultTraits::INTERNAL_SLOTS>;

template
class BTreeLeafNode<memoryindex::FieldIndexBase::WordKey,
                    memoryindex::FieldIndexBase::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeStore<memoryindex::FieldIndexBase::WordKey,
                     memoryindex::FieldIndexBase::PostingListPtr,
                     search::btree::NoAggregated,
                     BTreeDefaultTraits::INTERNAL_SLOTS,
                     BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeIterator<memoryindex::FieldIndexBase::WordKey,
                    memoryindex::FieldIndexBase::PostingListPtr,
                    search::btree::NoAggregated,
                    const memoryindex::FieldIndexBase::KeyComp,
                    BTreeDefaultTraits>;

template
class BTree<memoryindex::FieldIndexBase::WordKey,
            memoryindex::FieldIndexBase::PostingListPtr,
            search::btree::NoAggregated,
            const memoryindex::FieldIndexBase::KeyComp,
            BTreeDefaultTraits>;

template
class BTreeRoot<memoryindex::FieldIndexBase::WordKey,
                memoryindex::FieldIndexBase::PostingListPtr,
                search::btree::NoAggregated,
                const memoryindex::FieldIndexBase::KeyComp,
                BTreeDefaultTraits>;

template
class BTreeRootBase<memoryindex::FieldIndexBase::WordKey,
                    memoryindex::FieldIndexBase::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::INTERNAL_SLOTS,
                    BTreeDefaultTraits::LEAF_SLOTS>;

template
class BTreeNodeAllocator<memoryindex::FieldIndexBase::WordKey,
                         memoryindex::FieldIndexBase::PostingListPtr,
                         search::btree::NoAggregated,
                         BTreeDefaultTraits::INTERNAL_SLOTS,
                         BTreeDefaultTraits::LEAF_SLOTS>;

}
