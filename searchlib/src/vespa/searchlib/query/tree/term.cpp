// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term.h"
#include <cassert>

namespace search::query {

Term::~Term() = default;

Term::Term(const vespalib::string & view, int32_t id, Weight weight)
    : _view(view),
      _id(id),
      _weight(weight),
      _ranked(true),
      _position_data(true),
      _prefix_match(false)
{ }

void Term::setStateFrom(const Term& other) {
    setRanked(other.isRanked());
    setPositionData(other.usePositionData());
    set_prefix_match(other.prefix_match());
    // too late to copy this state:
    assert(_view == other.getView());
    assert(_id == other.getId());
    assert(_weight == other.getWeight());
}

}
