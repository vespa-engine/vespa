// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/field_spec.h>
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
    using FieldSpec = search::queryeval::FieldSpec;

    struct FieldEntry final : search::fef::SimpleTermFieldData {
        vespalib::string field_name;
        bool attribute_field;
        bool filter_field;

        FieldEntry(const vespalib::string &name, uint32_t fieldId)
            : SimpleTermFieldData(fieldId),
              field_name(name),
              attribute_field(false),
              filter_field(false) {}

        [[nodiscard]] FieldSpec fieldSpec() const {
            return {field_name, getFieldId(), getHandle(), filter_field};
        }
        using SimpleTermFieldData::getHandle;
        [[nodiscard]] search::fef::TermFieldHandle getHandle(search::fef::MatchDataDetails requested_details) const override;
    };

private:
    vespalib::SmallVector<FieldEntry, 1u> _fields;

    void propagate_document_frequency(uint32_t matching_count_doc, uint32_t total_doc_count);

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
    ~ProtonTermData() override;
    void resolveFromChildren(const std::vector<search::query::Node *> &children);
    void allocateTerms(search::fef::MatchDataLayout &mdl);
    void setDocumentFrequency(uint32_t estHits, uint32_t numDocs);

    // ITermData interface
    [[nodiscard]] std::optional<vespalib::string> query_tensor_name() const override { return std::nullopt; }
    [[nodiscard]] size_t numFields() const final { return _fields.size(); }
    [[nodiscard]] const FieldEntry &field(size_t i) const final { return _fields[i]; }
    [[nodiscard]] const FieldEntry *lookupField(uint32_t fieldId) const final;
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
    ~ProtonTermBase() override;

    void resolve(const ViewResolver &resolver, const search::fef::IIndexEnvironment &idxEnv)
    {
        bool forceFilter = !Base::usePositionData();
        ProtonTermData::resolve(resolver, idxEnv, Base::getView(), forceFilter);
    }

    // ITermData interface
    [[nodiscard]] uint32_t getPhraseLength() const final { return numTerms<Base>(*this); }
    [[nodiscard]] search::query::Weight getWeight() const final { return Base::getWeight(); }
    [[nodiscard]] uint32_t getUniqueId() const final { return Base::getId(); }
};

template <typename Base>
ProtonTermBase<Base>::~ProtonTermBase() = default;

template <typename Base>
struct ProtonTerm final : public ProtonTermBase<Base> {
    using ProtonTermBase<Base>::ProtonTermBase;
    ~ProtonTerm();
};

template <typename Base>
ProtonTerm<Base>::~ProtonTerm() = default;

using ProtonAnd =         search::query::SimpleAnd;
using ProtonAndNot =      search::query::SimpleAndNot;
using ProtonNear =        search::query::SimpleNear;
using ProtonONear =       search::query::SimpleONear;
using ProtonOr =          search::query::SimpleOr;
using ProtonRank =        search::query::SimpleRank;
using ProtonWeakAnd =     search::query::SimpleWeakAnd;
using ProtonTrue =        search::query::SimpleTrue;
using ProtonFalse =       search::query::SimpleFalse;

struct ProtonEquiv final : public ProtonTermBase<search::query::Equiv> {
    search::fef::MatchDataLayout children_mdl;
    using ProtonTermBase::ProtonTermBase;
};

struct ProtonSameElement final : public ProtonTermBase<search::query::SameElement> {
    using ProtonTermBase::ProtonTermBase;
};

struct ProtonNearestNeighborTerm : public ProtonTermBase<search::query::NearestNeighborTerm> {
    using ProtonTermBase::ProtonTermBase;
    [[nodiscard]] std::optional<vespalib::string> query_tensor_name() const override {
        return ProtonTermBase::NearestNeighborTerm::get_query_tensor_name();
    }
};

using ProtonLocationTerm = ProtonTerm<search::query::LocationTerm>;
using ProtonNumberTerm =   ProtonTerm<search::query::NumberTerm>;
using ProtonPhrase =       ProtonTerm<search::query::Phrase>;

using ProtonPrefixTerm =      ProtonTerm<search::query::PrefixTerm>;
using ProtonRangeTerm =       ProtonTerm<search::query::RangeTerm>;
using ProtonStringTerm =      ProtonTerm<search::query::StringTerm>;
using ProtonSubstringTerm =   ProtonTerm<search::query::SubstringTerm>;
using ProtonSuffixTerm =      ProtonTerm<search::query::SuffixTerm>;
using ProtonWeightedSetTerm = ProtonTerm<search::query::WeightedSetTerm>;
using ProtonDotProduct =      ProtonTerm<search::query::DotProduct>;
using ProtonWandTerm =        ProtonTerm<search::query::WandTerm>;
using ProtonPredicateQuery =  ProtonTerm<search::query::PredicateQuery>;
using ProtonRegExpTerm =      ProtonTerm<search::query::RegExpTerm>;
using ProtonFuzzyTerm =       ProtonTerm<search::query::FuzzyTerm>;

struct ProtonNodeTypes {
    using And =                 ProtonAnd;
    using AndNot =              ProtonAndNot;
    using Equiv =               ProtonEquiv;
    using LocationTerm =        ProtonLocationTerm;
    using Near =                ProtonNear;
    using NumberTerm =          ProtonNumberTerm;
    using ONear =               ProtonONear;
    using Or =                  ProtonOr;
    using Phrase =              ProtonPhrase;
    using SameElement =         ProtonSameElement;
    using PrefixTerm =          ProtonPrefixTerm;
    using RangeTerm =           ProtonRangeTerm;
    using Rank =                ProtonRank;
    using StringTerm =          ProtonStringTerm;
    using SubstringTerm =       ProtonSubstringTerm;
    using SuffixTerm =          ProtonSuffixTerm;
    using WeakAnd =             ProtonWeakAnd;
    using WeightedSetTerm =     ProtonWeightedSetTerm;
    using DotProduct =          ProtonDotProduct;
    using WandTerm =            ProtonWandTerm;
    using PredicateQuery =      ProtonPredicateQuery;
    using RegExpTerm =          ProtonRegExpTerm;
    using NearestNeighborTerm = ProtonNearestNeighborTerm;
    using TrueQueryNode =       ProtonTrue;
    using FalseQueryNode =      ProtonFalse;
    using FuzzyTerm =           ProtonFuzzyTerm;
};

}
