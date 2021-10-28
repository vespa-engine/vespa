// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"


namespace vespalib::datastore {

/*
 * Representation of result of adding a value to unique store.
 */
class UniqueStoreAddResult {
    EntryRef _ref;
    bool _inserted;
public:
    UniqueStoreAddResult(EntryRef ref_, bool inserted_)
        : _ref(ref_),
          _inserted(inserted_)
    {
    }
    EntryRef ref() const { return _ref; }
    bool inserted() { return _inserted; }
};

}
