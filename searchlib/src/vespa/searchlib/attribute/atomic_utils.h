// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {

class AtomicEntryRef;
class EntryRef;

}

namespace search::attribute::atomic_utils {

/*
 * Helper class to map from atomic value to non-atomic value, e.g.
 * from AtomicEntryRef to EntryRef.
 */
template <typename MaybeAtomicValue>
class NonAtomicValue {
public:
    using type = MaybeAtomicValue;
};

template <>
class NonAtomicValue<vespalib::datastore::AtomicEntryRef>
{
public:
    using type = vespalib::datastore::EntryRef;
};

template <class MaybeAtomicValue>
using NonAtomicValue_t = typename NonAtomicValue<MaybeAtomicValue>::type;

}
