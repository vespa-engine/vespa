// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proxycmd.h"
#include "methods.h"
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <unistd.h>

class Application
{
    Flags _flags;
    bool parseOpts(int argc, char **argv);
public:
    void usage();
    int main(int argc, char **argv);

    Application() : _flags() {}
};

bool
Application::parseOpts(int argc, char **argv)
{
    int c = '?';
    while ((c = getopt(argc, argv, "m:s:p:h")) != -1) {
        switch (c) {
        case 'm':
            _flags.method = optarg;
            break;
        case 's':
            _flags.targethost = optarg;
            break;
        case 'p':
            _flags.portnumber = atoi(optarg);
            break;
        case 'h':
        default:
            return false; // triggers usage()
        }
    }
    const Method method = methods::find(_flags.method);
    if (optind + method.args <= argc) {
        for (int i = 0; i < method.args; ++i) {
            vespalib::string arg = argv[optind++];
            _flags.args.push_back(arg);
        }
    } else {
        std::cerr << "ERROR: method "<< _flags.method << " requires " << method.args
                  << " arguments, only got " << (argc - optind) << std::endl;
        return false;
    }
    if (optind != argc) {
        std::cerr << "ERROR: "<<(argc - optind)<<" extra arguments\n";
        return false;
    }
    _flags.method = method.rpcMethod;
    return true;
}

void
Application::usage(void)
{
    std::cerr <<
        "Usage: vespa-configproxy-cmd [options]"                                 << std::endl <<
        "    -m <method>                   method"                         << std::endl <<
        "    -s <hostname>                 hostname (default: localhost)"  << std::endl <<
        "    -p <port>                     port number (default: 19090)"   << std::endl <<
        "Available methods for -m option:"                                 << std::endl;
    methods::dump();
}

int
Application::main(int argc, char **argv)
{
    if (! parseOpts(argc, argv)) {
        usage();
        return 1;
    }
    ProxyCmd client(_flags);
    return client.action();
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    Application app;
    return app.main(argc, argv);
}
