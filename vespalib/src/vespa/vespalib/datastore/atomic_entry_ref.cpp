// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "atomic_entry_ref.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib::datastore {

vespalib::asciistream & operator << (vespalib::asciistream & os, const AtomicEntryRef &ref) {
    return os << "AtomicEntryRef(" << ref.load_relaxed().ref() << ")";
}

}
