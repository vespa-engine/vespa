// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node.h"
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::query {

/**
 * This is a leaf in the Query tree. Sort of. Phrases are both terms
 * and intermediate nodes.
 */
class Term
{
    vespalib::string _view;
    int32_t          _id;
    Weight           _weight;
    int32_t          _term_index;
    bool             _ranked;
    bool             _position_data;

public:
    virtual ~Term() = 0;

    void setTermIndex(int32_t term_index) { _term_index = term_index; }
    void setView(const vespalib::string & view) { _view = view; }
    void setRanked(bool ranked) { _ranked = ranked; }
    void setPositionData(bool position_data) { _position_data = position_data; }

    void setStateFrom(const Term& other);

    const vespalib::string & getView() const { return _view; }
    Weight getWeight() const { return _weight; }
    int32_t getId() const { return _id; }
    int32_t getTermIndex() const { return _term_index; }
    bool isRanked() const { return _ranked; }
    bool usePositionData() const { return _position_data; }

protected:
    Term(const vespalib::stringref &view, int32_t id, Weight weight);
};

/**
 * Generic functionality for most of Term's derived classes.
 */
template <typename T>
class TermBase : public Node, public Term {
    T _term;

public:
    typedef T Type;

    ~TermBase() override = 0;
    const T &getTerm() const { return _term; }

protected:
    TermBase(T term, const vespalib::stringref &view, int32_t id, Weight weight)
        : Term(view, id, weight),
          _term(std::move(term)) {
    }
};

template <typename T>
TermBase<T>::~TermBase() = default;

}
