// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statereporter.h"
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".status.statereporter");

namespace storage {

StateReporter::StateReporter(
        StorageComponentRegister& compReg,
        metrics::MetricManager& manager,
        ApplicationGenerationFetcher& generationFetcher,
        const std::string& name)
    : framework::StatusReporter("state", "State reporter"),
      _manager(manager),
      _metricsAdapter(manager),
      _stateApi(*this, *this, *this),
      _component(compReg, "statereporter"),
      _generationFetcher(generationFetcher),
      _name(name)
{
    LOG(debug, "Started state reporter");
    _component.registerStatusPage(*this);
}

StateReporter::~StateReporter() = default;

vespalib::string
StateReporter::getReportContentType(
        const framework::HttpUrlPath& /*path*/) const
{
    return "application/json";
}

namespace {

std::map<vespalib::string, vespalib::string>
getParams(const framework::HttpUrlPath &path)
{
    std::map<vespalib::string, vespalib::string> params = path.getAttributes();
    if (params.find("consumer") == params.end()) {
        params.insert(std::make_pair("consumer", "statereporter"));
    }
    return params;
}

}

bool
StateReporter::reportStatus(std::ostream& out,
                            const framework::HttpUrlPath& path) const
{
    // When we get here, capabilities have already been checked at a higher level, so
    // this will never fail unless a state API handler requires other capabilities than
    // we require for this reporter (in which case this will silently fail, but not
    // expose any other information).
    vespalib::net::ConnectionAuthContext dummy_ctx(vespalib::net::tls::PeerCredentials(), required_capabilities());
    auto status = _stateApi.get(path.getServerSpec(), path.getPath(), getParams(path), dummy_ctx);
    if (status.failed()) {
        LOG(debug, "State API reporting for path '%s' failed with status HTTP %d: %s",
            path.getPath().c_str(), status.status_code(), vespalib::string(status.status_message()).c_str());
        return false;
    }
    out << status.payload();
    return true;
}

vespalib::string
StateReporter::getMetrics(const vespalib::string &consumer)
{
    metrics::MetricLockGuard guard(_manager.getMetricLock());
    auto periods = _manager.getSnapshotPeriods(guard);
    if (periods.empty()) {
        return ""; // no configuration yet
    }
    auto interval = periods[0];

    // To get unset metrics, we have to copy active metrics, clear them
    // and then assign the snapshot
    metrics::MetricSnapshot snapshot(
            _manager.getMetricSnapshot(guard, interval).getName(), interval,
            _manager.getActiveMetrics(guard).getMetrics(), true);

    snapshot.reset();
    _manager.getMetricSnapshot(guard, interval).addToSnapshot(snapshot, _component.getClock().getSystemTime());

    vespalib::asciistream json;
    vespalib::JsonStream stream(json);
    metrics::JsonWriter metricJsonWriter(stream);
    _manager.visit(guard, snapshot, metricJsonWriter, consumer);
    stream.finalize();
    return json.str();
}

vespalib::string
StateReporter::getTotalMetrics(const vespalib::string &consumer)
{
    return _metricsAdapter.getTotalMetrics(consumer);
}

vespalib::HealthProducer::Health
StateReporter::getHealth() const
{
    lib::NodeState cns(*_component.getStateUpdater().getCurrentNodeState());
    bool up = cns.getState().oneOf("u");
    std::string message = up ? "" : "Node state: " + cns.toString(true);
    return { up, message };
}

void
StateReporter::getComponentConfig(Consumer &consumer)
{
    consumer.add(ComponentConfigProducer::Config(_generationFetcher.getComponentName(),
            _generationFetcher.getGeneration()));
}

} // storage
