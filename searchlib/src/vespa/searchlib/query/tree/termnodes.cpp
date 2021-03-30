// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termnodes.h"
#include <vespa/vespalib/util/exceptions.h>
#include <charconv>

using vespalib::IllegalArgumentException;
using vespalib::stringref;
using vespalib::make_string_short::fmt;
namespace search::query {

StringTerm::StringTerm(const Type &term, vespalib::stringref view, int32_t id, Weight weight)
    : QueryNodeMixinType(term, view, id, weight)
{}

NumberTerm::~NumberTerm() = default;
PrefixTerm::~PrefixTerm() = default;
RangeTerm::~RangeTerm() = default;
StringTerm::~StringTerm() = default;
SubstringTerm::~SubstringTerm() = default;
SuffixTerm::~SuffixTerm() = default;
LocationTerm::~LocationTerm() = default;
RegExpTerm::~RegExpTerm() = default;
WeightedSetTerm::~WeightedSetTerm() = default;
DotProduct::~DotProduct() = default;
WandTerm::~WandTerm() = default;

namespace {

void badType(const char *expected, const char *got) __attribute__((noinline));
void badType(const char *expected, const char *got) {
    throw IllegalArgumentException(fmt("Expected '%s' type, got '%s'", expected, got), VESPA_STRLOC);
}

class StringTermVector final : public MultiTerm::TermVector {
public:
    StringTermVector(uint32_t sz) : _terms() { _terms.reserve(sz); }
    void addTerm(stringref term, Weight weight) override {
        _terms.emplace_back(term, weight);
    }
    void addTerm(int64_t, Weight) override {
        badType("string", "int64_t");
    }
    StringAndWeight getAsString(uint32_t index) const override {
        const auto & v = _terms[index];
        return StringAndWeight(v.first, v.second);
    }
    IntegerAndWeight getAsInteger(uint32_t index) const override {
        const auto & v = _terms[index];
        int64_t value(0);
        std::from_chars(v.first.c_str(), v.first.c_str() + v.first.size(), value);
        return IntegerAndWeight(value, v.second);
    }
    Weight getWeight(uint32_t index) const override {
        return _terms[index].second;
    }
private:
    std::vector<std::pair<vespalib::string, Weight>> _terms;
};

class IntegerTermVector final : public MultiTerm::TermVector {
public:
    IntegerTermVector(uint32_t sz) : _terms() { _terms.reserve(sz); }
    void addTerm(stringref, Weight) override {
        badType("int64_t", "string");
    }
    void addTerm(int64_t term, Weight weight) override {
        _terms.emplace_back(term, weight);
    }
    StringAndWeight getAsString(uint32_t index) const override {
        const auto & v = _terms[index];
        auto res = std::to_chars(_scratchPad, _scratchPad + sizeof(_scratchPad)-1, v.first, 10);
        res.ptr[0] = '\0';
        return StringAndWeight(stringref(_scratchPad, res.ptr - _scratchPad), v.second);
    }
    IntegerAndWeight getAsInteger(uint32_t index) const override {
        return _terms[index];
    }
    Weight getWeight(uint32_t index) const override {
        return _terms[index].second;
    }
private:
    std::vector<IntegerAndWeight> _terms;
    mutable char                  _scratchPad[24];
};

}

MultiTerm::MultiTerm(uint32_t num_terms)
    : _terms(),
      _num_terms(num_terms),
      _type(Type::UNKNOWN)
{}

MultiTerm::~MultiTerm() = default;

void
MultiTerm::addTerm(vespalib::stringref term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<StringTermVector>(_num_terms);
        _type = Type::STRING;
    }
    _terms->addTerm(term, weight);
}

void
MultiTerm::addTerm(int64_t term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<IntegerTermVector>(_num_terms);
        _type = Type::INTEGER;
    }
    _terms->addTerm(term, weight);
}

}
