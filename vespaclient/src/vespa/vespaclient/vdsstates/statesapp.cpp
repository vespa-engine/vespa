// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespaclient/clusterlist/clusterlist.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/fastos/app.h>
#include <sstream>
#include <iostream>
#include <thread>
#include <cstdlib>
#include <sys/time.h>

#include <vespa/log/log.h>
LOG_SETUP("vdsstatetool");

namespace storage {

enum Mode { SETNODESTATE, GETNODESTATE, GETCLUSTERSTATE };

namespace {
    Mode getMode(std::string calledAs) {
        std::string::size_type pos = calledAs.rfind('/');
        if (pos != std::string::npos) {
            calledAs = calledAs.substr(pos + 1);
        }
        if (calledAs == "vdssetnodestate-bin") return SETNODESTATE;
        if (calledAs == "vdsgetclusterstate-bin") return GETCLUSTERSTATE;
        if (calledAs == "vdsgetsystemstate-bin") return GETCLUSTERSTATE;
        if (calledAs == "vdsgetnodestate-bin") return GETNODESTATE;
        std::cerr << "Tool called through unknown name '" << calledAs << "'. Assuming you want to "
                  << "get node state.\n";
        return GETNODESTATE;
    }

    uint64_t getTimeInMillis() {
        struct timeval t;
        gettimeofday(&t, 0);
        return (t.tv_sec * uint64_t(1000)) + (t.tv_usec / uint64_t(1000));
    }

    struct Sorter {
        bool operator()(const std::pair<std::string, std::string>& first,
                        const std::pair<std::string, std::string>& second)
            { return (first.first < second.first); }
    };

    const lib::State* getState(const std::string& s) {
        vespalib::string lower = vespalib::LowerCase::convert(s);
        if (lower == "up") return &lib::State::UP;
        if (lower == "down") return &lib::State::DOWN;
        if (lower == "retired") return &lib::State::RETIRED;
        if (lower == "maintenance") return &lib::State::MAINTENANCE;
        return 0;
    }

    template<typename T>
    struct ConfigReader : public T::Subscriber
    {
        T config;

        ConfigReader(const std::string& configId) {
            T::subscribe(configId, *this);
        }
        void configure(const T& c) { config = c; }
    };
}

struct Options : public vespalib::ProgramOptions {
    Mode _mode;
    bool _showSyntax;
    std::string _clusterName;
    vespaclient::ClusterList::Cluster _cluster;
    uint32_t _nodeIndex;
    std::string _slobrokConfigId;
    std::string _slobrokConnectionSpec;
    std::string _nodeType;
    bool _nonfriendlyOutput;
    std::string _state;
    std::string _message;
    std::string _doc;
    uint32_t _slobrokTimeout;

    Options(Mode mode);
    ~Options();

