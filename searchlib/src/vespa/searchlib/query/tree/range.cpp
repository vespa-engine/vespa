// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "range.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <format>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.tree.range");

namespace search::query {

Range::Range(int64_t f, int64_t t)
    : _spec(std::make_unique<NumericRangeSpec>())
{
    _spec->valid = true;
    _spec->valid_integers = true;
    _spec->lower_inclusive = true;
    _spec->upper_inclusive = true;
    _spec->int64_lower_limit = f;
    _spec->int64_upper_limit = t;
    _spec->fp_lower_limit = static_cast<double>(f);
    _spec->fp_upper_limit = static_cast<double>(t);
}

Range::Range(std::string range)
    : _spec(NumericRangeSpec::fromString(range))
{
    if (!_spec || !_spec->valid) {
        LOG(warning, "Failed to parse range string: '%s'", range.c_str());
    } else {
        LOG(debug, "range spec: %s -> %s", range.c_str(), getRangeString().c_str());
    }
}

Range::Range(const Range& other)
    : _spec(other._spec ? std::make_unique<NumericRangeSpec>(*other._spec) : nullptr)
{
}

Range& Range::operator=(const Range& other) {
    if (this != &other) {
        _spec = other._spec ? std::make_unique<NumericRangeSpec>(*other._spec) : nullptr;
    }
    return *this;
}

Range::~Range() = default;

std::string Range::getRangeString() const {
    if (!_spec) {
        return "";
    }

    // Check if this is a partial range (only upper or only lower limit set)
    bool has_lower = _spec->has_lower_limit();
    bool has_upper = _spec->has_upper_limit();

    // If we have diversity or range limit, we must use full range syntax
    bool must_use_full_syntax = _spec->has_range_limit() || _spec->with_diversity();

    std::string result;

    // Generate partial range syntax when possible
    // Partial range syntax only supports exclusive bounds (< and >)
    if (!must_use_full_syntax && has_lower && !has_upper && !_spec->lower_inclusive) {
        // Only lower limit with exclusive bound: ">value"
        if (_spec->valid_integers) {
            result = std::format(">{}", _spec->int64_lower_limit);
        } else {
            result = std::format(">{}", _spec->fp_lower_limit);
        }
    } else if (!must_use_full_syntax && !has_lower && has_upper && !_spec->upper_inclusive) {
        // Only upper limit with exclusive bound: "<value"
        if (_spec->valid_integers) {
            result = std::format("<{}", _spec->int64_upper_limit);
        } else {
            result = std::format("<{}", _spec->fp_upper_limit);
        }
    } else {
        // Full range syntax: [lower;upper]
        auto b_mark = _spec->lower_inclusive ? "[" : "<";
        auto e_mark = _spec->upper_inclusive ? "]" : ">";

        if (_spec->valid_integers) {
            // Use empty string for limits at min/max values
            std::string lower_str = has_lower ? std::to_string(_spec->int64_lower_limit) : "";
            std::string upper_str = has_upper ? std::to_string(_spec->int64_upper_limit) : "";
            result = std::format("{}{};{}", b_mark, lower_str, upper_str);
        } else {
            // Use empty string for limits at min/max values
            // Format floating point with general format to avoid trailing zeros
            std::string lower_str = has_lower ? std::format("{}", _spec->fp_lower_limit) : "";
            std::string upper_str = has_upper ? std::format("{}", _spec->fp_upper_limit) : "";
            result = std::format("{}{};{}", b_mark, lower_str, upper_str);
        }

        if (_spec->has_range_limit() || _spec->with_diversity()) {
            result += std::format(";{}", _spec->rangeLimit);
            if (_spec->with_diversity()) {
                result += std::format(";{};{}", _spec->diversityAttribute, _spec->maxPerGroup);
                if (_spec->with_diversity_cutoff()) {
                    result += std::format(";{}", _spec->diversityCutoffGroups);
                    if (_spec->diversityCutoffStrict) {
                        result += ";strict";
                    } else {
                        result += ";loose";
                    }
                }
            }
        }
        result += e_mark;
    }

    return result;
}

bool operator==(const Range &r1, const Range &r2) {
    const NumericRangeSpec* s1 = r1.getSpec();
    const NumericRangeSpec* s2 = r2.getSpec();
    if (s1 == s2) return true;
    if (!s1 || !s2) return false;
    return (*s1 == *s2);
}

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Range &range)
{
    return out << range.getRangeString();
}

}
