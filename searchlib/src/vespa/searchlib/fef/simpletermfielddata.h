// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itermfielddata.h"

namespace search::fef {

/**
 * Information about a single field that is being searched for a term
 * (described by the TermData class). The field may be either an index
 * field or an attribute field. If more information about the field is
 * needed, the field id may be used to consult the index environment.
 **/
class SimpleTermFieldData : public ITermFieldData
{
private:
    TermFieldHandle _handle;

public:
    /**
     * Side-cast copy constructor.
     **/
    SimpleTermFieldData(const ITermFieldData &rhs) noexcept
        : ITermFieldData(rhs),
          _handle(rhs.getHandle())
    {}

    /**
     * Create a new instance for the given field.
     *
     * @param fieldId the field being searched
     **/
    SimpleTermFieldData(uint32_t fieldId) noexcept
        : ITermFieldData(fieldId),
          _handle(IllegalHandle)
    {}

    using ITermFieldData::getHandle;

    TermFieldHandle getHandle(MatchDataDetails requestedDetails) const override {
        (void) requestedDetails;
        return _handle;
    }

    /**
     * Sets the match handle for this field.
     **/
    SimpleTermFieldData &setHandle(TermFieldHandle handle) noexcept {
        _handle = handle;
        return *this;
    }
};

}

