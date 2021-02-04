// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton.h>
#include <vespa/storage/storageserver/storagenode.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/fastos/app.h>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("proton");

typedef vespalib::SignalHandler SIG;
using vespa::config::search::core::ProtonConfig;

struct Params
{
    std::string identity;
    std::string serviceidentity;
    uint64_t subscribeTimeout;
    Params();
    ~Params();
};

Params::Params()
    : identity(),
      serviceidentity(),
      subscribeTimeout(60)
{}
Params::~Params() = default;

class App : public FastOS_Application
{
private:
    static void setupSignals();
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

Params
App::parseParams()
{
    Params params;
    vespalib::ProgramOptions parser(_argc, _argv);
    parser.setSyntaxMessage("proton -- the nextgen search core");
    parser.addOption("identity", params.identity, "Node identity and config id");
    std::string empty;
    parser.addOption("serviceidentity", params.serviceidentity, empty, "Service node identity and config id");
    parser.addOption("subscribeTimeout", params.subscribeTimeout, UINT64_C(600000), "Initial config subscribe timeout");
    try {
        parser.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException &e) {
        parser.writeSyntaxPage(std::cerr);
        throw;
    }
    return params;
}


using storage::spi::PersistenceProvider;

#include <vespa/storageserver/app/servicelayerprocess.h>

class ProtonServiceLayerProcess : public storage::ServiceLayerProcess {
    proton::Proton & _proton;
    metrics::MetricManager* _metricManager;

public:
    ProtonServiceLayerProcess(const config::ConfigUri & configUri,
                              proton::Proton & proton);
    ~ProtonServiceLayerProcess() override { shutdown(); }

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

ProtonServiceLayerProcess::ProtonServiceLayerProcess(const config::ConfigUri & configUri,
                                                     proton::Proton & proton)
    : ServiceLayerProcess(configUri),
      _proton(proton),
      _metricManager(nullptr)
{
    setMetricManager(_proton.getMetricManager());
}

void
ProtonServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
}

void
ProtonServiceLayerProcess::setupProvider()
{
    if (_metricManager != nullptr) {
        _context.getComponentRegister().setMetricManager(*_metricManager);
    }
}

storage::spi::PersistenceProvider &
ProtonServiceLayerProcess::getProvider()
{
    return _proton.getPersistence();
}

int64_t
ProtonServiceLayerProcess::getGeneration() const
{
    int64_t slGen = storage::ServiceLayerProcess::getGeneration();
    int64_t protonGen = _proton.getConfigGeneration();
    return std::min(slGen, protonGen);
}

namespace {

class ExitOnSignal {
    std::atomic<bool> _stop;
    std::thread       _thread;
    
public:
    ExitOnSignal();
    ~ExitOnSignal();
    void operator()();
};

ExitOnSignal::ExitOnSignal()
    : _stop(false),
      _thread()
{
    _thread = std::thread(std::ref(*this));
}

ExitOnSignal::~ExitOnSignal()
{
    _stop.store(true, std::memory_order_relaxed);
    _thread.join();
}

void
ExitOnSignal::operator()()
{
    while (!_stop.load(std::memory_order_relaxed)) {
        if (SIG::INT.check() || SIG::TERM.check()) {
            EV_STOPPING("proton", "unclean shutdown after interrupted init");
            std::_Exit(0);
        }
        std::this_thread::sleep_for(100ms);
    }
}

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
        protonUP = std::make_unique<proton::Proton>(params.identity, _argc > 0 ? _argv[0] : "proton", std::chrono::milliseconds(params.subscribeTimeout));
        proton::Proton & proton = *protonUP;
        proton::BootstrapConfig::SP configSnapshot = proton.init();
        if (proton.hasAbortedInit()) {
            EV_STOPPING("proton", "shutdown after aborted init");
        } else {
            const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
            vespalib::string basedir = protonConfig.basedir;
            vespalib::mkdir(basedir, true);
            if ( ! params.serviceidentity.empty()) {
                proton.getMetricManager().init(params.serviceidentity, proton.getThreadPool());
            } else {
                proton.getMetricManager().init(params.identity, proton.getThreadPool());
            }
            {
                ExitOnSignal exit_on_signal;
                proton.init(configSnapshot);
            }
            configSnapshot.reset();
            std::unique_ptr<ProtonServiceLayerProcess> spiProton;
            if ( ! params.serviceidentity.empty()) {
                spiProton = std::make_unique<ProtonServiceLayerProcess>(params.serviceidentity, proton);
                spiProton->setupConfig(std::chrono::milliseconds(params.subscribeTimeout));
                spiProton->createNode();
                EV_STARTED("servicelayer");
            }
            EV_STARTED("proton");
            while (!(SIG::INT.check() || SIG::TERM.check() || (spiProton && spiProton->getNode().attemptedStopped()))) {
                std::this_thread::sleep_for(1000ms);
                if (spiProton && spiProton->configUpdated()) {
                    storage::ResumeGuard guard(spiProton->getNode().pause());
                    spiProton->updateConfig();
                }
            }
            // Ensure metric manager and state server are shut down before we start tearing
            // down any service layer components that they may end up transitively using.
            protonUP->shutdown_config_fetching_and_state_exposing_components_once();
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
    } catch (const config::InvalidConfigException & e) {
        LOG(warning, "Invalid config failure: '%s'", e.what());
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
