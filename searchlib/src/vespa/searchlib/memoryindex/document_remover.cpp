// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "document_remover.h"
#include "i_field_index_remove_listener.h"
#include "wordstore.h"
#include <vespa/searchlib/common/sort.h>

namespace search::memoryindex {

using Builder = CompactDocumentWordsStore::Builder;
using Iterator = CompactDocumentWordsStore::Iterator;

DocumentRemover::DocumentRemover(const WordStore &wordStore)
    : _store(),
      _builder(),
      _wordFieldDocTuples(),
      _wordStore(wordStore)
{
}

DocumentRemover::~DocumentRemover() = default;

void
DocumentRemover::remove(uint32_t docId, IFieldIndexRemoveListener &listener)
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
DocumentRemover::insert(datastore::EntryRef wordRef, uint32_t docId)
{
    _wordFieldDocTuples.emplace_back(wordRef, docId);
}

void
DocumentRemover::flush()
{
    if (_wordFieldDocTuples.empty()) {
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
    _wordFieldDocTuples.clear();
}

}
