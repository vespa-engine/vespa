// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "match_data_details.h"

namespace search::fef {

/**
 * Interface to information about a single field that is being
 * searched for a term (described by the ITermData interface). The
 * field may be either an index field or an attribute field. If more
 * information about the field is needed, the field id may be used to
 * consult the index environment.
 **/
class ITermFieldData
{
protected:
    virtual ~ITermFieldData() {}

public:
    /**
     * Obtain the global field id.
     *
     * @return field id
     **/
    virtual uint32_t getFieldId() const = 0;


    /**
     * Returns the number of documents matching this term.
     */
    virtual uint32_t get_matching_doc_count() const = 0;

    /**
     * Returns the total number of documents in the corpus.
     */
    virtual uint32_t get_total_doc_count() const = 0;

    /**
     * Obtain the document frequency. This is a value between 0 and 1
     * indicating the ratio of the matching documents to the corpus.
     *
     * @return document frequency
    **/
    double getDocFreq() const {
        return (double)get_matching_doc_count() / (double)get_total_doc_count();
    }

    /**
     * Obtain the match handle for this field,
     * requesting normal match data in the corresponding TermFieldMatchData.
     *
     * @return match handle (or IllegalHandle)
     **/
    TermFieldHandle getHandle() const {
        return getHandle(MatchDataDetails::Normal);
    }

    /**
     * Obtain the match handle for this field,
     * requesting match data with the given details in the corresponding TermFieldMatchData.
     *
     * @return match handle (or IllegalHandle)
     **/
    virtual TermFieldHandle getHandle(MatchDataDetails requested_details) const = 0;
};

}
