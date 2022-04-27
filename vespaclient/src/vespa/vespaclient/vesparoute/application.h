// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "mynetwork.h"
#include "params.h"
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routing/hopblueprint.h>
#include <set>
namespace vesparoute {

/**
 * Command-line feeder running on document api.
 */
class Application {
private:
    std::unique_ptr<MyNetwork>        _net;
    std::unique_ptr<mbus::MessageBus> _mbus;
    Params                          _params;

    /** Parses the arguments of this application into the given params object. */
    bool parseArgs(int argc, char **argv);

    /** Prints help for this application. */
    void printHelp() const;

    /** Prints the full content of the given table. */
    void printDump() const;

    /** Prints information about all hops in the given table. */
    void listHops() const;

    /** Prints detailed information about the named hops. */
    void printHops() const;

    /** Prints information about all routes in the given table. */
    void listRoutes() const;

    /** Prints detailed information about the named routes. */
    void printRoutes() const;

    /** Prints information about all routable services. */
    void printServices() const;

    /** Fills the given spec list with all available routable services. */
    void getServices(slobrok::api::IMirrorAPI::SpecList &ret, uint32_t depth = 10) const;

    /** Returns whether or not the given spec resolves to a mbus service. */
    bool isService(FRT_Supervisor &frt, const std::string &spec) const;

    /** Returns a route corresponding to the given string. */
    mbus::Route getRoute(const std::string &str) const;

    /** Returns a hop corresponding to the given string. */
    mbus::HopBlueprint getHop(const std::string &str) const;

    /** Verifies the content of the given route. */
    bool verifyRoute(const mbus::Route &route, std::set<std::string> &errors) const;

    /** Verifies the content of the given hop. */
    bool verifyHop(const mbus::HopBlueprint &hop, std::set<std::string> &errors) const;

public:
    Application();
    ~Application();
    int main(int argc, char **argv);
};

}
