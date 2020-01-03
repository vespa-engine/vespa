// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_spec.h"
#include <vespa/searchlib/fef/matchdata.h>

namespace search::queryeval {

inline fef::TermFieldMatchData *
FieldSpecBase::resolve(fef::MatchData &md) const {
    return md.resolveTermField(getHandle());
}
inline const fef::TermFieldMatchData *
FieldSpecBase::resolve(const fef::MatchData &md) const {
    return md.resolveTermField(getHandle());
}

}
