// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ordereddocumentinserter.h"
#include "i_document_insert_listener.h"

#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btree.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.ordered_document_inserter");

namespace search::memoryindex {

namespace {

const vespalib::string emptyWord = "";

}

OrderedDocumentInserter::OrderedDocumentInserter(MemoryFieldIndex &fieldIndex)
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

OrderedDocumentInserter::~OrderedDocumentInserter()
{
    flush();
}


void
OrderedDocumentInserter::flushWord()
{
    if (_removes.empty() && _adds.empty()) {
        return;
    }
    //XXX: Feature store leak, removed features not marked dead
    PostingListStore &postingListStore(_fieldIndex.getPostingListStore());
    datastore::EntryRef pidx(_dItr.getData());
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


void
OrderedDocumentInserter::flush()
{
    flushWord();
    _listener.flush();
}


void
OrderedDocumentInserter::setNextWord(const vespalib::stringref word)
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
        datastore::EntryRef wordRef = _fieldIndex.addWord(_word);
        WordKey insertKey(wordRef);
        DictionaryTree &dTree(_fieldIndex.getDictionaryTree());
        dTree.insert(_dItr, insertKey, datastore::EntryRef().ref());
    }
    assert(_dItr.valid());
    assert(_word == wordStore.getWord(_dItr.getKey()._wordRef));
}


void
OrderedDocumentInserter::add(uint32_t docId,
                               const index::DocIdAndFeatures &features)
{
    assert(docId != noDocId);
    assert(_prevDocId == noDocId || _prevDocId < docId ||
           (_prevDocId == docId && !_prevAdd));
    datastore::EntryRef featureRef = _fieldIndex.addFeatures(features);
    _adds.push_back(PostingListKeyDataType(docId, featureRef.ref()));
    _listener.insert(_dItr.getKey()._wordRef, docId);
    _prevDocId = docId;
    _prevAdd = true;
}


void
OrderedDocumentInserter::remove(uint32_t docId)
{
    assert(docId != noDocId);
    assert(_prevDocId == noDocId || _prevDocId < docId);
    _removes.push_back(docId);
    _prevDocId = docId;
    _prevAdd = false;
}


void
OrderedDocumentInserter::rewind()
{
    assert(_removes.empty() && _adds.empty());
    _word = "";
    _prevDocId = noDocId;
    _prevAdd = false;
    _dItr.begin();
}


datastore::EntryRef
OrderedDocumentInserter::getWordRef() const
{
    return _dItr.getKey()._wordRef;
}

}
