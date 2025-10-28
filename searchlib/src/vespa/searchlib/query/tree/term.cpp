// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term.h"
#include <cassert>
#include <vespa/vespalib/util/classname.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.tree.term");

namespace search::query {

Term::~Term() = default;

Term::Term(const std::string & view, int32_t id, Weight weight)
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

queryeval::FieldSpec Term::inner_field_spec(const queryeval::FieldSpec& parentSpec) const {
    auto me = vespalib::getClassName(*this);
    LOG(debug, "fallback inner_field_spec called for %s", me.c_str());
    // should mostly not be called, always returns spec with invalid handle
    const std::string& name = parentSpec.getName();
    uint32_t fieldId = parentSpec.getFieldId();
    fef::FilterThreshold threshold = parentSpec.get_filter_threshold();
    return queryeval::FieldSpec(name, fieldId, fef::IllegalHandle, threshold);
}

}
