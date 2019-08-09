// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_entry_base.h"

namespace search::datastore {

/*
 * Class for entries in unique store.
 */
template <typename EntryT>
class UniqueStoreEntry : public UniqueStoreEntryBase {
    using EntryType = EntryT;
    EntryType _value;
public:
    UniqueStoreEntry()
        : UniqueStoreEntryBase(),
          _value()
    {
    }
    explicit UniqueStoreEntry(const EntryType& value)
        : UniqueStoreEntryBase(),
          _value(value)
    {
    }
    explicit UniqueStoreEntry(EntryType&& value)
        : UniqueStoreEntryBase(),
          _value(std::move(value))
    {
    }

    const EntryType& value() const { return _value; }
};

}
