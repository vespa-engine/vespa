// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numeric_range_spec.h"
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <charconv>

namespace search {

namespace {

bool parseNumberAsInt64(const char *startp, const char *endptr, int64_t &t2) {
    if (*startp == '+')
        ++startp;
    auto result = std::from_chars(startp, endptr, t2);
    if (result.ec == std::errc{} && result.ptr == endptr) {
        return true;
    }
    return false;
}

bool parseNumberAsDouble(const char *startp, const char *endptr, double &target) {
    if (startp == endptr) return false;
    char *parsed_to;
    double dv = vespalib::locale::c::strtod(startp, &parsed_to);
    if (parsed_to != endptr) [[unlikely]] {
        return false;
    }
    if (std::isnan(dv)) [[unlikely]] {
        return false;
    }
    target = dv;
    return true;
}

struct TwiceParser {
    bool invalid = true;
    bool valid_i = false;
    double d = 0.0;
    int64_t i = 0;
    TwiceParser(std::string_view view) {
        const char *s = view.data();
        const char *e = view.data() + view.size();
        if (parseNumberAsDouble(s, e, d)) {
            invalid = false;
            valid_i = parseNumberAsInt64(s, e, i);
        }
    }
};

bool isFullRange(std::string_view s) noexcept {
    const size_t sz(s.size());
    return (sz >= 3u) &&
            (s[0] == '<' || s[0] == '[') &&
            (s[sz-1] == '>' || s[sz-1] == ']');
}

bool isPartialRange(std::string_view s) noexcept {
    return (s.size() > 1) &&
            ((s[0] == '<') || (s[0] == '>'));
}

std::unique_ptr<NumericRangeSpec> parsePartialRange(const std::string_view term) {
    auto result = std::make_unique<NumericRangeSpec>();
    TwiceParser parsed(term.substr(1));
    if (parsed.invalid) {
        return {};
    }
    if (term[0] == '<') {
        result->upper_inclusive = false;
        result->fp_upper_limit = parsed.d;
        result->int64_upper_limit = parsed.i;
        result->valid_integers = parsed.valid_i;
        result->valid = true;
        return result;
    }
    if (term[0] == '>') {
        result->lower_inclusive = false;
        result->fp_lower_limit = parsed.d;
        result->int64_lower_limit = parsed.i;
        result->valid_integers = parsed.valid_i;
        result->valid = true;
        return result;
    }
    return {};
}

std::unique_ptr<NumericRangeSpec> parseNoRange(std::string_view term) {
    TwiceParser parsed(term);
    if (parsed.invalid) return {};
    auto result = std::make_unique<NumericRangeSpec>();
    result->lower_inclusive = true;
    result->upper_inclusive = true;
    result->fp_lower_limit = parsed.d;
    result->fp_upper_limit = parsed.d;
    result->valid_integers = parsed.valid_i;
    result->int64_lower_limit = parsed.i;
    result->int64_upper_limit = parsed.i;
    result->valid = true;
    return result;
}

std::unique_ptr<NumericRangeSpec> parseFullRange(std::string_view _term) {
    auto result = std::make_unique<NumericRangeSpec>();
    std::string_view rest(_term.data() + 1, _term.size() - 2);
    std::string_view parts[9];
    size_t numParts(0);
    while (! rest.empty() && ((numParts + 1) < 9)) {
        size_t pos(rest.find(';'));
        if (pos != std::string::npos) {
            parts[numParts++] = rest.substr(0, pos);
            rest = rest.substr(pos + 1);
            if (rest.empty()) {
                parts[numParts++] = rest;
            }
        } else {
            parts[numParts++] = rest;
            rest = std::string_view();
        }
    }
    if (numParts < 2) return {};
    if (9 <= numParts) return {};

    result->lower_inclusive = (_term[0] == '[');
    result->upper_inclusive = (_term[_term.size() - 1] == ']');

    bool valid_i = true;
    if (parts[0].empty()) {
        // note empty -> no limit
        result->lower_inclusive = true; // <;3] is same as [;3]
    } else {
        TwiceParser parsed(parts[0]);
        if (parsed.invalid) return {};
        valid_i = parsed.valid_i;
        result->fp_lower_limit = parsed.d;
        result->int64_lower_limit = parsed.i;
    }
    if (parts[1].empty()) {
        // note empty -> no limit
        result->upper_inclusive = true; // [3;> is same as [3;]
    } else {
        TwiceParser parsed(parts[1]);
        if (parsed.invalid) return {};
        valid_i = valid_i && parsed.valid_i;
        result->fp_upper_limit = parsed.d;
        result->int64_upper_limit = parsed.i;
    }
    result->valid_integers = valid_i;
    if (numParts > 2) {
        result->rangeLimit = static_cast<int32_t>(strtol(parts[2].data(), nullptr, 0));
        if (numParts > 3) {
            if (numParts < 5) {
                return {};
            }
            result->diversityAttribute = parts[3];
            result->maxPerGroup = strtoul(parts[4].data(), nullptr, 0);
            if ((result->maxPerGroup > 0) && (numParts > 5)) {
                char *err = nullptr;
                size_t cutoffGroups = strtoul(parts[5].data(), &err, 0);
                if ((err == nullptr) || (size_t(err - parts[5].data()) == parts[5].size())) {
                    result->diversityCutoffGroups = cutoffGroups;
                }
                if (numParts > 6) {
                    result->diversityCutoffStrict = (parts[6] == "strict");
                    if (numParts > 7) {
                        return {};
                    }
                }
            }
        }
    }
    result->valid = true;
    return result;
}

} // namespace

std::unique_ptr<NumericRangeSpec>
NumericRangeSpec::fromString(std::string_view stringRep) {
    if (isFullRange(stringRep)) {
        return parseFullRange(stringRep);
    } else if (isPartialRange(stringRep)) {
        return parsePartialRange(stringRep);
    } else {
        return parseNoRange(stringRep);
    }
}

} // namespace search
