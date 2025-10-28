// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "multisearch.h"
#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>
#include <vespa/searchlib/fef/indexproperties.h>

namespace search::queryeval {

class IElementGapInspector;
class ISourceSelector;

//-----------------------------------------------------------------------------

class AndNotBlueprint : public IntermediateBlueprint
{
    bool _elementwise;
public:
    AndNotBlueprint();
    AndNotBlueprint(bool elementwise);
    ~AndNotBlueprint() override;
    bool supports_termwise_children() const override { return true; }
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    AndNotBlueprint * asAndNot() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearchImpl(FilterConstraint constraint) const override;
private:
    AnyFlow my_flow(InFlow in_flow) const override;
    uint8_t calculate_cost_tier() const override {
        return (childCnt() > 0) ? get_children()[0]->getState().cost_tier() : State::COST_TIER_NORMAL;
    }
    bool may_need_unpack(size_t index) const override { return index == 0 || _elementwise; }
};

//-----------------------------------------------------------------------------

/** normal AND operator */
class AndBlueprint : public IntermediateBlueprint
{
public:
    ~AndBlueprint() override;
    bool supports_termwise_children() const override { return true; }
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    AndBlueprint * asAnd() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearchImpl(FilterConstraint constraint) const override;
private:
    AnyFlow my_flow(InFlow in_flow) const override;
};

//-----------------------------------------------------------------------------

/** normal OR operator */
class OrBlueprint : public IntermediateBlueprint
{
public:
    ~OrBlueprint() override;
    bool supports_termwise_children() const override { return true; }
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    OrBlueprint * asOr() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearchImpl(FilterConstraint constraint) const override;
private:
    AnyFlow my_flow(InFlow in_flow) const override;
    uint8_t calculate_cost_tier() const override;
};

//-----------------------------------------------------------------------------

class WeakAndBlueprint : public IntermediateBlueprint
{
private:
    std::unique_ptr<WeakAndPriorityQueue>  _scores;
    uint32_t               _n;
    wand::StopWordStrategy _stop_word_strategy;
    std::vector<uint32_t>  _weights;
    MatchingPhase          _matching_phase;

    AnyFlow my_flow(InFlow in_flow) const override;
public:
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    Blueprint::UP get_replacement() override;
    void sort(Children &children, InFlow in_flow) const override;
    bool always_needs_unpack() const override;
    WeakAndBlueprint * asWeakAnd() noexcept final { return this; }
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearchImpl(FilterConstraint constraint) const override;

    explicit WeakAndBlueprint(uint32_t n) : WeakAndBlueprint(n, wand::StopWordStrategy::none(), true) {}
    WeakAndBlueprint(uint32_t n, wand::StopWordStrategy stop_word_strategy, bool thread_safe);
    ~WeakAndBlueprint() override;
    void addTerm(Blueprint::UP bp, uint32_t weight) {
        addChild(std::move(bp));
        _weights.push_back(weight);
    }
    uint32_t getN() const noexcept { return _n; }
    const std::vector<uint32_t> &getWeights() const noexcept { return _weights; }
    void set_matching_phase(MatchingPhase matching_phase) noexcept override;
};

//-----------------------------------------------------------------------------

class NearBlueprint : public IntermediateBlueprint
{
private:
    uint32_t                    _window;
    uint32_t                    _num_negative_terms;
    uint32_t                    _exclusion_distance;
    const IElementGapInspector& _element_gap_inspector;

    AnyFlow my_flow(InFlow in_flow) const override;
public:
    ~NearBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearchImpl(FilterConstraint constraint) const override;
    NearBlueprint(uint32_t window, uint32_t num_negative_terms, uint32_t exclusion_distance, const IElementGapInspector& element_gap_inspector) noexcept
        : _window(window),
          _num_negative_terms(num_negative_terms),
          _exclusion_distance(exclusion_distance),
          _element_gap_inspector(element_gap_inspector)
    {}
};

//-----------------------------------------------------------------------------

class ONearBlueprint  : public IntermediateBlueprint
{
private:
    uint32_t                    _window;
    uint32_t                    _num_negative_terms;
    uint32_t                    _exclusion_distance;
    const IElementGapInspector& _element_gap_inspector;

    AnyFlow my_flow(InFlow in_flow) const override;
public:
    ~ONearBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearchImpl(FilterConstraint constraint) const override;
    ONearBlueprint(uint32_t window, uint32_t num_negative_terms, uint32_t exclusion_distance, const IElementGapInspector& element_gap_inspector) noexcept
        : _window(window),
          _num_negative_terms(num_negative_terms),
          _exclusion_distance(exclusion_distance),
          _element_gap_inspector(element_gap_inspector)
    {}
};

//-----------------------------------------------------------------------------

class RankBlueprint final : public IntermediateBlueprint
{
public:
    ~RankBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    Blueprint::UP get_replacement() override;
    void sort(Children &children, InFlow in_flow) const override;
    bool isRank() const noexcept final { return true; }
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearchImpl(FilterConstraint constraint) const override;
    uint8_t calculate_cost_tier() const override {
        return (childCnt() > 0) ? get_children()[0]->getState().cost_tier() : State::COST_TIER_NORMAL;
    }
private:
    AnyFlow my_flow(InFlow in_flow) const override;
};

//-----------------------------------------------------------------------------

class SourceBlenderBlueprint final : public IntermediateBlueprint
{
private:
    const ISourceSelector &_selector;

    AnyFlow my_flow(InFlow in_flow) const override;
public:
    explicit SourceBlenderBlueprint(const ISourceSelector &selector) noexcept;
    ~SourceBlenderBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children &children, InFlow in_flow) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearchImpl(FilterConstraint constraint) const override;

    /** check if this blueprint has the same source selector as the other */
    bool isCompatibleWith(const SourceBlenderBlueprint &other) const;
    SourceBlenderBlueprint * asSourceBlender() noexcept final { return this; }
    uint8_t calculate_cost_tier() const override;
    const ISourceSelector &getSelector() const { return _selector; }
};

}
