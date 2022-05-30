// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_api.h"
#include <vespa/vespalib/util/jsonwriter.h>

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

vespalib::string get_consumer(const std::map<vespalib::string,vespalib::string> &params,
                              vespalib::stringref default_consumer)
{
    auto consumer_lookup = params.find("consumer");
    if (consumer_lookup == params.end()) {
        return default_consumer;
    }
    return consumer_lookup->second;
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

vespalib::string respond_metrics(const vespalib::string &consumer,
                                 const HealthProducer &healthProducer,
                                 MetricsProducer &metricsProducer)
{
    JSONStringer json;
    json.beginObject();
    build_health_status(json, healthProducer);
    { // metrics
        vespalib::string metrics = metricsProducer.getMetrics(consumer);
        if (!metrics.empty()) {
            json.appendKey("metrics");
            json.appendJSON(metrics);
        }
    }
    json.endObject();
    return json.toString();
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

} // namespace vespalib::<unnamed>

vespalib::string
StateApi::get(const vespalib::string &host,
              const vespalib::string &path,
              const std::map<vespalib::string,vespalib::string> &params) const
{
    if (path == "/state/v1/" || path == "/state/v1") {
        return respond_root(_handler_repo, host);
    } else if (path == "/state/v1/health") {
        return respond_health(_healthProducer);
    } else if (path == "/state/v1/metrics") {
        // Using a 'statereporter' consumer by default removes many uninteresting per-thread
        // metrics but retains their aggregates.
        return respond_metrics(get_consumer(params, "statereporter"), _healthProducer, _metricsProducer);
    } else if (path == "/state/v1/config") {
        return respond_config(_componentConfigProducer);
    } else if (path == "/metrics/total") {
        return _metricsProducer.getTotalMetrics(get_consumer(params, ""));
    } else {
        return _handler_repo.get(host, path, params);
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
