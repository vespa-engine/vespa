// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include "lib/configstatus.h"
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-config-status");

class Application {
    ConfigStatus::Flags _flags;
    vespalib::string _cfgId;
    vespalib::string _specString;
    int parseOpts(int argc, char **argv);
    vespalib::string getSources();
    HostFilter parse_host_set(vespalib::stringref raw_arg) const;
public:
    void usage(const char *self);
    int main(int argc, char **argv);

    Application();
    ~Application();
};

Application::Application()
    : _flags(),
      _cfgId("admin/model"),
      _specString("")
{}
Application::~Application() { }

int Application::parseOpts(int argc, char **argv) {
    int c = '?';
    while ((c = getopt(argc, argv, "c:s:vC:f:")) != -1) {
        switch (c) {
        case 'v':
            _flags.verbose = true;
            break;
        case 'C':
            _cfgId = optarg;
            break;
        case 'c':
            _specString = optarg;
            break;
        case 'h':
            usage(argv[0]);
            std::_Exit(0);
        case 'f':
            _flags.host_filter = parse_host_set(optarg);
            break;
        default:
            usage(argv[0]);
            std::_Exit(1);
        }
    }
    if (_specString.empty()) {
        _specString = getSources();
    }
    return optind;
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

void Application::usage(const char *self) {
    std::cerr << "vespa-config-status version 1.0\n"
              << "Usage: " << self << " [options]\n"
              << "options: [-v] for verbose\n"
              << "         [-c host] or [-c host:port] to specify config server\n"
              << "         [-f host0,...,hostN] filter to only query config\n"
                 "         status for the given comma-separated set of hosts\n"
              << std::endl;
}

int Application::main(int argc, char **argv) {
    parseOpts(argc, argv);
    fprintf(stderr, "Getting config from: %s\n", _specString.c_str());
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
    vespalib::SignalHandler::PIPE.ignore();
    Application app;
    return app.main(argc, argv);
}