    bool validate() {
        if (_nodeType != ""
            && _nodeType != "storage" && _nodeType != "distributor")
        {
            std::cerr << "Illegal nodetype '" << _nodeType << "'.\n";
            return false;
        }
        if (_mode == SETNODESTATE) {
            const lib::State* state = getState(_state);
            if (state == 0) {
                std::cerr << "Illegal state '" << _state << "'.\n";
                return false;
            }
            if (*state == lib::State::RETIRED ||
                *state == lib::State::MAINTENANCE)
            {
                if (_nodeType != "storage") {
                    std::cerr << "Given state is only valid for storage nodes. "
                              << "Thus you need to specify only to\n"
                              << "set state of storage nodes.\n";
                    return false;
                }
            }
            if (*state != lib::State::UP && *state != lib::State::RETIRED
                && _message == "")
            {
                std::cerr << "You should always have a reason for setting the "
                             "node in a non-available state.\n";
                return false;
            }
        }

        vespaclient::ClusterList clusterList;
        try {
            _cluster = clusterList.verifyContentCluster(_clusterName);
            _clusterName = _cluster.getName();
        } catch (const vespaclient::ClusterList::ClusterNotFoundException& e) {
            std::cerr << e.getMessage() << "\n";
            std::_Exit(1);
        }
        return true;
    }
};

Options::Options(Mode mode)
    : _mode(mode), _cluster("", ""), _nodeIndex(0xffffffff), _nonfriendlyOutput(false), _slobrokTimeout(0)
{
    _doc = "https://yahoo.github.io/vespa/";
    if (_mode == SETNODESTATE) {
        setSyntaxMessage(
                "Set the wanted node state of a storage node. This will "
                "override the state the node is in in the cluster state, if "
                "the current state is \"better\" than the wanted state. "
                "For instance, a node that is currently in initializing state "
                "can be forced into down state, while a node that is currently"
                " down can not be forced into retired state, but can be forced"
                " into maintenance state.\n\n"
                "For more info on states refer to\n" + _doc
        );
    } else if (_mode == GETCLUSTERSTATE) {
        setSyntaxMessage(
                "Get the cluster state of a given cluster.\n\n"
                "For more info on states refer to\n" + _doc
        );
    } else {
        setSyntaxMessage(
                "Retrieve the state of a one or more storage services from the "
                "fleet controller. Will list the state of the locally running "
                "services, possibly restricted to less by options.\n\n"
                "The result will show the slobrok address of the service, and "
                "three states. The first state will show how the state of that "
                "given service looks in the current cluster state. This state "
                "is the state the fleetcontroller is reporting to all nodes "
                "in the cluster this service is in. The second state is the "
                "reported state, which is the state the given node is reporting"
                " to be in itself. The third state is the wanted state, which "
                "is the state we want the node to be in. In most cases this "
                "should be the up state, but in some cases the fleet controller"
                " or an administrator may have set the wanted state otherwise, "
                "in order to get problem nodes out of the cluster.\n\n"
                "For more info on states refer to\n" + _doc
        );
    }
    addOption("h help", _showSyntax, false,
              "Show this help page.");

    addOption("c cluster", _clusterName, std::string("storage"),
              "Which cluster to connect to. By default it will attempt to connect to cluster named 'storage'.");
    if (_mode != GETCLUSTERSTATE) {
        addOption("t type", _nodeType, std::string(""),
                  "Node type to query. This can either be 'storage' or "
                  "'distributor'. If not specified, the operation will "
                  "affect both types.");
        addOption("i index", _nodeIndex, uint32_t(0xffffffff),
                  "The node index of the distributor or storage node to "
                  "contact. If not specified, all indexes running locally "
                  "on this node will be queried");
    }
    if (_mode != SETNODESTATE) {
        addOption("r raw", _nonfriendlyOutput, false,
                  "Show the serialized state formats directly instead of "
                  "reformatting them to look more user friendly.");
    }
    if (_mode == SETNODESTATE) {
        addArgument("Wanted state", _state,
                    "Wanted state to set node in. "
                    "This must be one of up, down or maintenance. Or if "
                    "it's not a distributor it can also be retired.");
        addArgument("Reason", _message, std::string(""),
                    "Give a reason for why you're altering the wanted "
                    "state, which will show up in various admin tools. "
                    "(Use double quotes to give a reason with whitespace "
                    "in it)");
    }
    addOptionHeader("Advanced options. Not needed for most usecases");
    addOption("l slobrokconfig", _slobrokConfigId,
              std::string("admin/slobrok.0"),
              "Config id of slobrok. Will use the default config id of admin/slobrok.0 if not specified.");
    addOption("p slobrokspec", _slobrokConnectionSpec, std::string(""),
              "Slobrok connection spec. By setting this, this application "
              "will not need config at all, but will use the given "
              "connection spec to talk with slobrok.");
    addOption("s slobroktimeout", _slobrokTimeout, uint32_t(5 * 60),
              "Seconds to wait for slobrok client to connect to a slobrok server before failing.");
}
Options::~Options() {}


struct StateApp : public FastOS_Application {
    Options _options;

    StateApp(std::string calledAs) : _options(getMode(calledAs)) {}

    int Main() override {
        _options.setCommandLineArguments(_argc, _argv);
        try{
            _options.parse();
        } catch (vespalib::InvalidCommandLineArgumentsException& e) {
            if (!_options._showSyntax) {
                std::cerr << e.getMessage() << "\n";
                _options.writeSyntaxPage(std::cerr, false);
                std::cerr << "\n";
                return 1;
            }
        }
        if (_options._showSyntax) {
            _options.writeSyntaxPage(std::cerr, false);
            std::cerr << "\n";
            return 0;
        }
        if (!_options.validate()) {
            _options.writeSyntaxPage(std::cerr, false);
            return 1;
        }
        return run();
    }

