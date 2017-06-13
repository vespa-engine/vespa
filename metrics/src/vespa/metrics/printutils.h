// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * This file contains utility functions to help print out metric snapshots in
 * a user friendly way. It defines value types, functions for retrieving and
 * doing algorithmics with the values, and printing them in an HTML table.
 *
 * This is used by storage to print HTML metrics report for its status page.
 */

#pragma once

#include "metricmanager.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>

namespace metrics {
namespace printutils {

typedef std::pair<int64_t, bool> LongValue;
typedef std::pair<double, bool> DoubleValue;

struct MetricSource {
    typedef Metric::String String;

    const MetricSnapshot& _snapshot;
    String _metricsPrefix;
        // If no map is supplied, this map will own the data in the metrics
        // accessed map.
    std::map<String, Metric::SP> _metricsAccessedOwner;
    std::map<String, Metric::SP>& _metricsAccessed;

    MetricSource(const MetricSnapshot& s,
                 const String& metricsPrefix,
                 std::map<String, Metric::SP>* metricsAccessed = 0);
    ~MetricSource();
    String createAbsoluteMetricName(const String& name) const;

    struct SourceMetricVisitor : public metrics::MetricVisitor {
        String _stringPath;
        vespalib::StringTokenizer::TokenList _path;
        int32_t _pathIndex;
        Metric::UP _resultMetric;
        bool _prefixMatch;
        std::vector<String> _prefixMatches;

        SourceMetricVisitor(const String& path, bool prefixMatch);
        ~SourceMetricVisitor();

        void checkForPrefixMatch(const Metric& metric);

        bool visitMetricSet(const MetricSet& set, bool) override;
        void doneVisitingMetricSet(const MetricSet&) override { --_pathIndex; }
        bool visitMetric(const Metric& metric, bool) override;
    };

    const Metric* getMetric(const String& name);

    std::vector<String>
    getPathsMatchingPrefix(const String& prefix) const;
};

// Addition functions. Ensure that if floating point value is used,
// result ends up as floating point too.
LongValue operator+(LongValue addend1, LongValue addend2);

template<typename ValueType1, typename ValueType2>
DoubleValue operator+(std::pair<ValueType1, bool> addend1,
                      std::pair<ValueType2, bool> addend2)
{
    return DoubleValue(addend1.first + addend2.first,
                       addend1.second && addend2.second);
}

// Subtraction functions. Ensure that if floating point value is used,
// result ends up as floating point too.
LongValue operator-(LongValue minuend, LongValue subtrahend);

template<typename ValueType1, typename ValueType2>
DoubleValue operator-(std::pair<ValueType1, bool> minuend,
                      std::pair<ValueType2, bool> subtrahend)
{
    return DoubleValue(minuend.first - subtrahend.first,
                       minuend.second && subtrahend.second);
}

// Multiplication functions. Ensure that if floating point value is used,
// result ends up as floating point too.

LongValue operator*(LongValue factor1, LongValue factor2);

template<typename ValueType1, typename ValueType2>
DoubleValue operator*(std::pair<ValueType1, bool> factor1,
                      std::pair<ValueType2, bool> factor2)
{
    return std::pair<double, bool>(factor1.first * factor2.first,
                                   factor1.second && factor2.second);
}

// Division functions. Ensure that if floating point value is used,
// result ends up as floating point too.

LongValue operator/(LongValue dividend, LongValue divisor);

template<typename ValueType1, typename ValueType2>
DoubleValue operator/(std::pair<ValueType1, bool> dividend,
                                  std::pair<ValueType2, bool> divisor)
{
        // In case divisor is integer, we will core if we attempt to divide
        // with it.
    if (dividend.first == 0) return DoubleValue(
            0, dividend.second && divisor.second);
    if (divisor.first == 0) return DoubleValue(
            std::numeric_limits<double>().infinity(),
            dividend.second && divisor.second);
    return DoubleValue(
            dividend.first / static_cast<double>(divisor.first),
            dividend.second && divisor.second);
}

// Min/Max functions
template<typename ValueType>
std::pair<ValueType, bool> getMin(std::pair<ValueType, bool> val1,
                                  std::pair<ValueType, bool> val2)
{
    if (!val1.second) return val2;
    if (!val2.second) return val1;
    return std::pair<ValueType, bool>(std::min(val1.first, val2.first), true);
}

template<typename ValueType>
std::pair<ValueType, bool> getMax(std::pair<ValueType, bool> val1,
                                  std::pair<ValueType, bool> val2)
{
    if (!val1.second) return val2;
    if (!val2.second) return val1;
    return std::pair<ValueType, bool>(std::max(val1.first, val2.first), true);
}

// Wrapper types for primitives. Would be nice to allow primitives directly, but
// using a wrapper we can get by with less operator overloads above.

template<typename ValueType>
struct VW : public std::pair<ValueType, bool> {
    VW(ValueType val) : std::pair<ValueType, bool>(val, true) {}
};

typedef VW<int64_t> LVW;
typedef VW<double> DVW;


LongValue getLongMetric(const std::string& name, MetricSource& source);
DoubleValue getDoubleMetric(const std::string& name, MetricSource& source);
std::string getValueString(LongValue value, const char* format = "%'lld");
std::string getValueString(DoubleValue value, const char* format = "%'f");

template<typename ValueType>
std::string getByteValueString(std::pair<ValueType, bool> val)
{
    static const int64_t k = (1ul << 10);
    static const int64_t m = (1ul << 20);
    static const int64_t g = (1ul << 30);

    if (!val.second) return "na";
    std::pair<int64_t, bool> value(
            static_cast<int64_t>(val.first), val.second);
    if (value.first < 64 * k) {
        return getValueString(value, "%'llu B");
    }
    if (value.first < 64 * m) {
        value.first /= k;
        return getValueString(value, "%'llu kB");
    }
    if (value.first < 64 * g) {
        value.first /= m;
        return getValueString(value, "%'llu MB");
    }
    value.first /= g;
    return getValueString(value, "%'llu GB");
}

struct HttpTable {
    std::string title;
    std::string topLeftText;
    std::vector<std::string> colNames;
    std::vector<std::string> rowNames;
    struct Cell {
        bool set;
        std::string value;

        Cell() : set(false), value() {}

        void operator=(const std::string& val) { value = val; set = true; }
    };
    struct Row {
        std::vector<Cell> cells;
        Cell& operator[](uint32_t i) {
            if (i >= cells.size()) cells.resize(i + 1);
            return cells[i];
        }
    };
    std::vector<Row> cells;

    HttpTable(const std::string& title_, const std::string& topLeftText_);
    ~HttpTable();

    Row& operator[](uint32_t i);
    void fillInEmptyHoles();
    void print(std::ostream& out);
};

} // printutils
} // metrics

