// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusmetricconsumer.h"
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <boost/assign.hpp>
#include <boost/lexical_cast.hpp>
#include <vespa/metrics/printutils.h>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/textwriter.h>
#include <vespa/metrics/xmlwriter.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/xmlstream.h>

#include <vespa/log/log.h>
LOG_SETUP(".status.metricreporter");

namespace storage {

StatusMetricConsumer::StatusMetricConsumer(
        StorageComponentRegister& compReg, metrics::MetricManager& manager,
        const std::string& name)
    : framework::StatusReporter("metrics", "Performance metrics"),
      _manager(manager),
      _component(compReg, "statusmetricsconsumer"),
      _name(name),
      _startTime(_component.getClock().getTimeInSeconds()),
      _processedTime(0)
{
    LOG(debug, "Started metrics consumer");
    setlocale(LC_NUMERIC, "");
    _component.registerMetricUpdateHook(*this, framework::SecondTime(3600));
    _component.registerStatusPage(*this);
}

StatusMetricConsumer::~StatusMetricConsumer() = default;

void
StatusMetricConsumer::updateMetrics(const MetricLockGuard & guard)
{
    metrics::MemoryConsumption::UP mc(_manager.getMemoryConsumption(guard));
    // TODO is this hook needed anymore?
}

vespalib::string
StatusMetricConsumer::getReportContentType(
        const framework::HttpUrlPath& path) const
{
    if (!path.hasAttribute("format")) {
        return "text/html";
    }

    if (path.getAttribute("format") == "xml") {
        return "application/xml";
    }

    if (path.getAttribute("format") == "text") {
        return "text/plain";
    }

    if (path.getAttribute("format") == "json") {
        return "application/json";
    }

    return "text/html";
}

namespace {
    void addSnapshotAsTableRow(const metrics::MetricSnapshot& snapshot,
                               const metrics::MetricSnapshotSet* building,
                               std::ostream& out,
                               int32_t interval = -3)
    {
        if (interval == -3) interval = snapshot.getPeriod();
        std::string name(snapshot.getName());
        if (interval == -1) {
            name = "Clone of total metrics with active metrics added";
        }
        std::vector<char> buffer(40);
        out << "  <tr>\n"
            << "    <td>" << name << "</td>\n";
        //if (snapshot.getToTime() != 0 || interval < 0 || building != 0)
        for (uint32_t format=0; format<4; ++format) {
                // 0 XML - 1 HTML - 2 text, 3 JSON
            out << "    <td>";
            bool linked = false;
            for (uint32_t tmp=0; tmp<2; ++tmp) { // 0 last - 1 temp
                if (tmp == 1) {
                    if (building == 0 || !building->hasTemporarySnapshot()
                        || building->getBuilderCount() == 0)
                    {
                        continue;
                    }
                } else {
                    if ((snapshot.getToTime() == 0 && interval >= 0)
                        || snapshot.getToTime() == snapshot.getFromTime())
                    {
                        continue;
                    }
                }
                if (tmp == 1) out << "&nbsp;&nbsp;";

                const char* formatStr = "xml";
                const char* consumer = "status";
                switch (format) {
                case 0:
                    formatStr = "xml";
                    break;
                case 1:
                    formatStr = "html";
                    break;
                case 2:
                    formatStr = "text";
                    break;
                case 3:
                    formatStr = "json";
                    consumer = "yamas";
                    break;
                }

                linked = true;
                out << "<a href=\""
                    << "?interval=" << interval
                    << "&format=" << formatStr
                    << "&consumer=" << consumer
                    << "&verbosity=0"
                    << "&pattern=.*"
                    << "&callsnapshothooks=0"
                    << "&tmpsnapshot=" << tmp
                    << "\">"
                    << (tmp == 0 ? (interval > 0 ? "Last complete"
                                                 : "Current")
                                 : "Building")
                    << "</a>";
            }
            if (!linked) {
                out << "None taken yet";
            }
            out << "</td>\n";
        }
        out << "</tr>\n";
    }

}

bool
StatusMetricConsumer::reportStatus(std::ostream& out,
                                   const framework::HttpUrlPath& path) const
{
        // Update metrics unless 'dontcallupdatehooks' is 1. Update
        // snapshot metrics too, if callsnapshothooks is set to 1.
    if (path.get("dontcallupdatehooks", 0) == 0) {
        bool updateSnapshotHooks = path.get("callsnapshothooks", 0) == 1;
        LOG(debug, "Updating metrics ahead of status page view%s",
            updateSnapshotHooks ? ", calling snapshot hooks too" : ".");
        _manager.updateMetrics(updateSnapshotHooks);
    } else {
        LOG(debug, "Not calling update hooks as dontcallupdatehooks option "
                   "has been given");
    }
    framework::SecondTime currentTime(_component.getClock().getTimeInSeconds());
    bool html = (!path.hasAttribute("format")
                 || path.getAttribute("format") == "html");
    bool xml = (!html && path.getAttribute("format") == "xml");
    bool json = (!html && path.getAttribute("format") == "json");

    int verbosity(path.get("verbosity", 0));
        // We have to copy unset values if using HTML as HTML version gathers
        // metrics for calculations and thus needs unset values.
    bool copyUnset = (html || verbosity >= 2);
    bool temporarySnap = (path.get("tmpsnapshot", 0) == 1);

    framework::PartlyHtmlStatusReporter htmlReporter(*this);
    if (html) {
        htmlReporter.reportHtmlHeader(out, path);
    }

    if (path.hasAttribute("task") && path.getAttribute("task") == "reset") {
        {
            vespalib::MonitorGuard sync(_waiter);
            _manager.reset(currentTime.getTime());
        }
        if (html) {
            out << "<p>Metrics reset at " << currentTime << ".</p>\n";
        }
    }

    if (html) {
        out << "<p><div align=\"right\"><a href=\"?task=reset\">"
            << "Reset all metrics</a></div></p>\n"
            << "<p>Metrics available at " << framework::SecondTime(currentTime)
            << "</p>\n"
            << "<table border=\"1\" cellspacing=\"3\">\n"
            << "  <tr><td>Display metrics from</td>\n"
            << "      <td>XML raw data</td>\n"
            << "      <td>HTML presentation</td>\n"
            << "      <td>Text output</td>\n"
            << "      <td>JSON output</td></tr>\n";

        metrics::MetricLockGuard metricLock(_manager.getMetricLock());
        std::vector<uint32_t> intervals(
                _manager.getSnapshotPeriods(metricLock));
        addSnapshotAsTableRow(
                _manager.getActiveMetrics(metricLock), 0, out, -2);
        for (uint32_t i=0; i<intervals.size(); ++i) {
            addSnapshotAsTableRow(
                    _manager.getMetricSnapshot(metricLock, intervals[i]),
                    &_manager.getMetricSnapshotSet(metricLock, intervals[i]),
                    out);
        }
        addSnapshotAsTableRow(
                _manager.getTotalMetricSnapshot(metricLock), 0, out);
        addSnapshotAsTableRow(
                _manager.getTotalMetricSnapshot(metricLock), 0, out, -1);
        out << "</table>\n";

        out << "<h3>Metrics explanation</h3>\n"
            << "<p>\n"
            << "The active metrics are currently being updated. The snapshots "
            << "update at given time intervals, such that you can always view "
            << "activity for a full time window. A total snapshot is also "
            << "available. It is updated each time the shortest interval "
            << "snapshot is updated. In addition it is possible to view a "
            << "truly total metric set for all metrics since start, but this "
            << "is a bit more expensive as we need to take a copy of the total "
            << "snapshot to add the active metrics to it.\n"
            << "</p><p>\n"
            << "The XML view has a verbosity option that can be adjusted to "
            << "control the amount of detail shown. With the default verbosity "
            << "only the critical parts are shown. Currently, verbosity 1 adds "
            << "descriptions. Verbosity 2 adds unused metrics and total values "
            << "for value metrics. Verbosity 3 add tags.\n"
            << "</p><p>\n"
            << "The Text view uses a pattern that has a regular expression. "
            << "Only metrics whose name path matches the regular expression "
            << "will be shown.\n"
            << "</p><p>\n"
            << "Both XML, json and text view use consumer identifiers to detect "
            << "what metrics to show. The default status consumer hides "
            << "metrics that are part of sums and shows everything else. Use an "
            << "empty consumer string to see all metrics. The 'log' consumer "
            << "can be used to see metrics logged. JSON uses the yamas consumer as "
            << "default.\n"
            << "</p>\n";
    }

    if (path.hasAttribute("interval")) {
        // Grab the snapshot we want to view more of
        int32_t interval(
                boost::lexical_cast<int32_t>(path.getAttribute("interval")));
        metrics::MetricLockGuard metricLock(_manager.getMetricLock());
        std::unique_ptr<metrics::MetricSnapshot> generated;
        const metrics::MetricSnapshot* snapshot;
        if (interval == -2) {
            snapshot = &_manager.getActiveMetrics(metricLock);
            _manager.getActiveMetrics(metricLock).setToTime(
                    currentTime.getTime());
        } else if (interval == -1) {
            // "Prime" the metric structure by first fetching the set of active
            // metrics (complete with structure) and resetting these. This
            // leaves us with an empty metrics set to which we can (in order)
            // add the total and the active metrics. If this is not done, non-
            // written metrics won't be included even if copyUnset is true.
            generated.reset(new metrics::MetricSnapshot(
                    "Total metrics from start until current time", 0,
                    _manager.getActiveMetrics(metricLock).getMetrics(),
                    copyUnset));
            generated->reset(0);
            _manager.getTotalMetricSnapshot(metricLock).addToSnapshot(
                    *generated, currentTime.getTime());
            _manager.getActiveMetrics(metricLock).addToSnapshot(
                    *generated, currentTime.getTime());
            generated->setFromTime(
                    _manager.getTotalMetricSnapshot(metricLock).getFromTime());
            snapshot = generated.get();
        } else if (interval == 0) {
            if (copyUnset) {
                generated.reset(new metrics::MetricSnapshot(
                        _manager.getTotalMetricSnapshot(metricLock).getName(),
                        0,
                        _manager.getActiveMetrics(metricLock).getMetrics(),
                        true));
                generated->reset(0);
                _manager.getTotalMetricSnapshot(metricLock).addToSnapshot(
                        *generated, currentTime.getTime());
                snapshot = generated.get();
            } else {
                snapshot = &_manager.getTotalMetricSnapshot(metricLock);
            }
        } else {
            if (copyUnset) {
                generated.reset(new metrics::MetricSnapshot(
                        _manager.getMetricSnapshot(metricLock, interval)
                                .getName(), 0,
                        _manager.getActiveMetrics(metricLock).getMetrics(),
                        true));
                generated->reset(0);
                _manager.getMetricSnapshot(metricLock, interval, temporarySnap)
                        .addToSnapshot(*generated, currentTime.getTime());
                snapshot = generated.get();
            } else {
                snapshot = &_manager.getMetricSnapshot(
                        metricLock, interval, temporarySnap);
            }
        }

        std::string consumer = path.getAttribute("consumer", "");
        if (html) {
            printHtmlMetricsReport(out, *snapshot,
                                   path.hasAttribute("includenotused"));
        } else if (xml) {
            out << "<?xml version=\"1.0\"?>\n";
            vespalib::XmlOutputStream xos(out);
            metrics::XmlWriter xmlWriter(xos, snapshot->getPeriod(), verbosity);
            _manager.visit(metricLock, *snapshot, xmlWriter, consumer);
            out << "\n";
        } else if (json) {
            vespalib::asciistream jsonStreamData;
            vespalib::JsonStream stream(jsonStreamData, true);
            stream << Object() << "metrics";
            metrics::JsonWriter metricJsonWriter(stream);
            _manager.visit(metricLock, *snapshot, metricJsonWriter, consumer);
            stream << End();
            stream.finalize();
            out << jsonStreamData.str();
        } else {
            std::string pattern = path.getAttribute("pattern", ".*");
            metrics::TextWriter textWriter(out, snapshot->getPeriod(),
                                           pattern, verbosity > 0);
            _manager.visit(metricLock, *snapshot, textWriter, consumer);
        }
    }
    if (html) {
        htmlReporter.reportHtmlFooter(out, path);
    }
    return true;
}

void
StatusMetricConsumer::waitUntilTimeProcessed(framework::SecondTime t) const
{
    return; // Return straight away as thread is not running now.
        // This is used in unit testing to wait for internal thread to have
        // generated snapshots. Wait aggressively and signal other thread to
        // make it do it quick (as it uses fake timer)
    vespalib::MonitorGuard sync(_waiter);
    while (_processedTime < t) {
        sync.signal();
        sync.wait(1);
    }
}

void
StatusMetricConsumer::writeXmlTags(std::ostream& out,
                                   const vespalib::StringTokenizer& name,
                                   std::vector<std::string>& xmlTags) const
{
    // Find how many common elements exist.
    uint32_t equalUpToIndex = 0;
    if (name.size() != 0) {
        uint32_t n = std::min((size_t)name.size() - 1, xmlTags.size());
        while (equalUpToIndex < n &&
               name[equalUpToIndex] == xmlTags[equalUpToIndex])
        {
            ++equalUpToIndex;
        }
    }

    // End old tags that aren't common.
    for (uint32_t i=0, n=xmlTags.size() - equalUpToIndex; i<n; ++i) {
        for (uint32_t j=0; j<xmlTags.size(); ++j) out << "  ";
        std::string xmlname(xmlTags.back());
        std::replace(xmlname.begin(), xmlname.end(), ' ', '_');
        out << "</" << xmlname << ">\n";
        xmlTags.pop_back();
    }

    // Create new tags that aren't common.
    for (uint32_t i=equalUpToIndex; i + 1 <name.size(); ++i) {
        std::string xmlname(name[i]);
        if (xmlname.size() > 0) {
            for (uint32_t j=0; j<=xmlTags.size(); ++j) out << "  ";
            xmlTags.push_back(xmlname);
            std::replace(xmlname.begin(), xmlname.end(), ' ', '_');
            out << "<" << xmlname << ">\n";
        }
    }
}

namespace {
    struct UnusedMetricPrinter : public metrics::MetricVisitor {
        const std::map<metrics::Metric::String,
                       metrics::Metric::SP>& _usedMetrics;
        std::ostream& _out;

