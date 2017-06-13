// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termnodes.h"

namespace search {
namespace query {

NumberTerm::~NumberTerm() {}

PrefixTerm::~PrefixTerm() {}

RangeTerm::~RangeTerm() {}

StringTerm::StringTerm(const Type &term, const vespalib::stringref &view, int32_t id, Weight weight)
    : QueryNodeMixinType(term, view, id, weight)
{}
StringTerm::~StringTerm() {}

SubstringTerm::~SubstringTerm() {}

SuffixTerm::~SuffixTerm() {}

LocationTerm::~LocationTerm() {}

RegExpTerm::~RegExpTerm() {}

}  // namespace query
}  // namespace search
