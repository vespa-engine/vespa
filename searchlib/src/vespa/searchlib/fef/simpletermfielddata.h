// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    uint32_t        _fieldId;
    uint32_t        _matching_doc_count;
    uint32_t        _total_doc_count;
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

    uint32_t getFieldId() const override final { return _fieldId; }

    uint32_t get_matching_doc_count() const override { return _matching_doc_count; }

    uint32_t get_total_doc_count() const override { return _total_doc_count; }

    using ITermFieldData::getHandle;

    TermFieldHandle getHandle(MatchDataDetails requestedDetails) const override {
        (void) requestedDetails;
        return _handle;
    }

    /**
     * Sets the document frequency.
     **/
    SimpleTermFieldData &setDocFreq(uint32_t matching_doc_count, uint32_t total_doc_count) {
        _matching_doc_count = matching_doc_count;
        _total_doc_count = total_doc_count;
        return *this;
    }

    /**
     * Sets the match handle for this field.
     **/
    SimpleTermFieldData &setHandle(TermFieldHandle handle) {
        _handle = handle;
        return *this;
    }
};

}

