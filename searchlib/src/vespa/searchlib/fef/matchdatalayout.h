// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "matchdata.h"

namespace search::fef {

/**
 * This class is used to describe the layout of term match data and
 * features within MatchData objects for a single query.
 **/
class MatchDataLayout
{
private:
    std::vector<uint32_t> _fieldIds;
public:
    /**
     * Create an empty object.
     **/
    MatchDataLayout();
    MatchDataLayout(MatchDataLayout &&) noexcept = default;
    MatchDataLayout & operator=(MatchDataLayout &&) noexcept = default;
    MatchDataLayout(const MatchDataLayout &) = default;
    MatchDataLayout & operator=(const MatchDataLayout &) = delete;
    ~MatchDataLayout();

    /**
     * Allocate space for a term field match data structure.
     *
     * @param fieldId the field ID the space will be used for
     * @return handle to be used with match data objects
     **/
    TermFieldHandle allocTermField(uint32_t fieldId) {
        _fieldIds.push_back(fieldId);
        return _fieldIds.size() - 1;
    }
    void reserve(size_t sz) { _fieldIds.reserve(sz); }

    /**
     * Create a match data object with the layout described by this
     * object. Note that this method should only be invoked after all
     * terms and features have been allocated.
     *
     * @return auto-pointer to a match data object
     **/
    MatchData::UP createMatchData() const;
};

}
