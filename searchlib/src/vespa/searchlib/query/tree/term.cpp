// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term.h"
#include <cassert>

namespace search::query {

Term::~Term() = default;

Term::Term(const vespalib::stringref &view, int32_t id, Weight weight) :
    _view(view),
    _id(id),
    _weight(weight),
    _term_index(-1),
    _ranked(true),
    _position_data(true)
{ }

void Term::setStateFrom(const Term& other) {
    setTermIndex(other.getTermIndex());
    setRanked(other.isRanked());
    setPositionData(other.usePositionData());
    // too late to copy this state:
    assert(_view == other.getView());
    assert(_id == other.getId());
    assert(_weight == other.getWeight());
}

}
