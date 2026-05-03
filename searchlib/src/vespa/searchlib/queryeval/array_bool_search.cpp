// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_search.h"

#include <vespa/searchlib/fef/termfieldmatchdata.h>

#include <cassert>

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.array_bool_search");

namespace search::queryeval {

ArrayBoolSearch::ArrayBoolSearch(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                                 fef::TermFieldMatchData* tfmd)
    : _attr(attr), _element_filter(element_filter), _tfmd(tfmd) {
    assert(!_element_filter.empty());
    assert(tfmd != nullptr);
}

/**
 * Implementation of ArrayBoolSearch that handles only a single element id.
 */
template <bool want_true, bool strict> class ArrayBoolSearchSingleImpl : public ArrayBoolSearch {
    uint32_t _element_id;

public:
    ArrayBoolSearchSingleImpl(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter,
                              fef::TermFieldMatchData* tfmd)
        : ArrayBoolSearch(attr, element_filter, tfmd), _element_id(0) {
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

    void doUnpack(uint32_t docid) override { _tfmd->resetOnlyDocId(docid); }

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
                             fef::TermFieldMatchData* tfmd)
        : ArrayBoolSearch(attr, element_filter, tfmd) {}

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

    void doUnpack(uint32_t docid) override { _tfmd->resetOnlyDocId(docid); }

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
                                               fef::TermFieldMatchData*             tfmd) {
    if (element_filter.size() == 1) {
        return std::make_unique<ArrayBoolSearchSingleImpl<want_true, strict>>(attr, element_filter, tfmd);
    } else {
        return std::make_unique<ArrayBoolSearchMultiImpl<want_true, strict>>(attr, element_filter, tfmd);
    }
}

template <bool want_true>
std::unique_ptr<ArrayBoolSearch> resolve_strict(bool strict, const attribute::ArrayBoolAttribute& attr,
                                                const std::vector<uint32_t>& element_filter,
                                                fef::TermFieldMatchData*     tfmd) {
    if (strict) {
        return resolve_multi<want_true, true>(attr, element_filter, tfmd);
    } else {
        return resolve_multi<want_true, false>(attr, element_filter, tfmd);
    }
}

std::unique_ptr<ArrayBoolSearch> resolve_want_true(bool want_true, bool strict,
                                                   const attribute::ArrayBoolAttribute& attr,
                                                   const std::vector<uint32_t>&         element_filter,
                                                   fef::TermFieldMatchData*             tfmd) {
    if (want_true) {
        return resolve_strict<true>(strict, attr, element_filter, tfmd);
    } else {
        return resolve_strict<false>(strict, attr, element_filter, tfmd);
    }
}

} // namespace

std::unique_ptr<ArrayBoolSearch> ArrayBoolSearch::create(const ArrayBoolAttribute&    attr,
                                                         const std::vector<uint32_t>& element_filter, bool want_true,
                                                         bool strict, fef::TermFieldMatchData* tfmd) {
    return resolve_want_true(want_true, strict, attr, element_filter, tfmd);
}

} // namespace search::queryeval
