// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_term_vector.h"
#include <cassert>
#include <charconv>

namespace search::query {

StringTermVector::StringTermVector(uint32_t sz)
    : _terms()
{
    _terms.reserve(sz);
}

StringTermVector::~StringTermVector() = default;

void
StringTermVector::addTerm(vespalib::stringref, Weight)
{
    // Will/should never happen
    assert(false);
}

void
StringTermVector::addTerm(int64_t, Weight)
{
    // Will/should never happen
    assert(false);
}

void
StringTermVector::addTerm(vespalib::stringref term)
{
    _terms.emplace_back(term);
}

TermVector::StringAndWeight
StringTermVector::getAsString(uint32_t index) const
{
    const auto & v = _terms[index];
    return {v, Weight(1)};
}


TermVector::IntegerAndWeight
StringTermVector::getAsInteger(uint32_t index) const
{
    const auto & v = _terms[index];
    int64_t value(0);
    std::from_chars(v.c_str(), v.c_str() + v.size(), value);
    return {value, Weight(1)};
}

Weight
StringTermVector::getWeight(uint32_t) const
{
    return Weight(1);
}

uint32_t
StringTermVector::size() const
{
    return _terms.size();
}

}
