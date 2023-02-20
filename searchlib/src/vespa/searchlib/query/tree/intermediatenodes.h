// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
public:
    virtual ~Equiv() = 0;

    Equiv(int32_t id, Weight weight)
        : _id(id), _weight(weight)
    {}

    Weight getWeight() const { return _weight; }
    int32_t getId() const { return _id; }
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
        : Term(view, id, weight), _expensive(false) {}
    virtual ~Phrase() = 0;
    Phrase &set_expensive(bool value) {
        _expensive = value;
        return *this;
    }
    bool is_expensive() const { return _expensive; }
private:
    bool _expensive;
};

class SameElement : public QueryNodeMixin<SameElement, Intermediate>, public Term {
public:
    SameElement(const vespalib::string &view, int32_t id, Weight weight)
        : Term(view, id, weight), _expensive(false) {}
    virtual ~SameElement() = 0;
    SameElement &set_expensive(bool value) {
        _expensive = value;
        return *this;
    }
    bool is_expensive() const { return _expensive; }
private:
    bool _expensive;
};

}
