// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config-model.h>
#include <vespa/config/subscription/configuri.h>
#include <string>

class ModelInspect

{
public:
    struct Flags {
        bool verbose;
        bool makeuri;
        bool tagfilt;
        std::vector<std::string> tagFilter;
        Flags();
        Flags(const Flags &);
        Flags & operator = (const Flags &);
        Flags(Flags &&) = default;
        Flags & operator = (Flags &&) = default;
        ~Flags();
    };

    ModelInspect(Flags flags, const config::ConfigUri &uri, std::ostream &out);
    virtual ~ModelInspect();

    int action(int cnt, char *argv[]);

    virtual void yamlDump();
    virtual void listHosts();
    virtual void listServices();
    virtual void listClusters();
    virtual void listConfigIds();
    virtual int listHost(const std::string host);
    virtual int listCluster(const std::string cluster);
    virtual int listAllPorts();
    virtual int listService(const std::string svctype);
    virtual int listService(const std::string cluster,
                    const std::string svctype);
    virtual int listConfigId(const std::string configid);
    virtual int getIndexOf(const std::string service, const std::string host);

private:
    std::unique_ptr<cloud::config::ModelConfig> _cfg;
    Flags                _flags;
    std::ostream        &_out;

    void printService(const cloud::config::ModelConfig::Hosts::Services &svc,
                      const std::string &host);
    void printPort(const std::string &host, int port,
                   const std::string &tags);
    void dumpService(const cloud::config::ModelConfig::Hosts::Services &svc,
                     const std::string &host);

};
