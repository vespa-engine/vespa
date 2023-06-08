// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
public:
    ITermFieldData(uint32_t fieldId)
        : _fieldId(fieldId),
          _matching_doc_count(0),
          _total_doc_count(1)
    { }
    /**
     * Obtain the global field id.
     *
     * @return field id
     **/
    uint32_t getFieldId() const { return _fieldId; }

    /**
     * Returns the number of documents matching this term.
     */
    uint32_t get_matching_doc_count() const { return _matching_doc_count; }

    /**
     * Returns the total number of documents in the corpus.
     */
    uint32_t get_total_doc_count() const { return _total_doc_count; }

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
     * Sets the document frequency.
     **/
    ITermFieldData &setDocFreq(uint32_t matching_doc_count, uint32_t total_doc_count) {
        _matching_doc_count = matching_doc_count;
        _total_doc_count = total_doc_count;
        return *this;
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
protected:
    virtual ~ITermFieldData() = default;
private:
    uint32_t    _fieldId;
    uint32_t    _matching_doc_count;
    uint32_t    _total_doc_count;
};

}
