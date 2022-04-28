// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_remover.h"
#include "i_field_index_remove_listener.h"
#include "word_store.h"
#include <vespa/searchlib/common/sort.h>

namespace search::memoryindex {

using Builder = CompactWordsStore::Builder;
using Iterator = CompactWordsStore::Iterator;

FieldIndexRemover::FieldIndexRemover(const WordStore &wordStore)
    : _store(),
      _builder(),
      _wordFieldDocTuples(),
      _wordStore(wordStore)
{
}

FieldIndexRemover::~FieldIndexRemover() = default;

void
FieldIndexRemover::remove(uint32_t docId, IFieldIndexRemoveListener &listener)
{
    Iterator itr = _store.get(docId);
    if (itr.valid()) {
        for (; itr.valid(); ++itr) {
            vespalib::stringref word = _wordStore.getWord(itr.wordRef());
            listener.remove(word, docId);
        }
        _store.remove(docId);
    }
}

void
FieldIndexRemover::insert(vespalib::datastore::EntryRef wordRef, uint32_t docId)
{
    _wordFieldDocTuples.emplace_back(wordRef, docId);
}

void
FieldIndexRemover::flush()
{
    if (_wordFieldDocTuples.empty()) {
        _store.commit();
        return;
    }
    ShiftBasedRadixSorter<WordFieldDocTuple, WordFieldDocTuple::Radix, std::less<WordFieldDocTuple>, 24, true>::
       radix_sort(WordFieldDocTuple::Radix(), std::less<WordFieldDocTuple>(), &_wordFieldDocTuples[0], _wordFieldDocTuples.size(), 16);
    auto builder = std::make_unique<Builder>(_wordFieldDocTuples[0]._docId);
    for (const auto &tuple : _wordFieldDocTuples) {
        if (builder->docId() != tuple._docId) {
            _store.insert(*builder);
            builder = std::make_unique<Builder>(tuple._docId);
        }
        builder->insert(tuple._wordRef);
    }
    _store.insert(*builder);
    _store.commit();
    _wordFieldDocTuples.clear();
}

}
