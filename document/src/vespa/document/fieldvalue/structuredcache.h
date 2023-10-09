// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldvalue.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace document {

class StructuredCache {
public:
    using ModificationStatus = fieldvalue::ModificationStatus;
    struct ValuePair {
        fieldvalue::ModificationStatus status;
        FieldValue::UP value;

        ValuePair() : status(ModificationStatus::NOT_MODIFIED), value() {}

        ValuePair(ModificationStatus status_, FieldValue::UP value_)
            : status(status_),
              value(std::move(value_))
        {}
    };

    using Cache = vespalib::hash_map<Field, ValuePair>;

    void remove(const Field &field) {
        ValuePair &entry = _cache[field];
        entry.status = ModificationStatus::REMOVED;
        entry.value.reset();
    }

    Cache::iterator find(const Field &field) {
        return _cache.find(field);
    }

    void set(const Field &field, FieldValue::UP value, ModificationStatus status) {
        ValuePair &entry = _cache[field];
        // If the entry has previously been tagged modified, the value we're now reinserting
        // is likely based on those changes. We cannot lose that modification status.
        entry.status = ((status == ModificationStatus::NOT_MODIFIED) &&
                        (entry.status == ModificationStatus::MODIFIED))
                       ? ModificationStatus::MODIFIED
                       : status;
        entry.value = std::move(value);
    }

    Cache::iterator begin() { return _cache.begin(); }

    Cache::iterator end() { return _cache.end(); }

private:
    Cache _cache;
};

}
