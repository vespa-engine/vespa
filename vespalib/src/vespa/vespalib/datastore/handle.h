// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"

namespace vespalib::datastore {

/**
 * Handle to data allocated in a data store and a EntryRef used for read-only access to data later.
 */
template <typename EntryT>
struct Handle
{
    EntryRef ref;
    EntryT *data;
    Handle(EntryRef ref_, EntryT *data_) : ref(ref_), data(data_) {}
    Handle() : ref(), data() {}
    bool operator==(const Handle<EntryT> &rhs) const {
        return ref == rhs.ref &&
                data == rhs.data;
    }
};

}
