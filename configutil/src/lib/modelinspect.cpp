// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "modelinspect.h"
#include <lib/tags.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/common/exceptions.h>
#include <iostream>
#include <algorithm>
#include <cstdlib>
#include <set>

using configdefinitions::tagsContain;
using configdefinitions::upcase;

ModelInspect::Flags::Flags()
    : verbose(false), makeuri(false), tagfilt(false),
      tagFilter()
{}

ModelInspect::Flags::Flags(const Flags &) = default;
ModelInspect::Flags & ModelInspect::Flags::operator = (const Flags &) = default;
ModelInspect::Flags::~Flags() { }

ModelInspect::ModelInspect(Flags flags, const config::ConfigUri &uri, std::ostream &out)
    : _cfg(), _flags(flags), _out(out)
{
    if (_flags.verbose) {
        std::cerr << "subscribing to model config with configid " << uri.getConfigId() << "\n";
    }

    try {
        _cfg = config::ConfigGetter<cloud::config::ModelConfig>::getConfig(uri.getConfigId(), uri.getContext());
    } catch (config::ConfigRuntimeException &e) {
        std::cerr << e.getMessage() << "\n";
    }
    if (_cfg) {
        if (_flags.verbose) std::cerr << "success!\n";
    } else {
        std::cerr << "FATAL ERROR: failed to get model configuration.\n";
        std::_Exit(1);
    }
}

ModelInspect::~ModelInspect() = default;

void
ModelInspect::printPort(const vespalib::string &host, int port,
                        const vespalib::string &tags)
{
    if (_flags.tagfilt) {
        for (size_t i = 0; i < _flags.tagFilter.size(); ++i) {
            if (! tagsContain(tags, _flags.tagFilter[i])) {
                return;
            }
        }
    }
    if (_flags.makeuri && tagsContain(tags, "HTTP")) {
        _out << "    http://" << host << ":" << port << "/";
    } else {
        _out << "    tcp/" << host << ":" << port;
    }
    if (_flags.tagfilt) {
        _out << "\n";
    } else {
        vespalib::string upper = upcase(tags);
        _out << " (" << upper << ")\n";
    }
}

void
ModelInspect::printService(const cloud::config::ModelConfig::Hosts::Services &svc,
                           const vespalib::string &host)
{
    if (!_flags.tagfilt) {
        _out << svc.name << " @ " << host << " : " << svc.clustertype << std::endl;
        _out << svc.configid << std::endl;
    }
    for (size_t i = 0; i < svc.ports.size(); ++i) {
        printPort(host, svc.ports[i].number, svc.ports[i].tags);
    }
}

int
ModelInspect::action(int cnt, char **argv)
{
    const vespalib::string cmd = *argv++;
    if (cnt == 1) {
        if (cmd == "yamldump") {
            yamlDump();
            return 0;
        }
        if (cmd == "hosts") {
            listHosts();
            return 0;
        }
        if (cmd == "services") {
            listServices();
            return 0;
        }
        if (cmd == "clusters") {
            listClusters();
            return 0;
        }
        if (cmd == "configids") {
            listConfigIds();
            return 0;
        }
        if (cmd == "filter:hosts") {
            if (!_flags.tagfilt) {
                std::cerr << "filter needs some filter options" << std::endl;
                return 1;
            }
            std::cerr << "not implemented" << std::endl;
            return 1;
        }
        if (cmd == "filter:ports") {
            if (!_flags.tagfilt) {
                std::cerr << "filter needs some filter options" << std::endl;
                return 1;
            }
            return listAllPorts();
        }
    }
    if (cnt == 2) {
        vespalib::string arg = *argv++;
        if (cmd == "host") {
            return listHost(arg);
        }
        if (cmd == "cluster") {
            return listCluster(arg);
        }
        if (cmd == "service") {
            size_t colon = arg.find(':');
            if (colon != vespalib::string::npos)  {
                return listService(arg.substr(0, colon),
                                   arg.substr(colon + 1));
            } else {
                return listService(arg);
            }
        }
        if (cmd == "configid") {
            return listConfigId(arg);
        }
    }
    if (cnt == 3) {
        vespalib::string arg1 = *argv++;
        vespalib::string arg2 = *argv++;
        if (cmd == "get-index-of") {
            return getIndexOf(arg1, arg2);
        }
    };
    std::cerr << "bad args '" << cmd << "' (got " << cnt << " arguments)" << std::endl;
    return 1;
}

