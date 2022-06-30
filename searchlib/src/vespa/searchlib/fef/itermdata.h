// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itermfielddata.h"
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/stllike/string.h>
#include <cstddef>
#include <optional>

namespace search::fef {

/**
 * Interface to static match data for a single unit (term/phrase/etc).
 **/
class ITermData
{
protected:
    virtual ~ITermData() {}

public:
    /**
     * Returns the term weight.
     **/
    virtual query::Weight getWeight() const = 0;

    /**
     * Returns the number of terms represented by this term data object.
     **/
    virtual uint32_t getPhraseLength() const = 0;

    /**
     * Obtain the unique id of this term. 0 means not set.
     *
     * @return unique id or 0
     **/
    virtual uint32_t getUniqueId() const = 0;

    /**
     * Returns the name of a query tensor this term is referencing, if set.
     */
    virtual std::optional<vespalib::string> query_tensor_name() const = 0;

    /**
     * Get number of fields searched
     **/
    virtual size_t numFields() const = 0;

    /**
     * Direct access to data for individual fields
     * @param i local index, must have: 0 <= i < numFields()
     */
    virtual const ITermFieldData &field(size_t i) const = 0;

    /**
     * Obtain information about a specific field that may be searched
     * by this term. If the requested field is not searched by this
     * term, NULL will be returned.
     *
     * @param fieldId global field ID
     * @return term field data, or NULL if not found
     **/
    virtual const ITermFieldData *lookupField(uint32_t fieldId) const = 0;
};

/**
 * convenience adapter for easy iteration
 **/
class ITermFieldRangeAdapter
{
    const ITermData& _ref;
    size_t _idx;
    size_t _lim;
public:
    explicit ITermFieldRangeAdapter(const ITermData& ref)
        : _ref(ref), _idx(0), _lim(ref.numFields())
    {}

    bool valid() const { return (_idx < _lim); }

    const ITermFieldData& get() const  { return _ref.field(_idx); }

    void next() { ++_idx; }
};

}
