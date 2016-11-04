// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.wordstore");
#include "wordstore.h"
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search {
namespace memoryindex {

constexpr size_t MIN_CLUSTERS = 1024;

WordStore::WordStore()
    : _store(),
      _numWords(0),
      _type(RefType::align(1),
            MIN_CLUSTERS,
            RefType::offsetSize() / RefType::align(1)),
      _typeId(0)
{
    _store.addType(&_type);
    _store.initActiveBuffers();
}


WordStore::~WordStore(void)
{
    _store.dropBuffers();
}

datastore::EntryRef
WordStore::addWord(const vespalib::stringref word)
{
    _store.ensureBufferCapacity(_typeId, RefType::align(word.size() + 1));
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    datastore::BufferState &state = _store.getBufferState(activeBufferId);
    size_t oldSize = state.size();
    RefType ref(oldSize, activeBufferId);
    assert(oldSize == ref.offset());
    char *be = _store.getBufferEntry<char>(activeBufferId, oldSize);
    for (size_t i = 0; i < word.size(); ++i) {
        *be++ = word[i];
    }
    *be++ = 0;
    state.pushed_back(word.size() + 1);
    size_t pad = RefType::pad(state.size());
    for (size_t i = 0; i < pad; ++i) {
        *be++ = 0;
    }
    state.pushed_back(pad);
    ++_numWords;
    return ref;
}


} // namespace search::memoryindex
} // namespace search

