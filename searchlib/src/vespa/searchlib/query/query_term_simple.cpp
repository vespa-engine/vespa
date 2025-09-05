// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_simple.h"
#include "base.h"
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/classname.h>
#include <cmath>
#include <limits>
#include <charconv>

namespace {

bool couldBeValidNumber(std::string_view text) {
    // TODO hex format support
    bool seenSign = false;
    bool seenDigit = false;
    bool seenDot = false;
    bool seenExp = false;
    bool foundStart = false;
    bool trailingSpaces = false;

    for (char ch : text) {
        if (ch == ' ' || ch == '\t') {
            if (foundStart) trailingSpaces = true;
            // skip leading and trailing spaces
            continue;
        } else if (trailingSpaces) {
            // found non-space after trailing spaces
            return false;
        } else {
            // found non-space after leading spaces
            foundStart = true;
        }
        switch (ch) {
        case '+':
        case '-':
            if (seenSign) return false;
            seenSign = true;
            continue;
        case '.':
            if (seenDot) return false;
            seenDot = true;
            continue;
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
            seenDigit = true;
            continue;
        case 'e':
        case 'E':
            if (seenExp) return false;
            if (! seenDigit) return false;
            seenExp = true;
            seenDigit = false;
            seenSign = false;
            continue;
        case 'i':
        case 'I':
            if (seenDigit || seenDot) return false;
            // TODO: check for "inf"
            return true;
        default:
            // fprintf(stderr, "non-number; bad char '%02x'\n", (int)ch);
            return false;
        }
    }
    return seenDigit;
}

/*
optional plus (``+'') or minus sign (``-'') followed by either:

a decimal significand consisting of a sequence of decimal digits optionally containing a decimal-point character
 or
a hexadecimal significand consisting of a ``0X'' or ``0x'' followed by a sequence of hexadecimal digits optionally containing a decimal-point character.

In both cases, the significand may be optionally followed by an exponent.
An exponent consists of an ``E'' or ``e'' (for decimal constants)
 or a ``P'' or ``p'' (for hexadecimal constants),
 followed by an optional plus or minus sign, followed by a sequence of decimal digits.
For decimal constants, the exponent indicates the power of 10 by which the significand should be scaled. For hexadecimal constants, the scaling is instead done by powers of 2.

Alternatively, if the portion of the string following the optional plus or minus sign begins with ``INFINITY''
*/

template <typename N>
constexpr bool isValidInteger(int64_t value) noexcept
{
    return (value >= std::numeric_limits<N>::min()) &&
           (value <= std::numeric_limits<N>::max());
}

constexpr bool isRepresentableByInt64(double d) noexcept {
    return (d > double(std::numeric_limits<int64_t>::min())) &&
           (d < double(std::numeric_limits<int64_t>::max()));
}

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

template<typename T, typename D>
bool convertText(std::string_view txt, T& target, bool up, bool down, T towards, D d)
{
    if (txt.empty())
        return true;
    const char * q = txt.data();
    const char * qend = q + txt.size();
    const char * err = nullptr;
    T got = d.fromstr(q, qend, &err);
    if (err != qend)
        return false;
    if (up) {
        target = d.nearestUpward(got, towards);
    } else if (down) {
        target = d.nearestDownwd(got, towards);
    } else {
        target = got;
    }
    return true;
}

struct IntDecoder {
    static int64_t fromstr(const char * q, const char * qend, const char ** end) noexcept {
        int64_t v(0);
        for (;q < qend && (std::isspace(static_cast<unsigned char>(*q)) || (*q == '+')); q++);
        std::from_chars_result err = std::from_chars(q, qend, v, 10);
        if (err.ec == std::errc::result_out_of_range) {
            v = (*q == '-') ? std::numeric_limits<int64_t>::min() : std::numeric_limits<int64_t>::max();
        }
        *end = err.ptr;
        return v;
    }
    static int64_t nearestDownwd(int64_t n, int64_t min) noexcept { return (n > min ? n - 1 : n); }
    static int64_t nearestUpward(int64_t n, int64_t max) noexcept { return (n < max ? n + 1 : n); }
};

template <typename T>
struct FloatDecoder {
    static T fromstr(const char * q, const char * qend, const char ** end) noexcept {
        T v(0);
#if defined(_LIBCPP_VERSION) && _LIBCPP_VERSION < 200000
        std::string tmp(q, qend - q);
        char* tmp_end = nullptr;
        const char *tmp_cstring = tmp.c_str();
        if constexpr (std::is_same_v<T, float>) {
            v = vespalib::locale::c::strtof_au(tmp_cstring, &tmp_end);
        } else {
            v = vespalib::locale::c::strtod_au(tmp_cstring, &tmp_end);
        }
        if (end != nullptr) {
            *end = (tmp_end != nullptr) ? (q + (tmp_end - tmp_cstring)) : nullptr;
        }
#else
        for (;q < qend && (std::isspace(static_cast<unsigned char>(*q)) || (*q == '+')); q++);
        std::from_chars_result err = std::from_chars(q, qend, v);
        if (err.ec == std::errc::result_out_of_range) {
            v = (*q == '-') ? -std::numeric_limits<T>::infinity() : std::numeric_limits<T>::infinity();
        }
        *end = err.ptr;
#endif
        return v;
    }
    static T nearestDownwd(T n, T min) noexcept {
        return std::nextafter(n, min);
    }
    static T nearestUpward(T n, T max) noexcept {
        return std::nextafter(n, max);
    }
};

}

