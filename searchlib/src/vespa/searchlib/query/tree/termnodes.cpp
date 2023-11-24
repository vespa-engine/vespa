// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termnodes.h"
#include <vespa/vespalib/util/exceptions.h>
#include <charconv>
#include <cassert>

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
FuzzyTerm::~FuzzyTerm() = default;
InTerm::~InTerm() = default;

namespace {

class WeightedStringTermVector final : public TermVector {
public:
    explicit WeightedStringTermVector(uint32_t sz) : _terms() { _terms.reserve(sz); }
    ~WeightedStringTermVector() override;
    void addTerm(stringref term, Weight weight) override {
        _terms.emplace_back(term, weight);
    }
    void addTerm(int64_t value, Weight weight) override {
        char buf[24];
        auto res = std::to_chars(buf, buf + sizeof(buf), value, 10);
        addTerm(stringref(buf, res.ptr - buf), weight);
    }
    [[nodiscard]] StringAndWeight getAsString(uint32_t index) const override {
        const auto & v = _terms[index];
        return {v.first, v.second};
    }
    [[nodiscard]] IntegerAndWeight getAsInteger(uint32_t index) const override {
        const auto & v = _terms[index];
        int64_t value(0);
        std::from_chars(v.first.c_str(), v.first.c_str() + v.first.size(), value);
        return {value, v.second};
    }
    [[nodiscard]] Weight getWeight(uint32_t index) const override {
        return _terms[index].second;
    }
    [[nodiscard]] uint32_t size() const override { return _terms.size(); }
private:
    std::vector<std::pair<vespalib::string, Weight>> _terms;
};

class WeightedIntegerTermVector final : public TermVector {
public:
    explicit WeightedIntegerTermVector(uint32_t sz) : _terms() { _terms.reserve(sz); }
    void addTerm(stringref, Weight) override {
        // Will/should never happen
        assert(false);
    }
    void addTerm(int64_t term, Weight weight) override {
        _terms.emplace_back(term, weight);
    }
    StringAndWeight getAsString(uint32_t index) const override {
        const auto & v = _terms[index];
        auto res = std::to_chars(_scratchPad, _scratchPad + sizeof(_scratchPad)-1, v.first, 10);
        res.ptr[0] = '\0';
        return {stringref(_scratchPad, res.ptr - _scratchPad), v.second};
    }
    IntegerAndWeight getAsInteger(uint32_t index) const override {
        return _terms[index];
    }
    Weight getWeight(uint32_t index) const override {
        return _terms[index].second;
    }
    uint32_t size() const override { return _terms.size(); }
private:
    std::vector<IntegerAndWeight> _terms;
    mutable char                  _scratchPad[24];
};

WeightedStringTermVector::~WeightedStringTermVector() = default;

}

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
MultiTerm::addTerm(vespalib::stringref term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<WeightedStringTermVector>(_num_terms);
        _type = Type::STRING;
    }
    if (_type == Type::INTEGER) {
        _terms = downgrade();
        _type = Type::STRING;
    }
    _terms->addTerm(term, weight);
}

void
MultiTerm::addTerm(int64_t term, Weight weight) {
    if ( ! _terms) {
        _terms = std::make_unique<WeightedIntegerTermVector>(_num_terms);
        _type = Type::INTEGER;
    }
    _terms->addTerm(term, weight);
}

}