void
ModelInspect::dumpService(const cloud::config::ModelConfig::Hosts::Services &svc,
            const vespalib::string &host)
{
    _out << "- servicename: " << svc.name << "\n";
    _out << "  servicetype: " << svc.type << "\n";
    _out << "  clustertype: " << svc.clustertype << "\n";
    _out << "  clustername: " << svc.clustername << "\n";
    _out << "  index: "       << svc.index << "\n";
    _out << "  hostname: "    << host << "\n";
    _out << "  config-id: "   << svc.configid << "\n";

    if (svc.ports.size() > 0) {
        _out << "  ports: \n";
        for (size_t i = 0; i < svc.ports.size(); ++i) {
            _out << "  - " << svc.ports[i].number << "\n";
        }
    }
}

void
ModelInspect::yamlDump()
{
    _out << "--- \n";
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            dumpService(hconf.services[j], hconf.name);
        }
    }
}

void
ModelInspect::listHosts()
{
    std::vector<vespalib::string> hosts;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        hosts.push_back(hconf.name);
    }
    std::sort(hosts.begin(), hosts.end());
    for (size_t i = 0; i < hosts.size(); ++i) {
        _out << hosts[i] << std::endl;
    }
}

void
ModelInspect::listServices()
{
    typedef std::set<vespalib::string> Set;
    Set services;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            services.insert(hconf.services[j].type);
        }
    }
    for (Set::const_iterator it = services.begin(); it != services.end(); ++it) {
        _out << (*it) << std::endl;
    }
}

void
ModelInspect::listClusters()
{
    typedef std::set<vespalib::string> Set;
    Set clusters;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            clusters.insert(hconf.services[j].clustername);
        }
    }
    for (Set::const_iterator it = clusters.begin(); it != clusters.end(); ++it) {
        _out << (*it) << std::endl;
    }
}

void
ModelInspect::listConfigIds()
{
    std::vector<vespalib::string> configids;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            configids.push_back(hconf.services[j].configid);
        }
    }
    std::sort(configids.begin(), configids.end());
    for (size_t i = 0; i < configids.size(); ++i) {
        _out << configids[i] << std::endl;
    }
}

int
ModelInspect::listHost(const vespalib::string host)
{
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        if (host == hconf.name) {
            for (size_t j = 0; j < hconf.services.size(); ++j) {
                printService(hconf.services[j], host);
            }
            return 0;
        }
    }
    std::cerr << "no config found for host '" << host << "'\n";
    return 1;
}

int
ModelInspect::listCluster(const vespalib::string cluster)
{
    bool found = false;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            if (cluster == hconf.services[j].clustername) {
                found = true;
                printService(hconf.services[j], hconf.name);
            }
        }
    }
    if (found) return 0;
    std::cerr << "no config found for cluster '" << cluster << "'\n";
    return 1;
}

int
ModelInspect::listAllPorts()
{
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            printService(hconf.services[j], hconf.name);
        }
    }
    return 0;
}

int
ModelInspect::listService(const vespalib::string svctype)
{
    bool found = false;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            if (svctype == hconf.services[j].type) {
                found = true;
                printService(hconf.services[j], hconf.name);
            }
        }
    }
    if (found) return 0;
    std::cerr << "no services found with type '" << svctype << "'\n";
    return 1;
}


int
ModelInspect::listService(const vespalib::string cluster,
                          const vespalib::string svctype)
{
    bool found = false;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            if (cluster == hconf.services[j].clustername &&
                svctype == hconf.services[j].type)
            {
                found = true;
                printService(hconf.services[j], hconf.name);
            }
        }
    }
    if (found) return 0;
    std::cerr << "no services found with type '" << svctype << "' in cluster '" << cluster << "'\n";
    return 1;
}

int
ModelInspect::listConfigId(const vespalib::string configid)
{
    bool found = false;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        for (size_t j = 0; j < hconf.services.size(); ++j) {
            if (configid == hconf.services[j].configid) {
                found = true;
                printService(hconf.services[j], hconf.name);
            }
        }
    }
    if (found) return 0;
    std::cerr << "no services found with configid '" << configid << "'\n";
    return 1;
}

int
ModelInspect::getIndexOf(const vespalib::string service, const vespalib::string host)
{
    bool found = false;
    for (size_t i = 0; i < _cfg->hosts.size(); ++i) {
        const cloud::config::ModelConfig::Hosts &hconf = _cfg->hosts[i];
        if (host == hconf.name) {
            for (size_t j = 0; j < hconf.services.size(); ++j) {
                if (service == hconf.services[j].type) {
                    found = true;
                    _out << hconf.services[j].index << std::endl;
                }
            }
        }
    }
    if (found) return 0;
    std::cerr << "no service of type '" << service << "' found for host '" << host << "'\n";
    return 1;
}
