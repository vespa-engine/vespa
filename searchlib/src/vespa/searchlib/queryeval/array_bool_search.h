// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <cstdint>
#include <vector>

namespace search::attribute { class ArrayBoolAttribute; };

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

/**
 * Search iterator for checking ArrayBoolAttribute at one or more indices.
 * Matches a given document id if at least one of the specified element ids in the specified ArrayBoolAttribute
 * has the specified truth value.
 * Intended to replace the combination of a SameElementSearch and a (Filter)AttributeIteratorStrict/T,
 * as it does the same things but faster.
 */
class ArrayBoolSearch : public SearchIterator {
    using ArrayBoolAttribute = search::attribute::ArrayBoolAttribute;

    const ArrayBoolAttribute&    _attr;
    const std::vector<uint32_t>& _element_filter;
    bool                         _want_true;
    bool                         _strict;
    fef::TermFieldMatchData*     _tfmd;

public:
    // Use the create(...) method instead.
    ArrayBoolSearch(const ArrayBoolAttribute& attr,
                    const std::vector<uint32_t>& element_filter,
                    bool want_true,
                    bool strict,
                    fef::TermFieldMatchData* tfmd);
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override;

    bool check_array(uint32_t docid) const;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;

    bool want_true() const { return _want_true; }
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
     **/
    static std::unique_ptr<ArrayBoolSearch> create(const ArrayBoolAttribute& attr,
                                                   const std::vector<uint32_t>& element_filter,
                                                   bool want_true,
                                                   bool strict,
                                                   fef::TermFieldMatchData* tfmd);
};

} // namespace