        UnusedMetricPrinter(const std::map<metrics::Metric::String,
                                           metrics::Metric::SP>& used,
                            std::ostream& out)
            : _usedMetrics(used), _out(out) {}

        bool visitMetric(const metrics::Metric& metric, bool) override {
            std::map<metrics::Metric::String,
                     metrics::Metric::SP>::const_iterator it(
                        _usedMetrics.find(metric.getPath()));
            if (it == _usedMetrics.end()) {
                std::string result = metric.toString();
                std::string::size_type pos1 = result.find(' ');
                std::string::size_type pos2 = result.rfind('"');
                _out << metric.getPath() << result.substr(pos1, pos2 + 1 - pos1)
                     << "\n";
            }
            return true;
        }
    };
}

void
StatusMetricConsumer::printHtmlMetricsReport(
        std::ostream& out, const metrics::MetricSnapshot& data,
        bool includeNotUsed) const
{
    std::map<String, Metric::SP> usedMetrics;

    out << "<h2>Metrics report for the last "
        << framework::SecondTime(data.getLength())
                .toString(framework::DIFFERENCE)
        << ", from " << framework::SecondTime(data.getFromTime()) << " to "
        << framework::SecondTime(data.getFromTime() + data.getLength())
        << "</h2>\n";

    out << "<p>\n"
        << "Note that min/max values are currently always from start of "
        << "process, as current procedure for gathering data doesn't let us "
        << "know min/max for windows.\n"
        << "</p>\n";

        // Storage node metrics
    if (_component.getNodeType() == lib::NodeType::STORAGE) {
        printStorageHtmlReport(out, usedMetrics, data);
        printOperationHtmlReport(out, usedMetrics, data);
        printMaintOpHtmlReport(out, usedMetrics, data);
        out << "<br>\n"
            << "<table cellpadding=\"0\" cellspacing=\"0\">\n"
            << "<td valign=\"top\">\n";
        out << "</td><td>&nbsp;&nbsp;</td>\n"
            << "<td valign=\"top\">\n";
        out << "</td></table><br>\n";
        printMergeHtmlReport(out, usedMetrics, data);
        printVisitHtmlReport(out, usedMetrics, data);
    }

    if (includeNotUsed) {
        out << "<h2>Metrics not used by user friendly overview above</h2>\n";
        out << "<pre>\n";
        UnusedMetricPrinter metricPrinter(usedMetrics, out);
        data.getMetrics().visit(metricPrinter);
        out << "</pre>\n";
    }
}

void
StatusMetricConsumer::printStorageHtmlReport(
        std::ostream& out,
        std::map<metrics::Metric::String, metrics::Metric::SP>& usedMetrics,
        const metrics::MetricSnapshot& snapshot) const
{
    using namespace boost::assign;
    using namespace metrics::printutils;

    MetricSource ms(snapshot, "vds.datastored", &usedMetrics);
    HttpTable table("Disk storage utilization", "Disk");
    table.colNames += "Stored data", "Document count";

    std::vector<std::string> diskMetrics(ms.getPathsMatchingPrefix("disk_"));
    std::map<uint32_t, std::string> indexMap;
    for(std::vector<std::string>::const_iterator it = diskMetrics.begin();
        it != diskMetrics.end(); ++it)
    {
        std::string::size_type pos = it->find('_');
        indexMap[boost::lexical_cast<uint32_t>(it->substr(pos + 1))] = *it;
    }

    std::map<uint32_t, std::string>::const_iterator it = indexMap.begin();
    for (uint32_t i=0; i<indexMap.size(); ++i) {
        std::ostringstream ost;
        ost << it->first;
        table.rowNames += ost.str();
        table[i][0] = getByteValueString(
                getLongMetric(it->second + ".bytes.value", ms));
        table[i][1] = getValueString(
                getLongMetric(it->second + ".docs.value", ms));
        ++it;
    }
    table.rowNames += "Total";
    table[diskMetrics.size()][0] = getByteValueString(
            getLongMetric("alldisks.bytes.value", ms));
    table[diskMetrics.size()][1] = getValueString(
            getLongMetric("alldisks.docs.value", ms));

    table.print(out);
}

void
StatusMetricConsumer::printOperationHtmlReport(
        std::ostream& out,
        std::map<metrics::Metric::String, metrics::Metric::SP>& usedMetrics,
        const metrics::MetricSnapshot& snapshot) const
{
    using namespace boost::assign;
    using namespace metrics::printutils;

    LVW timePeriod(snapshot.getLength());

    MetricSource ms(snapshot, "vds.filestor.alldisks.allthreads", &usedMetrics);
    HttpTable table("External load", "Operation");
    table.colNames += "Ops/s", "Ops", "Failed", "Not found",
                      "Min latency", "Avg latency", "Max latency";
    LongValue failed;

    table.rowNames += "Put";
    table[0][0] = getValueString(
            DVW(1.0) * getLongMetric("put.sum.count.count", ms) / timePeriod,
            "%'3.2f");
    table[0][1] = getValueString(getLongMetric("put.sum.count.count", ms));
    LongValue failedPuts = getLongMetric("put.sum.count.count", ms)
                         - getLongMetric("put.sum.latency.count", ms);
    table[0][2] = getValueString(failedPuts) + getValueString(
            DVW(100) * failedPuts / getLongMetric("put.sum.count.count", ms),
            " (%'3.2f %%)");
    table[0][4] = getValueString(
            getDoubleMetric("put.sum.latency.min", ms), "%'3.2f ms");
    table[0][5] = getValueString(
            getDoubleMetric("put.sum.latency.average", ms), "%'3.2f ms");
    table[0][6] = getValueString(
            getDoubleMetric("put.sum.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Update";
    table[1][0] = getValueString(
            DVW(1.0) * getLongMetric("update.sum.count.count", ms) / LVW(timePeriod),
            "%'3.2f");
    table[1][1] = getValueString(getLongMetric("update.sum.count.count", ms));
    LongValue failedUpdates = getLongMetric("update.sum.count.count", ms)
                            - getLongMetric("update.sum.latency.count", ms);
    table[1][2] = getValueString(failedUpdates) + getValueString(
            DVW(100) * failedUpdates / getLongMetric("update.sum.count.count", ms),
            " (%'3.2f %%)");
    LongValue notFoundUpdates = getLongMetric("update.sum.not_found.count", ms);
    table[1][3] = getValueString(notFoundUpdates) + getValueString(
            DVW(100) * notFoundUpdates / getLongMetric("update.sum.count.count", ms),
            " (%'3.2f %%)");
    table[1][4] = getValueString(
            getDoubleMetric("update.sum.latency.min", ms), "%'3.2f ms");
    table[1][5] = getValueString(
            getDoubleMetric("update.sum.latency.average", ms), "%'3.2f ms");
    table[1][6] = getValueString(
            getDoubleMetric("update.sum.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Remove";
    table[2][0] = getValueString(
            DVW(1.0) * getLongMetric("remove.sum.count.count", ms) / LVW(timePeriod),
            "%'3.2f");
    table[2][1] = getValueString(getLongMetric("remove.sum.count.count", ms));
    LongValue failedRemoves = getLongMetric("remove.sum.count.count", ms)
                            - getLongMetric("remove.sum.latency.count", ms);
    table[2][2] = getValueString(failedRemoves) + getValueString(
            DVW(100) * failedRemoves / getLongMetric("remove.sum.count.count", ms),
            " (%'3.2f %%)");
    LongValue notFoundRemoves = getLongMetric("remove.sum.not_found.count", ms);
    table[2][3] = getValueString(notFoundRemoves) + getValueString(
            DVW(100) * notFoundRemoves / getLongMetric("remove.sum.count.count", ms),
            " (%'3.2f %%)");
    table[2][4] = getValueString(
            getDoubleMetric("remove.sum.latency.min", ms), "%'3.2f ms");
    table[2][5] = getValueString(
            getDoubleMetric("remove.sum.latency.average", ms), "%'3.2f ms");
    table[2][6] = getValueString(
            getDoubleMetric("remove.sum.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Get";
    table[3][0] = getValueString(
            DVW(1.0) * getLongMetric("get.sum.count.count", ms) / LVW(timePeriod),
            "%'3.2f");
    table[3][1] = getValueString(getLongMetric("get.sum.count.count", ms));
    LongValue failedGets = getLongMetric("get.sum.count.count", ms)
                         - getLongMetric("get.sum.latency.count", ms);
    table[3][2] = getValueString(failedGets) + getValueString(
            DVW(100) * failedGets / getLongMetric("get.sum.count.count", ms),
            " (%'3.2f %%)");
    LongValue notFoundGets = getLongMetric("get.sum.not_found.count", ms);
    table[3][3] = getValueString(notFoundGets) + getValueString(
            DVW(100) * notFoundGets / getLongMetric("get.sum.count.count", ms),
            " (%'3.2f %%)");
    table[3][4] = getValueString(
            getDoubleMetric("get.sum.latency.min", ms), "%'3.2f ms");
    table[3][5] = getValueString(
            getDoubleMetric("get.sum.latency.average", ms), "%'3.2f ms");
    table[3][6] = getValueString(
            getDoubleMetric("get.sum.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Visit";
    std::string visPrefix = "../../../visitor.allthreads.";
    LongValue completedVis = getLongMetric(visPrefix + "completed.sum.count", ms);
    failed = getLongMetric(visPrefix + "failed.sum.count", ms);
    LongValue totalVis = failed + completedVis;
    // Not adding aborted to this count for now
    //                   + getLongMetric(visPrefix + "aborted.count", ms)
    table[4][0] = getValueString(DVW(1.0) * completedVis / timePeriod, "%'3.2f");
    table[4][1] = getValueString(completedVis);
    table[4][2] = getValueString(failed)
            + getValueString(DVW(100) * failed / totalVis, " (%'3.2f %%)");
    table[4][4] = getValueString(
            getDoubleMetric(visPrefix + "averagevisitorlifetime.sum.min", ms),
            "%'3.2f ms");
    table[4][5] = getValueString(
            getDoubleMetric(visPrefix + "averagevisitorlifetime.sum.average", ms),
            "%'3.2f ms");
    table[4][6] = getValueString(
            getDoubleMetric(visPrefix + "averagevisitorlifetime.sum.max", ms),
            "%'3.2f ms");

    /*
    table.rowNames += "Stat";
    table[3][0] = getValueString(
            getQuotient(getLongMetric("stats.count", ms),
                        LVW(timePeriod)), "%'3.2f");
    table[3][1] = getValueString(getLongMetric("stats.count", ms));
    LongValue failedStats = getDiff(
            getLongMetric("stats.count", ms),
            getLongMetric("statlatencytotal.count", ms));
    table[3][2] = getValueString(failedStats) + getValueString(
            getProduct(LVW(100), getQuotient(failedStats,
                                        getLongMetric("stats.count", ms))),
            " (%'3.2f %%)");
    LongValue notFoundStats = getLongMetric("statsnotfound.count", ms);
    table[3][3] = getValueString(notFoundStats) + getValueString(
            getProduct(LVW(100), getQuotient(notFoundStat,
                                        getLongMetric("stats.count", ms))),
            " (%'3.2f %%)");
    table[3][4] = getValueString(
            getDoubleMetric("statlatencytotal.average", ms), "%'3.2f ms");
    */

    table.rowNames += "Revert";
    table[5][0] = getValueString(
            DVW(1.0) * getLongMetric("revert.sum.count.count", ms) / LVW(timePeriod),
            "%'3.2f");
    table[5][1] = getValueString(getLongMetric("revert.sum.count.count", ms));
    LongValue failedReverts = getLongMetric("revert.sum.count.count", ms)
                            - getLongMetric("revert.sum.latency.count", ms);
    table[5][2] = getValueString(failedReverts) + getValueString(
            DVW(100) * failedReverts / getLongMetric("revert.sum.count.count", ms),
            " (%'3.2f %%)");
    LongValue notFoundReverts = getLongMetric("revert.sum.not_found.count", ms);
    table[5][3] = getValueString(notFoundReverts) + getValueString(
            DVW(100) * notFoundReverts / getLongMetric("revert.sum.count.count", ms),
            " (%'3.2f %%)");
    table[5][4] = getValueString(
            getDoubleMetric("revert.sum.latency.min", ms), "%'3.2f ms");
    table[5][5] = getValueString(
            getDoubleMetric("revert.sum.latency.average", ms), "%'3.2f ms");
    table[5][6] = getValueString(
            getDoubleMetric("revert.sum.latency.max", ms), "%'3.2f ms");

    table.print(out);
}

void
StatusMetricConsumer::printMaintOpHtmlReport(
        std::ostream& out,
        std::map<metrics::Metric::String, metrics::Metric::SP>& usedMetrics,
        const metrics::MetricSnapshot& snapshot) const
{
    using namespace boost::assign;
    using namespace metrics::printutils;

    MetricSource ms(snapshot, "vds.filestor.alldisks.allthreads", &usedMetrics);
    HttpTable table("Maintenance load", "Operation");
    table.colNames += "Ops/s", "Ops", "Failed", "Not found",
                      "Min latency", "Avg latency", "Max latency", "Notes";
    LongValue failed;

    LVW timePeriod(snapshot.getLength());

    table.rowNames += "Merge bucket";
    table[0][0] = getValueString(
            DVW(1.0) * getLongMetric("mergebuckets.count.count", ms)
            / timePeriod, "%'3.2f");
    table[0][1] = getValueString(getLongMetric("mergebuckets.count.count", ms));
    failed = getLongMetric("mergebuckets.count.count", ms);
    table[0][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("mergebuckets.count.count", ms),
            " (%'3.2f %%)");
    table[0][4] = getValueString(
            getDoubleMetric("mergelatencytotal.min", ms), "%'3.2f ms");
    table[0][5] = getValueString(
            getDoubleMetric("mergelatencytotal.average", ms), "%'3.2f ms");
    table[0][6] = getValueString(
            getDoubleMetric("mergelatencytotal.max", ms), "%'3.2f ms");

    table.rowNames += "Split bucket";
    table[1][0] = getValueString(
            DVW(1.0) * getLongMetric("splitbuckets.count.count", ms)
            / timePeriod, "%'3.2f");
    table[1][1] = getValueString(getLongMetric("splitbuckets.count.count", ms));
    failed = getLongMetric("splitbuckets.failed.count", ms);
    table[1][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("splitbuckets.count.count", ms),
            " (%'3.2f %%)");
    table[1][4] = getValueString(
            getDoubleMetric("splitbuckets.latency.min", ms), "%'3.2f ms");
    table[1][5] = getValueString(
            getDoubleMetric("splitbuckets.latency.average", ms), "%'3.2f ms");
    table[1][6] = getValueString(
            getDoubleMetric("splitbuckets.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Delete bucket";
    table[2][0] = getValueString(
            DVW(1.0) * getLongMetric("deletebuckets.count.count", ms)
            / timePeriod, "%'3.2f");
    table[2][1] = getValueString(getLongMetric("deletebuckets.count.count", ms));
    failed = getLongMetric("deletebuckets.failed.count", ms);
    table[2][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("deletebuckets.count.count", ms),
            " (%'3.2f %%)");
    table[2][4] = getValueString(
            getDoubleMetric("deletebuckets.latency.min", ms), "%'3.2f ms");
    table[2][5] = getValueString(
            getDoubleMetric("deletebuckets.latency.average", ms), "%'3.2f ms");
    table[2][6] = getValueString(
            getDoubleMetric("deletebuckets.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Buckets verified";
    table[3][0] = getValueString(
            DVW(1.0) * getLongMetric("bucketverified.count.count", ms)
            / timePeriod, "%'3.2f");
    table[3][1] = getValueString(getLongMetric("bucketverified.count.count", ms));
    failed = getLongMetric("bucketverified.failed.count", ms);
    table[3][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("bucketverified.count.count", ms),
            " (%'3.2f %%)");
    table[3][4] = getValueString(
            getDoubleMetric("bucketverified.latency.min", ms), "%'3.2f ms");
    table[3][5] = getValueString(
            getDoubleMetric("bucketverified.latency.average", ms), "%'3.2f ms");
    table[3][6] = getValueString(
            getDoubleMetric("bucketverified.latency.max", ms), "%'3.2f ms");
    table[3][7] = "Buckets repaired: "
                + getValueString(getLongMetric("bucketfixed.count", ms))
                + getValueString(DVW(100)
                        * getLongMetric("bucketfixed.count", ms)
                        / getLongMetric("bucketverified.count.count", ms),
                                 " (%'3.2f %%)");

    table.rowNames += "List buckets";
    table[4][0] = getValueString(
            DVW(1.0) * getLongMetric("readbucketlist.count.count", ms)
            / timePeriod, "%'3.2f");
    table[4][1] = getValueString(
            getLongMetric("readbucketlist.count.count", ms));
    failed = getLongMetric("readbucketlist.failed.count", ms);
    table[4][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("readbucketlist.count.count", ms),
            " (%'3.2f %%)");
    table[4][4] = getValueString(
            getDoubleMetric("readbucketlist.latency.min", ms),
            "%'3.2f ms");
    table[4][5] = getValueString(
            getDoubleMetric("readbucketlist.latency.average", ms),
            "%'3.2f ms");
    table[4][6] = getValueString(
            getDoubleMetric("readbucketlist.latency.max", ms),
            "%'3.2f ms");

    table.rowNames += "Read bucket info";
    table[5][0] = getValueString(
            DVW(1.0) * getLongMetric("readbucketinfo.count.count", ms)
            / timePeriod, "%'3.2f");
    table[5][1] = getValueString(getLongMetric("readbucketinfo.count.count", ms));
    failed = getLongMetric("readbucketinfo.failed.count", ms);
    table[5][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("readbucketinfo.count.count", ms),
            " (%'3.2f %%)");
    table[5][4] = getValueString(
            getDoubleMetric("readbucketinfo.latency.min", ms),
            "%'3.2f ms");
    table[5][5] = getValueString(
            getDoubleMetric("readbucketinfo.latency.average", ms),
            "%'3.2f ms");
    table[5][6] = getValueString(
            getDoubleMetric("readbucketinfo.latency.max", ms),
            "%'3.2f ms");

    table.rowNames += "Bucket move";
    table[6][0] = getValueString(
            DVW(1.0) * getLongMetric("movedbuckets.count.count", ms)
            / timePeriod, "%'3.2f");
    table[6][1] = getValueString(getLongMetric("movedbuckets.count.count", ms));
    failed = getLongMetric("movedbuckets.failed.count", ms);
    table[6][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("movedbuckets.count.count", ms),
            " (%'3.2f %%)");
    table[6][4] = getValueString(
            getDoubleMetric("movedbuckets.latency.min", ms), "%'3.2f ms");
    table[6][5] = getValueString(
            getDoubleMetric("movedbuckets.latency.average", ms), "%'3.2f ms");
    table[6][6] = getValueString(
            getDoubleMetric("movedbuckets.latency.max", ms), "%'3.2f ms");

    table.rowNames += "Internal join";
    table[7][0] = getValueString(
            DVW(1.0) * getLongMetric("internaljoin.count.count", ms)
            / timePeriod, "%'3.2f");
    table[7][1] = getValueString(getLongMetric("internaljoin.count.count", ms));
    failed = getLongMetric("internaljoin.failed.count", ms);
    table[7][2] = getValueString(failed) + getValueString(
            DVW(100) * failed / getLongMetric("internaljoin.count.count", ms),
            " (%'3.2f %%)");
    table[7][4] = getValueString(
            getDoubleMetric("internaljoin.latency.min", ms),
            "%'3.2f ms");
    table[7][5] = getValueString(
            getDoubleMetric("internaljoin.latency.average", ms),
            "%'3.2f ms");
    table[7][6] = getValueString(
            getDoubleMetric("internaljoin.latency.max", ms),
            "%'3.2f ms");

    table.print(out);

}

void
StatusMetricConsumer::printMergeHtmlReport(
        std::ostream& out,
        std::map<metrics::Metric::String, metrics::Metric::SP>& usedMetrics,
        const metrics::MetricSnapshot& snapshot) const
{
    using namespace boost::assign;
    using namespace metrics::printutils;

    LVW timePeriod(snapshot.getLength());

    MetricSource ms(snapshot, "vds.filestor.alldisks.allthreads", &usedMetrics);
    HttpTable table("Merge frequency and partial latencies", "Type");
    table.colNames += "Ops/s", "Ops", "Failed",
                      "Min latency", "Avg latency", "Max latency";
    LongValue failed;

    table.rowNames += "Complete merge";
    table[0][0] = getValueString(
            DVW(1.0) * getLongMetric("mergebuckets.count.count", ms)
            / timePeriod, "%'3.2f");
    table[0][1] = getValueString(getLongMetric("mergebuckets.count.count", ms));
    failed = getLongMetric("mergebuckets.count.count", ms)
           - getLongMetric("mergelatencytotal.count", ms);
    table[0][2] = getValueString(failed) + getValueString(
            DVW(100.0) * failed / getLongMetric("mergebuckets.count.count", ms),
            " (%'3.2f %%)");
    table[0][3] = getValueString(
            getDoubleMetric("mergelatencytotal.min", ms), "%'3.2f ms");
    table[0][4] = getValueString(
            getDoubleMetric("mergelatencytotal.average", ms), "%'3.2f ms");
    table[0][5] = getValueString(
            getDoubleMetric("mergelatencytotal.max", ms), "%'3.2f ms");

    table.rowNames += "Metadata read";
    LongValue metadataCount = getLongMetric("getbucketdiff.count.count", ms)
                            + getLongMetric("mergebuckets.count.count", ms);
    table[1][0] = getValueString(DVW(1.0) * metadataCount
            / timePeriod, "%'3.2f");
    table[1][1] = getValueString(metadataCount);
    failed = metadataCount
           - getLongMetric("mergemetadatareadlatency.count", ms);
    table[1][2] = getValueString(failed) + getValueString(
            DVW(100.0) * failed / metadataCount, " (%'3.2f %%)");
    table[1][3] = getValueString(
            getDoubleMetric("mergemetadatareadlatency.min", ms),
            "%'3.2f ms");
    table[1][4] = getValueString(
            getDoubleMetric("mergemetadatareadlatency.average", ms),
            "%'3.2f ms");
    table[1][5] = getValueString(
            getDoubleMetric("mergemetadatareadlatency.max", ms),
            "%'3.2f ms");

    table.rowNames += "Successful data reads";
    table[2][0] = getValueString(
            DVW(1.0) * getLongMetric("mergedatareadlatency.count", ms)
            / timePeriod, "%'3.2f");
    table[2][1] = getValueString(
            getLongMetric("mergedatareadlatency.count", ms));
    table[2][3] = getValueString(
            getDoubleMetric("mergedatareadlatency.min", ms),
            "%'3.2f ms");
    table[2][4] = getValueString(
            getDoubleMetric("mergedatareadlatency.average", ms),
            "%'3.2f ms");
    table[2][5] = getValueString(
            getDoubleMetric("mergedatareadlatency.max", ms),
            "%'3.2f ms");

    table.rowNames += "Successful data writes";
    table[3][0] = getValueString(
            DVW(1.0) * getLongMetric("mergedatawritelatency.count", ms)
            / timePeriod, "%'3.2f");
    table[3][1] = getValueString(
            getLongMetric("mergedatawritelatency.count", ms));
    table[3][3] = getValueString(
            getDoubleMetric("mergedatawritelatency.min", ms),
            "%'3.2f ms");
    table[3][4] = getValueString(
            getDoubleMetric("mergedatawritelatency.average", ms),
            "%'3.2f ms");
    table[3][5] = getValueString(
            getDoubleMetric("mergedatawritelatency.max", ms),
            "%'3.2f ms");

    HttpTable table2("Other merge properties", "Property");
    table2.colNames += "Per merge value", "Total value";

    table2.rowNames += "Bytes merged";
    table2[0][0] = getByteValueString(
            DVW(1.0) * getLongMetric("bytesmerged.count", ms)
            / getLongMetric("mergebuckets.count.count", ms));
    table2[0][1] = getByteValueString(getLongMetric("bytesmerged.count", ms));

    table2.rowNames += "Network efficiency";
    table2[1][1] = getValueString(
            DVW(100)
            * getDoubleMetric("mergeavgdatareceivedneeded.average", ms),
            "%'3.2f %%");

    table2.rowNames += "Average merges pending";
    table2[2][1] = getValueString(
            getDoubleMetric("../pendingmerge.average", ms), "%'3.2f");

    table2.rowNames += "Data transfers";
    table2[3][0] = getValueString(
            DVW(1.0) * getLongMetric("applybucketdiff.count.count", ms)
            / getLongMetric("mergebuckets.count.count", ms), "%'3.2f");
    table2[3][1] = getValueString(
            getLongMetric("applybucketdiff.count.count", ms));

    table2.rowNames += "Merge master percentage";
    table2[4][1] = getValueString(
            DVW(100) * getLongMetric("mergebuckets.count.count", ms)
            / (getLongMetric("getbucketdiff.count.count", ms)
               + getLongMetric("mergebuckets.count.count", ms)), "%'3.2f %%");

    out << "<table cellpadding=\"0\" cellspacing=\"0\">\n"
        << "<td valign=\"top\" width=\"60%\">\n";
    table.print(out);
    out << "<p><font size=\"-1\"><div align=\"left\">\n"
        << "Complete merge lists amount of merges this node has been master of. Metadata read shows how many merges this node has taken part of in total. By looking at the data reads and writes one can see how many of these are done per merge if one compares it to the amount of metadata reads.\n"
        << "</div></font></p>\n";
    out << "</td><td width=\"1%\">&nbsp;&nbsp;</td>\n"
        << "<td valign=\"top\" width=\"39%\">\n";
    table2.print(out);
    out << "<p><font size=\"-1\"><div align=\"left\">\n"
        << "Bytes merged sets how many bytes have been merged into this node. The network efficiency states how big percent the byte merged was of the total data received during the merge. (A low efficiency indicates that data being merged between two nodes are routed through this node, which doesn't need it). Pending merges are the number of merges this node is master for that is currently being processed. Lastly, a percentage is shown, giving how many of total merges gone through this node the node has actually been master of.\n"
        << "</div></font></p>\n";
    out << "</td></table>\n";
}

void
StatusMetricConsumer::printVisitHtmlReport(
        std::ostream& out,
        std::map<metrics::Metric::String, metrics::Metric::SP>& usedMetrics,
        const metrics::MetricSnapshot& snapshot) const
{
    using namespace boost::assign;
    using namespace metrics::printutils;

    LVW timePeriod(snapshot.getLength());

    MetricSource ms(snapshot, "vds.visitor.allthreads", &usedMetrics);
    HttpTable table("Visiting", "Type");
    table.colNames += "Value", "Percentage";
    std::string persPrefix = "../../filestor.alldisks.allthreads.";
    std::string diskPrefix = "../../filestor.alldisks.";
    LongValue completedVisitors = getLongMetric("completed.sum.count", ms);
    LongValue failedVisitors = getLongMetric("failed.sum.count", ms);
    LongValue abortedVisitors = getLongMetric("aborted.sum.count", ms);
    LongValue totalVisitors = completedVisitors + failedVisitors
                            + abortedVisitors;
    DoubleValue bucketsPerVisitor(
            DVW(1) * getLongMetric(persPrefix + "visit.sum.count.count", ms)
            / completedVisitors);
    LongValue totalTime = getLongMetric("averagevisitorlifetime.sum.average", ms);

    table.rowNames += "Pending visitors";
    table[0][0] = getValueString(
            getLongMetric("created.sum.count", ms) - completedVisitors);

    table.rowNames += "Completed visitors";
    table[1][0] = getValueString(completedVisitors);
    table[1][1] = getValueString(
            DVW(100.0) * completedVisitors / totalVisitors, "%'3.2f %%");

    table.rowNames += "Failed visitors";
    table[2][0] = getValueString(failedVisitors);
    table[2][1] = getValueString(
            DVW(100.0) * failedVisitors / totalVisitors, "%'3.2f %%");

    table.rowNames += "Aborted visitors";
    table[3][0] = getValueString(abortedVisitors);
    table[3][1] = getValueString(
            DVW(100.0) * abortedVisitors / totalVisitors, "%'3.2f %%");

    table.rowNames += "Buckets visited per visitor";
    table[4][0] = getValueString(bucketsPerVisitor, "%'3.2f");

    table.rowNames += "Average size of visitor manager queue (min/average/max)";
    table[5][0] = getValueString(getLongMetric("queuesize.min", ms),
                                 "%'llu")
            + getValueString(getLongMetric("queuesize.average", ms),
                                 " / %'llu")
            + getValueString(getLongMetric("queuesize.max", ms),
                                 " / %'llu");

    table.rowNames += "Total latency of completed visitors (min/average/max)";
    table[6][0] = getValueString(getLongMetric(
                + "averagevisitorlifetime.sum.min", ms), "%'llu ms")
            + getValueString(getLongMetric(
                        "averagevisitorlifetime.sum.average", ms), " / %'llu ms")
            + getValueString(getLongMetric(
                        "averagevisitorlifetime.sum.max", ms), " / %'llu ms");
    table[6][1] = getValueString(DVW(100), "%'3.2f %%");


    table.rowNames += "Time spent in visitor manager queue (min/average/max)";
    LongValue queueWait = getLongMetric("averagequeuewait.sum.average", ms);
    table[7][0] = getValueString(getLongMetric("averagequeuewait.sum.min", ms))
            + getValueString(queueWait, " ms / %'llu ms / ")
            + getValueString(
                    getLongMetric("averagequeuewait.sum.max", ms), "%'llu ms");
    table[7][1] = getValueString(DVW(100) * queueWait / totalTime, "%'3.2f %%");

    table.rowNames += "Time spent processing in visitor thread";
    LongValue cpuTime = getLongMetric("averageprocessingtime.sum.average", ms);
    table[8][0] = getValueString(cpuTime, "%" PRId64 " ms");
    table[8][1] = getValueString(DVW(100) * cpuTime / totalTime, "%'3.2f %%");

    table.print(out);

    out << "<p><font size=\"-1\"><div align=\"left\">\n"
        << "Note that the buckets and blocks per visitor values assume that there are no aborted visitors. If there is a considerable amount of aborted visitors that has done persistence requests, these calculated numbers will be too large. Average time spent per visitor waiting in peristence layer, reading metadata or data, and the time used processing in visitor threads are also calculated values. If visiting consistently happens while cluster does not have average load, these values may also be a bit off.\n"
        << "</div></font></p>\n";
}

} // storage
