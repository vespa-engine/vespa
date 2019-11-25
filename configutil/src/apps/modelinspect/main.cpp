// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <iostream>
#include <lib/modelinspect.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-model-inspect");

class Application : public FastOS_Application
{
    ModelInspect::Flags _flags;
    vespalib::string _cfgId;
    vespalib::string _specString;
    int parseOpts();
    vespalib::string getSources();
    config::ConfigUri getConfigUri();
public:
    void usage();
    int Main() override;

    Application();
    ~Application();
};

Application::Application() : _flags(), _cfgId("admin/model"), _specString("") {}
Application::~Application() { }

int
Application::parseOpts()
{
    char c = '?';
    const char *optArg = NULL;
    int optInd = 0;
    while ((c = GetOpt("hvut:c:C:", optArg, optInd)) != -1) {
        switch (c) {
        case 'v':
            _flags.verbose = true;
            break;
        case 'u':
            _flags.makeuri = true;
            break;
        case 't':
            _flags.tagFilter.push_back(optArg);
            _flags.tagfilt = true;
            break;
        case 'C':
            _cfgId = optArg;
            break;
        case 'c':
            _specString = optArg;
            break;
        case 'h':
            return _argc;
        default:
            usage();
            exit(1);
        }
    }
    if (_specString.empty()) {
        _specString = getSources();
    }
    return optInd;
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
        LOG(fatal, "Failed to set up model configuration source, will exit: %s", e.what());
        exit(1);
    }
}

void
Application::usage()
{
    std::cerr <<
        "vespa-model-inspect version 2.0"                                  << std::endl <<
        "Usage: " << _argv[0] << " [options] <command> <options>"          << std::endl <<
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
Application::Main()
{
    int cnt = parseOpts();
    if (_argc == cnt) {
        usage();
        return 0;
    }

    config::ConfigUri uri = getConfigUri();
    ModelInspect model(_flags, uri, std::cout);
    return model.action(_argc - cnt, &_argv[cnt]);
}

int
main(int argc, char** argv)
{
    vespa::Defaults::bootstrap(argv[0]);
    Application app;
    return app.Entry(argc, argv);
}
