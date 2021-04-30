// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <iostream>
#include <cstdlib>
#include "lib/configstatus.h"
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-config-status");

class Application : public FastOS_Application {
    ConfigStatus::Flags _flags;
    vespalib::string _cfgId;
    vespalib::string _specString;
    int parseOpts();
    vespalib::string getSources();
    HostFilter parse_host_set(vespalib::stringref raw_arg) const;
public:
    void usage();
    int Main() override;

    Application();
    ~Application();
};

Application::Application()
    : _flags(),
      _cfgId("admin/model"),
      _specString("")
{}
Application::~Application() { }

int Application::parseOpts() {
    int c = '?';
    const char *optArg = NULL;
    int optInd = 0;
    while ((c = GetOpt("c:s:vC:f:", optArg, optInd)) != -1) {
        switch (c) {
        case 'v':
            _flags.verbose = true;
            break;
        case 'C':
            _cfgId = optArg;
            break;
        case 'c':
            _specString = optArg;
            break;
        case 'h':
            usage();
            std::_Exit(0);
        case 'f':
            _flags.host_filter = parse_host_set(optArg);
            break;
        default:
            usage();
            std::_Exit(1);
        }
    }
    if (_specString.empty()) {
        _specString = getSources();
    }
    return optInd;
}

HostFilter Application::parse_host_set(vespalib::stringref raw_arg) const {
    vespalib::StringTokenizer tokenizer(raw_arg, ",");
    tokenizer.removeEmptyTokens();

    HostFilter::HostSet hosts;
    for (auto& host : tokenizer) {
        hosts.emplace(host);
    }
    return HostFilter(std::move(hosts));
}

void Application::usage() {
    std::cerr << "vespa-config-status version 1.0\n"
              << "Usage: " << _argv[0] << " [options]\n"
              << "options: [-v] for verbose\n"
              << "         [-c host] or [-c host:port] to specify config server\n"
              << "         [-f host0,...,hostN] filter to only query config\n"
                 "         status for the given comma-separated set of hosts\n"
              << std::endl;
}

int Application::Main() {
    parseOpts();

    config::ServerSpec spec(_specString);
    config::ConfigUri uri = config::ConfigUri::createFromSpec(_cfgId, spec);
    ConfigStatus status(_flags, uri);

    return status.action();
}

vespalib::string Application::getSources() {
    vespalib::string specs;
    for (std::string v : vespa::Defaults::vespaConfigSourcesRpcAddrs()) {
        if (! specs.empty()) specs += ",";
        specs += v;
    }
    return specs;
}

int main(int argc, char **argv) {
    Application app;
    return app.Entry(argc, argv);
}
