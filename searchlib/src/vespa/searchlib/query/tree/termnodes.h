// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "location.h"
#include "predicate_query_term.h"
#include "querynodemixin.h"
#include "range.h"
#include "term.h"
#include "term_vector.h"
#include "const_bool_nodes.h"
#include <vespa/vespalib/util/vespa_dll_local.h>
#include <limits>

namespace search::query {

using StringBase = TermBase<std::string>;

class NumberTerm : public QueryNodeMixin<NumberTerm, StringBase >
{
public:
    NumberTerm(Type term, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight) {}
    virtual ~NumberTerm() = 0;
};

//-----------------------------------------------------------------------------

class PrefixTerm : public QueryNodeMixin<PrefixTerm, StringBase >
{
public:
    PrefixTerm(const Type &term, const std::string & view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~PrefixTerm() = 0;
};

//-----------------------------------------------------------------------------

class RangeTerm : public QueryNodeMixin<RangeTerm, TermBase<Range> >
{
public:
    RangeTerm(const Type& term, const std::string & view,
              int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RangeTerm() = 0;
};

//-----------------------------------------------------------------------------

class StringTerm : public QueryNodeMixin<StringTerm, StringBase >
{
public:
    StringTerm(const Type &term, std::string view, int32_t id, Weight weight);
    virtual ~StringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SubstringTerm : public QueryNodeMixin<SubstringTerm, StringBase >
{
 public:
    SubstringTerm(const Type &term, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SubstringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SuffixTerm : public QueryNodeMixin<SuffixTerm, StringBase >
{
public:
    SuffixTerm(const Type &term, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SuffixTerm() = 0;
};

//-----------------------------------------------------------------------------

class LocationTerm : public QueryNodeMixin<LocationTerm, TermBase<Location> >
{
public:
    LocationTerm(const Type &term, const std::string & view, int32_t id, Weight weight)
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
    PredicateQuery(PredicateQueryTerm::UP term, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(std::move(term), view, id, weight)
    {}
};

//-----------------------------------------------------------------------------

class RegExpTerm : public QueryNodeMixin<RegExpTerm, StringBase>
{
public:
    RegExpTerm(const Type &term, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RegExpTerm() = 0;
};

//-----------------------------------------------------------------------------

class FuzzyTerm : public QueryNodeMixin<FuzzyTerm, StringBase> {
    uint32_t _max_edit_distance;
    uint32_t _prefix_lock_length;
    // Prefix match mode is stored in parent Term
public:
    FuzzyTerm(const Type &term, const std::string & view, int32_t id, Weight weight, uint32_t max_edit_distance,
              uint32_t prefix_lock_length, bool prefix_match)
        : QueryNodeMixinType(term, view, id, weight),
          _max_edit_distance(max_edit_distance),
          _prefix_lock_length(prefix_lock_length)
    {
        set_prefix_match(prefix_match);
    }

    [[nodiscard]] uint32_t max_edit_distance() const { return _max_edit_distance; }
    [[nodiscard]] uint32_t prefix_lock_length() const { return _prefix_lock_length; }

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
public:
    struct HnswParams {
        std::optional<double> distance_threshold;
        std::optional<double> approximate_threshold;
        std::optional<double> exploration_slack;
        std::optional<double> filter_first_exploration;
        std::optional<double> filter_first_threshold;
        std::optional<double> post_filter_threshold;
        std::optional<double> target_hits_max_adjustment_factor;
        std::optional<uint32_t> explore_additional_hits;

        HnswParams() = default;
    };

private:
    std::string _query_tensor_name;
    uint32_t _target_num_hits;
    bool _allow_approximate;
    HnswParams _hnsw_params;

public:
    NearestNeighborTerm(std::string_view query_tensor_name, std::string field_name,
                        int32_t id, Weight weight, uint32_t target_num_hits,
                        bool allow_approximate,
                        HnswParams hnsw_params = HnswParams())
        : QueryNodeMixinType(std::move(field_name), id, weight),
          _query_tensor_name(query_tensor_name),
          _target_num_hits(target_num_hits),
          _allow_approximate(allow_approximate),
          _hnsw_params(std::move(hnsw_params))
    {}
    ~NearestNeighborTerm() override;
    const std::string& get_query_tensor_name() const { return _query_tensor_name; }
    uint32_t get_target_num_hits() const { return _target_num_hits; }
    bool get_allow_approximate() const { return _allow_approximate; }
    uint32_t get_explore_additional_hits() const { return _hnsw_params.explore_additional_hits.value_or(0); }
    const HnswParams& get_hnsw_params() const { return _hnsw_params; }
    double get_distance_threshold() const { return _hnsw_params.distance_threshold.value_or(std::numeric_limits<double>::infinity()); }
};

class MultiTerm : public Node {
public:
    enum class Type {STRING, INTEGER, WEIGHTED_STRING, WEIGHTED_INTEGER, UNKNOWN};
    using StringAndWeight = TermVector::StringAndWeight;
    using IntegerAndWeight = TermVector::IntegerAndWeight;
    ~MultiTerm() override;
    void addTerm(std::string_view term, Weight weight);
    void addTerm(int64_t term, Weight weight);
    // Note that the first refers to a zero terminated string.
    // That is required as the comparator for the enum store requires it.
    StringAndWeight getAsString(uint32_t index) const { return _terms->getAsString(index); }
    IntegerAndWeight getAsInteger(uint32_t index) const { return _terms->getAsInteger(index); }
    Weight weight(uint32_t index) const { return _terms->getWeight(index); }
    uint32_t getNumTerms() const { return _num_terms; }
    Type getType() const { return _type; }
protected:
    explicit MultiTerm(uint32_t num_terms);
    MultiTerm(std::unique_ptr<TermVector> terms, Type type);
private:
    VESPA_DLL_LOCAL std::unique_ptr<TermVector> downgrade() __attribute__((noinline));
    std::unique_ptr<TermVector> _terms;
    uint32_t _num_terms;
    Type _type;
};

class WeightedSetTerm : public QueryNodeMixin<WeightedSetTerm, MultiTerm>, public Term {
public:
    WeightedSetTerm(uint32_t num_terms, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight)
    {}
    WeightedSetTerm(std::unique_ptr<TermVector> terms, Type type, const std::string & view, int32_t id, Weight weight)
      : QueryNodeMixinType(std::move(terms), type),
        Term(view, id, weight)
    {}
    virtual ~WeightedSetTerm() = 0;
};

class DotProduct : public QueryNodeMixin<DotProduct, MultiTerm>, public Term {
public:
    DotProduct(uint32_t num_terms, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight)
    {}
    DotProduct(std::unique_ptr<TermVector> terms, Type type, const std::string & view, int32_t id, Weight weight)
      : QueryNodeMixinType(std::move(terms), type),
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
    WandTerm(uint32_t num_terms, const std::string & view, int32_t id, Weight weight,
             uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : QueryNodeMixinType(num_terms),
          Term(view, id, weight),
          _targetNumHits(targetNumHits),
          _scoreThreshold(scoreThreshold),
          _thresholdBoostFactor(thresholdBoostFactor)
    {}
    WandTerm(std::unique_ptr<TermVector> terms, Type type,
             const std::string & view, int32_t id, Weight weight,
             uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : QueryNodeMixinType(std::move(terms), type),
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

class InTerm : public QueryNodeMixin<InTerm, MultiTerm>, public Term {
public:
    InTerm(std::unique_ptr<TermVector> terms, MultiTerm::Type type, const std::string & view, int32_t id, Weight weight)
        : QueryNodeMixinType(std::move(terms), type),
          Term(view, id, weight)
    {
    }
    virtual ~InTerm() = 0;
};

class WordAlternatives : public QueryNodeMixin<WordAlternatives, TermNode> {
    std::vector<std::unique_ptr<StringTerm>> _children;
public:
    virtual ~WordAlternatives() = 0;
    const std::vector<std::unique_ptr<StringTerm>> &getChildren() const noexcept { return _children; }
    uint32_t getNumTerms() const noexcept { return _children.size(); }

    WordAlternatives(std::vector<std::unique_ptr<StringTerm>> children,
                     const std::string & view, int32_t id, Weight weight)
      : QueryNodeMixinType(view, id, weight),
        _children(std::move(children))
    {}

    // compatibility layer
    WordAlternatives(std::unique_ptr<TermVector> terms, const std::string & view, int32_t id, Weight weight);
};


}
