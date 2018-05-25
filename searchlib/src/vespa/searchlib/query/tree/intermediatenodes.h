// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "intermediate.h"
#include "querynodemixin.h"
#include "term.h"

namespace search::query {

class And : public QueryNodeMixin<And, Intermediate> {
public:
    virtual ~And() = 0;
};

//-----------------------------------------------------------------------------

class AndNot : public QueryNodeMixin<AndNot, Intermediate> {
public:
    virtual ~AndNot() = 0;
};

//-----------------------------------------------------------------------------

class Or : public QueryNodeMixin<Or, Intermediate> {
public:
    virtual ~Or() = 0;
};

//-----------------------------------------------------------------------------

class WeakAnd : public QueryNodeMixin<WeakAnd, Intermediate> {
    uint32_t _minHits;
    vespalib::string _view;
public:
    virtual ~WeakAnd() = 0;

    WeakAnd(uint32_t minHits, const vespalib::string & view) : _minHits(minHits), _view(view) {}

    uint32_t getMinHits() const { return _minHits; }
    const vespalib::string & getView() const { return _view; }
};

//-----------------------------------------------------------------------------

class Equiv : public QueryNodeMixin<Equiv, Intermediate> {
private:
    int32_t _id;
    Weight  _weight;
    int32_t _term_index;
public:
    virtual ~Equiv() = 0;

    Equiv(int32_t id, Weight weight)
        : _id(id), _weight(weight), _term_index(-1)
    {}
    void setTermIndex(int32_t term_index) { _term_index = term_index; }

    Weight getWeight() const { return _weight; }
    int32_t getId() const { return _id; }
    int32_t getTermIndex() const { return _term_index; }
};

//-----------------------------------------------------------------------------

class Rank : public QueryNodeMixin<Rank, Intermediate> {
public:
    virtual ~Rank() = 0;
};

//-----------------------------------------------------------------------------

class Near : public QueryNodeMixin<Near, Intermediate>
{
    uint32_t _distance;

 public:
    Near(size_t distance) : _distance(distance) {}
    virtual ~Near() = 0;

    size_t getDistance() const { return _distance; }
};

//-----------------------------------------------------------------------------

class ONear : public QueryNodeMixin<ONear, Intermediate>
{
    uint32_t _distance;

 public:
    ONear(size_t distance) : _distance(distance) {}
    virtual ~ONear() = 0;

    size_t getDistance() const { return _distance; }
};

//-----------------------------------------------------------------------------

class Phrase : public QueryNodeMixin<Phrase, Intermediate>, public Term {
public:
    Phrase(const vespalib::string &view, int32_t id, Weight weight)
        : Term(view, id, weight) {}
    virtual ~Phrase() = 0;
};

class SameElement : public QueryNodeMixin<SameElement, Intermediate>, public Term {
public:
    SameElement(const vespalib::string &view, int32_t id, Weight weight)
            : Term(view, id, weight) {}
    virtual ~SameElement() = 0;
};

class WeightedSetTerm : public QueryNodeMixin<WeightedSetTerm, Intermediate>, public Term {
public:
    WeightedSetTerm(const vespalib::string &view, int32_t id, Weight weight)
        : Term(view, id, weight) {}
    virtual ~WeightedSetTerm() = 0;
};

class DotProduct : public QueryNodeMixin<DotProduct, Intermediate>, public Term {
public:
    DotProduct(const vespalib::string &view, int32_t id, Weight weight)
        : Term(view, id, weight) {}
    virtual ~DotProduct() = 0;
};

class WandTerm : public QueryNodeMixin<WandTerm, Intermediate>, public Term {
private:
    uint32_t _targetNumHits;
    int64_t  _scoreThreshold;
    double   _thresholdBoostFactor;
public:
    WandTerm(const vespalib::string &view, int32_t id, Weight weight,
             uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : Term(view, id, weight),
          _targetNumHits(targetNumHits),
          _scoreThreshold(scoreThreshold),
          _thresholdBoostFactor(thresholdBoostFactor) {}
    virtual ~WandTerm() = 0;
    uint32_t getTargetNumHits() const { return _targetNumHits; }
    int64_t getScoreThreshold() const { return _scoreThreshold; }
    double getThresholdBoostFactor() const { return _thresholdBoostFactor; }
};

}
