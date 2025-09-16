// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_string_term_vector.h"
#include <cassert>
#include <charconv>

namespace search::query {

WeightedStringTermVector::WeightedStringTermVector(uint32_t sz) : _terms() { _terms.reserve(sz); }

void WeightedStringTermVector::addTerm(std::string_view term, Weight weight) {
    _terms.emplace_back(term, weight);
}

void WeightedStringTermVector::addTerm(int64_t value, Weight weight) {
    char buf[24];
    auto res = std::to_chars(buf, buf + sizeof(buf), value, 10);
    addTerm(std::string_view(buf, res.ptr - buf), weight);
}

TermVector::StringAndWeight WeightedStringTermVector::getAsString(uint32_t index) const {
    const auto & v = _terms[index];
    return {v.first, v.second};
}

TermVector::IntegerAndWeight WeightedStringTermVector::getAsInteger(uint32_t index) const {
    const auto & v = _terms[index];
    int64_t value(0);
    std::from_chars(v.first.c_str(), v.first.c_str() + v.first.size(), value);
    return {value, v.second};
}

Weight WeightedStringTermVector::getWeight(uint32_t index) const {
    return _terms[index].second;
}

uint32_t WeightedStringTermVector::size() const { return _terms.size(); }

WeightedStringTermVector::~WeightedStringTermVector() = default;

}
