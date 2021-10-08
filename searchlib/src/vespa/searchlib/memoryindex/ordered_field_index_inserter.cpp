// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_field_index_insert_listener.h"
#include "ordered_field_index_inserter.h"

#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btree.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.ordered_document_inserter");

namespace search::memoryindex {

namespace {

const vespalib::string emptyWord = "";

uint16_t cap_u16(uint32_t val) { return std::min(val, static_cast<uint32_t>(std::numeric_limits<uint16_t>::max())); }

}

template <bool interleaved_features>
OrderedFieldIndexInserter<interleaved_features>::OrderedFieldIndexInserter(FieldIndexType& fieldIndex)
    : _word(),
      _prevDocId(noDocId),
      _prevAdd(false),
      _fieldIndex(fieldIndex),
      _dItr(_fieldIndex.getDictionaryTree().begin()),
      _listener(_fieldIndex.getDocumentRemover()),
      _removes(),
      _adds()
{
}

template <bool interleaved_features>
OrderedFieldIndexInserter<interleaved_features>::~OrderedFieldIndexInserter() = default;

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::flushWord()
{
    if (_removes.empty() && _adds.empty()) {
        return;
    }
    //XXX: Feature store leak, removed features not marked dead
    PostingListStore &postingListStore(_fieldIndex.getPostingListStore());
    vespalib::datastore::EntryRef pidx(_dItr.getData());
    postingListStore.apply(pidx,
                           &_adds[0],
                           &_adds[0] + _adds.size(),
                           &_removes[0],
                           &_removes[0] + _removes.size());
    if (pidx.ref() != _dItr.getData()) {
        // Before updating ref
        std::atomic_thread_fence(std::memory_order_release);
        _dItr.writeData(pidx.ref());
    }
    _removes.clear();
    _adds.clear();
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::flush()
{
    flushWord();
    _listener.flush();
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::commit()
{
    _fieldIndex.commit();
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::setNextWord(const vespalib::stringref word)
{
    // TODO: Adjust here if zero length words should be legal.
    assert(_word < word);
    _word = word;
    _prevDocId = noDocId;
    _prevAdd = false;
    flushWord();
    const WordStore &wordStore(_fieldIndex.getWordStore());
    KeyComp cmp(wordStore, _word);
    WordKey key;
    if (_dItr.valid() && cmp(_dItr.getKey(), key)) {
        _dItr.binarySeek(key, cmp);
    }
    if (!_dItr.valid() || cmp(key, _dItr.getKey())) {
        vespalib::datastore::EntryRef wordRef = _fieldIndex.addWord(_word);

        WordKey insertKey(wordRef);
        DictionaryTree &dTree(_fieldIndex.getDictionaryTree());
        dTree.insert(_dItr, insertKey, vespalib::datastore::EntryRef().ref());
    }
    assert(_dItr.valid());
    assert(_word == wordStore.getWord(_dItr.getKey()._wordRef));
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::add(uint32_t docId,
                                                     const index::DocIdAndFeatures &features)
{
    assert(docId != noDocId);
    assert(_prevDocId == noDocId || _prevDocId < docId ||
           (_prevDocId == docId && !_prevAdd));
    vespalib::datastore::EntryRef featureRef = _fieldIndex.addFeatures(features);
    _adds.push_back(PostingListKeyDataType(docId, PostingListEntryType(featureRef,
                                                                       cap_u16(features.num_occs()),
                                                                       cap_u16(features.field_length()))));
    _listener.insert(_dItr.getKey()._wordRef, docId);
    _prevDocId = docId;
    _prevAdd = true;
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::remove(uint32_t docId)
{
    assert(docId != noDocId);
    assert(_prevDocId == noDocId || _prevDocId < docId);
    _removes.push_back(docId);
    _prevDocId = docId;
    _prevAdd = false;
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::rewind()
{
    assert(_removes.empty() && _adds.empty());
    _word = "";
    _prevDocId = noDocId;
    _prevAdd = false;
    _dItr.begin();
}

template <bool interleaved_features>
vespalib::datastore::EntryRef
OrderedFieldIndexInserter<interleaved_features>::getWordRef() const
{
    return _dItr.getKey()._wordRef;
}

template class OrderedFieldIndexInserter<false>;
template class OrderedFieldIndexInserter<true>;

}
