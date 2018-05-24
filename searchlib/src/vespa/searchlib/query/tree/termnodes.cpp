// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termnodes.h"

namespace search::query {

NumberTerm::~NumberTerm() = default;

PrefixTerm::~PrefixTerm() = default;

RangeTerm::~RangeTerm() = default;

StringTerm::StringTerm(const Type &term, const vespalib::stringref &view, int32_t id, Weight weight)
    : QueryNodeMixinType(term, view, id, weight)
{}
StringTerm::~StringTerm() = default;

SubstringTerm::~SubstringTerm() = default;

SuffixTerm::~SuffixTerm() = default;

LocationTerm::~LocationTerm() = default;

RegExpTerm::~RegExpTerm() = default;

}
