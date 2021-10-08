// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

class WordStore {
public:
    using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::AlignedEntryRefT<22, 2>>;
    using RefType = DataStoreType::RefType;

private:
    DataStoreType           _store;
    uint32_t                _numWords;
    vespalib::datastore::BufferType<char> _type;
    const uint32_t          _typeId;

public:
    WordStore();
    ~WordStore();
    vespalib::datastore::EntryRef addWord(const vespalib::stringref word);
    const char *getWord(vespalib::datastore::EntryRef ref) const {
        RefType internalRef(ref);
        return _store.getEntry<char>(internalRef);
    }

    vespalib::MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }
};

}
