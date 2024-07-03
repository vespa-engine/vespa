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

struct IntDecoder {
    static int64_t fromstr(const char * q, const char * qend, const char ** end) noexcept {
        int64_t v(0);
        for (;q < qend && (isspace(*q) || (*q == '+')); q++);
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
#if defined(_LIBCPP_VERSION) && _LIBCPP_VERSION < 190000
        vespalib::string tmp(q, qend - q);
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
        for (;q < qend && (isspace(*q) || (*q == '+')); q++);
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

QueryTermSimple::QueryTermSimple(const string & term_, Type type)
    : _rangeLimit(0),
      _maxPerGroup(0),
      _diversityCutoffGroups(std::numeric_limits<uint32_t>::max()),
      _type(type),
      _diversityCutoffStrict(false),
      _valid(true),
      _fuzzy_prefix_match(false),
      _term(term_),
      _diversityAttribute(),
      _fuzzy_max_edit_distance(2),
      _fuzzy_prefix_lock_length(0)
{
    if (isFullRange(_term)) {
        string_view rest(_term.c_str() + 1, _term.size() - 2);
        string_view parts[9];
        size_t numParts(0);
        while (! rest.empty() && ((numParts + 1) < NELEMS(parts))) {
            size_t pos(rest.find(';'));
            if (pos != vespalib::string::npos) {
                parts[numParts++] = rest.substr(0, pos);
                rest = rest.substr(pos + 1);
                if (rest.empty()) {
                    parts[numParts++] = rest;
                }
            } else {
                parts[numParts++] = rest;
                rest = string_view();
            }
        }
        _valid = (numParts >= 2) && (numParts < NELEMS(parts));
        if (_valid && numParts > 2) {
            _rangeLimit = static_cast<int32_t>(strtol(parts[2].data(), nullptr, 0));
            if (numParts > 3) {
                _valid = (numParts >= 5);
                if (_valid) {
                    _diversityAttribute = parts[3];
                    _maxPerGroup = strtoul(parts[4].data(), nullptr, 0);
                    if ((_maxPerGroup > 0) && (numParts > 5)) {
                        char *err = nullptr;
                        size_t cutoffGroups = strtoul(parts[5].data(), &err, 0);
                        if ((err == nullptr) || (size_t(err - parts[5].data()) == parts[5].size())) {
                            _diversityCutoffGroups = cutoffGroups;
                        }
                        if (numParts > 6) {
                            _diversityCutoffStrict = (parts[6] == "strict");
                            _valid = (numParts == 7);
                        }
                    }
                }
            }
        }
    }
} 

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
                err = const_cast<char *>(_term.end() - 1);
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

vespalib::string
QueryTermSimple::getClassName() const
{
    return vespalib::getClassName(*this);
}

}

void
visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const search::QueryTermSimple *obj)
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
visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const search::QueryTermSimple &obj)
{
    visit(self, name, &obj);
}
