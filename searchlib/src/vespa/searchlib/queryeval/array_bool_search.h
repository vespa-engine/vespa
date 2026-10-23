// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiterator.h"

#include <vespa/searchlib/attribute/array_bool_attribute.h>

#include <cstdint>
#include <vector>

namespace search::attribute {
class ArrayBoolAttribute;
};

namespace search::fef {
class TermFieldMatchData;
}

namespace search::queryeval {

/**
 * Search iterator for checking ArrayBoolAttribute at one or more indices.
 * Matches a given document id if at least one of the specified element ids in the specified ArrayBoolAttribute
 * has the specified truth value.
 * Intended to replace the combination of a SameElementSearch and a (Filter)AttributeIteratorStrict/T,
 * as it does the same things but faster.
 */
class ArrayBoolSearch : public SearchIterator {
protected:
    using ArrayBoolAttribute = search::attribute::ArrayBoolAttribute;

    const ArrayBoolAttribute&    _attr;
    const std::vector<uint32_t>& _element_filter;
    fef::TermFieldMatchData*     _tfmd;
    bool                         _unpack_element_positions;
    std::vector<uint32_t>        _matching_elements;

    ArrayBoolSearch(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                    fef::TermFieldMatchData* tfmd, bool unpack_element_positions);
    void doUnpack(uint32_t docid) override;

public:
    virtual bool get_want_true() const = 0;
    const std::vector<uint32_t>& get_element_filter() const { return _element_filter; }
    const ArrayBoolAttribute& get_attribute() const { return _attr; }

    /**
     * Create an ArrayBoolSearch.
     *
     * @param attr The ArrayBoolAttribute to search.
     * @param element_filter The indices to check. Has to be non-empty.
     * @param want_true The truth value to check for.
     * @param strict Whether the iterator should  be strict.
     * @param tfmd TermFieldMatchData to unpack into.
     * @param unpack_element_positions Whether to unpack one position per matching element into tfmd (when normal
     *                                 features are needed), exposing the matching element ids to ranking.
     **/
    static std::unique_ptr<ArrayBoolSearch> create(const ArrayBoolAttribute&    attr,
                                                   const std::vector<uint32_t>& element_filter, bool want_true,
                                                   bool strict, fef::TermFieldMatchData* tfmd,
                                                   bool unpack_element_positions = false);
};

} // namespace search::queryeval
