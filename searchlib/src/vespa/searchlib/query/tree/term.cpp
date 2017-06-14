// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term.h"

namespace search {
namespace query {

Term::~Term() { }

Term::Term(const vespalib::stringref &view, int32_t id, Weight weight) :
    _view(view),
    _id(id),
    _weight(weight),
    _term_index(-1),
    _ranked(true),
    _position_data(true)
{ }

}  // namespace query
}  // namespace search
