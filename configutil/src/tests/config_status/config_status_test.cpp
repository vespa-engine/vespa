// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <lib/configstatus.h>
#include <iostream>
#include <vespa/fastlib/net/httpserver.h>
#include <vespa/config-model.h>
#include <vespa/config/config.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/vespalib/stllike/string.h>

using namespace config;

class HTTPStatus : private Fast_HTTPServer {
private:
    std::string _reply;
    bool _fail;

    virtual void onGetRequest(const string &, const string &,
                      Fast_HTTPConnection &conn) {
        if (_fail) {
            conn.Output(conn.GetHTTPVersion().c_str());
            conn.Output(" 500 Error\r\n");
            conn.Output("Connection: close\r\n");
            conn.Output("\r\n");
        } else {
            conn.Output(conn.GetHTTPVersion().c_str());
            conn.Output(" 200 OK\r\n");
            conn.Output("Content-Type: application/json\r\n\r\n");
            conn.Output(_reply.c_str());
        }
    };


public:
    HTTPStatus(std::string reply)
        : Fast_HTTPServer(0), _reply(reply), _fail(false)
        {
            Start();
        };
    HTTPStatus(bool fail)
        : Fast_HTTPServer(0), _reply(""), _fail(fail)
        {
            Start();
        };

    int getListenPort() { return Fast_HTTPServer::getListenPort(); }

    ~HTTPStatus() {
        Stop();
    };
};

class Status {
public:
    ConfigStatus::Flags flags;
    std::unique_ptr<ConfigStatus> status;

    Status(int httpport) : flags() {
        flags.verbose = true;
        ConfigSet set;
        ConfigContext::SP ctx(new ConfigContext(set));
        cloud::config::ModelConfigBuilder builder;
        
        cloud::config::ModelConfigBuilder::Hosts::Services::Ports port;
        port.number = httpport;
        port.tags = "http state";

        cloud::config::ModelConfigBuilder::Hosts::Services service;
        service.name = "qrserver";
        service.type = "qrserver";
        service.configid = "qrserver/cluster.default";
        service.clustertype = "qrserver";
        service.clustername = "default";
        service.ports.push_back(port);

        cloud::config::ModelConfigBuilder::Hosts host;
        host.services.push_back(service);
        host.name = "localhost";

        builder.hosts.push_back(host);

        set.addBuilder("admin/model", &builder);
        config::ConfigUri uri("admin/model", ctx);
        std::unique_ptr<ConfigStatus> s(new ConfigStatus(flags, uri));
        status = std::move(s);
    };

    ~Status() {
    };
};

TEST_FF("all ok", HTTPStatus(std::string("{\"config\": { \"all\": { \"generation\": 1 } }}")), Status(f1.getListenPort())) {
    ASSERT_EQUAL(0, f2.status->action());
}

TEST_FF("generation too old", HTTPStatus(std::string("{\"config\": { \"all\": { \"generation\": 0 } }}")), Status(f1.getListenPort())) {
    ASSERT_EQUAL(1, f2.status->action());
}

TEST_FF("bad json", HTTPStatus(std::string("{")), Status(f1.getListenPort())) {
    ASSERT_EQUAL(1, f2.status->action());
}

TEST_FF("http failure", HTTPStatus(true), Status(f1.getListenPort())) {
    ASSERT_EQUAL(1, f2.status->action());
}

TEST_MAIN() { TEST_RUN_ALL(); }
