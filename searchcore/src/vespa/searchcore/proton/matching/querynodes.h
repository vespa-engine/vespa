// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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

namespace proton::matching {

class ViewResolver;

class ProtonTermData : public search::fef::ITermData
{
public:
    typedef search::queryeval::FieldSpec FieldSpec;

    struct FieldEntry final : search::fef::SimpleTermFieldData {
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
        search::fef::TermFieldHandle getHandle() const override;
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
    ProtonTermData();
    ProtonTermData(const ProtonTermData &);
    ProtonTermData & operator = (const ProtonTermData &);
    ProtonTermData(ProtonTermData &&) = default;
    ProtonTermData & operator = (ProtonTermData &&) = default;
    ~ProtonTermData();
    void resolveFromChildren(const std::vector<search::query::Node *> &children);
    void allocateTerms(search::fef::MatchDataLayout &mdl);
    void setDocumentFrequency(uint32_t estHits, uint32_t numDocs);

    // ITermData interface
    size_t numFields() const override final { return _fields.size(); }
    const FieldEntry &field(size_t i) const override final { return _fields[i]; }
    const FieldEntry *lookupField(uint32_t fieldId) const override final;
};

template <typename NodeType> inline uint32_t numTerms(const NodeType &) { return 1; }

template <>
inline uint32_t numTerms<search::query::Phrase>(const search::query::Phrase &n) {
    return n.getChildren().size();
}

template <typename Base>
struct ProtonTermBase : public Base,
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
    uint32_t getPhraseLength() const override final { return numTerms<Base>(*this); }
    uint32_t getTermIndex() const override final { return -1; }
    search::query::Weight getWeight() const override final { return Base::getWeight(); }
    uint32_t getUniqueId() const override final { return Base::getId(); }
};

template <typename Base>
struct ProtonTerm : public ProtonTermBase<Base> {
    using ProtonTermBase<Base>::ProtonTermBase;
    ~ProtonTerm() {}
};

typedef search::query::SimpleAnd     ProtonAnd;
typedef search::query::SimpleAndNot  ProtonAndNot;
typedef search::query::SimpleNear    ProtonNear;
typedef search::query::SimpleONear   ProtonONear;
typedef search::query::SimpleOr      ProtonOr;
typedef search::query::SimpleRank    ProtonRank;
typedef search::query::SimpleWeakAnd ProtonWeakAnd;

struct ProtonEquiv final : public ProtonTermBase<search::query::Equiv>
{
    search::fef::MatchDataLayout children_mdl;
    using ProtonTermBase::ProtonTermBase;
};

typedef ProtonTerm<search::query::LocationTerm>    ProtonLocationTerm;
typedef ProtonTerm<search::query::NumberTerm>      ProtonNumberTerm;
typedef ProtonTerm<search::query::Phrase>          ProtonPhrase;
typedef ProtonTerm<search::query::SameElement>     ProtonSameElement;

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
    typedef ProtonSameElement     SameElement;
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

}
