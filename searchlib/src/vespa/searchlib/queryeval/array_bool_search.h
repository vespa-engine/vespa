// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

#include <vector>

namespace search::attribute { class ArrayBoolAttribute; };

namespace search::queryeval {

class ArrayBoolSearch : public SearchIterator {
    using ArrayBoolAttribute = search::attribute::ArrayBoolAttribute;

    const ArrayBoolAttribute& _attr;
    const std::vector<uint32_t>& _element_filter;
    bool _want_true;
    bool _strict;
    const fef::TermFieldMatchData* _tfmd;
public:
    ArrayBoolSearch(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter, bool want_true, bool strict, const fef::TermFieldMatchData* tfmd);
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override;

    bool check_array(uint32_t docid) const;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    void and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) override;
};

} // namespace
