// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/datastore.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace memoryindex {

class WordStore
{
public:
    typedef btree::DataStoreT<btree::AlignedEntryRefT<22, 2> > DataStoreType;
    typedef DataStoreType::RefType RefType;

private:
    DataStoreType           _store;
    uint32_t                _numWords;
    btree::BufferType<char> _type;
    const uint32_t          _typeId;

public:
    WordStore();
    ~WordStore();
    btree::EntryRef addWord(const vespalib::stringref word);
    const char * getWord(btree::EntryRef ref) const
    {
        RefType internalRef(ref);
        return _store.getBufferEntry<char>(internalRef.bufferId(),
                                           internalRef.offset());
    }

    MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }
};

} // namespace search::memoryindex
} // namespace search

