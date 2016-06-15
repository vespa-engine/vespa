// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/simpletermfielddata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <memory>
#include <vector>

namespace proton {
namespace matching {

class ViewResolver;

class ProtonTermData : public search::fef::ITermData
{
public:
    typedef search::queryeval::FieldSpec FieldSpec;

    struct FieldEntry : search::fef::SimpleTermFieldData {
        vespalib::string field_name;
        bool attribute_field;
        bool filter_field;

        FieldEntry(const vespalib::string &name, uint32_t fieldId)
            : SimpleTermFieldData(fieldId),
              field_name(name),
              attribute_field(false),
              filter_field(false) {}

        FieldSpec fieldSpec() const {
            return FieldSpec(field_name, getFieldId(),
                             getHandle(), filter_field);
        }
        virtual search::fef::TermFieldHandle getHandle() const;
    };

private:
    std::vector<FieldEntry> _fields;

    void setDocumentFrequency(double docFreq);

protected:
    void resolve(const ViewResolver &resolver,
                 const search::fef::IIndexEnvironment &idxEnv,
                 const vespalib::string &view,
                 bool forceFilter);

public:
    void resolveFromChildren(const std::vector<search::query::Node *> &children);
    void allocateTerms(search::fef::MatchDataLayout &mdl);
    void setDocumentFrequency(uint32_t estHits, uint32_t numDocs);

    // ITermData interface
    virtual size_t numFields() const;
    virtual const FieldEntry &field(size_t i) const;
    virtual const FieldEntry *lookupField(uint32_t fieldId) const;
};

namespace {
template <typename NodeType> uint32_t numTerms(const NodeType &) { return 1; }
template <>
uint32_t numTerms<search::query::Phrase>(const search::query::Phrase &n) {
    return n.getChildren().size();
}
} // namespace proton::matching::<unnamed>

template <typename Base>
struct ProtonTerm : public Base,
                    public ProtonTermData
{
    using Base::Base;

    void resolve(const ViewResolver &resolver,
                 const search::fef::IIndexEnvironment &idxEnv)
    {
        bool forceFilter = !Base::usePositionData();
        ProtonTermData::resolve(resolver, idxEnv, Base::getView(), forceFilter);
    }

    // ITermData interface
    virtual uint32_t getPhraseLength() const { return numTerms<Base>(*this); }
    virtual uint32_t getTermIndex() const { return -1; }
    virtual search::query::Weight getWeight() const { return Base::getWeight(); }
    virtual uint32_t getUniqueId() const { return Base::getId(); }
};

typedef search::query::SimpleAnd     ProtonAnd;
typedef search::query::SimpleAndNot  ProtonAndNot;
typedef search::query::SimpleNear    ProtonNear;
typedef search::query::SimpleONear   ProtonONear;
typedef search::query::SimpleOr      ProtonOr;
typedef search::query::SimpleRank    ProtonRank;
typedef search::query::SimpleWeakAnd ProtonWeakAnd;

struct ProtonEquiv : public ProtonTerm<search::query::Equiv>
{
    search::fef::MatchDataLayout children_mdl;
    using ProtonTerm::ProtonTerm;
};

typedef ProtonTerm<search::query::LocationTerm>    ProtonLocationTerm;
typedef ProtonTerm<search::query::NumberTerm>      ProtonNumberTerm;
typedef ProtonTerm<search::query::Phrase>          ProtonPhrase;
typedef ProtonTerm<search::query::PrefixTerm>      ProtonPrefixTerm;
typedef ProtonTerm<search::query::RangeTerm>       ProtonRangeTerm;
typedef ProtonTerm<search::query::StringTerm>      ProtonStringTerm;
typedef ProtonTerm<search::query::SubstringTerm>   ProtonSubstringTerm;
typedef ProtonTerm<search::query::SuffixTerm>      ProtonSuffixTerm;
typedef ProtonTerm<search::query::WeightedSetTerm> ProtonWeightedSetTerm;
typedef ProtonTerm<search::query::DotProduct>      ProtonDotProduct;
typedef ProtonTerm<search::query::WandTerm>        ProtonWandTerm;
typedef ProtonTerm<search::query::PredicateQuery>  ProtonPredicateQuery;
typedef ProtonTerm<search::query::RegExpTerm>      ProtonRegExpTerm;

struct ProtonNodeTypes {
    typedef ProtonAnd             And;
    typedef ProtonAndNot          AndNot;
    typedef ProtonEquiv           Equiv;
    typedef ProtonLocationTerm    LocationTerm;
    typedef ProtonNear            Near;
    typedef ProtonNumberTerm      NumberTerm;
    typedef ProtonONear           ONear;
    typedef ProtonOr              Or;
    typedef ProtonPhrase          Phrase;
    typedef ProtonPrefixTerm      PrefixTerm;
    typedef ProtonRangeTerm       RangeTerm;
    typedef ProtonRank            Rank;
    typedef ProtonStringTerm      StringTerm;
    typedef ProtonSubstringTerm   SubstringTerm;
    typedef ProtonSuffixTerm      SuffixTerm;
    typedef ProtonWeakAnd         WeakAnd;
    typedef ProtonWeightedSetTerm WeightedSetTerm;
    typedef ProtonDotProduct      DotProduct;
    typedef ProtonWandTerm        WandTerm;
    typedef ProtonPredicateQuery  PredicateQuery;
    typedef ProtonRegExpTerm      RegExpTerm;
};

}  // namespace matching
}  // namespace proton

