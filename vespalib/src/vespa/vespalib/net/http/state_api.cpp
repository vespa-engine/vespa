// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_api.h"
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/net/tls/capability.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <functional>

using vespalib::net::tls::Capability;
using vespalib::net::tls::CapabilitySet;

namespace vespalib {

namespace {

struct ConfigRenderer : ComponentConfigProducer::Consumer {
    JSONStringer &json;
    ConfigRenderer(JSONStringer &j) : json(j) {}
    void add(const ComponentConfigProducer::Config &config) override {
        json.appendKey(config.name);
        json.beginObject();
        json.appendKey("generation");
        json.appendInt64(config.gen);
        if (!config.msg.empty()) {
            json.appendKey("message");
            json.appendString(config.msg);
        }
        json.endObject();
    }
};

struct ConfigGenerationObserver : ComponentConfigProducer::Consumer {
    size_t maxGen;
    bool seenSome;
    ConfigGenerationObserver() : maxGen(0), seenSome(false) {}
    void add(const ComponentConfigProducer::Config &config) override {
        if (seenSome) {
            maxGen = std::max(maxGen, config.gen);
        } else {
            maxGen = config.gen;
            seenSome = true;
        }
    }
};

void build_health_status(JSONStringer &json, const HealthProducer &healthProducer) {
    HealthProducer::Health health = healthProducer.getHealth();
    json.appendKey("status");
    json.beginObject();
    json.appendKey("code");
    if (health.ok) {
        json.appendString("up");
    } else {
        json.appendString("down");
        json.appendKey("message");
        json.appendString(health.msg);
    }
    json.endObject();
}

vespalib::string get_param(const std::map<vespalib::string,vespalib::string> &params,
                           std::string_view param_name,
                           std::string_view default_value)
{
    auto maybe_value = params.find(param_name);
    if (maybe_value == params.end()) {
        return default_value;
    }
    return maybe_value->second;
}

void render_link(JSONStringer &json, const vespalib::string &host, const vespalib::string &path) {
    json.beginObject();
    json.appendKey("url");
    json.appendString("http://" + host + path);
    json.endObject();
}

vespalib::string respond_root(const JsonHandlerRepo &repo, const vespalib::string &host) {
    JSONStringer json;
    json.beginObject();
    json.appendKey("resources");
    json.beginArray();
    for (auto path: {"/state/v1/health", "/state/v1/metrics", "/state/v1/config"}) {
        render_link(json, host, path);
    }
    for (const vespalib::string &path: repo.get_root_resources()) {
        render_link(json, host, path);
    }
    json.endArray();
    json.endObject();
    return json.toString();
}

vespalib::string respond_health(const HealthProducer &healthProducer) {
    JSONStringer json;
    json.beginObject();
    build_health_status(json, healthProducer);
    json.endObject();
    return json.toString();
}

vespalib::string respond_json_metrics(const vespalib::string &consumer,
                                      const HealthProducer &healthProducer,
                                      MetricsProducer &metricsProducer)
{
    JSONStringer json;
    json.beginObject();
    build_health_status(json, healthProducer);
    { // metrics
        vespalib::string metrics = metricsProducer.getMetrics(consumer, MetricsProducer::ExpositionFormat::JSON);
        if (!metrics.empty()) {
            json.appendKey("metrics");
            json.appendJSON(metrics);
        }
    }
    json.endObject();
    return json.toString();
}

JsonGetHandler::Response cap_check_and_respond_metrics(
        const net::ConnectionAuthContext &auth_ctx,
        const std::map<vespalib::string,vespalib::string> &params,
        const vespalib::string& default_consumer,
        std::function<JsonGetHandler::Response(const vespalib::string&, MetricsProducer::ExpositionFormat)> response_fn)
{
    if (!auth_ctx.capabilities().contains(Capability::content_metrics_api())) {
        return JsonGetHandler::Response::make_failure(403, "Forbidden");
    }
    auto consumer   = get_param(params, "consumer", default_consumer);
    auto format_str = get_param(params, "format", "json");
    auto format     = (format_str == "prometheus" ? MetricsProducer::ExpositionFormat::Prometheus
                                                  : MetricsProducer::ExpositionFormat::JSON);
    return response_fn(consumer, format);
}

vespalib::string respond_config(ComponentConfigProducer &componentConfigProducer) {
    JSONStringer json;
    json.beginObject();
    { // config
        ConfigRenderer renderer(json);
        json.appendKey("config");
        json.beginObject();
        ConfigGenerationObserver observer;
        componentConfigProducer.getComponentConfig(observer);
        if (observer.seenSome) {
            json.appendKey("generation");
            json.appendInt64(observer.maxGen);
        }
        componentConfigProducer.getComponentConfig(renderer);
        json.endObject();
    }
    json.endObject();
    return json.toString();
}

JsonGetHandler::Response cap_checked(const net::ConnectionAuthContext &auth_ctx,
                                     CapabilitySet required_caps,
                                     std::function<vespalib::string()> fn)
{
    if (!auth_ctx.capabilities().contains_all(required_caps)) {
        return JsonGetHandler::Response::make_failure(403, "Forbidden");
    }
    return JsonGetHandler::Response::make_ok_with_json(fn());
}

JsonGetHandler::Response cap_checked(const net::ConnectionAuthContext &auth_ctx,
                                     Capability required_cap,
                                     std::function<vespalib::string()> fn)
{
    return cap_checked(auth_ctx, CapabilitySet::of({required_cap}), std::move(fn));
}

constexpr const char* prometheus_content_type() noexcept {
    return "text/plain; version=0.0.4";
}

} // namespace vespalib::<unnamed>

JsonGetHandler::Response
StateApi::get(const vespalib::string &host,
              const vespalib::string &path,
              const std::map<vespalib::string,vespalib::string> &params,
              const net::ConnectionAuthContext &auth_ctx) const
{
    if (path == "/state/v1/" || path == "/state/v1") {
        return cap_checked(auth_ctx, CapabilitySet::make_empty(), [&] { // TODO consider http_unclassified
            return respond_root(_handler_repo, host);
        });
    } else if (path == "/state/v1/health") {
        return cap_checked(auth_ctx, CapabilitySet::make_empty(), [&] { // TODO consider http_unclassified
            return respond_health(_healthProducer);
        });
    } else if (path == "/state/v1/metrics") {
        // Using a 'statereporter' consumer by default removes many uninteresting per-thread
        // metrics but retains their aggregates (side note: per-thread metrics are NOT included
        // in Prometheus metrics regardless of the specified consumer).
        return cap_check_and_respond_metrics(auth_ctx, params, "statereporter", [&](auto& consumer, auto format) {
            if (format == MetricsProducer::ExpositionFormat::Prometheus) {
                auto metrics_text = _metricsProducer.getMetrics(consumer, MetricsProducer::ExpositionFormat::Prometheus);
                return JsonGetHandler::Response::make_ok_with_content_type(std::move(metrics_text), prometheus_content_type());
            } else {
                auto json = respond_json_metrics(consumer, _healthProducer, _metricsProducer);
                return JsonGetHandler::Response::make_ok_with_json(std::move(json));
            }
        });
    } else if (path == "/state/v1/config") {
        return cap_checked(auth_ctx, Capability::content_state_api(), [&] {
            return respond_config(_componentConfigProducer);
        });
    } else if (path == "/metrics/total") {
        return cap_check_and_respond_metrics(auth_ctx, params, "", [&](auto& consumer, auto format) {
            if (format == MetricsProducer::ExpositionFormat::Prometheus) {
                auto metrics_text = _metricsProducer.getTotalMetrics(consumer, MetricsProducer::ExpositionFormat::Prometheus);
                return JsonGetHandler::Response::make_ok_with_content_type(std::move(metrics_text), prometheus_content_type());
            } else {
                auto json = _metricsProducer.getTotalMetrics(consumer, vespalib::MetricsProducer::ExpositionFormat::JSON);
                return JsonGetHandler::Response::make_ok_with_json(std::move(json));
            }
        });
    } else {
        // Assume this is for the nested state v1 stuff; may delegate capability check to handler later if desired.
        if (!auth_ctx.capabilities().contains(Capability::content_state_api())) {
            return Response::make_failure(403, "Forbidden");
        }
        return _handler_repo.get(host, path, params, auth_ctx);
    }
}

//-----------------------------------------------------------------------------

StateApi::StateApi(const HealthProducer &hp,
                   MetricsProducer &mp,
                   ComponentConfigProducer &ccp)
    : _healthProducer(hp),
      _metricsProducer(mp),
      _componentConfigProducer(ccp)
{
}

StateApi::~StateApi() = default;

} // namespace vespalib