    int run() {
        fnet::frt::StandaloneFRT supervisor;

        std::unique_ptr<slobrok::api::MirrorAPI> slobrok;
        if (_options._slobrokConnectionSpec == "") {
            config::ConfigUri config(_options._slobrokConfigId);
            slobrok = std::make_unique<slobrok::api::MirrorAPI>(supervisor.supervisor(), slobrok::ConfiguratorFactory(config));
        } else {
            std::vector<std::string> specList;
            specList.push_back(_options._slobrokConnectionSpec);
            slobrok = std::make_unique<slobrok::api::MirrorAPI>(supervisor.supervisor(), slobrok::ConfiguratorFactory(specList));
        }
        LOG(debug, "Waiting for slobrok data to be available.");
        uint64_t startTime = getTimeInMillis();
        uint64_t warnTime = 5 * 1000;
        uint64_t timeout = _options._slobrokTimeout * 1000;
        while (true) {
            uint64_t currentTime = getTimeInMillis();
            if (currentTime >= startTime + timeout) break;
            if (slobrok->ready()) break;
            if (currentTime >= startTime + warnTime) {
                if (warnTime > 5000) {
                    std::cerr << "Still waiting for slobrok to respond. Have "
                              << "gotten no response in "
                              << ((currentTime - startTime) / 1000)
                              << " seconds.\n";
                } else {
                    std::cerr << "Waiting for slobrok server to respond. Have "
                              << "gotten no response in "
                              << ((currentTime - startTime) / 1000) << "\n"
                              << "seconds. Likely cause being one or more "
                              << "slobrok server nodes being down.\n(Thus not "
                              << "replying that socket is closed)\n";
                }
                warnTime *= 4;
            }
            std::this_thread::sleep_for(10ms);
        }
        if (!slobrok->ready()) {
            std::cerr << "Slobrok not ready.\n";
            return 1;
        }

        config::ConfigUri uri(_options._cluster.getConfigId());
        lib::Distribution distribution(*config::ConfigGetter<vespa::config::content::StorDistributionConfig>::getConfig(uri.getConfigId(), uri.getContext()));

        LOG(debug, "Got slobrok data");
        std::string mask = "storage/cluster." + _options._cluster.getName() + "/fleetcontroller/*";
        slobrok::api::MirrorAPI::SpecList specs = slobrok->lookup(mask);
        if (specs.size() == 0) {
            std::cerr << "No fleet controller could be found for '"
                      << mask << ".\n";
            return 1;
        }
        std::sort(specs.begin(), specs.end(), Sorter());
        LOG(debug, "Found fleet controller %s - %s\n",
            specs.front().first.c_str(), specs.front().second.c_str());
        FRT_Target *target = supervisor.supervisor().GetTarget(specs.front().second.c_str());
        if (!_options._nonfriendlyOutput && _options._mode == GETNODESTATE)
        {
            std::cerr <<
"Shows the various states of one or more nodes in a Vespa Storage cluster.\n"
"There exist three different type of node states. They are:\n"
"\n"
"  Reported state - The state reported to the fleet controller by the node.\n"
"  Wanted state   - The state administrators want the node to be in.\n"
"  Current state  - The state of a given node in the current cluster state.\n"
"                   This is the state all the other nodes know about. This\n"
"                   state is a product of the other two states and fleet\n"
"                   controller logic to keep the cluster stable.\n"
"\n"
"For more information about states of Vespa storage nodes, refer to\n"
                    << _options._doc << "\n\n";
        }
        bool failed = false;
        for (int i=0; i<2; ++i) {
            std::string nodeType(_options._nodeType);
            if ((_options._nodeType != "" || _options._mode == GETCLUSTERSTATE)
                && i > 0)
            {
                break;
            }
            if (_options._nodeType == "") {
                nodeType = (i == 0 ? "storage" : "distributor");
            }
            std::vector<uint32_t> indexes;
            if (_options._nodeIndex != 0xffffffff
                || _options._mode == GETCLUSTERSTATE)
            {
                indexes.push_back(_options._nodeIndex);
            } else {
                std::string hostname(vespa::Defaults::vespaHostname());
                FRT_RPCRequest* req = supervisor.supervisor().AllocRPCRequest();
                req->SetMethodName("getNodeList");
                target->InvokeSync(req, 10.0);
                std::string prefix = _options._cluster.getConfigId() + "/" + nodeType + "/";
                failed = (req->GetErrorCode() != FRTE_NO_ERROR);
                if (failed) {
                    std::cerr << "Failed RPC call against "
                              << specs.front().second << ".\nError "
                              << req->GetErrorCode() << " : "
                              << req->GetErrorMessage() << "\n";
                    break;
                }
                uint32_t arraySize(
                        req->GetReturn()->GetValue(0)._string_array._len);
                for (uint32_t j=0; j<arraySize; ++j) {
                    std::string slobrokAddress(req->GetReturn()->GetValue(0)
                                                    ._string_array._pt[j]._str);
                    std::string rpcAddress(req->GetReturn()->GetValue(1)
                                                    ._string_array._pt[j]._str);
                    std::string::size_type pos = slobrokAddress.find(prefix);
                    std::string::size_type match = rpcAddress.find(hostname);
                    //std::cerr << "1. '" << slobrokAddress << "'.\n";
                    //std::cerr << "2. '" << rpcAddress << "'.\n";
                    if (pos != std::string::npos && match != std::string::npos)
                    {
                        uint32_t index = atoi(slobrokAddress.substr(
                                    pos + prefix.size()).c_str());
                        indexes.push_back(index);
                    }
                }
            }
            if (indexes.size() == 0) {
                std::cerr << "Could not find any storage or distributor "
                          << "services on this node.\n"
                          << "Specify node index with --index parameter.\n";
                failed = true;
                break;
            }
            for (uint32_t j=0; j<indexes.size(); ++j) {
                FRT_RPCRequest* req = supervisor.supervisor().AllocRPCRequest();
                if (_options._mode == GETNODESTATE) {
                    req->SetMethodName("getNodeState");
                    req->GetParams()->AddString(nodeType.c_str());
                    req->GetParams()->AddInt32(indexes[j]);
                } else if (_options._mode == SETNODESTATE) {
                    req->SetMethodName("setNodeState");
                    std::ostringstream address;
                    address << _options._cluster.getConfigId() << "/"
                            << nodeType << "/" << indexes[j];
                    lib::NodeState ns(lib::NodeType::get(nodeType),
                                         *getState(_options._state));
                    ns.setDescription(_options._message);
                    req->GetParams()->AddString(address.str().c_str());
                    req->GetParams()->AddString(ns.toString(false).c_str());
                } else {
                    req->SetMethodName("getSystemState");
                }
                target->InvokeSync(req, 10.0);
                failed = (req->GetErrorCode() != FRTE_NO_ERROR);
                if (failed) {
                    std::cerr << "Failed RPC call against "
                              << specs.front().second
                              << ".\nError " << req->GetErrorCode() << " : "
                              << req->GetErrorMessage() << "\n";
                    break;
                } else {
                    bool friendly = !_options._nonfriendlyOutput;
                    if (_options._mode == GETNODESTATE) {
                        lib::NodeState current(
                                req->GetReturn()->GetValue(0)._string._str);
                        lib::NodeState reported(
                                req->GetReturn()->GetValue(1)._string._str);
                        lib::NodeState wanted(
                                req->GetReturn()->GetValue(2)._string._str);
                        std::cout << "Node state of "
                                  << _options._cluster.getConfigId() << "/" << nodeType
                                  << "/" << indexes[j];
                        std::cout << "\nCurrent state: ";
                        current.print(std::cout, friendly, "   ");
                        std::cout << "\nReported state ";
                        reported.print(std::cout, friendly, "   ");
                        std::cout << "\nWanted state:  ";
                        wanted.print(std::cout, friendly, "   ");
                        std::cout << "\n\n";
                    } else if (_options._mode == SETNODESTATE) {
                        std::string result(
                                req->GetReturn()->GetValue(0)._string._str);
                        if (result != "") {
                            std::cout << result << "\n";
                        }
                    } else {
                        std::string rawstate(
                                req->GetReturn()->GetValue(1)._string._str);
                        lib::ClusterState state(rawstate);
                        if (friendly) {
                            state.printStateGroupwise(std::cout, distribution,
                                                      true, "");
                        } else {
                            std::cout << rawstate << "\n";
                        }
                        std::cout << "\n";
                    }
                }
                req->SubRef();
            }
        }
        target->SubRef();
        return (failed ? 1 : 0);
    }
};

} // storage

int
main(int argc, char **argv)
{
    assert(argc > 0);
    storage::StateApp client(argv[0]);
    return client.Entry(argc, argv);
}

