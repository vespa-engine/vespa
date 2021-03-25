// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "location.h"
#include "predicate_query_term.h"
#include "querynodemixin.h"
#include "range.h"
#include "term.h"

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


}
