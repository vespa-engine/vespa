// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "integer_term_vector.h"
#include <cassert>
#include <charconv>

namespace search::query {

IntegerTermVector::IntegerTermVector(uint32_t sz)
    : _terms()
{
    _terms.reserve(sz);
}

IntegerTermVector::~IntegerTermVector() = default;

void
IntegerTermVector::addTerm(vespalib::stringref, Weight)
{
    // Will/should never happen
    assert(false);
}

void
IntegerTermVector::addTerm(int64_t, Weight)
{
    // Will/should never happen
    assert(false);
}

void
IntegerTermVector::addTerm(int64_t term)
{
    _terms.emplace_back(term);
}

TermVector::StringAndWeight
IntegerTermVector::getAsString(uint32_t index) const
{
    const auto & v = _terms[index];
    auto res = std::to_chars(_scratchPad, _scratchPad + sizeof(_scratchPad) - 1, v, 10);
    res.ptr[0] = '\0';
    return {vespalib::stringref(_scratchPad, res.ptr - _scratchPad), Weight(1)};
}

TermVector::IntegerAndWeight
IntegerTermVector::getAsInteger(uint32_t index) const
{
    return {_terms[index], Weight(1)};
}

Weight
IntegerTermVector::getWeight(uint32_t) const
{
    return Weight(1);

}

uint32_t
IntegerTermVector::size() const
{
    return _terms.size();
}

}
