// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "location.h"
#include "predicate_query_term.h"
#include "querynodemixin.h"
#include "range.h"
#include "term.h"
#include "const_bool_nodes.h"

namespace search::query {

typedef TermBase<vespalib::string> StringBase;

class NumberTerm : public QueryNodeMixin<NumberTerm, StringBase >
{
public:
    NumberTerm(Type term, vespalib::stringref view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight) {}
    virtual ~NumberTerm() = 0;
};

//-----------------------------------------------------------------------------

class PrefixTerm : public QueryNodeMixin<PrefixTerm, StringBase >
{
public:
    PrefixTerm(const Type &term, vespalib::stringref view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~PrefixTerm() = 0;
};

//-----------------------------------------------------------------------------

class RangeTerm : public QueryNodeMixin<RangeTerm, TermBase<Range> >
{
public:
    RangeTerm(const Type& term, vespalib::stringref view,
              int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RangeTerm() = 0;
};

//-----------------------------------------------------------------------------

class StringTerm : public QueryNodeMixin<StringTerm, StringBase >
{
public:
    StringTerm(const Type &term, vespalib::stringref view, int32_t id, Weight weight);
    virtual ~StringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SubstringTerm : public QueryNodeMixin<SubstringTerm, StringBase >
{
 public:
    SubstringTerm(const Type &term, vespalib::stringref view,
                  int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SubstringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SuffixTerm : public QueryNodeMixin<SuffixTerm, StringBase >
{
public:
    SuffixTerm(const Type &term, vespalib::stringref view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SuffixTerm() = 0;
};

//-----------------------------------------------------------------------------

class LocationTerm : public QueryNodeMixin<LocationTerm, TermBase<Location> >
{
public:
    LocationTerm(const Type &term, vespalib::stringref view,
                 int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    bool isLocationTerm() const override { return true; }
    virtual ~LocationTerm() = 0;
};

//-----------------------------------------------------------------------------

class PredicateQuery : public QueryNodeMixin<PredicateQuery,
                                             TermBase<PredicateQueryTerm::UP> >
{
public:
    PredicateQuery(PredicateQueryTerm::UP term, vespalib::stringref view,
                   int32_t id, Weight weight)
        : QueryNodeMixinType(std::move(term), view, id, weight)
    {}
};

//-----------------------------------------------------------------------------

class RegExpTerm : public QueryNodeMixin<RegExpTerm, StringBase>
{
public:
    RegExpTerm(const Type &term, vespalib::stringref view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RegExpTerm() = 0;
};

//-----------------------------------------------------------------------------

class FuzzyTerm : public QueryNodeMixin<FuzzyTerm, StringBase> {
private:
    uint32_t _maxEditDistance;
    uint32_t _prefixLength;
public:
    FuzzyTerm(const Type &term, vespalib::stringref view,
               int32_t id, Weight weight, uint32_t maxEditDistance, uint32_t prefixLength)
            : QueryNodeMixinType(term, view, id, weight),
              _maxEditDistance(maxEditDistance),
              _prefixLength(prefixLength)
    {}

    uint32_t getMaxEditDistance() const { return _maxEditDistance; }
    uint32_t getPrefixLength() const { return _prefixLength; }

    virtual ~FuzzyTerm() = 0;
};

/**
 * Term matching the K nearest neighbors in a multi-dimensional vector space.
 *
 * The query point is specified as a dense tensor of order 1.
 * This is found in fef::IQueryEnvironment using the query tensor name as key.
 * The field name is the name of a dense document tensor of order 1.
 * Both tensors are validated to have the same tensor type before the query is sent to the backend.
 *
 * Target num hits (K) is a hint to how many neighbors to return.
 * The actual returned number might be higher (or lower if the query returns fewer hits).
 */
class NearestNeighborTerm : public QueryNodeMixin<NearestNeighborTerm, TermNode> {
private:
    vespalib::string _query_tensor_name;
    uint32_t _target_num_hits;
    bool _allow_approximate;
    uint32_t _explore_additional_hits;
    double _distance_threshold;

public:
    NearestNeighborTerm(vespalib::stringref query_tensor_name, vespalib::stringref field_name,
                        int32_t id, Weight weight, uint32_t target_num_hits,
                        bool allow_approximate, uint32_t explore_additional_hits,
                        double distance_threshold)
        : QueryNodeMixinType(field_name, id, weight),
          _query_tensor_name(query_tensor_name),
          _target_num_hits(target_num_hits),
          _allow_approximate(allow_approximate),
          _explore_additional_hits(explore_additional_hits),
          _distance_threshold(distance_threshold)
    {}
    virtual ~NearestNeighborTerm() {}
    const vespalib::string& get_query_tensor_name() const { return _query_tensor_name; }
    uint32_t get_target_num_hits() const { return _target_num_hits; }
    bool get_allow_approximate() const { return _allow_approximate; }
    uint32_t get_explore_additional_hits() const { return _explore_additional_hits; }
    double get_distance_threshold() const { return _distance_threshold; }
};

class MultiTerm : public Node {
public:
    enum class Type {STRING, INTEGER, UNKNOWN};
    using StringAndWeight = std::pair<vespalib::stringref, Weight>;
    using IntegerAndWeight = std::pair<int64_t, Weight>;
    struct TermVector {
        using StringAndWeight = MultiTerm::StringAndWeight;
        using IntegerAndWeight = MultiTerm::IntegerAndWeight;
        virtual ~TermVector() = default;
        virtual void addTerm(vespalib::stringref term, Weight weight) = 0;
        virtual void addTerm(int64_t term, Weight weight) = 0;
        virtual StringAndWeight getAsString(uint32_t index) const = 0;
        virtual IntegerAndWeight getAsInteger(uint32_t index) const = 0;
        virtual Weight getWeight(uint32_t index) const = 0;
        virtual uint32_t size() const = 0;
    };
    ~MultiTerm() override;
    void addTerm(vespalib::stringref term, Weight weight);
    void addTerm(int64_t term, Weight weight);
    // Note that the first refers to a zero terminated string.
    // That is required as the comparator for the enum store requires it.
    StringAndWeight getAsString(uint32_t index) const { return _terms->getAsString(index); }
    IntegerAndWeight getAsInteger(uint32_t index) const { return _terms->getAsInteger(index); }
    Weight weight(uint32_t index) const { return _terms->getWeight(index); }
    uint32_t getNumTerms() const { return _num_terms; }
    Type getType() const { return _type; }
protected:
    MultiTerm(uint32_t num_terms);
private:
    VESPA_DLL_LOCAL std::unique_ptr<TermVector> downgrade() __attribute__((noinline));
    std::unique_ptr<TermVector> _terms;
    uint32_t _num_terms;
    Type _type;
};

class WeightedSetTerm : public QueryNodeMixin<WeightedSetTerm, MultiTerm>, public Term {
public:
    WeightedSetTerm(uint32_t num_terms, const vespalib::string &view, int32_t id, Weight weight)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight)
    {}
    virtual ~WeightedSetTerm() = 0;
};

class DotProduct : public QueryNodeMixin<DotProduct, MultiTerm>, public Term {
public:
    DotProduct(uint32_t num_terms, const vespalib::string &view, int32_t id, Weight weight)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight)
    {}
    virtual ~DotProduct() = 0;
};

class WandTerm : public QueryNodeMixin<WandTerm, MultiTerm>, public Term {
private:
    uint32_t _targetNumHits;
    int64_t  _scoreThreshold;
    double   _thresholdBoostFactor;
public:
    WandTerm(uint32_t num_terms, const vespalib::string &view, int32_t id, Weight weight,
             uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight),
          _targetNumHits(targetNumHits),
          _scoreThreshold(scoreThreshold),
          _thresholdBoostFactor(thresholdBoostFactor)
    {}
    virtual ~WandTerm() = 0;
    uint32_t getTargetNumHits() const { return _targetNumHits; }
    int64_t getScoreThreshold() const { return _scoreThreshold; }
    double getThresholdBoostFactor() const { return _thresholdBoostFactor; }
};

}
