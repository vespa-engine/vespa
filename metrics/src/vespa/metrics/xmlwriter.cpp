// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "xmlwriter.h"
#include "countmetric.h"
#include "metricset.h"
#include "metricsnapshot.h"
#include "valuemetric.h"
#include <vespa/vespalib/util/xmlstream.h>
#include <sstream>

namespace metrics {

XmlWriter::XmlWriter(vespalib::xml::XmlOutputStream& xos,
                     [[maybe_unused]] uint32_t period, int verbosity)
    : _xos(xos), _verbosity(verbosity) {}

bool
XmlWriter::visitSnapshot(const MetricSnapshot& snapshot)
{
    using namespace vespalib::xml;
    _xos << XmlTag("snapshot") << XmlAttribute("name", snapshot.getName())
         << XmlAttribute("from", snapshot.getFromTime())
         << XmlAttribute("to", snapshot.getToTime())
         << XmlAttribute("period", snapshot.getPeriod());
    return true;
}

void
XmlWriter::doneVisitingSnapshot(const MetricSnapshot&)
{
    using namespace vespalib::xml;
    _xos << XmlEndTag();
}

bool
XmlWriter::visitMetricSet(const MetricSet& set, bool)
{
    using namespace vespalib::xml;
    if (set.used() || _verbosity >= 2) {
        _xos << XmlTag(set.getName(), XmlTagFlags::CONVERT_ILLEGAL_CHARACTERS);
        printCommonXmlParts(set);
        return true;
    }
    return false;
}
void
XmlWriter::doneVisitingMetricSet(const MetricSet&) {
    using namespace vespalib::xml;
    _xos << XmlEndTag();
}

bool
XmlWriter::visitCountMetric(const AbstractCountMetric& metric, bool)
{
    MetricValueClass::UP values(metric.getValues());
    if (!metric.inUse(*values) && _verbosity < 2) return true;
    using namespace vespalib::xml;
    std::ostringstream ost;
    _xos << XmlTag(metric.getName(), XmlTagFlags::CONVERT_ILLEGAL_CHARACTERS)
         << XmlAttribute(metric.sumOnAdd()
                        ? "count" : "value", values->toString("count"));
    printCommonXmlParts(metric);
    _xos << XmlEndTag();
    return true;
}

bool
XmlWriter::visitValueMetric(const AbstractValueMetric& metric, bool)
{
    MetricValueClass::UP values(metric.getValues());
    if (!metric.inUse(*values) && _verbosity < 2) return true;
    using namespace vespalib::xml;
    _xos << XmlTag(metric.getName(), XmlTagFlags::CONVERT_ILLEGAL_CHARACTERS)
         << XmlAttribute("average", values->getLongValue("count") == 0
                    ? 0 : values->getDoubleValue("total")
                          / values->getDoubleValue("count"))
         << XmlAttribute("last", values->toString("last"));
    if (!metric.summedAverage()) {
        if (values->getLongValue("count") > 0) {
            _xos << XmlAttribute("min", values->toString("min"))
                 << XmlAttribute("max", values->toString("max"));
        }
        _xos << XmlAttribute("count", values->getLongValue("count"));
        if (_verbosity >= 2) {
            _xos << XmlAttribute("total", values->toString("total"));
        }
    }
    printCommonXmlParts(metric);
    _xos << XmlEndTag();
    return true;
}

void
XmlWriter::printCommonXmlParts(const Metric& metric) const
{
    using namespace vespalib::xml;
    const Metric::Tags& tags(metric.getTags());
    if (_verbosity >= 3 && tags.size() > 0) {
        std::ostringstream ost;
        // XXX print tag values as well
        ost << tags[0].key();
        for (uint32_t i=1; i<tags.size(); ++i) {
            ost << "," << tags[i].key();
        }
        _xos << XmlAttribute("tags", ost.str());
    }
    if (_verbosity >= 1 && !metric.getDescription().empty()) {
        _xos << XmlAttribute("description", metric.getDescription());
    }
}

} // metrics
