// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/defaults.h>
#include <vespa/log/log.h>
LOG_SETUP("vespa-config-status");
#include <iostream>
#include <lib/configstatus.h>

class Application : public FastOS_Application
{
    ConfigStatus::Flags _flags;
    vespalib::string _cfgId;
    vespalib::string _specString;
    int parseOpts();
    vespalib::string getSources();
public:
    void usage(void);
    int Main(void);

    Application() : _flags(), _cfgId("admin/model"), _specString("") {}
};

int
Application::parseOpts()
{
    char c = '?';
    const char *optArg = NULL;
    int optInd = 0;
    while ((c = GetOpt("c:s:vC:", optArg, optInd)) != -1) {
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
            exit(0);
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


void
Application::usage(void)
{
    std::cerr <<
        "vespa-config-status version 1.0"                                  << std::endl <<
        "Usage: " << _argv[0] << " [options] "                             << std::endl <<
        "options: [-v] for verbose"                                        << std::endl <<
        "         [-c host] or [-c host:port] to specify config server"    << std::endl <<
        std::endl;
}

int
Application::Main(void)
{
    parseOpts();

    config::ServerSpec spec(_specString);
    config::ConfigUri uri = config::ConfigUri::createFromSpec(_cfgId, spec);
    ConfigStatus status(_flags, uri);

    return status.action();
}

vespalib::string
Application::getSources(void)
{
    vespalib::string cmd = vespa::Defaults::vespaHome();
    cmd.append("libexec/vespa/vespa-config.pl -configsources");
    FILE* fp = popen(cmd.c_str(), "r");
    if (fp == 0) {
        std::cerr << "Failed to run " << cmd << " ("
                  << errno << "): " << strerror(errno) << "\n";
        return "";
    }
    vespalib::asciistream specs;
    char data[500];
    while (fgets(data, 500, fp) != 0) {
        specs << &data[0] << "\n";
    }
    pclose(fp);
    return specs.str();
}

int
main(int argc, char **argv)
{
    Application app;
    return app.Entry(argc, argv);
}
