// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itermfielddata.h"

namespace search {
namespace fef {

/**
 * Information about a single field that is being searched for a term
 * (described by the TermData class). The field may be either an index
 * field or an attribute field. If more information about the field is
 * needed, the field id may be used to consult the index environment.
 **/
class SimpleTermFieldData : public ITermFieldData
{
private:
    uint32_t        _fieldId;
    double          _docFreq;
    TermFieldHandle _handle;

public:
    /**
     * Side-cast copy constructor.
     **/
    SimpleTermFieldData(const ITermFieldData &rhs);

    /**
     * Create a new instance for the given field.
     *
     * @param fieldId the field being searched
     **/
    SimpleTermFieldData(uint32_t fieldId);

    /**
     * Obtain the field id.
     *
     * @return field id
     **/
    virtual uint32_t getFieldId() const { return _fieldId; }

    /**
     * Obtain the document frequency.
     *
     * @return document frequency
     **/
    virtual double getDocFreq() const { return _docFreq; }

    /**
     * Obtain the match handle for this field.
     *
     * @return match handle
     **/
    virtual TermFieldHandle getHandle() const {
        return _handle;
    }

    /**
     * Sets the document frequency.
     *
     * @return this object (for chaining)
     * @param docFreq document frequency
     **/
    SimpleTermFieldData &setDocFreq(double docFreq) {
        _docFreq = docFreq;
        return *this;
    }

    /**
     * Sets the match handle for this field.
     *
     * @return this object (for chaining)
     * @param handle match handle
     **/
    SimpleTermFieldData &setHandle(TermFieldHandle handle) {
        _handle = handle;
        return *this;
    }
};

} // namespace fef
} // namespace search

