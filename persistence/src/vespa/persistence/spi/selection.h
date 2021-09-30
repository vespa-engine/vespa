// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::Selection
 * \ingroup spi
 *
 * \brief Use a matcher to find what documents one is interested in.
 */

#pragma once

#include "documentselection.h"
#include <persistence/spi/types.h>

namespace storage::spi {

class Selection {
public:
    typedef std::vector<Timestamp> TimestampSubset;
private:
    DocumentSelection _documentSelection;
    Timestamp         _fromTimestamp;
    Timestamp         _toTimestamp;
    TimestampSubset   _timestampSubset;

public:
    Selection(const DocumentSelection& docSel);
    ~Selection();

    const DocumentSelection& getDocumentSelection() const {
        return _documentSelection;
    }

    /**
     * All the timestamp stuff will disappear when we rewrite selection.
     */
    /**
     * Specifies that only documents with a timestamp newer than or equal
     * to the given value shall be included in the result.
     */
    void setFromTimestamp(Timestamp fromTimestamp) {
        _fromTimestamp = fromTimestamp;
    }
    /**
     * Specifies that only documents with a timestamp older than or equal
     * to the given value shall be included in the result.
     */
    void setToTimestamp(Timestamp toTimestamp) {
        _toTimestamp = toTimestamp;
    }

    /**
     * Assign an explicit subset of timestamps to iterate over.
     * If non-empty, document selection, timestamp range and include removes
     * will be ignored; all specified entries are returned if they exist.
     * Timestamps MUST be in strictly increasing order.
     */
    void setTimestampSubset(const TimestampSubset& timestampSubset) {
        _timestampSubset = timestampSubset;
    }
    const TimestampSubset& getTimestampSubset() const {
        return _timestampSubset;
    }

    Timestamp getFromTimestamp() const { return _fromTimestamp; }
    Timestamp getToTimestamp() const { return _toTimestamp; }
};

}
