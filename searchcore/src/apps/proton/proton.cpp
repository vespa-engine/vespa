// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton.h>
#include <vespa/storage/storageserver/storagenode.h>
#include <vespa/searchlib/util/statefile.h>
#include <vespa/searchlib/util/sigbushandler.h>
#include <vespa/searchlib/util/ioerrorhandler.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/fastos/app.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("proton");

typedef vespalib::SignalHandler SIG;
using vespa::config::search::core::ProtonConfig;

struct Params
{
    std::string identity;
    std::string serviceidentity;
    uint64_t subscribeTimeout;
    ~Params();
};

Params::~Params() {}

class App : public FastOS_Application
{
private:
    void setupSignals();
    Params parseParams();
public:
    int Main() override;
};

void
App::setupSignals()
{
    SIG::PIPE.ignore();
    SIG::INT.hook();
    SIG::TERM.hook();
}

namespace {

vespalib::string
getStateString(search::StateFile &stateFile)
{
    std::vector<char> buf;
    stateFile.readState(buf);
    if (!buf.empty() && buf[buf.size() - 1] == '\n')
        buf.resize(buf.size() - 1);
    return vespalib::string(buf.begin(), buf.end());
}

bool stateIsDown(const vespalib::string &stateString)
{
    return strstr(stateString.c_str(), "state=down") != nullptr;
}

}

Params
App::parseParams()
{
    Params params;
    vespalib::ProgramOptions parser(_argc, _argv);
    parser.setSyntaxMessage("proton -- the nextgen search core");
    parser.addOption("identity", params.identity, "Node identity and config id");
    std::string empty("");
    parser.addOption("serviceidentity", params.serviceidentity, empty, "Service node identity and config id");
    parser.addOption("subscribeTimeout", params.subscribeTimeout, 600000UL, "Initial config subscribe timeout");
    try {
        parser.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException &e) {
        parser.writeSyntaxPage(std::cerr);
        throw;
    }
    return params;
}


#include "downpersistence.h"

using storage::spi::PersistenceProvider;
using storage::spi::DownPersistence;

#include <vespa/storageserver/app/servicelayerprocess.h>

class ProtonServiceLayerProcess : public storage::ServiceLayerProcess {
    proton::Proton & _proton;
    metrics::MetricManager* _metricManager;
    PersistenceProvider *_downPersistence;

public:
    ProtonServiceLayerProcess(const config::ConfigUri & configUri,
                              proton::Proton & proton,
                              PersistenceProvider *downPersistence);
    ~ProtonServiceLayerProcess() { shutdown(); }

    void shutdown() override;
    void setupProvider() override;
    storage::spi::PersistenceProvider& getProvider() override;

    void setMetricManager(metrics::MetricManager& mm) {
        // The service layer will call init(...) and stop() on the metric
        // manager provided. Current design is that rather than depending
        // on every component properly unregistering metrics and update
        // hooks, the service layer stops metric manager ahead of shutting
        // down component.
        _metricManager = &mm;
    }
    int64_t getGeneration() const override;
};

ProtonServiceLayerProcess::ProtonServiceLayerProcess(const config::ConfigUri &
                                                     configUri,
                                                     proton::Proton & proton,
                                                     PersistenceProvider *
                                                     downPersistence)
    : ServiceLayerProcess(configUri),
      _proton(proton),
      _metricManager(0),
      _downPersistence(downPersistence)
{
    if (!downPersistence) {
        setMetricManager(_proton.getMetricManager());
    }
}

void
ProtonServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
}

void
ProtonServiceLayerProcess::setupProvider()
{
    if (_metricManager != 0) {
        _context.getComponentRegister().setMetricManager(*_metricManager);
    }
}

storage::spi::PersistenceProvider &
ProtonServiceLayerProcess::getProvider()
{
    return _downPersistence ? *_downPersistence : _proton.getPersistence();
}

int64_t
ProtonServiceLayerProcess::getGeneration() const
{
    int64_t slGen = storage::ServiceLayerProcess::getGeneration();
    int64_t protonGen = _proton.getConfigGeneration();
    return std::min(slGen, protonGen);
}

