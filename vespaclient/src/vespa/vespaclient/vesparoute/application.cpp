// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "application.h"

#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/configagent.h>
#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/messagebus/routing/routedirective.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcsendv1.h>
#include <vespa/messagebus/network/rpcsendv2.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/supervisor.h>

using config::ConfigGetter;
using document::DocumenttypesConfig;
using messagebus::MessagebusConfig;
using document::DocumentTypeRepo;

namespace vesparoute {

Application::Application() :
    _loadTypes(),
    _net(),
    _mbus(),
    _params()
{ }

Application::~Application() {}


    int
Application::Main()
{
    try {
        if (_argc == 1) {
            _params.setListRoutes(true);
            _params.setListHops(true);
        } else if (!parseArgs()) {
            return EXIT_SUCCESS;
        }

        std::shared_ptr<const DocumentTypeRepo> repo(
                new DocumentTypeRepo(
                        *ConfigGetter<DocumenttypesConfig>::getConfig(_params.getDocumentTypesConfigId())));
        _net.reset(new MyNetwork(_params.getRPCNetworkParams()));
        _mbus.reset(
                new mbus::MessageBus(
                        *_net,
                        mbus::MessageBusParams()
                        .setRetryPolicy(mbus::IRetryPolicy::SP())
                        .addProtocol(mbus::IProtocol::SP(
                                        new documentapi::DocumentProtocol(
                                                _loadTypes, repo)))));
        mbus::ConfigAgent cfg(*_mbus);
        cfg.configure(ConfigGetter<MessagebusConfig>::getConfig(_params.getRoutingConfigId()));

        // _P_A_R_A_N_O_I_A_
        mbus::RoutingTable::SP table = _mbus->getRoutingTable(_params.getProtocol());
        if (table.get() == NULL) {
            throw config::InvalidConfigException(vespalib::make_string("There is no routing table for protocol '%s'.",
                                                                       _params.getProtocol().c_str()));
        }
        for (std::vector<std::string>::iterator it = _params.getHops().begin();
             it != _params.getHops().end(); ++it)
        {
            if (table->getHop(*it) == NULL) {
                throw config::InvalidConfigException(vespalib::make_string("There is no hop named '%s' for protocol '%s'.",
                                                                           it->c_str(), _params.getProtocol().c_str()));
            }
        }

        // Perform requested action.
        if (_params.getDump()) {
            printDump();
            return EXIT_SUCCESS;
        }
        if (_params.getListRoutes()) {
            listRoutes();
        }
        if (_params.getListHops()) {
            listHops();
        }
        if (!_params.getRoutes().empty()) {
            printRoutes();
        }
        if (!_params.getHops().empty()) {
            printHops();
        }
        if (_params.getListServices()) {
            printServices();
        }

        _mbus.reset();
        _net.reset();
    } catch(std::exception &e) {
        std::string err(e.what());
        printf("ERROR: %s\n", err.substr(0, err.find_first_of('\n')).c_str());
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

bool
Application::parseArgs()
{
    for (int arg = 1; arg < _argc; arg++) {
        if (strcasecmp(_argv[arg], "--documenttypesconfigid") == 0) {
            if (++arg < _argc) {
                _params.setDocumentTypesConfigId(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'documenttypesconfigid'.");
            }
        } else if (strcasecmp(_argv[arg], "--dump") == 0) {
            _params.setDump(true);
        } else if (strcasecmp(_argv[arg], "--help") == 0 ||
                   strcasecmp(_argv[arg], "-h") == 0) {
            printHelp();
            return false;
        } else if (strcasecmp(_argv[arg], "--hop") == 0) {
            if (++arg < _argc) {
                _params.getHops().push_back(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'hop'.");
            }
        } else if (strcasecmp(_argv[arg], "--hops") == 0) {
            _params.setListHops(true);
        } else if (strcasecmp(_argv[arg], "--identity") == 0) {
            if (++arg < _argc) {
                _params.getRPCNetworkParams().setIdentity(mbus::Identity(_argv[arg]));
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'identity'.");
            }
        } else if (strcasecmp(_argv[arg], "--listenport") == 0) {
            if (++arg < _argc) {
                _params.getRPCNetworkParams().setListenPort(atoi(_argv[arg]));
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'listenport'.");
            }
        } else if (strcasecmp(_argv[arg], "--protocol") == 0) {
            if (++arg < _argc) {
                _params.setProtocol(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'protocol'.");
            }
        } else if (strcasecmp(_argv[arg], "--route") == 0) {
            if (++arg < _argc) {
                _params.getRoutes().push_back(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'route'.");
            }
        } else if (strcasecmp(_argv[arg], "--routes") == 0) {
            _params.setListRoutes(true);
        } else if (strcasecmp(_argv[arg], "--routingconfigid") == 0) {
            if (++arg < _argc) {
                _params.setRoutingConfigId(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'routingconfigid'.");
            }
        } else if (strcasecmp(_argv[arg], "--services") == 0) {
            _params.setListServices(true);
        } else if (strcasecmp(_argv[arg], "--slobrokconfigid") == 0) {
            if (++arg < _argc) {
                _params.getRPCNetworkParams().setSlobrokConfig(_argv[arg]);
            } else {
                throw config::InvalidConfigException("Missing value for parameter 'slobrokconfigid'.");
            }
        } else if (strcasecmp(_argv[arg], "--verify") == 0) {
            _params.setVerify(true);
        } else {
            throw config::InvalidConfigException(vespalib::make_string("Unknown option '%s'.", _argv[arg]));
        }
    }
    return true;
}

void
Application::printHelp() const
{
    printf("Usage: vespa-route [OPTION]...\n"
           "Options:\n"
           "  --documenttypesconfigid <id>  Sets the config id that supplies document configuration.\n"
           "  --dump                          Prints the complete content of the routing table.\n"
           "  --help                          Prints this help.\n"
           "  --hop <name>                    Prints detailed information about hop <name>.\n"
           "  --hops                          Prints a list of all available hops.\n"
           "  --identity <id>                 Sets the identity of message bus.\n"
           "  --listenport <num>              Sets the port message bus will listen to.\n"
           "  --protocol <name>               Sets the name of the protocol whose routing to inspect.\n"
           "  --route <name>                  Prints detailed information about route <name>.\n"
           "  --routes                        Prints a list of all available routes.\n"
           "  --routingconfigid <id>          Sets the config id that supplies the routing tables.\n"
           "  --services                      Prints a list of all available services.\n"
           "  --slobrokconfigid <id>          Sets the config id that supplies the slobrok server list.\n"
           "  --verify                        All hops and routes are verified when routing.\n");
}

bool
Application::verifyRoute(const mbus::Route &route, std::set<std::string> &errors) const
{
    for (uint32_t i = 0; i < route.getNumHops(); ++i) {
        std::string str = route.getHop(i).toString();
        mbus::HopBlueprint hop = getHop(str);
        std::set<std::string> hopErrors;
        if (!verifyHop(hop, hopErrors)) {
            for (std::set<std::string>::iterator err = hopErrors.begin();
                 err != hopErrors.end(); ++err)
            {
                errors.insert(vespalib::make_string("for hop '%s', %s", str.c_str(), err->c_str()));
            }
        }
    }
    return errors.empty();
}

bool
Application::verifyHop(const mbus::HopBlueprint &hop, std::set<std::string> &errors) const
{
    // _P_A_R_A_N_O_I_A_
    if (!hop.hasDirectives()) {
        errors.insert("is empty");
        return false;
    }

    // Look for a policy directive.
    for (uint32_t i = 0; i < hop.getNumDirectives(); ++i) {
        if (hop.getDirective(i)->getType() == mbus::IHopDirective::TYPE_POLICY) {
            return true; // can do whatever
        }
    }
    if (hop.hasRecipients()) {
        errors.insert("has recipients but no policy");
    }

    // Look for route or hop names.
    const mbus::RoutingTable &table = *_mbus->getRoutingTable(_params.getProtocol());
    if (hop.getDirective(0)->getType() == mbus::IHopDirective::TYPE_ROUTE) {
        const mbus::RouteDirective &dir = static_cast<const mbus::RouteDirective &>(*hop.getDirective(0));
        if (table.getRoute(dir.getName()) == nullptr) {
            errors.insert(vespalib::make_string("route '%s' not found", dir.getName().c_str()));
            return false;
        } else {
            return true;
        }
    }

    std::string selector = hop.create()->toString();
    if (table.getHop(selector) != nullptr) {
        return true;
    } else if (table.getRoute(selector) != nullptr) {
        return true;
    }

    // Must be service pattern, perform slobrok lookup.
    slobrok::api::IMirrorAPI::SpecList lst = _net->getMirror().lookup(selector);
    if (lst.empty()) {
        errors.insert("no matching services");
        return false;
    }

    return errors.empty();
}

void
Application::printDump() const
{
    const mbus::RoutingTable &table = *_mbus->getRoutingTable(_params.getProtocol());
    printf("<protocol name='%s'>\n", _params.getProtocol().c_str());
    for (mbus::RoutingTable::HopIterator it = table.getHopIterator();
         it.isValid(); it.next())
    {
        std::set<std::string> errors;
        bool ok = verifyHop(it.getHop(), errors);

        printf("    <hop name='%s' selector='%s'", it.getName().c_str(), it.getHop().create()->toString().c_str());
        if (it.getHop().getIgnoreResult()) {
            printf(" ignore-result='true'");
        }
        if (ok && !it.getHop().hasRecipients()) {
            printf(" />\n");
        } else {
            printf(">\n");
            for (uint32_t r = 0; r < it.getHop().getNumRecipients(); ++r) {
                printf("        <recipient session='%s' />\n", it.getHop().getRecipient(r).toString().c_str());
            }
            for (std::set<std::string>::iterator err = errors.begin();
                 err != errors.end(); ++err) {
                printf("        <error>%s</error>\n", err->c_str());
            }
            printf("    </hop>\n");
        }
    }
    for (mbus::RoutingTable::RouteIterator it = table.getRouteIterator();
         it.isValid(); it.next())
    {
        std::set<std::string> errors;
        bool ok = verifyRoute(it.getRoute(), errors);
        printf("    <route name='%s' hops='%s'", it.getName().c_str(), it.getRoute().toString().c_str());
        if (ok) {
            printf(" />\n");
        } else {
            printf(">\n");
            for (std::set<std::string>::iterator err = errors.begin();
                 err != errors.end(); ++err) {
                printf("        <error>%s</error>\n", err->c_str());
            }
            printf("    </route>\n");
        }

    }
    printf("</protocol>\n");

    slobrok::api::IMirrorAPI::SpecList services;
    getServices(services);
    printf("<services>\n");
    for (slobrok::api::IMirrorAPI::SpecList::iterator it = services.begin();
         it != services.end(); ++it)
    {
        printf("    <service name='%s' spec='%s'/>\n", it->first.c_str(), it->second.c_str());
    }
    printf("</services>\n");
}

void
Application::listHops() const
{
    const mbus::RoutingTable &table = *_mbus->getRoutingTable(_params.getProtocol());
    if (table.hasHops()) {
        printf("There are %d hop(s):\n", table.getNumHops());

        uint32_t hop = 0;
        for (mbus::RoutingTable::HopIterator it = table.getHopIterator();
             it.isValid(); it.next())
        {
            printf("%5d. %s\n", ++hop, it.getName().c_str());
        }
    } else {
        printf("There are no hops configured.\n");
    }
    printf("\n");
}

void
Application::printHops() const
{
    const mbus::RoutingTable &table = *_mbus->getRoutingTable(_params.getProtocol());
    const std::vector<std::string> &hops = _params.getHops();
    for (uint32_t i = 0; i < hops.size(); ++i) {
        const mbus::HopBlueprint &hop = *table.getHop(hops[i]);
        printf("The hop '%s' has selector:\n       %s",
               hops[i].c_str(), hop.create()->toString().c_str());

        std::set<std::string> errors;
        if (_params.getVerify() && verifyHop(hop, errors)) {
            printf(" (verified)\n");
        } else {
            printf("\n");
        }

        if (hop.hasRecipients()) {
            printf("And %d recipient(s):\n", hop.getNumRecipients());
            for (uint32_t r = 0; r < hop.getNumRecipients(); ++r) {
                std::string service = hop.getRecipient(r).toString();
                printf("%5d. %s\n", r + 1, service.c_str());
            }
        }

        if (hop.getIgnoreResult()) {
            printf("Any results from routing through this hop are ignored.\n");
        }

        if (!errors.empty()) {
            printf("It has %zd error(s):\n", errors.size());
            uint32_t err = 1;
            for (std::set<std::string>::iterator it = errors.begin();
                 it != errors.end(); ++err, ++it)
            {
                printf("%5d. %s\n", err, it->c_str());
            }
        }
        printf("\n");
    }
}

void
Application::listRoutes() const
{
    const mbus::RoutingTable &table = *_mbus->getRoutingTable(_params.getProtocol());
    if (table.hasRoutes()) {
        printf("There are %d route(s):\n", table.getNumRoutes());

        uint32_t route = 0;
        for (mbus::RoutingTable::RouteIterator it = table.getRouteIterator();
             it.isValid(); it.next())
        {
            printf("%5d. %s\n", ++route, it.getName().c_str());
        }
    } else {
        printf("There are no routes configured.\n");
    }
    printf("\n");
}

void
Application::printRoutes() const
{
    const std::vector<std::string> &routes = _params.getRoutes();
    for (uint32_t i = 0; i < routes.size(); ++i) {
        std::set<std::string> errors;

        mbus::Route route = getRoute(routes[i]);
        printf("The route '%s' has %d hop(s):\n",
               routes[i].c_str(), route.getNumHops());
        for (uint32_t hop = 0; hop < route.getNumHops(); ++hop) {
            std::string str = route.getHop(hop).toString();
            if (_params.getVerify() && verifyRoute(route, errors)) {
                str += " (verified)";
            }
            printf("%5d. %s\n", hop + 1, str.c_str());
        }
        if (!errors.empty()) {
            printf("It has %zd error(s):\n", errors.size());
            uint32_t err = 1;
            for (std::set<std::string>::iterator it = errors.begin();
                 it != errors.end(); ++err, ++it)
            {
                printf("%5d. %s\n", err, it->c_str());
            }
        }
        printf("\n");
    }
}

void
Application::printServices() const
{
    slobrok::api::IMirrorAPI::SpecList services;
    getServices(services);
    if (!services.empty()) {
        std::set<std::string> lst;
        for (slobrok::api::IMirrorAPI::SpecList::iterator it = services.begin();
             it != services.end(); ++it)
        {
            lst.insert(it->first);
        }
        printf("There are %zd service(s):\n", services.size());
        uint32_t service = 1;
        for (std::set<std::string>::iterator it = lst.begin();
             it != lst.end(); ++it, ++service)
        {
            printf("%5d. %s\n", service, it->c_str());
        }
    } else {
        printf("There are no services available.\n");
    }
    printf("\n");
}

void
Application::getServices(slobrok::api::IMirrorAPI::SpecList &ret, uint32_t depth) const
{
    FRT_Supervisor frt;
    frt.Start();

    std::string pattern = "*";
    for (uint32_t i = 0; i < depth; ++i) {
        slobrok::api::IMirrorAPI::SpecList lst = _net->getMirror().lookup(pattern);
        for (slobrok::api::IMirrorAPI::SpecList::iterator it = lst.begin();
             it != lst.end(); ++it)
        {
            if (isService(frt, it->second)) {
                ret.push_back(*it);
            }
        }
        pattern.append("/*");
    }

    frt.ShutDown(true);
}

bool
Application::isService(FRT_Supervisor &frt, const std::string &spec) const
{
    FRT_Target *target = frt.GetTarget(spec.c_str());
    if (target == NULL) {
        return false;
    }
    FRT_RPCRequest *req = frt.AllocRPCRequest();
    req->SetMethodName("frt.rpc.getMethodList");
    target->InvokeSync(req, 5.0);

    bool ret = false;
    if (!req->IsError()) {
        uint32_t numMethods      = req->GetReturn()->GetValue(0)._string_array._len;
        FRT_StringValue *methods = req->GetReturn()->GetValue(0)._string_array._pt;
        FRT_StringValue *argList = req->GetReturn()->GetValue(1)._string_array._pt;
        FRT_StringValue *retList = req->GetReturn()->GetValue(2)._string_array._pt;

        for (uint32_t i = 0; i < numMethods; ++i) {
            if (mbus::RPCSendV1::isCompatible(methods[i]._str,argList[i]._str, retList[i]._str) ||
                mbus::RPCSendV2::isCompatible(methods[i]._str,argList[i]._str, retList[i]._str)) {
                ret = true;
                break;
            }
        }
    }

    req->SubRef();
    target->SubRef();
    return ret;
}

mbus::HopBlueprint
Application::getHop(const std::string &str) const
{
    const mbus::HopBlueprint *ret = _mbus->getRoutingTable(_params.getProtocol())->getHop(str);
    if (ret == NULL) {
        return mbus::HopBlueprint(mbus::HopSpec("anonymous", str));
    }
    return *ret;
}

mbus::Route
Application::getRoute(const std::string &str) const
{
    const mbus::Route *ret = _mbus->getRoutingTable(_params.getProtocol())->getRoute(str);
    if (ret != NULL) {
        return *ret;
    }
    return mbus::Route::parse(str);
}

}
