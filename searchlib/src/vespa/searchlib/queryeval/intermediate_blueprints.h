// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "isourceselector.h"
#include "searchable.h"
#include <vespa/searchlib/queryeval/multisearch.h>
#include <vector>
#include <map>

namespace search {
namespace queryeval {

//-----------------------------------------------------------------------------

class AndNotBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void optimize_self() override;
    virtual Blueprint::UP get_replacement() override;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;
private:
    virtual bool isPositive(size_t index) const { return index == 0; }
};

//-----------------------------------------------------------------------------

class AndBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void optimize_self() override;
    virtual Blueprint::UP get_replacement() override;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;
};

//-----------------------------------------------------------------------------

class OrBlueprint : public IntermediateBlueprint
{
public:
    bool supports_termwise_children() const override { return true; }
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void optimize_self() override;
    virtual Blueprint::UP get_replacement() override;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;
};

//-----------------------------------------------------------------------------

class WeakAndBlueprint : public IntermediateBlueprint
{
private:
    uint32_t              _n;
    std::vector<uint32_t> _weights;

public:
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;

    WeakAndBlueprint(uint32_t n) : _n(n) {}
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
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual bool should_optimize_children() const override { return false; }
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;

    NearBlueprint(uint32_t window) : _window(window) {}
};

//-----------------------------------------------------------------------------

class ONearBlueprint : public IntermediateBlueprint
{
private:
    uint32_t _window;

public:
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual bool should_optimize_children() const override { return false; }
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;

    ONearBlueprint(uint32_t window) : _window(window) {}
};

//-----------------------------------------------------------------------------

class RankBlueprint : public IntermediateBlueprint
{
public:
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void optimize_self() override;
    virtual Blueprint::UP get_replacement() override;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;
};

//-----------------------------------------------------------------------------

class SourceBlenderBlueprint : public IntermediateBlueprint
{
private:
    const ISourceSelector &_selector;

public:
    SourceBlenderBlueprint(const ISourceSelector &selector);
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const;
    virtual FieldSpecBaseList exposeFields() const;
    virtual void sort(std::vector<Blueprint*> &children) const;
    virtual bool inheritStrict(size_t i) const;
    /**
     * Will return the index matching the given sourceId.
     * @param sourceId The sourceid to find.
     * @return The index to the child representing the sourceId. -1 if not found.
     */
    ssize_t findSource(uint32_t sourceId) const;
    virtual SearchIterator::UP
    createIntermediateSearch(const MultiSearch::Children &subSearches,
                             bool strict, search::fef::MatchData &md) const;

    /** check if this blueprint has the same source selector as the other */
    bool isCompatibleWith(const SourceBlenderBlueprint &other) const;
};

//-----------------------------------------------------------------------------

} // namespace queryeval
} // namespace search