namespace search {

void
QueryTermSimple::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    visit(visitor, "term", _term);
    visit(visitor, "type", static_cast<uint32_t>(_type));
}

template <typename N>
QueryTermSimple::RangeResult<N>
QueryTermSimple::getFloatRange() const noexcept
{
    N lowRaw, highRaw;
    bool valid = getAsFloatTerm(lowRaw, highRaw);
    RangeResult<N> res;
    res.valid = valid;
    if (!valid) {
        res.low = std::numeric_limits<N>::infinity();
        res.high = -std::numeric_limits<N>::infinity();
        res.adjusted = true;
    } else {
        res.low = lowRaw;
        res.high = highRaw;
    }
    return res;
}

bool
QueryTermSimple::getRangeInternal(int64_t & low, int64_t & high) const noexcept
{
    bool valid = getAsIntegerTerm(low, high);
    if ( ! valid ) {
        double l(0), h(0);
        valid = getAsFloatTerm(l, h);
        if (valid) {
            if ((l == h) && isRepresentableByInt64(l)) {
                low = high = static_cast<int64_t>(std::round(l));
            } else {
                if (l > double(std::numeric_limits<int64_t>::min())) {
                    if (l < double(std::numeric_limits<int64_t>::max())) {
                        low = std::ceil(l);
                    } else {
                        low = std::numeric_limits<int64_t>::max();
                    }
                }
                if (h < double(std::numeric_limits<int64_t>::max())) {
                    if (h > double(std::numeric_limits<int64_t>::min())) {
                        high = std::floor(h);
                    } else {
                        high = std::numeric_limits<int64_t>::min();
                    }
                }
            }
        }
    }
    return valid;
}

template <typename N>
QueryTermSimple::RangeResult<N>
QueryTermSimple::getIntegerRange() const noexcept
{
    int64_t lowRaw, highRaw;
    bool valid = getRangeInternal(lowRaw, highRaw);
    RangeResult<N> res;
    res.valid = valid;
    if (valid) {
        bool validLow = isValidInteger<N>(lowRaw);
        if (validLow) {
            res.low = lowRaw;
        } else {
            res.low = (lowRaw < static_cast<int64_t>(std::numeric_limits<N>::min()) ?
                       std::numeric_limits<N>::min() : std::numeric_limits<N>::max());
            res.adjusted = true;
        }
        bool validHigh = isValidInteger<N>(highRaw);
        if (validHigh) {
            res.high = highRaw;
        } else {
            res.high = (highRaw > static_cast<int64_t>(std::numeric_limits<N>::max()) ?
                        std::numeric_limits<N>::max() : std::numeric_limits<N>::min());
            res.adjusted = true;
        }
    } else {
        res.low = std::numeric_limits<N>::max();
        res.high = std::numeric_limits<N>::min();
        res.adjusted = true;
    }
    return res;
}

template <>
QueryTermSimple::RangeResult<float>
QueryTermSimple::getRange() const noexcept
{
    return getFloatRange<float>();
}

template <>
QueryTermSimple::RangeResult<double>
QueryTermSimple::getRange() const noexcept
{
    return getFloatRange<double>();
}

template <>
QueryTermSimple::RangeResult<int8_t>
QueryTermSimple::getRange() const noexcept
{
    return getIntegerRange<int8_t>();
}

template <>
QueryTermSimple::RangeResult<int16_t>
QueryTermSimple::getRange() const noexcept
{
    return getIntegerRange<int16_t>();
}

template <>
QueryTermSimple::RangeResult<int32_t>
QueryTermSimple::getRange() const noexcept
{
    return getIntegerRange<int32_t>();
}

template <>
QueryTermSimple::RangeResult<int64_t>
QueryTermSimple::getRange() const noexcept
{
    return getIntegerRange<int64_t>();
}

bool
QueryTermSimple::getAsIntegerTerm(int64_t & lower, int64_t & upper) const noexcept
{
    lower = std::numeric_limits<int64_t>::min();
    upper = std::numeric_limits<int64_t>::max();
    return getAsNumericTerm(lower, upper, IntDecoder());
}

