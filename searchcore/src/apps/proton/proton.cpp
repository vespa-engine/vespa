// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton.h>
#include <vespa/storage/storageserver/storagenode.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <vespa/fastos/file.h>
#include <filesystem>
#include <iostream>
#include <thread>
#include <fcntl.h>

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

class App
{
private:
    static void setupSignals();
    static void setup_fadvise();
    Params parseParams(int argc, char **argv);
    void startAndRun(FastOS_ThreadPool & threadPool, FNET_Transport & transport, int argc, char **argv);
public:
    int main(int argc, char **argv);
};

void
App::setupSignals()
{
    SIG::PIPE.ignore();
    SIG::INT.hook();
    SIG::TERM.hook();
    SIG::enable_cross_thread_stack_tracing();
}

void
App::setup_fadvise()
{
#ifdef __linux__
    char * fadvise = getenv("VESPA_FADVISE_OPTIONS");
    if (fadvise != nullptr) {
        int fadviseOptions(0);
        if (strstr(fadvise, "SEQUENTIAL")) { fadviseOptions |= POSIX_FADV_SEQUENTIAL; }
        if (strstr(fadvise, "RANDOM"))     { fadviseOptions |= POSIX_FADV_RANDOM; }
        if (strstr(fadvise, "WILLNEED"))   { fadviseOptions |= POSIX_FADV_WILLNEED; }
        if (strstr(fadvise, "DONTNEED"))   { fadviseOptions |= POSIX_FADV_DONTNEED; }
        if (strstr(fadvise, "NOREUSE"))    { fadviseOptions |= POSIX_FADV_NOREUSE; }
        FastOS_FileInterface::setDefaultFAdviseOptions(fadviseOptions);
    }
#endif
}

Params
App::parseParams(int argc, char **argv)
{
    Params params;
    vespalib::ProgramOptions parser(argc, argv);
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

fnet::TransportConfig
buildTransportConfig() {
    uint32_t numProcs = std::thread::hardware_concurrency();
    return fnet::TransportConfig(std::max(1u, std::min(4u, numProcs/8)));
}

class Transport {
public:
    Transport(const fnet::TransportConfig & config, FastOS_ThreadPool & threadPool)
        : _transport(config)
    {
        _transport.Start(&threadPool);
    }
    ~Transport() {
        _transport.ShutDown(true);
    }
    FNET_Transport & transport() { return _transport; }
private:
    FNET_Transport _transport;
};

}

void
App::startAndRun(FastOS_ThreadPool & threadPool, FNET_Transport & transport, int argc, char **argv) {
    Params params = parseParams(argc, argv);
    LOG(debug, "identity: '%s'", params.identity.c_str());
    LOG(debug, "serviceidentity: '%s'", params.serviceidentity.c_str());
    LOG(debug, "subscribeTimeout: '%" PRIu64 "'", params.subscribeTimeout);
    std::chrono::milliseconds subscribeTimeout(params.subscribeTimeout);

    config::ConfigServerSpec configServerSpec(transport);
    config::ConfigUri identityUri(params.identity, std::make_shared<config::ConfigContext>(configServerSpec));
    auto protonUP = std::make_unique<proton::Proton>(threadPool, transport, identityUri,
                                                     (argc > 0) ? argv[0] : "proton", subscribeTimeout);
    proton::Proton & proton = *protonUP;
    proton::BootstrapConfig::SP configSnapshot = proton.init();
    if (proton.hasAbortedInit()) {
        EV_STOPPING("proton", "shutdown after aborted init");
    } else {
        const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
        vespalib::string basedir = protonConfig.basedir;
        std::filesystem::create_directories(std::filesystem::path(basedir));
        {
            ExitOnSignal exit_on_signal;
            proton.init(configSnapshot);
        }
        configSnapshot.reset();
        std::unique_ptr<ProtonServiceLayerProcess> spiProton;

        if ( ! params.serviceidentity.empty()) {
            spiProton = std::make_unique<ProtonServiceLayerProcess>(identityUri.createWithNewId(params.serviceidentity), proton);
            spiProton->setupConfig(subscribeTimeout);
            spiProton->createNode();
            EV_STARTED("servicelayer");
        } else {
            proton.getMetricManager().init(identityUri, threadPool);
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
}

int
App::main(int argc, char **argv)
{
    try {
        setupSignals();
        setup_fadvise();
        FastOS_ThreadPool threadPool(128_Ki);
        Transport transport(buildTransportConfig(), threadPool);
        startAndRun(threadPool, transport.transport(), argc, argv);
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
    LOG(debug, "Fully stopped, all destructors run.)");
    return 0;
}

int main(int argc, char **argv) {
    App app;
    return app.main(argc, argv);
}
