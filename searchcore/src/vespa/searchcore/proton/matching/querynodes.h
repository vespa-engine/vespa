// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
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
    using ITermFieldData = search::fef::ITermFieldData;
    using TermFieldHandle = search::fef::TermFieldHandle;
    using MatchDataDetails = search::fef::MatchDataDetails;

    struct FieldEntry final : ITermFieldData {
        FieldSpec _field_spec;
        bool attribute_field;

        FieldEntry(const std::string &name, uint32_t fieldId, bool is_filter) noexcept
            : FieldEntry(name, fieldId, search::fef::FilterThreshold(is_filter))
        {}
        FieldEntry(const std::string &name, uint32_t fieldId, search::fef::FilterThreshold threshold) noexcept
            : ITermFieldData(fieldId),
              _field_spec(name, fieldId, search::fef::IllegalHandle, threshold),
              attribute_field(false)
        {}

        [[nodiscard]] const FieldSpec & fieldSpec() const noexcept {
            return _field_spec;
        }
        [[nodiscard]] TermFieldHandle getHandle() const { return getHandle(MatchDataDetails::Normal); }
        [[nodiscard]] TermFieldHandle getHandle(MatchDataDetails requested_details) const override;
        [[nodiscard]] const std::string & getName() const noexcept { return _field_spec.getName(); }
        [[nodiscard]] bool is_filter() const noexcept { return _field_spec.isFilter(); }

        void disable_filter() noexcept { _field_spec.disable_filter(); }
    };

private:
    vespalib::SmallVector<FieldEntry, 1u> _fields;

    void propagate_document_frequency(uint32_t matching_count_doc, uint32_t total_doc_count);

protected:
    void resolve(const ViewResolver &resolver, const search::fef::IIndexEnvironment &idxEnv,
                 const std::string &view, bool forceFilter);

public:
    ProtonTermData() noexcept;
    ProtonTermData(const ProtonTermData &) = delete;
    ProtonTermData & operator = (const ProtonTermData &) = delete;
    ~ProtonTermData() override;
    void resolveFromChildren(const std::vector<search::query::Node *> &children);
    void allocateTerms(search::fef::MatchDataLayout &mdl);
    void setDocumentFrequency(uint32_t estHits, uint32_t numDocs);
    // clear fields, and use just the provided entry:
    void useFieldEntry(const FieldEntry &source);

    // ITermData interface
    [[nodiscard]] std::optional<std::string> query_tensor_name() const override { return std::nullopt; }
    [[nodiscard]] size_t numFields() const final { return _fields.size(); }
    [[nodiscard]] const FieldEntry &field(size_t i) const final { return _fields[i]; }
    [[nodiscard]] const FieldEntry *lookupField(uint32_t fieldId) const final;
    // Write access to field is needeed by TagNeededHandlesVisitor
    [[nodiscard]] FieldEntry &field(size_t i) noexcept { return _fields[i]; }
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
struct ProtonTermWithFields : public ProtonTermBase<Base> {
    using ProtonTermBase<Base>::ProtonTermBase;
    using ProtonTermBase<Base>::numFields;
    using ProtonTermBase<Base>::field;
    ~ProtonTermWithFields() override;
    virtual search::queryeval::FieldSpec inner_field_spec(const search::queryeval::FieldSpec& parentSpec) const override;
};

template <typename Base>
ProtonTermWithFields<Base>::~ProtonTermWithFields() = default;

template <typename Base>
struct ProtonTerm final : public ProtonTermWithFields<Base> {
    using ProtonTermWithFields<Base>::ProtonTermWithFields;
    ~ProtonTerm() override;
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
    using ProtonTermBase::ProtonTermBase;
    ~ProtonEquiv() override;
};
struct ProtonSameElement final : public ProtonTermBase<search::query::SameElement> {
    bool expose_match_data_for_same_element = true;
    using ProtonTermBase::ProtonTermBase;
    ~ProtonSameElement() override;
};

struct ProtonNearestNeighborTerm : public ProtonTermBase<search::query::NearestNeighborTerm> {
    using ProtonTermBase::ProtonTermBase;
    [[nodiscard]] std::optional<std::string> query_tensor_name() const override {
        return ProtonTermBase::NearestNeighborTerm::get_query_tensor_name();
    }
    ~ProtonNearestNeighborTerm() override;
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
using ProtonInTerm =          ProtonTerm<search::query::InTerm>;

struct ProtonWordAlternatives final : public ProtonTermWithFields<search::query::WordAlternatives> {
    ProtonWordAlternatives(std::vector<std::unique_ptr<search::query::StringTerm>> children,
                           const std::string & view, int32_t id, search::query::Weight weight);
    ProtonWordAlternatives(std::vector<std::unique_ptr<ProtonStringTerm>> children,
                           const std::string & view, int32_t id, search::query::Weight weight);
    // compatibility constructor
    ProtonWordAlternatives(std::unique_ptr<search::query::TermVector> terms, const std::string & view, int32_t id, search::query::Weight weight);
    ~ProtonWordAlternatives() override;
};

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
    using InTerm =              ProtonInTerm;
    using WordAlternatives =    ProtonWordAlternatives;
};

extern template struct ProtonTerm<search::query::LocationTerm>;
extern template struct ProtonTerm<search::query::NumberTerm>;
extern template struct ProtonTerm<search::query::Phrase>;
extern template struct ProtonTerm<search::query::PrefixTerm>;
extern template struct ProtonTerm<search::query::RangeTerm>;
extern template struct ProtonTerm<search::query::StringTerm>;
extern template struct ProtonTerm<search::query::SubstringTerm>;
extern template struct ProtonTerm<search::query::SuffixTerm>;
extern template struct ProtonTerm<search::query::WeightedSetTerm>;
extern template struct ProtonTerm<search::query::DotProduct>;
extern template struct ProtonTerm<search::query::WandTerm>;
extern template struct ProtonTerm<search::query::PredicateQuery>;
extern template struct ProtonTerm<search::query::RegExpTerm>;
extern template struct ProtonTerm<search::query::FuzzyTerm>;
extern template struct ProtonTerm<search::query::InTerm>;
extern template struct ProtonTerm<search::query::WordAlternatives>;

}
