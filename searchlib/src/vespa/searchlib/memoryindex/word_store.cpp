// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "word_store.h"
#include <vespa/vespalib/datastore/datastore.hpp>

namespace search::memoryindex {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;

WordStore::WordStore()
    : _store(),
      _numWords(0),
      _type(RefType::align(1),
            MIN_BUFFER_ARRAYS,
            RefType::offsetSize() / RefType::align(1)),
      _typeId(0)
{
    _store.addType(&_type);
    _store.init_primary_buffers();
}


WordStore::~WordStore()
{
    _store.dropBuffers();
}

vespalib::datastore::EntryRef
WordStore::addWord(const vespalib::stringref word)
{
    size_t wordSize = word.size() + 1;
    size_t bufferSize = RefType::align(wordSize);
    auto result = _store.rawAllocator<char>(_typeId).alloc(bufferSize);
    char *be = result.data;
    for (size_t i = 0; i < word.size(); ++i) {
        *be++ = word[i];
    }
    *be++ = 0;
    for (size_t i = wordSize; i < bufferSize; ++i) {
        *be++ = 0;
    }
    ++_numWords;
    return result.ref;
}

}
