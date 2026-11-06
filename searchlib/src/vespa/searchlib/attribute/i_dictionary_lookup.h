// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {
class EntryComparator;
}

namespace search::attribute {

/**
 * Interface that abstracts lookups in a dictionary.
 * Intended to be used for EnumHintSearchContext-like classes.
 */
class IDictionaryLookup {
public:
    virtual ~IDictionaryLookup() = default;
    virtual void lookupTerm(const vespalib::datastore::EntryComparator& comp) = 0;
    virtual void lookupRange(const vespalib::datastore::EntryComparator& low,
                             const vespalib::datastore::EntryComparator& high) = 0;

    virtual bool supports_range_limit() const { return false; }
    virtual void apply_range_limit(long /*range_limit*/) {}
};

} // namespace search::attribute
