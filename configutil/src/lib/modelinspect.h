// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config-model.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/subscription/configuri.h>

class ModelInspect

{
public:
    struct Flags {
        bool verbose;
        bool makeuri;
        bool tagfilt;
        std::vector<vespalib::string> tagFilter;
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
    virtual int listHost(const vespalib::string host);
    virtual int listCluster(const vespalib::string cluster);
    virtual int listAllPorts();
    virtual int listService(const vespalib::string svctype);
    virtual int listService(const vespalib::string cluster,
                    const vespalib::string svctype);
    virtual int listConfigId(const vespalib::string configid);
    virtual int getIndexOf(const vespalib::string service, const vespalib::string host);

private:
    std::unique_ptr<cloud::config::ModelConfig> _cfg;
    Flags                _flags;
    std::ostream        &_out;

    void printService(const cloud::config::ModelConfig::Hosts::Services &svc,
                      const vespalib::string &host);
    void printPort(const vespalib::string &host, int port,
                   const vespalib::string &tags);
    void dumpService(const cloud::config::ModelConfig::Hosts::Services &svc,
                     const vespalib::string &host);

};
