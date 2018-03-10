// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configstatus.h"
#include "tags.h"
#include <vespa/fnet/frt/frt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vbench/http/http_result_handler.h>
#include <vbench/http/server_spec.h>
#include <vbench/http/http_client.h>
#include <vespa/config/common/exceptions.h>
#include <iostream>

using configdefinitions::tagsContain;

struct ComponentTraverser : public vespalib::slime::ObjectTraverser
{
    const std::string _configId;
    std::string _component;
    enum {
        ROOT,
        COMPONENT
    } _state;
    std::map<std::string, int64_t> &_generations;

    ComponentTraverser(std::string configId,
                       std::map<std::string, int64_t> &generations)
        : _configId(configId), _state(ROOT), _generations(generations)
    {}

    ~ComponentTraverser();

    void object(const vespalib::slime::Inspector &inspector) {
        inspector.traverse(*this);
    }

    static void collect(const std::string configId, const vespalib::Slime &slime,
                        std::map<std::string, int64_t> &generations) {
        ComponentTraverser traverser(configId, generations);
        slime.get()["config"].traverse(traverser);
    }

    void field(const vespalib::Memory &symbol_name, const vespalib::slime::Inspector &inspector) override {
        switch (_state) {
        case ROOT:
            _component = symbol_name.make_string();
            _state = COMPONENT;
            inspector.traverse(*this);
            _state = ROOT;
            break;
        case COMPONENT:
            const std::string key = symbol_name.make_string();
            int64_t value;
            if (key == "generation") {
                if (inspector.type().getId() == vespalib::slime::DOUBLE::ID) {
                    value = (int64_t) inspector.asDouble();
                    _generations[_component] = value;
                } else if (inspector.type().getId() == vespalib::slime::LONG::ID) {
                    value = inspector.asLong();
                    _generations[_component] = value;
                } else {
                    value = 0;
                    std::cerr << _configId << ":" << _component <<
                        "Generation has wrong type" << std::endl;
                }
            }

            break;
        }
    }
};

ComponentTraverser::~ComponentTraverser() {}

class MyHttpHandler : public vbench::HttpResultHandler {
private:
    std::string _json;
    std::string _error;
    std::string _configId;

public:

    MyHttpHandler(std::string configId)
        : _json(), _error(), _configId(configId)
    {}
    ~MyHttpHandler();

    void handleHeader(const vbench::string &name, const vbench::string &value) override {
        (void) name;
        (void) value;
    }

    void handleContent(const vbench::Memory &data) override {
        _json += std::string(data.data, data.size);
    }

    void handleFailure(const vbench::string &reason) override {
        std::cerr << _configId << ": Failed to fetch json: " << reason << std::endl;
        _error = reason;
    }

    bool failed() {
        return(_error.size() > 0);
    }

    std::string getJson() {
        return _json;
    }
};

MyHttpHandler::~MyHttpHandler() {}

ConfigStatus::ConfigStatus(Flags flags, const config::ConfigUri uri)
    : _cfg(), _flags(flags), _generation(0)
{
    if (_flags.verbose) {
        std::cerr << "Subscribing to model config with config id " <<
            uri.getConfigId() << std::endl;
    }
    try {
        config::ConfigSubscriber subscriber(uri.getContext());
        config::ConfigHandle<cloud::config::ModelConfig>::UP handle =
            subscriber.subscribe<cloud::config::ModelConfig>(uri.getConfigId());
        subscriber.nextConfig(0);
        _cfg = handle->getConfig();
        _generation = subscriber.getGeneration();
    } catch(config::ConfigRuntimeException &e) {
        std::cerr << e.getMessage() << std::endl;
    }

    if (_cfg.get() == NULL) {
        std::cerr << "FATAL ERROR: failed to get model configuration." << std::endl;
        exit(1);
    }
}

ConfigStatus::~ConfigStatus() {}

int
ConfigStatus::action()
{
    bool allUpToDate = true;

    for (size_t i = 0; i < _cfg->hosts.size(); i++) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        // TODO PERF: don't fetch entire model when we're only looking for
        // a subset of hosts.
        if (!_flags.host_filter.includes(hconf.name)) {
            continue;
        }

        for (size_t j = 0; j < hconf.services.size(); j++) {
            const cloud::config::ModelConfig::Hosts::Services &svc = hconf.services[j];
            if (svc.type == "configserver") {
                continue;
            }

            for (size_t k = 0; k < svc.ports.size(); k++) {
                std::string tags = svc.ports[k].tags;
                if (tagsContain(tags, "http") &&
                    tagsContain(tags, "state")) {
                    bool upToDate = checkServiceGeneration(svc.configid, hconf.name,
                                                           svc.ports[k].number,
                                                           "/state/v1/config");

                    if (!upToDate) {
                        if (svc.type == "searchnode" ||
                            svc.type == "topleveldispatch" ||
                            svc.type == "logd")
                        {
                            std::cerr << "[generation not up-to-date ignored]" << std::endl;
                        } else {
                            allUpToDate = false;
                        }
                    }
                    break;
                }
            }
        }
    }

    return allUpToDate ? 0 : 1;
}

bool
ConfigStatus::fetch_json(std::string configId, std::string host, int port,
                         std::string path, std::string &data)
{
    MyHttpHandler myHandler(configId);
    bool ok = vbench::HttpClient::fetch(vbench::ServerSpec(host, port), path, myHandler);

    if (ok) {
        data = myHandler.getJson();
        return true;
    } else {
        return false;
    }
}

bool
ConfigStatus::checkServiceGeneration(std::string configId, std::string host, int port, std::string path)
{
    std::string data;
    vespalib::Slime slime;

    if (!fetch_json(configId, host, port, path, data)) {
        return false;
    }

    size_t size = vespalib::slime::JsonFormat::decode(data, slime);

    if (size == 0) {
        std::cerr << configId << ": JSON parsing failed" << std::endl;
        return false;
    }

    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, false);

    if (slime.get()["config"].valid()) {
        std::map<std::string, int64_t> generations;
        ComponentTraverser::collect(configId, slime, generations);
        bool upToDate = true;

        std::map<std::string, int64_t>::iterator iter;
        for (iter = generations.begin(); iter != generations.end(); iter++) {
            if (iter->second != _generation) {
                std::cout << configId << ":" << iter->first << " has generation " <<
                    iter->second << " not " << _generation << std::endl;
                upToDate = false;
            } else {
                if (_flags.verbose) {
                    std::cout << configId << ":" << iter->first <<
                        " has the latest generation " << iter->second << std::endl;
                }
            }
        }

        return upToDate;
    } else {
        std::cerr << configId << ": No valid config object" << std::endl;

        return false;
    }
}
