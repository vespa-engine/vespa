// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

class WordStore {
public:
    using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::EntryRefT<22>>;
    using RefType = DataStoreType::RefType;
    static constexpr uint32_t buffer_array_size = 4u; // Must be a power of 2
    static constexpr uint32_t pad_constant = buffer_array_size - 1u;
    static uint32_t calc_pad(uint32_t val) { return (-val & pad_constant); }

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
        return _store.getEntryArray<char>(internalRef, buffer_array_size);
    }

    vespalib::MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }
};

}
