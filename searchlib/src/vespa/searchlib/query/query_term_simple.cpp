// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "base.h"
#include "query_term_simple.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <limits>

namespace {

template <typename N>
bool isValidInteger(int64_t value)
{
    return value >= std::numeric_limits<N>::min() && value <= std::numeric_limits<N>::max();
}

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
QueryTermSimple::getFloatRange() const
{
    double lowRaw, highRaw;
    bool valid = getAsDoubleTerm(lowRaw, highRaw);
    RangeResult<N> res;
    res.valid = valid;
    if (!valid) {
        res.low = std::numeric_limits<N>::max();
        res.high = - std::numeric_limits<N>::max();
        res.adjusted = true;
    } else {
        res.low = lowRaw;
        res.high = highRaw;
    }
    return res;
}

namespace {

bool isRepresentableByInt64(double d) {
    return    (d > double(std::numeric_limits<int64_t>::min()))
           && (d < double(std::numeric_limits<int64_t>::max()));
}

}

bool
QueryTermSimple::getRangeInternal(int64_t & low, int64_t & high) const
{
    bool valid = getAsIntegerTerm(low, high);
    if ( ! valid ) {
        double l(0), h(0);
        valid = getAsDoubleTerm(l, h);
        if (valid) {
            if ((l == h) && isRepresentableByInt64(l)) {
                low = high = std::round(l);
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
QueryTermSimple::getIntegerRange() const
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
QueryTermSimple::getRange() const
{
    return getFloatRange<float>();
}

template <>
QueryTermSimple::RangeResult<double>
QueryTermSimple::getRange() const
{
    return getFloatRange<double>();
}

template <>
QueryTermSimple::RangeResult<int8_t>
QueryTermSimple::getRange() const
{
    return getIntegerRange<int8_t>();
}

template <>
QueryTermSimple::RangeResult<int16_t>
QueryTermSimple::getRange() const
{
    return getIntegerRange<int16_t>();
}

template <>
QueryTermSimple::RangeResult<int32_t>
QueryTermSimple::getRange() const
{
    return getIntegerRange<int32_t>();
}

template <>
QueryTermSimple::RangeResult<int64_t>
QueryTermSimple::getRange() const
{
    return getIntegerRange<int64_t>();
}

template <int B>
struct IntDecoder {
    static int64_t fromstr(const char * v, char ** end) { return strtoll(v, end, B); }
    static int64_t nearestDownwd(int64_t n, int64_t min) { return (n > min ? n - 1 : n); }
    static int64_t nearestUpward(int64_t n, int64_t max) { return (n < max ? n + 1 : n); }
};

struct DoubleDecoder {
    static double fromstr(const char * v, char ** end) { return vespalib::locale::c::strtod(v, end); }
    static double nearestDownwd(double n, double min) { return std::nextafterf(n, min); }
    static double nearestUpward(double n, double max) { return std::nextafterf(n, max); }
};

bool QueryTermSimple::getAsIntegerTerm(int64_t & lower, int64_t & upper) const
{
    lower = std::numeric_limits<int64_t>::min();
    upper = std::numeric_limits<int64_t>::max();
    return getAsNumericTerm(lower, upper, IntDecoder<10>());
}

bool QueryTermSimple::getAsDoubleTerm(double & lower, double & upper) const
{
    lower = - std::numeric_limits<double>::max();
    upper =   std::numeric_limits<double>::max();
    return getAsNumericTerm(lower, upper, DoubleDecoder());
}

QueryTermSimple::~QueryTermSimple() = default;

namespace {

bool isFullRange(vespalib::stringref s) {
    const size_t sz(s.size());
    return (sz >= 3u) &&
           (s[0] == '<' || s[0] == '[') &&
           (s[sz-1] == '>' || s[sz-1] == ']');
}

}

QueryTermSimple::QueryTermSimple(const string & term_, Type type)
    : _rangeLimit(0),
      _maxPerGroup(0),
      _diversityCutoffGroups(std::numeric_limits<uint32_t>::max()),
      _type(type),
      _diversityCutoffStrict(false),
      _valid(true),
      _term(term_),
      _diversityAttribute()
{
    if (isFullRange(_term)) {
        stringref rest(_term.c_str() + 1, _term.size() - 2);
        stringref parts[9];
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
                rest = stringref();
            }
        }
        _valid = (numParts >= 2) && (numParts < NELEMS(parts));
        if (_valid && numParts > 2) {
            _rangeLimit = strtol(parts[2].data(), nullptr, 0);
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
QueryTermSimple::getAsNumericTerm(T & lower, T & upper, D d) const
{
    bool valid(empty());
    size_t sz(_term.size());
    if (sz) {
        char *err(nullptr);
        T low(lower);
        T high(upper);
        const char * q = _term.c_str();
        const char first(q[0]);
        const char last(q[sz-1]);
        q += ((first == '<') || (first == '>') || (first == '[')) ? 1 : 0;
        T ll = d.fromstr(q, &err);
        valid = isValid() && ((*err == 0) || (*err == ';'));
        if (valid) {
            if (first == '<' && (*err == 0)) {
                high = d.nearestDownwd(ll, lower);
            } else if (first == '>' && (*err == 0)) {
                low = d.nearestUpward(ll, upper);
            } else if ((first == '[') || (first == '<')) {
                if (q != err) {
                    low = (first == '[') ? ll : d.nearestUpward(ll, upper);
                }
                q = err + 1;
                T hh = d.fromstr(q, &err);
                bool hasUpperLimit(q != err);
                if (*err == ';') {
                    err = const_cast<char *>(_term.end() - 1);
                }
                valid = (*err == last) && ((last == ']') || (last == '>'));
                if (hasUpperLimit) {
                    high = (last == ']') ? hh : d.nearestDownwd(hh, lower);
                }
            } else {
                low = high = ll;
            }
        }
        if (valid) {
            lower = low;
            upper = high;
        }
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
    if (obj != 0) {
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
