// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "multisearch.h"

namespace search::queryeval {

class ISourceSelector;

//-----------------------------------------------------------------------------

class AndNotBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    AndNotBlueprint * asAndNot() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearch(bool strict, FilterConstraint constraint) const override;
private:
    uint8_t calculate_cost_tier() const override {
        return (childCnt() > 0) ? get_children()[0]->getState().cost_tier() : State::COST_TIER_NORMAL;
    }
    bool isPositive(size_t index) const override { return index == 0; }
};

//-----------------------------------------------------------------------------

/** normal AND operator */
class AndBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    AndBlueprint * asAnd() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearch(bool strict, FilterConstraint constraint) const override;
private:
    double computeNextHitRate(const Blueprint & child, double hit_rate, bool use_estimate) const override;
};

//-----------------------------------------------------------------------------

/** normal OR operator */
class OrBlueprint : public IntermediateBlueprint
{
public:
    ~OrBlueprint() override;
    bool supports_termwise_children() const override { return true; }
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    OrBlueprint * asOr() noexcept final { return this; }
    Blueprint::UP get_replacement() override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearch(bool strict, FilterConstraint constraint) const override;
private:
    double computeNextHitRate(const Blueprint & child, double hit_rate, bool use_estimate) const override;
    uint8_t calculate_cost_tier() const override;
};

//-----------------------------------------------------------------------------

class WeakAndBlueprint : public IntermediateBlueprint
{
private:
    uint32_t              _n;
    std::vector<uint32_t> _weights;

public:
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    bool always_needs_unpack() const override;
    WeakAndBlueprint * asWeakAnd() noexcept final { return this; }
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;

    explicit WeakAndBlueprint(uint32_t n) noexcept : _n(n) {}
    ~WeakAndBlueprint() override;
    void addTerm(Blueprint::UP bp, uint32_t weight) {
        addChild(std::move(bp));
        _weights.push_back(weight);
    }
    uint32_t getN() const { return _n; }
    const std::vector<uint32_t> &getWeights() const { return _weights; }
};

//-----------------------------------------------------------------------------

class NearBlueprint : public IntermediateBlueprint
{
private:
    uint32_t _window;

public:
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    bool should_optimize_children() const override { return false; }
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIteratorUP createSearch(fef::MatchData &md, bool strict) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;

    explicit NearBlueprint(uint32_t window) noexcept : _window(window) {}
};

//-----------------------------------------------------------------------------

class ONearBlueprint  : public IntermediateBlueprint
{
private:
    uint32_t _window;

public:
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    bool should_optimize_children() const override { return false; }
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIteratorUP createSearch(fef::MatchData &md, bool strict) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;

    explicit ONearBlueprint(uint32_t window) noexcept : _window(window) {}
};

//-----------------------------------------------------------------------------

class RankBlueprint final : public IntermediateBlueprint
{
public:
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self(OptimizePass pass) override;
    Blueprint::UP get_replacement() override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    bool isRank() const noexcept final { return true; }
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearch(bool strict, FilterConstraint constraint) const override;
    uint8_t calculate_cost_tier() const override {
        return (childCnt() > 0) ? get_children()[0]->getState().cost_tier() : State::COST_TIER_NORMAL;
    }
};

//-----------------------------------------------------------------------------

class SourceBlenderBlueprint final : public IntermediateBlueprint
{
private:
    const ISourceSelector &_selector;

public:
    explicit SourceBlenderBlueprint(const ISourceSelector &selector) noexcept;
    ~SourceBlenderBlueprint() override;
    double calculate_relative_estimate() const final;
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(MultiSearch::Children subSearches,
                             bool strict, fef::MatchData &md) const override;
    SearchIterator::UP
    createFilterSearch(bool strict, FilterConstraint constraint) const override;

    /** check if this blueprint has the same source selector as the other */
    bool isCompatibleWith(const SourceBlenderBlueprint &other) const;
    SourceBlenderBlueprint * asSourceBlender() noexcept final { return this; }
    uint8_t calculate_cost_tier() const override;
};

}
