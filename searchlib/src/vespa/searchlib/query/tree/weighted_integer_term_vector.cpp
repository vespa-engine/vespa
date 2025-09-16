// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_integer_term_vector.h"
#include <charconv>
#include <cassert>

namespace search::query {

WeightedIntegerTermVector::WeightedIntegerTermVector(uint32_t sz)
  : _terms(),
    _scratchPad()
{
    _terms.reserve(sz);
}

void WeightedIntegerTermVector::addTerm(std::string_view, Weight) {
    // Will/should never happen
    assert(false);
}

void WeightedIntegerTermVector::addTerm(int64_t term, Weight weight) {
    _terms.emplace_back(term, weight);
}

TermVector::StringAndWeight WeightedIntegerTermVector::getAsString(uint32_t index) const {
    const auto & v = _terms[index];
    auto res = std::to_chars(_scratchPad, _scratchPad + sizeof(_scratchPad)-1, v.first, 10);
    res.ptr[0] = '\0';
    return {std::string_view(_scratchPad, res.ptr - _scratchPad), v.second};
}

TermVector::IntegerAndWeight WeightedIntegerTermVector::getAsInteger(uint32_t index) const {
    return _terms[index];
}

Weight WeightedIntegerTermVector::getWeight(uint32_t index) const {
    return _terms[index].second;
}

uint32_t WeightedIntegerTermVector::size() const { return _terms.size(); }

WeightedIntegerTermVector::~WeightedIntegerTermVector() = default;

}
