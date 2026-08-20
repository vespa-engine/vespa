// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range.h"

#include <vespa/vespalib/stllike/asciistream.h>

#include <format>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.tree.range");

namespace search::query {

StringRange::StringRange(const StringRange& other)
    : _spec(other._spec ? std::make_unique<StringRangeSpec>(*other._spec) : nullptr) {
}

StringRange& StringRange::operator=(const StringRange& other) {
    if (this != &other) {
        _spec = other._spec ? std::make_unique<StringRangeSpec>(*other._spec) : nullptr;
    }
    return *this;
}

StringRange::~StringRange() = default;

bool operator==(const StringRange& r1, const StringRange& r2) {
    const StringRangeSpec* s1 = r1.get_spec();
    const StringRangeSpec* s2 = r2.get_spec();
    if (s1 == s2)
        return true;
    if (!s1 || !s2)
        return false;
    return (*s1 == *s2);
}

vespalib::asciistream& operator<<(vespalib::asciistream& out, const StringRange& /*string_range*/) {
    // TODO
    return out << "TODO";
}

} // namespace search::query
