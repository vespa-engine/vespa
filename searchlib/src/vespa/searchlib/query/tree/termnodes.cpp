// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termnodes.h"
#include "weighted_integer_term_vector.h"
#include "weighted_string_term_vector.h"
#include <vespa/vespalib/util/exceptions.h>
#include <charconv>
#include <cassert>

using vespalib::IllegalArgumentException;
using vespalib::make_string_short::fmt;
namespace search::query {

StringTerm::StringTerm(const Type &term, std::string view, int32_t id, Weight weight)
    : QueryNodeMixinType(term, std::move(view), id, weight)
{}

NumberTerm::~NumberTerm() = default;
PrefixTerm::~PrefixTerm() = default;
RangeTerm::~RangeTerm() = default;
StringTerm::~StringTerm() = default;
SubstringTerm::~SubstringTerm() = default;
SuffixTerm::~SuffixTerm() = default;
LocationTerm::~LocationTerm() = default;
RegExpTerm::~RegExpTerm() = default;
NearestNeighborTerm::~NearestNeighborTerm() = default;
WeightedSetTerm::~WeightedSetTerm() = default;
DotProduct::~DotProduct() = default;
WandTerm::~WandTerm() = default;
FuzzyTerm::~FuzzyTerm() = default;
InTerm::~InTerm() = default;

MultiTerm::MultiTerm(uint32_t num_terms)
    : _terms(),
      _num_terms(num_terms),
      _type(Type::UNKNOWN)
{}

MultiTerm::MultiTerm(std::unique_ptr<TermVector> terms, Type type)
    : _terms(std::move(terms)),
      _num_terms(_terms->size()),
      _type(type)
{
}

MultiTerm::~MultiTerm() = default;

std::unique_ptr<TermVector>
MultiTerm::downgrade() {
    // Downgrade all number to string. This should really not happen
    auto new_terms = std::make_unique<WeightedStringTermVector>(_num_terms);
    for (uint32_t i(0), m(_terms->size()); i < m; i++) {
        auto v = _terms->getAsString(i);
        new_terms->addTerm(v.first, v.second);
    }
    return new_terms;
}

void
MultiTerm::addTerm(std::string_view term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<WeightedStringTermVector>(_num_terms);
        _type = Type::WEIGHTED_STRING;
    }
    if (_type != Type::WEIGHTED_STRING) {
        _terms = downgrade();
        _type = Type::WEIGHTED_STRING;
    }
    _terms->addTerm(term, weight);
}

void
MultiTerm::addTerm(int64_t term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<WeightedIntegerTermVector>(_num_terms);
        _type = Type::WEIGHTED_INTEGER;
    }
    _terms->addTerm(term, weight);
}

}
