// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ordered_field_index_inserter.h"
#include "i_field_index_insert_listener.h"

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
#include <cstdio>

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
      _adds(),
      _word_entries(),
      _removes_offset(0),
      _adds_offset(0)
{
}

template <bool interleaved_features>
OrderedFieldIndexInserter<interleaved_features>::~OrderedFieldIndexInserter() = default;

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::flushWord()
{
    if (_removes.size() == _removes_offset && _adds.size() == _adds_offset) {
        return;
    }
    _word_entries.emplace_back(_word, _adds.size() - _adds_offset, _removes.size() - _removes_offset);
    _adds_offset = _adds.size();
    _removes_offset = _removes.size();
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::flush()
{
    flushWord();
    assert(_adds_offset == _adds.size());
    assert(_removes_offset == _removes.size());
    if (!_adds.empty()) {
        _fieldIndex.add_features_guard_bytes();
    }
    const WordStore &wordStore(_fieldIndex.getWordStore());
    PostingListStore &postingListStore(_fieldIndex.getPostingListStore());
    size_t adds_offset = 0;
    size_t removes_offset = 0;
    for (const auto& word_entry : _word_entries) {
        auto word = std::get<0>(word_entry);
        vespalib::ConstArrayRef<PostingListKeyDataType> adds(_adds.data() + adds_offset, std::get<1>(word_entry));
        vespalib::ConstArrayRef<uint32_t> removes(_removes.data() + removes_offset, std::get<2>(word_entry));
        KeyComp cmp(wordStore, word);
        WordKey key;
        if (_dItr.valid() && cmp(_dItr.getKey(), key)) {
            _dItr.binarySeek(key, cmp);
        }
        if (!_dItr.valid() || cmp(key, _dItr.getKey())) {
            vespalib::datastore::EntryRef wordRef = _fieldIndex.addWord(word);
            WordKey insertKey(wordRef);
            DictionaryTree &dTree(_fieldIndex.getDictionaryTree());
            dTree.insert(_dItr, insertKey, vespalib::datastore::AtomicEntryRef());
        }
        assert(_dItr.valid());
        assert(word == wordStore.getWord(_dItr.getKey()._wordRef));
        for (auto& add_entry : adds) {
            _listener.insert(_dItr.getKey()._wordRef, add_entry._key);
        }
        //XXX: Feature store leak, removed features not marked dead
        vespalib::datastore::EntryRef pidx(_dItr.getData().load_relaxed());
        postingListStore.apply(pidx,
                               adds.begin(),
                               adds.end(),
                               removes.begin(),
                               removes.end());
        if (pidx != _dItr.getData().load_relaxed()) {
            _dItr.getWData().store_release(pidx);
        }
        adds_offset += adds.size();
        removes_offset += removes.size();
    }
    assert(adds_offset == _adds.size());
    assert(removes_offset == _removes.size());
    _removes_offset = 0;
    _adds_offset = 0;
    _adds.clear();
    _removes.clear();
    _word_entries.clear();
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
    flushWord();
    // TODO: Adjust here if zero length words should be legal.
    assert(_word < word);
    _word = word;
    _prevDocId = noDocId;
    _prevAdd = false;
}

template <bool interleaved_features>
void
OrderedFieldIndexInserter<interleaved_features>::add(uint32_t docId,
                                                     const index::DocIdAndFeatures &features)
{
    assert(docId != noDocId);
    assert(_prevDocId == noDocId || _prevDocId < docId ||
           (_prevDocId == docId && !_prevAdd));
    assert(features.num_occs() <= features.field_length());
    vespalib::datastore::EntryRef featureRef = _fieldIndex.addFeatures(features);
    _adds.push_back(PostingListKeyDataType(docId, PostingListEntryType(featureRef,
                                                                       cap_u16(features.num_occs()),
                                                                       cap_u16(features.field_length()))));
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
