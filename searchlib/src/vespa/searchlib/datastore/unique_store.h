// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entryref.h"
#include "i_compaction_context.h"
#include <vespa/vespalib/util/array.h>
#include <vespa/searchlib/btree/btree.h>

namespace search::datastore {

template <typename EntryT, typename RefT>
class UniqueStoreBuilder;

template <typename EntryT, typename RefT>
class UniqueStoreSaver;

/**
 * Datastore for unique values of type EntryT that is accessed via a
 * 32-bit EntryRef.
 */
template <typename EntryT, typename RefT = EntryRefT<22> >
class UniqueStore
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = EntryT;
    using RefType = RefT;
    using Saver = UniqueStoreSaver<EntryT, RefT>;
    using Builder = UniqueStoreBuilder<EntryT, RefT>;
    /*
     * Compare two values in data store based on reference.  Invalid
     * reference is mapped to local value reference to support
     * comparing with new value candidate outside data store.
     */
    class Compare {
        const DataStoreType &_store;
        const EntryType &_value;
public:
        Compare(const DataStoreType &store, const EntryType &value)
            : _store(store),
              _value(value)
        {
        }
        inline const EntryType &get(EntryRef ref) const {
            if (ref.valid()) {
                RefType iRef(ref);
                return *_store.template getEntry<EntryType>(iRef);
            } else {
                return _value;
            }
        }
        inline bool operator()(const EntryRef lhs, const EntryRef rhs) const
        {
            const EntryType &lhsValue = get(lhs);
            const EntryType &rhsValue = get(rhs);
            return lhsValue < rhsValue;
        }
    };
    using UniqueStoreBufferType = BufferType<EntryType>;
    using DictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
    using Dictionary = btree::BTree<EntryRef, uint32_t,
                                    btree::NoAggregated,
                                    Compare,
                                    DictionaryTraits>;
    class AddResult {
        EntryRef _ref;
        bool _inserted;
    public:
        AddResult(EntryRef ref_, bool inserted_)
            : _ref(ref_),
              _inserted(inserted_)
        {
        }
        EntryRef ref() const { return _ref; }
        bool inserted() { return _inserted; }
    };
private:
    DataStoreType _store;
    UniqueStoreBufferType _typeHandler;
    uint32_t _typeId;
    Dictionary _dict;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    UniqueStore();
    ~UniqueStore();
    EntryRef move(EntryRef ref);
    AddResult add(const EntryType &value);
    EntryRef find(const EntryType &value);
    const EntryType &get(EntryRef ref) const
    {
        RefType iRef(ref);
        return *_store.template getEntry<EntryType>(iRef);
    }
    void remove(EntryRef ref);
    ICompactionContext::UP compactWorst();
    MemoryUsage getMemoryUsage() const;

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);
    vespalib::GenerationHolder &getGenerationHolder() { return _store.getGenerationHolder(); }
    void setInitializing(bool initializing) { _store.setInitializing(initializing); }
    void freeze();
    uint32_t getNumUniques() const;

    Builder getBuilder(uint32_t uniqueValuesHint);
    Saver getSaver() const;

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref) const;
};

}
