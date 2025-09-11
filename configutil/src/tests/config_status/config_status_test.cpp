// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <lib/configstatus.h>
#include <vespa/config-model.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/portal/portal.h>

using namespace config;
using vespalib::Portal;
using vespalib::NullCryptoEngine;

auto create_server() { return Portal::create(std::make_shared<NullCryptoEngine>(), 0); }

class HTTPStatus : public vespalib::Portal::GetHandler {
private:
    Portal::SP _server;
    std::string _reply;
    bool _fail;
    Portal::Token::UP _root;

    void get(Portal::GetRequest request) override {
        if (_fail) {
            request.respond_with_error(500, "Error");
        } else {
            request.respond_with_content("application/json", _reply);
        }
    };

public:
    HTTPStatus(std::string reply)
        : _server(create_server()), _reply(reply), _fail(false),
          _root(_server->bind("/", *this)) {}
    HTTPStatus(bool fail)
        : _server(create_server()), _reply(""), _fail(fail),
          _root(_server->bind("/", *this)) {}

    int getListenPort() { return _server->listen_port(); }

    ~HTTPStatus() override;
};

HTTPStatus::~HTTPStatus()
{
    _root.reset();
}

class Status {
public:
    ConfigStatus::Flags flags;
    std::unique_ptr<ConfigStatus> status;

    Status(int http_port,
           const ConfigStatus::Flags& cfg_flags,
           const std::vector<std::string>& model_hosts)
        : flags(cfg_flags)
    {
        flags.verbose = true;
        ConfigSet set;
        auto ctx = std::make_shared<ConfigContext>(set);
        cloud::config::ModelConfigBuilder builder;
        
        cloud::config::ModelConfigBuilder::Hosts::Services::Ports port;
        port.number = http_port;
        port.tags = "http state";

        cloud::config::ModelConfigBuilder::Hosts::Services service;
        service.name = "qrserver";
        service.type = "qrserver";
        service.configid = "qrserver/cluster.default";
        service.clustertype = "qrserver";
        service.clustername = "default";
        service.ports.push_back(port);

        for (auto& mhost : model_hosts) {
            cloud::config::ModelConfigBuilder::Hosts host;
            host.services.push_back(service);
            host.name = mhost;

            builder.hosts.push_back(host);
        }

        set.addBuilder("admin/model", &builder);
        config::ConfigUri uri("admin/model", ctx);
        std::unique_ptr<ConfigStatus> s(new ConfigStatus(flags, uri));
        status = std::move(s);
    }

    Status(int http_port)
        : Status(http_port, ConfigStatus::Flags(), {{"localhost"}})
    {}

    ~Status() {
    }
};

std::string ok_json_at_gen_1() {
    return "{\"config\": { \"all\": { \"generation\": 1 } }}";
}

TEST(ConfigStatusTest, all_ok)
{
    HTTPStatus f1(ok_json_at_gen_1());
    Status f2(f1.getListenPort());
    ASSERT_EQ(0, f2.status->action());
}

TEST(ConfigStatusTest, generation_too_old)
{
    HTTPStatus f1(std::string("{\"config\": { \"all\": { \"generation\": 0 } }}"));
    Status f2(f1.getListenPort());
    ASSERT_EQ(1, f2.status->action());
}

TEST(ConfigStatusTest, bad_json)
{
    HTTPStatus f1(std::string("{"));
    Status f2(f1.getListenPort());
    ASSERT_EQ(1, f2.status->action());
}

TEST(ConfigStatusTest, http_failure)
{
    HTTPStatus f1(true);
    Status f2(f1.getListenPort());
    ASSERT_EQ(1, f2.status->action());
}

TEST(ConfigStatusTest, queried_host_set_can_be_constrained)
{
    HTTPStatus f1(ok_json_at_gen_1());
    HostFilter filter({"localhost"});
    std::vector<std::string> hosts(
            {"localhost", "no-such-host.foo.yahoo.com"});
    Status status(f1.getListenPort(), ConfigStatus::Flags(filter), hosts);
    // Non-existing host should never be contacted.
    ASSERT_EQ(0, status.status->action());
}

GTEST_MAIN_RUN_ALL_TESTS()
