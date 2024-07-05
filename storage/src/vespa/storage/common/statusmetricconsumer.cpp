// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusmetricconsumer.h"
#include <vespa/storageframework/generic/clock/clock.h>
#include <boost/lexical_cast.hpp>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/textwriter.h>
#include <vespa/metrics/prometheus_writer.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".status.metricreporter");

namespace storage {

StatusMetricConsumer::StatusMetricConsumer(StorageComponentRegister& compReg, metrics::MetricManager& manager, const std::string& name)
    : framework::StatusReporter("metrics", "Performance metrics"),
      _manager(manager),
      _component(compReg, "statusmetricsconsumer"),
      _name(name),
      _lock()
{
    LOG(debug, "Started metrics consumer");
    setlocale(LC_NUMERIC, "");
    _component.registerStatusPage(*this);
}

StatusMetricConsumer::~StatusMetricConsumer() = default;

vespalib::string
StatusMetricConsumer::getReportContentType(const framework::HttpUrlPath& path) const
{
    if (!path.hasAttribute("format")) {
        return "text/plain";
    }

    if (path.getAttribute("format") == "text") {
        return "text/plain";
    }

    if (path.getAttribute("format") == "prometheus") {
        return "text/plain; version=0.0.4";
    }

    if (path.getAttribute("format") == "json") {
        return "application/json";
    }

    return "text/plain";
}

bool
StatusMetricConsumer::reportStatus(std::ostream& out,
                                   const framework::HttpUrlPath& path) const
{
    _manager.updateMetrics();

    vespalib::system_time currentTime = _component.getClock().getSystemTime();
    const bool json = (path.getAttribute("format") == "json");
    const bool prometheus = (path.getAttribute("format") == "prometheus");

    int verbosity(path.get("verbosity", 0));
        // We have to copy unset values if using HTML as HTML version gathers
        // metrics for calculations and thus needs unset values.
    bool copyUnset = (verbosity >= 2);
    bool temporarySnap = (path.get("tmpsnapshot", 0) == 1);

    if (path.hasAttribute("task") && path.getAttribute("task") == "reset") {
        std::lock_guard guard(_lock);
        _manager.reset(currentTime);
    }

    if (path.hasAttribute("interval")) {
        // Grab the snapshot we want to view more of
        int32_t intervalS(boost::lexical_cast<int32_t>(path.getAttribute("interval")));
        metrics::MetricLockGuard metricLock(_manager.getMetricLock());
        std::unique_ptr<metrics::MetricSnapshot> generated;
        const metrics::MetricSnapshot* snapshot;
        if (intervalS == -2) {
            snapshot = &_manager.getActiveMetrics(metricLock);
            _manager.getActiveMetrics(metricLock).setToTime(currentTime);
        } else if (intervalS == -1) {
            // "Prime" the metric structure by first fetching the set of active
            // metrics (complete with structure) and resetting these. This
            // leaves us with an empty metrics set to which we can (in order)
            // add the total and the active metrics. If this is not done, non-
            // written metrics won't be included even if copyUnset is true.
            generated = std::make_unique<metrics::MetricSnapshot>(
                    "Total metrics from start until current time", 0s,
                    _manager.getActiveMetrics(metricLock).getMetrics(),
                    copyUnset);
            generated->reset();
            _manager.getTotalMetricSnapshot(metricLock).addToSnapshot(*generated, currentTime);
            _manager.getActiveMetrics(metricLock).addToSnapshot(*generated, currentTime);
            generated->setFromTime(_manager.getTotalMetricSnapshot(metricLock).getFromTime());
            snapshot = generated.get();
        } else if (intervalS == 0) {
            if (copyUnset) {
                generated = std::make_unique<metrics::MetricSnapshot>(
                        _manager.getTotalMetricSnapshot(metricLock).getName(), 0s,
                        _manager.getActiveMetrics(metricLock).getMetrics(), true);
                generated->reset();
                _manager.getTotalMetricSnapshot(metricLock).addToSnapshot(*generated, currentTime);
                snapshot = generated.get();
            } else {
                snapshot = &_manager.getTotalMetricSnapshot(metricLock);
            }
        } else {
            vespalib::duration interval = vespalib::from_s(intervalS);
            if (copyUnset) {
                generated = std::make_unique<metrics::MetricSnapshot>(
                        _manager.getMetricSnapshot(metricLock, interval).getName(), 0s,
                        _manager.getActiveMetrics(metricLock).getMetrics(), true);
                generated->reset();
                _manager.getMetricSnapshot(metricLock, interval, temporarySnap)
                        .addToSnapshot(*generated, currentTime);
                snapshot = generated.get();
            } else {
                snapshot = &_manager.getMetricSnapshot(metricLock, interval, temporarySnap);
            }
        }

        std::string consumer = path.getAttribute("consumer", "");
        if (json) {
            vespalib::asciistream jsonStreamData;
            vespalib::JsonStream stream(jsonStreamData, true);
            stream << Object() << "metrics";
            metrics::JsonWriter metricJsonWriter(stream);
            _manager.visit(metricLock, *snapshot, metricJsonWriter, consumer);
            stream << End();
            stream.finalize();
            out << jsonStreamData.str();
        } else if (prometheus) {
            vespalib::asciistream ps;
            metrics::PrometheusWriter pw(ps);
            _manager.visit(metricLock, *snapshot, pw, consumer);
            out << ps.str();
        } else {
            std::string pattern = path.getAttribute("pattern", ".*");
            metrics::TextWriter textWriter(out, snapshot->getPeriod(), pattern, verbosity > 0);
            _manager.visit(metricLock, *snapshot, textWriter, consumer);
        }
    }

    return true;
}

} // storage
