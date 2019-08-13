// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entryref.h"
#include "entry_comparator_wrapper.h"
#include "unique_store_add_result.h"
#include "unique_store_entry.h"
#include "unique_store_comparator.h"
#include "unique_store_dictionary_base.h"
#include "i_compaction_context.h"
#include "i_compactable.h"
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/btree/btree.h>

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
class UniqueStore : public ICompactable
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = EntryT;
    using WrappedEntryType = UniqueStoreEntry<EntryType>;
    using RefType = RefT;
    using Saver = UniqueStoreSaver<EntryT, RefT>;
    using Builder = UniqueStoreBuilder<EntryT, RefT>;
    using Compare = UniqueStoreComparator<EntryType, RefType>;
    using UniqueStoreBufferType = BufferType<WrappedEntryType>;
    template <typename, typename> friend class UniqueStoreBuilder;
private:
    DataStoreType _store;
    UniqueStoreBufferType _typeHandler;
    uint32_t _typeId;
    std::unique_ptr<UniqueStoreDictionaryBase> _dict;
    using generation_t = vespalib::GenerationHandler::generation_t;

    EntryRef allocate(const EntryType &value);
    void hold(EntryRef ref);
public:
    UniqueStore();
    ~UniqueStore();
    EntryRef move(EntryRef ref) override;
    UniqueStoreAddResult add(const EntryType &value);
    EntryRef find(const EntryType &value);
    const WrappedEntryType &getWrapped(EntryRef ref) const
    {
        RefType iRef(ref);
        return *_store.template getEntry<WrappedEntryType>(iRef);
    }
    const EntryType &get(EntryRef ref) const
    {
        return getWrapped(ref).value();
    }
    void remove(EntryRef ref);
    ICompactionContext::UP compactWorst();
    vespalib::MemoryUsage getMemoryUsage() const;

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
