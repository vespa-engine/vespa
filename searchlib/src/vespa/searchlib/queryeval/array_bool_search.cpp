// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <cassert>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.array_bool_search");

namespace search::queryeval {

ArrayBoolSearch::ArrayBoolSearch(const ArrayBoolAttribute& attr,
                                 const std::vector<uint32_t>& element_filter,
                                 bool want_true,
                                 bool strict,
                                 fef::TermFieldMatchData* tfmd)
    : _attr(attr),
      _element_filter(element_filter),
      _want_true(want_true),
      _strict(strict),
      _tfmd(tfmd) {
    assert(!_element_filter.empty());
    assert(tfmd != nullptr);
}

void ArrayBoolSearch::doSeek(uint32_t docid) {
    while (docid < getEndId()) {
        if (check_array(docid)) {
            setDocId(docid);
            return;
        }
        if (_strict) {
            ++docid;
        } else {
            return;
        }
    }
    setAtEnd();
}

void ArrayBoolSearch::doUnpack(uint32_t docid) {
    _tfmd->resetOnlyDocId(docid);
}

SearchIterator::Trinary ArrayBoolSearch::is_strict() const {
    return _strict ? Trinary::True : Trinary::False;
}

bool ArrayBoolSearch::check_array(uint32_t docid) const {
    auto bools = _attr.get_bools(docid);

    for (auto i : _element_filter) {
        if (i >= bools.size()) {
            break;
        }
        if (bools[i] == _want_true) {
            return true;
        }
    }

    return false;
}

void ArrayBoolSearch::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) {
    auto bools = _attr.get_bools(docid);

    for (auto i : _element_filter) {
        if (i >= bools.size()) {
            break;
        }
        if (bools[i] == _want_true) {
            element_ids.push_back(i);
        }
    }
}

std::unique_ptr<ArrayBoolSearch> ArrayBoolSearch::create(const ArrayBoolAttribute& attr,
                                                         const std::vector<uint32_t>& element_filter,
                                                         bool want_true,
                                                         bool strict,
                                                         fef::TermFieldMatchData* tfmd) {
    return std::make_unique<ArrayBoolSearch>(attr, element_filter, want_true, strict, tfmd);
}

} // namespace
