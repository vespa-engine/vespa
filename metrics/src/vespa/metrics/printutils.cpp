// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "printutils.h"

namespace metrics {
namespace printutils {

typedef std::pair<int64_t, bool> LongValue;
typedef std::pair<double, bool> DoubleValue;

MetricSource::MetricSource(const MetricSnapshot& s,
                           const String& metricsPrefix,
                           std::map<String, Metric::SP>* metricsAccessed)
    : _snapshot(s),
      _metricsPrefix(metricsPrefix),
      _metricsAccessedOwner(),
      _metricsAccessed(metricsAccessed == 0 ? _metricsAccessedOwner
                                            : *metricsAccessed)
{
}

MetricSource::~MetricSource() {}

Metric::String
MetricSource::createAbsoluteMetricName(const String& name) const {
    String prefix = _metricsPrefix;
    String addition = name;
    while (addition.find("../") == 0) {
        String::size_type pos1 = prefix.rfind('.');
        if (pos1 == String::npos)
            throw vespalib::IllegalArgumentException(
                    "Cannot go back anymore in path " + prefix,
                    VESPA_STRLOC);
        prefix = prefix.substr(0, pos1);
        addition = addition.substr(3);
    }
    return (prefix.empty() ? addition : prefix + "." + addition);
}

MetricSource::SourceMetricVisitor::SourceMetricVisitor(const String& path, bool prefixMatch)
    : _stringPath(path),
      _path(vespalib::StringTokenizer(path, ".").getTokens()),
      _pathIndex(-1),
      _resultMetric(),
      _prefixMatch(prefixMatch),
      _prefixMatches()
{
}
       
MetricSource::SourceMetricVisitor::~SourceMetricVisitor() { } 

void
MetricSource::SourceMetricVisitor::checkForPrefixMatch(const Metric& metric) {
    if (metric.getName().size() >= _path[_pathIndex].size()) {
        if (metric.getName().find(_path[_pathIndex]) == 0) {
            _prefixMatches.push_back(metric.getName());
        }
    }
}

bool
MetricSource::SourceMetricVisitor::visitMetricSet(const MetricSet& set, bool) {
    if (_pathIndex == -1) {
        _pathIndex = 0;
        return true;
    }
    if (_prefixMatch
        && static_cast<size_t>(_pathIndex + 1) == _path.size())
    {
        for (const Metric * entry : set.getRegisteredMetrics()) {
            checkForPrefixMatch(*entry);
        }
        return false;
    }
    if (set.getName() != _path[_pathIndex]) return false;
    if (static_cast<size_t>(++_pathIndex) >= _path.size()) {
        throw vespalib::IllegalArgumentException(
                "Path " + _stringPath + " points to a metric set. "
                "Only primitive metrics can be retrieved.",
                VESPA_STRLOC);
    }
    return true;
}
bool
MetricSource::SourceMetricVisitor::visitMetric(const Metric& metric, bool) {
    if (_prefixMatch) {
        checkForPrefixMatch(metric);
    }
    if (_path[_pathIndex] != metric.getName()) {
        return true;
    }
    if (_prefixMatch) {
        throw vespalib::IllegalArgumentException(
                "Cannot find existing entries with prefix "
                + _stringPath + " since element " + metric.getName()
                + " is not a metric set", VESPA_STRLOC);
    }
    if (static_cast<size_t>(_pathIndex + 1) < _path.size()) {
        throw vespalib::IllegalArgumentException(
                "Path " + _stringPath + " cannot exist since element "
                + _path[_pathIndex] + " is not a metric set: "
                + metric.toString(),
                VESPA_STRLOC);
    }
    std::vector<Metric::LP> ownerList;
    _resultMetric.reset(metric.clone(ownerList, Metric::INACTIVE, 0));
    if (!ownerList.empty()) {
        throw vespalib::IllegalArgumentException(
                "Metric " + metric.getName() + " added entries to "
                "owners list when cloning. This should not happen "
                "for primitive metrics.", VESPA_STRLOC);
    }
    return false;
}

const Metric*
MetricSource::getMetric(const String& name) {
    String path = createAbsoluteMetricName(name);
    std::map<String, Metric::SP>::const_iterator it(
            _metricsAccessed.find(path));
    if (it != _metricsAccessed.end()) {
        return it->second.get();
    }
    SourceMetricVisitor visitor(path, false);
    _snapshot.getMetrics().visit(visitor);
    if (visitor._resultMetric.get() == 0) {
        throw vespalib::IllegalArgumentException(
                "Metric " + path + " was not found.", VESPA_STRLOC);
    }
    Metric::SP metric(visitor._resultMetric.release());
    _metricsAccessed[path] = metric;
    return metric.get();
}

std::vector<Metric::String>
MetricSource::getPathsMatchingPrefix(const String& prefix) const
{
    String path = createAbsoluteMetricName(prefix);
    SourceMetricVisitor visitor(path, true);
    _snapshot.getMetrics().visit(visitor);
    return visitor._prefixMatches;
}

// Addition functions. Ensure that if floating point value is used,
// result ends up as floating point too.
LongValue operator+(LongValue addend1, LongValue addend2)
{
    return LongValue(addend1.first + addend2.first,
                     addend1.second && addend2.second);
}

// Subtraction functions. Ensure that if floating point value is used,
// result ends up as floating point too.
LongValue operator-(LongValue minuend, LongValue subtrahend)
{
    return LongValue(minuend.first - subtrahend.first,
                     minuend.second && subtrahend.second);
}

// Multiplication functions. Ensure that if floating point value is used,
// result ends up as floating point too.

LongValue operator*(LongValue factor1, LongValue factor2)
{
    return std::pair<int64_t, bool>(factor1.first * factor2.first,
                                    factor1.second && factor2.second);
}

// Division functions. Ensure that if floating point value is used,
// result ends up as floating point too.

LongValue operator/(LongValue dividend, LongValue divisor)
{
    if (dividend.first == 0) return LongValue(
            0, dividend.second && divisor.second);
    if (divisor.first == 0) return LongValue(
            std::numeric_limits<int64_t>().max(),
            dividend.second && divisor.second);
    return LongValue(dividend.first / divisor.first,
                     dividend.second && divisor.second);
}

/** Get metric with given name from source. Set bool true if existing. */
LongValue getLongMetric(const std::string& name, MetricSource& source)
{
    std::string::size_type pos = name.rfind('.');
    const Metric* metric = (pos == std::string::npos
            ? 0 : source.getMetric(name.substr(0, pos)));
    try{
        return LongValue(metric == 0
                ? 0 : metric->getLongValue(name.substr(pos+1)), metric != 0);
    } catch (vespalib::IllegalArgumentException& e) {
        return LongValue(0, false);
    }
}

/** Get metric with given name from source. Set bool true if existing. */
DoubleValue getDoubleMetric(const std::string& name, MetricSource& source)
{
    std::string::size_type pos = name.rfind('.');
    const Metric* metric = (pos == std::string::npos
            ? 0 : source.getMetric(name.substr(0, pos)));
    try{
        return DoubleValue(metric == 0
               ? 0.0 : metric->getDoubleValue(name.substr(pos+1)), metric != 0);
    } catch (vespalib::IllegalArgumentException& e) {
        return DoubleValue(0, false);
    }
}

std::string getValueString(LongValue value, const char* format)
{
    if (!value.second) return "na";
    std::vector<char> buffer(30);
    snprintf(&buffer[0], 30, format, value.first);
    return std::string(&buffer[0]);
}

std::string getValueString(DoubleValue value, const char* format)
{
    if (!value.second) return "na";
    std::vector<char> buffer(30);
    snprintf(&buffer[0], 30, format, value.first);
    return std::string(&buffer[0]);
}

HttpTable::HttpTable(const std::string& title_, const std::string& topLeftText_)
    : title(title_), topLeftText(topLeftText_)
{}
HttpTable::~HttpTable() { }

HttpTable::Row&
HttpTable::operator[](uint32_t i) {
    if (i >= cells.size()) cells.resize(i + 1);
    return cells[i];
}

void
HttpTable::fillInEmptyHoles() {
    if (rowNames.size() < cells.size()) rowNames.resize(cells.size());
    if (rowNames.size() > cells.size()) cells.resize(rowNames.size());
    for (uint32_t i=0; i<cells.size(); ++i) {
        if (colNames.size() < cells[i].cells.size())
            colNames.resize(cells[i].cells.size());
        if (colNames.size() > cells[i].cells.size())
            cells[i].cells.resize(colNames.size());
    }
}

void
HttpTable::print(std::ostream& out) {
    out << "<h3>" << title << "</h3>\n";
    out << "<table border=\"1\">\n";
    fillInEmptyHoles();
    for (uint32_t i=0; i<=rowNames.size(); ++i) {
        if (i == 0) {
            out << "<tr><th>" << topLeftText << "</th>";
            for (uint32_t j=0; j<colNames.size(); ++j) {
                out << "<th>" << colNames[j] << "</th>";
            }
            out << "</tr>\n";
        } else {
            out << "<tr><td>" << rowNames[i - 1] << "</td>";
            for (uint32_t j=0; j<colNames.size(); ++j) {
                out << "<td align=\"right\">"
                    << (cells[i - 1][j].set ? cells[i - 1][j].value : "-")
                    << "</td>";
            }
            out << "</tr>\n";
        }
    }
    out << "</table>\n";
}

} // printutils
} // metrics