bool
QueryTermSimple::getAsFloatTerm(double & lower, double & upper) const noexcept
{
    lower = -std::numeric_limits<double>::infinity();
    upper = std::numeric_limits<double>::infinity();
    return getAsNumericTerm(lower, upper, FloatDecoder<double>());
}

bool
QueryTermSimple::getAsFloatTerm(float & lower, float & upper) const noexcept
{
    lower = -std::numeric_limits<float>::infinity();
    upper = std::numeric_limits<float>::infinity();
    return getAsNumericTerm(lower, upper, FloatDecoder<float>());
}

QueryTermSimple::~QueryTermSimple() = default;

NumericRangeSpec QueryTermSimple::emptyNumericRange;

std::unique_ptr<NumericRangeSpec> parsePartialRange(const std::string &term) {
    auto result = std::make_unique<NumericRangeSpec>();
    std::string_view rest(term.c_str() + 1, term.size() - 1);
    if (term[0] == '<' && couldBeValidNumber(rest)) {
        result->upperLimitTxt = rest;
        result->lower_inclusive = true; // include '-Inf' or equivalent
        return result;
    }
    if (term[0] == '>' && couldBeValidNumber(rest)) {
        result->lowerLimitTxt = rest;
        result->upper_inclusive = true; // include '+Inf' or equivalent
        return result;
    }
    return {};
}

std::unique_ptr<NumericRangeSpec> parseNoRange(const std::string &term) {
    if (couldBeValidNumber(term)) {
        auto result = std::make_unique<NumericRangeSpec>();
        result->lower_inclusive = true;
        result->lowerLimitTxt = term;
        result->upperLimitTxt = term;
        result->upper_inclusive = true;
        return result;
    }
    return {};
}

std::unique_ptr<NumericRangeSpec> parseFullRange(const std::string &_term) {
    auto result = std::make_unique<NumericRangeSpec>();
    std::string_view rest(_term.c_str() + 1, _term.size() - 2);
    std::string_view parts[9];
    size_t numParts(0);
    while (! rest.empty() && ((numParts + 1) < NELEMS(parts))) {
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
    if (NELEMS(parts) <= numParts) return {};
    bool _valid = true;
    result->lowerLimitTxt = parts[0];
    result->upperLimitTxt = parts[1];
    // note empty -> no limit
    if ((! parts[0].empty()) && (! couldBeValidNumber(parts[0]))) {
        return {};
    }
    if ((! parts[1].empty()) && (! couldBeValidNumber(parts[1]))) {
        return {};
    }
    if (_valid) {
        result->lower_inclusive = (_term[0] == '[');
        result->upper_inclusive = (_term[_term.size() - 1] == ']');
    }
    if (_valid && numParts > 2) {
        result->rangeLimit = static_cast<int32_t>(strtol(parts[2].data(), nullptr, 0));
        if (numParts > 3) {
            _valid = (numParts >= 5);
            if (_valid) {
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
                        _valid = (numParts == 7);
                    }
                }
            }
        }
    }
    if (_valid) {
        return result;
    }
    return {};
}

QueryTermSimple::QueryTermSimple(const string & term_, Type type)
    : _type(type),
      _valid(true),
      _fuzzy_prefix_match(false),
      _term(term_),
      _fuzzy_max_edit_distance(2),
      _fuzzy_prefix_lock_length(0)
{
    if (isFullRange(_term)) {
        _numeric_range = parseFullRange(_term);
        _valid = bool(_numeric_range);
    } else if (isPartialRange(_term)) {
        _numeric_range = parsePartialRange(_term);
        _valid = bool(_numeric_range);
    } else {
        _numeric_range = parseNoRange(_term);
    }
}

template <typename T, typename D>
bool
QueryTermSimple::getAsNumericTerm(T & lower, T & upper, D d) const noexcept
{
    if (empty() || ! _numeric_range) return false;
    T low(lower);
    T high(upper);
    if (! convertText(_numeric_range->lowerLimitTxt, low,
                      ! _numeric_range->lower_inclusive, false,
                      upper, d))
    {
        return false;
    }
    if (! convertText(_numeric_range->upperLimitTxt, high,
                      false, ! _numeric_range->upper_inclusive,
                      lower, d))
    {
        return false;
    }
    lower = low;
    upper = high;
    return true;
}

std::string
QueryTermSimple::getClassName() const
{
    return vespalib::getClassName(*this);
}

} // namespace

void
visit(vespalib::ObjectVisitor &self, const std::string &name, const search::QueryTermSimple *obj)
{
    if (obj != nullptr) {
        self.openStruct(name, obj->getClassName());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void
visit(vespalib::ObjectVisitor &self, const std::string &name, const search::QueryTermSimple &obj)
{
    visit(self, name, &obj);
}