int
App::Main()
{
    proton::Proton::UP protonUP;
    try {
        setupSignals();
        Params params = parseParams();
        LOG(debug, "identity: '%s'", params.identity.c_str());
        LOG(debug, "serviceidentity: '%s'", params.serviceidentity.c_str());
        LOG(debug, "subscribeTimeout: '%" PRIu64 "'", params.subscribeTimeout);
        std::unique_ptr<search::StateFile> stateFile;
        std::unique_ptr<search::SigBusHandler> sigBusHandler;
        std::unique_ptr<search::IOErrorHandler> ioErrorHandler;
        protonUP = std::make_unique<proton::Proton>(params.identity, _argc > 0 ? _argv[0] : "proton", params.subscribeTimeout);
        proton::Proton & proton = *protonUP;
        proton::BootstrapConfig::SP configSnapshot = proton.init();
        if (proton.hasAbortedInit()) {
            EV_STOPPING("proton", "shutdown after aborted init");
        } else {
            const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
            vespalib::string basedir = protonConfig.basedir;
            bool stopOnIOErrors = protonConfig.stoponioerrors;
            vespalib::mkdir(basedir, true);
            // TODO: Test that we can write to new file in directory
            stateFile.reset(new search::StateFile(basedir + "/state"));
            int stateGen = stateFile->getGen();
            vespalib::string stateString = getStateString(*stateFile);
            std::unique_ptr<PersistenceProvider> downPersistence;
            if (stateIsDown(stateString)) {
                LOG(error, "proton state string is %s", stateString.c_str());
                if (stopOnIOErrors) {
                    if ( !params.serviceidentity.empty()) {
                        downPersistence.reset(new DownPersistence("proton state string is " + stateString));
                    } else {
                        LOG(info, "Sleeping 900 seconds due to proton state");
                        int sleepLeft = 900;
                        while (!(SIG::INT.check() || SIG::TERM.check()) && sleepLeft > 0) {
                            FastOS_Thread::Sleep(1000);
                            --sleepLeft;
                        }
                        EV_STOPPING("proton", "shutdown after stop on io errors");
                        return 1;
                    }
                }
            }
            sigBusHandler.reset(new search::SigBusHandler(stateFile.get()));
            ioErrorHandler.reset(new search::IOErrorHandler(stateFile.get()));
            if ( ! params.serviceidentity.empty()) {
                proton.getMetricManager().init(params.serviceidentity, proton.getThreadPool());
            } else {
                proton.getMetricManager().init(params.identity, proton.getThreadPool());
            }
            if (!downPersistence) {
                proton.init(configSnapshot);
            }
            configSnapshot.reset();
            std::unique_ptr<ProtonServiceLayerProcess> spiProton;
            if ( ! params.serviceidentity.empty()) {
                spiProton.reset(new ProtonServiceLayerProcess(params.serviceidentity, proton, downPersistence.get()));
                spiProton->setupConfig(params.subscribeTimeout);
                spiProton->createNode();
                EV_STARTED("servicelayer");
            }
            EV_STARTED("proton");
            while (!(SIG::INT.check() || SIG::TERM.check() || (spiProton && spiProton->getNode().attemptedStopped()))) {
                FastOS_Thread::Sleep(1000);
                if (spiProton && spiProton->configUpdated()) {
                    storage::ResumeGuard guard(spiProton->getNode().pause());
                    spiProton->updateConfig();
                }
                if (stateGen != stateFile->getGen()) {
                    stateGen = stateFile->getGen();
                    stateString = getStateString(*stateFile);
                    if (stateIsDown(stateString)) {
                        LOG(error, "proton state string is %s", stateString.c_str());
                        if (stopOnIOErrors) {
                            if (spiProton) {
                                // report down state to cluster controller.
                                spiProton->getNode().notifyPartitionDown(0, "proton state string is " + stateString);
                                FastOS_Thread::Sleep(1000);
                            }
                            EV_STOPPING("proton", "shutdown after new stop on io errors");
                            return 1;
                        }
                    }
                }
            }
            if (spiProton) {
                spiProton->getNode().requestShutdown("controlled shutdown");
                spiProton->shutdown();
                EV_STOPPING("servicelayer", "clean shutdown");
            }
            protonUP.reset();
            EV_STOPPING("proton", "clean shutdown");
        }
    } catch (const vespalib::InvalidCommandLineArgumentsException &e) {
        LOG(warning, "Invalid commandline arguments: '%s'", e.what());
        return 1;
    } catch (const config::ConfigTimeoutException &e) {
        LOG(warning, "Error subscribing to initial config: '%s'", e.what());
        return 1;
    } catch (const vespalib::PortListenException &e) {
        LOG(warning, "Failed listening to a network port(%d) with protocol(%s): '%s'",
                   e.get_port(), e.get_protocol().c_str(), e.what());
        return 1;
    } catch (const vespalib::NetworkSetupFailureException & e) {
        LOG(warning, "Network failure: '%s'", e.what());
        return 1;
    } catch (const vespalib::IllegalStateException & e) {
        LOG(error, "Unknown IllegalStateException: '%s'", e.what());
        throw;
    }
    protonUP.reset();
    LOG(debug, "Fully stopped, all destructors run.)");
    return 0;
}

int main(int argc, char **argv) {
    App app;
    return app.Entry(argc, argv);
}
