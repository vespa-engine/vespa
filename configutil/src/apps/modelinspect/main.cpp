// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include "lib/modelinspect.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-model-inspect");

class Application
{
    ModelInspect::Flags _flags;
    vespalib::string _cfgId;
    vespalib::string _specString;
    int parseOpts(int argc, char **argv);
    vespalib::string getSources();
    config::ConfigUri getConfigUri();
public:
    void usage(const char *self);
    int main(int argc, char **argv);

    Application();
    ~Application();
};

Application::Application() : _flags(), _cfgId("admin/model"), _specString("") {}
Application::~Application() { }

int
Application::parseOpts(int argc, char **argv)
{
    int c = '?';
    while ((c = getopt(argc, argv, "hvut:c:C:")) != -1) {
        switch (c) {
        case 'v':
            _flags.verbose = true;
            break;
        case 'u':
            _flags.makeuri = true;
            break;
        case 't':
            _flags.tagFilter.push_back(optarg);
            _flags.tagfilt = true;
            break;
        case 'C':
            _cfgId = optarg;
            break;
        case 'c':
            _specString = optarg;
            break;
        case 'h':
            return argc;
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

vespalib::string
Application::getSources()
{
    vespalib::string specs;
    for (std::string v : vespa::Defaults::vespaConfigSourcesRpcAddrs()) {
        if (! specs.empty()) specs += ",";
        specs += v;
    }
    return specs;
}

config::ConfigUri
Application::getConfigUri()
{
    try {
        return config::ConfigUri::createFromSpec(_cfgId, config::ServerSpec(_specString));
    }
    catch (std::exception &e) {
        std::cerr << "FATAL ERROR: failed to set up model configuration: " << e.what() << "\n";
        std::_Exit(1);
    }
}

void
Application::usage(const char *self)
{
    std::cerr <<
        "vespa-model-inspect version 2.0"                                  << std::endl <<
        "Usage: " << self << " [options] <command> <options>"              << std::endl <<
        "options: [-u] for URLs, [-v] for verbose"                         << std::endl <<
        "         [-c host] or [-c host:port] to specify server"           << std::endl <<
        "         [-t tag] to filter on a port tag"                        << std::endl <<
        "Where command is:"                                                << std::endl <<
        "    hosts - show all hosts"                                       << std::endl <<
        "    services - show all services"                                 << std::endl <<
        "    clusters - show all cluster names"                            << std::endl <<
        "    configids - show all config IDs"                              << std::endl <<
        "    filter:ports - list ports matching filter options"            << std::endl <<
        "    host <hostname> - show services on a given host"              << std::endl <<
        "    service [cluster:]<servicetype>"                                           <<
        " - show all instances of a given servicetype"                     << std::endl <<
        "    cluster <clustername>"                                                     <<
        " - show all services associated with the cluster"                 << std::endl <<
        "    configid <configid>"                                                       <<
        " - show service using configid"                                   << std::endl <<
        "    get-index-of <servicetype> <host>"                                         <<
        " - show all indexes for instances of the servicetype on the host" << std::endl <<
        std::endl;
}

int
Application::main(int argc, char **argv)
{
    int cnt = parseOpts(argc, argv);
    if (argc == cnt) {
        usage(argv[0]);
        return 0;
    }

    config::ConfigUri uri = getConfigUri();
    ModelInspect model(_flags, uri, std::cout);
    return model.action(argc - cnt, &argv[cnt]);
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    vespa::Defaults::bootstrap(argv[0]);
    Application app;
    return app.main(argc, argv);
}
