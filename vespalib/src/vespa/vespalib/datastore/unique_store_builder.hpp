// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_builder.h"
#include "datastore.hpp"
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btreeiterator.h>
#include <vespa/vespalib/btree/btreenode.h>

namespace search::datastore {

template <typename EntryT, typename RefT>
UniqueStoreBuilder<EntryT, RefT>::UniqueStoreBuilder(UniqueStoreType &store, UniqueStoreDictionaryBase &dict, uint32_t uniqueValuesHint)
    : _store(store),
      _dict(dict),
      _refs(),
      _refCounts()
{
    _refs.reserve(uniqueValuesHint);
    _refs.push_back(EntryRef());
}

template <typename EntryT, typename RefT>
UniqueStoreBuilder<EntryT, RefT>::~UniqueStoreBuilder()
{
}

template <typename EntryT, typename RefT>
void
UniqueStoreBuilder<EntryT, RefT>::setupRefCounts()
{
    _refCounts.resize(_refs.size());
}


template <typename EntryT, typename RefT>
void
UniqueStoreBuilder<EntryT, RefT>::makeDictionary()
{
    _dict.build(_refs, _refCounts, [this](EntryRef ref) { _store.hold(ref); });
}

}

