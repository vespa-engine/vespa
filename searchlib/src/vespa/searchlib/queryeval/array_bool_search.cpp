// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_search.h"

#include <vespa/searchlib/fef/termfieldmatchdata.h>

#include <cassert>

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.array_bool_search");

namespace search::queryeval {

ArrayBoolSearch::ArrayBoolSearch(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                                 fef::TermFieldMatchData* tfmd, bool unpack_element_positions)
    : _attr(attr),
      _element_filter(element_filter),
      _tfmd(tfmd),
      _unpack_element_positions(unpack_element_positions),
      _matching_elements() {
    assert(!_element_filter.empty());
    assert(tfmd != nullptr);
}

void ArrayBoolSearch::doUnpack(uint32_t docid) {
    if (_unpack_element_positions && _tfmd->needs_normal_features()) {
        _tfmd->reset(docid);
        _matching_elements.clear();
        get_element_ids(docid, _matching_elements);
        for (uint32_t element_id : _matching_elements) {
            _tfmd->appendPosition(fef::TermFieldMatchDataPosition(element_id, 0, 1, 1));
        }
    } else {
        _tfmd->resetOnlyDocId(docid);
    }
}

/**
 * Implementation of ArrayBoolSearch that handles only a single element id.
 */
template <bool want_true, bool strict> class ArrayBoolSearchSingleImpl : public ArrayBoolSearch {
    uint32_t _element_id;

public:
    ArrayBoolSearchSingleImpl(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                              fef::TermFieldMatchData* tfmd, bool unpack_element_positions)
        : ArrayBoolSearch(attr, element_filter, tfmd, unpack_element_positions), _element_id(0) {
        assert(element_filter.size() == 1);
        _element_id = element_filter[0];
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False; }

    void doSeek(uint32_t docid) override {
        while (docid < getEndId()) {
            if (check_array(docid)) {
                setDocId(docid);
                return;
            }
            if (strict) {
                ++docid;
            } else {
                return;
            }
        }
        setAtEnd();
    }

    bool check_array(uint32_t docid) const {
        auto bools = _attr.get_bools(docid);
        return _element_id < bools.size() && bools[_element_id] == want_true;
    }

    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override {
        auto bools = _attr.get_bools(docid);

        if (_element_id < bools.size() && bools[_element_id] == want_true) {
            element_ids.push_back(_element_id);
        }
    }

    bool get_want_true() const override { return want_true; }
};

/**
 * Standard implementation of ArrayBoolSearch (with support for multiple element ids).
 */
template <bool want_true, bool strict> class ArrayBoolSearchMultiImpl : public ArrayBoolSearch {
public:
    ArrayBoolSearchMultiImpl(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                             fef::TermFieldMatchData* tfmd, bool unpack_element_positions)
        : ArrayBoolSearch(attr, element_filter, tfmd, unpack_element_positions) {}

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False; }

    void doSeek(uint32_t docid) override {
        while (docid < getEndId()) {
            if (check_array(docid)) {
                setDocId(docid);
                return;
            }
            if (strict) {
                ++docid;
            } else {
                return;
            }
        }
        setAtEnd();
    }

    bool check_array(uint32_t docid) const {
        auto bools = _attr.get_bools(docid);

        for (auto i : _element_filter) {
            if (i >= bools.size()) {
                break;
            }
            if (bools[i] == want_true) {
                return true;
            }
        }

        return false;
    }

    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override {
        auto bools = _attr.get_bools(docid);

        for (auto i : _element_filter) {
            if (i >= bools.size()) {
                break;
            }
            if (bools[i] == want_true) {
                element_ids.push_back(i);
            }
        }
    }

    bool get_want_true() const override { return want_true; }
};

namespace {

template <bool want_true, bool strict>
std::unique_ptr<ArrayBoolSearch> resolve_multi(const attribute::ArrayBoolAttribute& attr,
                                               const std::vector<uint32_t>&         element_filter,
                                               fef::TermFieldMatchData* tfmd, bool unpack_element_positions) {
    if (element_filter.size() == 1) {
        return std::make_unique<ArrayBoolSearchSingleImpl<want_true, strict>>(attr, element_filter, tfmd,
                                                                              unpack_element_positions);
    } else {
        return std::make_unique<ArrayBoolSearchMultiImpl<want_true, strict>>(attr, element_filter, tfmd,
                                                                             unpack_element_positions);
    }
}

template <bool want_true>
std::unique_ptr<ArrayBoolSearch> resolve_strict(bool strict, const attribute::ArrayBoolAttribute& attr,
                                                const std::vector<uint32_t>& element_filter,
                                                fef::TermFieldMatchData* tfmd, bool unpack_element_positions) {
    if (strict) {
        return resolve_multi<want_true, true>(attr, element_filter, tfmd, unpack_element_positions);
    } else {
        return resolve_multi<want_true, false>(attr, element_filter, tfmd, unpack_element_positions);
    }
}

std::unique_ptr<ArrayBoolSearch> resolve_want_true(bool want_true, bool strict,
                                                   const attribute::ArrayBoolAttribute& attr,
                                                   const std::vector<uint32_t>&         element_filter,
                                                   fef::TermFieldMatchData* tfmd, bool unpack_element_positions) {
    if (want_true) {
        return resolve_strict<true>(strict, attr, element_filter, tfmd, unpack_element_positions);
    } else {
        return resolve_strict<false>(strict, attr, element_filter, tfmd, unpack_element_positions);
    }
}

} // namespace

std::unique_ptr<ArrayBoolSearch> ArrayBoolSearch::create(const ArrayBoolAttribute&    attr,
                                                         const std::vector<uint32_t>& element_filter, bool want_true,
                                                         bool strict, fef::TermFieldMatchData* tfmd,
                                                         bool unpack_element_positions) {
    return resolve_want_true(want_true, strict, attr, element_filter, tfmd, unpack_element_positions);
}

} // namespace search::queryeval
