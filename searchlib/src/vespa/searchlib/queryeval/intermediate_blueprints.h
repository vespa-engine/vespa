// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self() override;
    Blueprint::UP get_replacement() override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;
private:
    bool isPositive(size_t index) const override { return index == 0; }
};

//-----------------------------------------------------------------------------

class AndBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self() override;
    Blueprint::UP get_replacement() override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;
};

//-----------------------------------------------------------------------------

class OrBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self() override;
    Blueprint::UP get_replacement() override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;
};

//-----------------------------------------------------------------------------

class WeakAndBlueprint : public IntermediateBlueprint
{
private:
    uint32_t              _n;
    std::vector<uint32_t> _weights;

public:
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;

    WeakAndBlueprint(uint32_t n) : _n(n) {}
    ~WeakAndBlueprint();
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
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    bool should_optimize_children() const override { return false; }
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;

    NearBlueprint(uint32_t window) : _window(window) {}
};

//-----------------------------------------------------------------------------

class ONearBlueprint : public IntermediateBlueprint
{
private:
    uint32_t _window;

public:
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    bool should_optimize_children() const override { return false; }
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;

    ONearBlueprint(uint32_t window) : _window(window) {}
};

//-----------------------------------------------------------------------------

class RankBlueprint : public IntermediateBlueprint
{
public:
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void optimize_self() override;
    Blueprint::UP get_replacement() override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;
};

//-----------------------------------------------------------------------------

class SourceBlenderBlueprint : public IntermediateBlueprint
{
private:
    const ISourceSelector &_selector;

public:
    SourceBlenderBlueprint(const ISourceSelector &selector);
    HitEstimate combine(const std::vector<HitEstimate> &data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(std::vector<Blueprint*> &children) const override;
    bool inheritStrict(size_t i) const override;
    /**
     * Will return the index matching the given sourceId.
     * @param sourceId The sourceid to find.
     * @return The index to the child representing the sourceId. -1 if not found.
     */
    ssize_t findSource(uint32_t sourceId) const;
    SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, fef::MatchData &md) const override;

    /** check if this blueprint has the same source selector as the other */
    bool isCompatibleWith(const SourceBlenderBlueprint &other) const;
};

}
