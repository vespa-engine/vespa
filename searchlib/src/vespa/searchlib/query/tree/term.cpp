// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term.h"

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

}
