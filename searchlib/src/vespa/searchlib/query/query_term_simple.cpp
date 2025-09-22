// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_simple.h"
#include "base.h"
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/classname.h>
#include <cmath>
#include <limits>
#include <charconv>

using search::NumericRangeSpec;

namespace {

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

/*
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
*/

bool isPartialRange(std::string_view s) noexcept {
    return (s.size() > 1) &&
            ((s[0] == '<') || (s[0] == '>'));
}

constexpr double NEG_INF = -std::numeric_limits<double>::infinity();
constexpr double POS_INF = std::numeric_limits<double>::infinity();

constexpr float NEG_INF_F = -std::numeric_limits<float>::infinity();
constexpr float POS_INF_F = std::numeric_limits<float>::infinity();

constexpr float NEG_MIN_F = -std::numeric_limits<float>::max();
constexpr float POS_MAX_F = std::numeric_limits<float>::max();

constexpr int64_t NEG_MIN_I64 = std::numeric_limits<int64_t>::min();
constexpr int64_t POS_MAX_I64 = std::numeric_limits<int64_t>::max();

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

std::unique_ptr<NumericRangeSpec> parseNoRange(const std::string &term) {
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
    RangeResult<N> res;
    if (_numeric_range) {
        res.valid = true;
        res.low = _numeric_range->fp_lower_limit;
        res.high = _numeric_range->fp_upper_limit;
        if (! _numeric_range->lower_inclusive) {
            res.low = std::nextafter(res.low, res.high);
        }
        if (! _numeric_range->upper_inclusive) {
            res.high = std::nextafter(res.high, res.low);
        }
    } else {
        res.valid = false;
        res.low = std::numeric_limits<N>::infinity();
        res.high = -std::numeric_limits<N>::infinity();
        res.adjusted = true;
    }
    return res;
}

bool
QueryTermSimple::getRangeInternal(int64_t & low, int64_t & high) const noexcept
{
    bool valid = getAsIntegerTerm(low, high);
    if ( ! valid ) {
        RangeResult<double> range = getFloatRange<double>();
        if (range.valid) {
            valid = true;
            double l = range.low;
            double h = range.high;
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
    lower = NEG_MIN_I64;
    upper = POS_MAX_I64;
    if (_numeric_range && _numeric_range->valid_integers) {
        lower = _numeric_range->int64_lower_limit;
        upper = _numeric_range->int64_upper_limit;
        if (! _numeric_range->lower_inclusive) {
            ++lower;
        }
        if (! _numeric_range->upper_inclusive) {
            --upper;
        }
        return true;
    }
    return false;
}

bool
QueryTermSimple::getAsFloatTerm(double & lower, double & upper) const noexcept
{
    auto range = getFloatRange<double>();
    lower = range.low;
    upper = range.high;
    return range.valid;
}

bool
QueryTermSimple::getAsFloatTerm(float & lower, float & upper) const noexcept
{
    auto range = getFloatRange<float>();
    lower = range.low;
    upper = range.high;
    return range.valid;
}

QueryTermSimple::~QueryTermSimple() = default;

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

NumericRangeSpec QueryTermSimple::emptyNumericRange;
QueryTermSimple::QueryTermSimple(Type type, std::unique_ptr<NumericRangeSpec> range)
  : _type(type),
    _valid(range),
    _fuzzy_prefix_match(false),
    _term("<range>"),
    _fuzzy_max_edit_distance(0),
    _fuzzy_prefix_lock_length(0)
{
    _numeric_range = std::move(range);
}

/*
template <typename T, typename D>
bool
QueryTermSimple::getAsNumericTerm(T & lower, T & upper, D d) const noexcept
{
    if (empty()) return false;

    size_t sz(_term.size());
    const char *err(nullptr);
    T low(lower);
    T high(upper);
    const char * q = _term.c_str();
    const char * qend = q + sz;
    const char first(q[0]);
    const char last(q[sz-1]);
    bool isRange = (first == '<') || (first == '>') || (first == '[');
    q += isRange ? 1 : 0;
    T ll = d.fromstr(q, qend, &err);
    bool valid = isValid() && ((*err == 0) || (*err == ';'));
    if (!valid) return false;

    if (*err == 0) {
        if (first == '<') {
            high = d.nearestDownwd(ll, lower);
        } else if (first == '>') {
            low = d.nearestUpward(ll, upper);
        } else {
            low = high = ll;
            valid = ! isRange;
        }
    } else {
        if ((first == '[') || (first == '<')) {
            if (q != err) {
                low = (first == '[') ? ll : d.nearestUpward(ll, upper);
            }
            q = err + 1;
            T hh = d.fromstr(q, qend, &err);
            bool hasUpperLimit(q != err);
            if (*err == ';') {
                err = const_cast<char *>(_term.data() + _term.size() - 1);
            }
            valid = (*err == last) && ((last == ']') || (last == '>'));
            if (hasUpperLimit) {
                high = (last == ']') ? hh : d.nearestDownwd(hh, lower);
            }
        } else {
            valid = false;
        }
    }
    if (valid) {
        lower = low;
        upper = high;
    }
    return valid;
}
*/

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
