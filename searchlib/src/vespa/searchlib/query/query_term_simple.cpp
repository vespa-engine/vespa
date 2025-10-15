// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_simple.h"
#include "base.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/classname.h>
#include <cmath>
#include <limits>

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

constexpr int64_t NEG_MIN_I64 = std::numeric_limits<int64_t>::min();
constexpr int64_t POS_MAX_I64 = std::numeric_limits<int64_t>::max();

bool isRange(std::string_view s) noexcept {
    if (s.size() < 2) return false;
    // Check for partial range: <value or >value
    if ((s[0] == '<') || (s[0] == '>')) {
        return true;
    }
    // Check for full range: [value;value] or <value;value>
    const size_t sz(s.size());
    if ((sz >= 3u) &&
        (s[0] == '<' || s[0] == '[') &&
        (s[sz-1] == '>' || s[sz-1] == ']')) {
        return true;
    }
    return false;
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
            res.low = std::nextafter(res.low, std::numeric_limits<N>::infinity());
        }
        if (! _numeric_range->upper_inclusive) {
            res.high = std::nextafter(res.high, -std::numeric_limits<N>::infinity());
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
    _numeric_range = NumericRangeSpec::fromString(_term);
    if (isRange(_term)) {
        _valid = bool(_numeric_range);
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
