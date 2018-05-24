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
    NumberTerm(Type term, const vespalib::stringref &view, int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight) {}
    virtual ~NumberTerm() = 0;
};

//-----------------------------------------------------------------------------

class PrefixTerm : public QueryNodeMixin<PrefixTerm, StringBase >
{
public:
    PrefixTerm(const Type &term, const vespalib::stringref &view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~PrefixTerm() = 0;
};

//-----------------------------------------------------------------------------

class RangeTerm : public QueryNodeMixin<RangeTerm, TermBase<Range> >
{
public:
    RangeTerm(const Type& term, const vespalib::stringref &view,
              int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RangeTerm() = 0;
};

//-----------------------------------------------------------------------------

class StringTerm : public QueryNodeMixin<StringTerm, StringBase >
{
public:
    StringTerm(const Type &term, const vespalib::stringref &view,
               int32_t id, Weight weight);
    virtual ~StringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SubstringTerm :
        public QueryNodeMixin<SubstringTerm, StringBase >
{
 public:
    SubstringTerm(const Type &term, const vespalib::stringref &view,
                  int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SubstringTerm() = 0;
};

//-----------------------------------------------------------------------------

class SuffixTerm : public QueryNodeMixin<SuffixTerm, StringBase >
{
public:
    SuffixTerm(const Type &term, const vespalib::stringref &view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~SuffixTerm() = 0;
};

//-----------------------------------------------------------------------------

class LocationTerm : public QueryNodeMixin<LocationTerm, TermBase<Location> >
{
public:
    LocationTerm(const Type &term, const vespalib::stringref &view,
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
    PredicateQuery(PredicateQueryTerm::UP term, const vespalib::stringref &view,
                   int32_t id, Weight weight)
        : QueryNodeMixinType(std::move(term), view, id, weight)
    {}
};

//-----------------------------------------------------------------------------

class RegExpTerm : public QueryNodeMixin<RegExpTerm, StringBase>
{
public:
    RegExpTerm(const Type &term, const vespalib::stringref &view,
               int32_t id, Weight weight)
        : QueryNodeMixinType(term, view, id, weight)
    {}
    virtual ~RegExpTerm() = 0;
};


}
