// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::StorageApp
 * \ingroup serverapp
 *
 * \brief The storage daemon application.
 *
 * This code is NOT unit tested and should be as minimal as possible.
 *
 * It should handle process signals and have the main method for the
 * application, but as little as possible else.
 */

#include "forcelink.h"
#include <vespa/persistence/spi/exceptions.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/storageserver/app/dummyservicelayerprocess.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/shutdownguard.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <csignal>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP("vds.application");

using namespace std::chrono_literals;
namespace storage {

namespace {

Process::UP createProcess(vespalib::stringref configId) {
    // FIXME: Rewrite parameter to config uri and pass when all subsequent configs are converted.
    config::ConfigUri uri(configId);
    std::unique_ptr<vespa::config::content::core::StorServerConfig> serverConfig = config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(uri.getConfigId(), uri.getContext());
    if (serverConfig->isDistributor) {
        return std::make_unique<DistributorProcess>(uri);
    } else switch (serverConfig->persistenceProvider.type) {
        case vespa::config::content::core::StorServerConfig::PersistenceProvider::Type::STORAGE:
        case vespa::config::content::core::StorServerConfig::PersistenceProvider::Type::DUMMY:
            return std::make_unique<DummyServiceLayerProcess>(uri);
        default:
            throw vespalib::IllegalStateException("Unknown persistence provider.", VESPA_STRLOC);
    }
}

} // End of anonymous namespace

class StorageApp : private vespalib::ProgramOptions
{
    std::string             _configId;
    bool                    _showSyntax;
    uint32_t                _maxShutdownTime;
    int                     _lastSignal;
    std::mutex              _signalLock;
    std::condition_variable _signalCond;
    Process::UP             _process;

public:
    StorageApp();
    ~StorageApp() override;

    void handleSignal(int signal) {
        _lastSignal = signal;
        _signalCond.notify_one();
    }
    void handleSignals();
    int main(int argc, char **argv);

private:
    vespalib::duration getMaxShutDownTime() { return std::chrono::milliseconds(_maxShutdownTime); }
    bool init(int argc, char **argv);
    bool gotSignal() { return _lastSignal != 0; }
};

StorageApp::StorageApp()
    : _showSyntax(false), _maxShutdownTime(120000), _lastSignal(0), _signalLock()
{
    setSyntaxMessage(
        "This is the main daemon used to start the storage nodes. The same "
        "actual binary is used for both storage and distributor nodes, but "
        "it is duplicated when installing, such that one can hotfix a "
        "distributor bug without restarting storage nodes.");
    addOption("c config-id", _configId,
        "The config identifier this storage node should use to request "
        "config. This identifier specifies whether the binary will behave "
        "as a storage or distributor, what cluster it belongs to, and the "
        "index it has in the cluster.");
    addOption("h help", _showSyntax, false, "Show this syntax help page.");
    addOption("t maxshutdowntime", _maxShutdownTime, uint32_t(120000),
        "Maximum amount of milliseconds we allow proper shutdown to run before "
        "abruptly killing the process.");
}

StorageApp::~StorageApp() = default;

bool StorageApp::init(int argc, char **argv)
{
    setCommandLineArguments(argc, argv);
    try{
        parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        std::cerr << e.getMessage() << "\n\n";
        writeSyntaxPage(std::cerr);
        std::_Exit(EXIT_FAILURE);
    }
    if (_showSyntax) {
        writeSyntaxPage(std::cerr);
        std::_Exit(0);
    }
    return true;
}

namespace {
    storage::StorageApp *sigtramp = nullptr;
    uint32_t _G_signalCount = 0;

    void killHandler(int sig) {
        if (_G_signalCount == 0) {
            _G_signalCount++;
            if (sigtramp == nullptr) _exit(EXIT_FAILURE);
            // note: this is not totally safe, sigtramp is not protected by a lock
            sigtramp->handleSignal(sig);
        } else if (_G_signalCount > 2) {
            fprintf(stderr, "Received another shutdown signal %u while "
                    "shutdown in progress (count=%u)\n",
                    sig, _G_signalCount);
        }
    }

    void setupKillHandler() {
        struct sigaction usr_action;
        sigset_t block_mask;

        /* Establish the signal handler. */
        sigfillset (&block_mask);
        usr_action.sa_handler = killHandler;
        usr_action.sa_mask = block_mask;
        usr_action.sa_flags = 0;
        sigaction (SIGTERM, &usr_action, nullptr);
        sigaction (SIGINT, &usr_action, nullptr);
    }
}

void StorageApp::handleSignals()
{
    if (gotSignal()) {
        int signal = _lastSignal;
        LOG(debug, "starting controlled shutdown of storage "
                   "(received signal %d)", signal);
        _process->getNode().requestShutdown("controlled shutdown");
    }
}

int StorageApp::main(int argc, char **argv)
{
    if (!init(argc, argv)) {
        return 255;
    }
    try{
        _process = createProcess(_configId);
        _process->setupConfig(600000ms);
        _process->createNode();
    } catch (const spi::HandledException & e) {
        LOG(warning, "Died due to known cause: %s", e.what());
        return 1;
    } catch (const vespalib::NetworkSetupFailureException & e) {
        LOG(warning, "Network failure: '%s'", e.what());
        return 1;
    } catch (const vespalib::IllegalStateException & e) {
        LOG(error, "Unknown IllegalStateException: '%s'", e.what());
        return 1;
    } catch (const vespalib::Exception & e) {
        LOG(error, "Caught exception when starting: %s", e.what());
        return 1;
    }

    // Not setting up kill handlers before storage is up. Before that
    // we can just die quickly with default handlers.
    LOG(debug, "Node created. Setting up kill handler.");
    setupKillHandler();

    // main loop - wait for termination signal
    while (!_process->getNode().attemptedStopped()) {
        if (_process->configUpdated()) {
            LOG(debug, "Config updated. Propagating config updates");
            ResumeGuard guard(_process->getNode().pause());
            _process->updateConfig();
        }
        // Wait until we get a kill signal.
        std::unique_lock guard(_signalLock);
        _signalCond.wait_for(guard, 1000ms);
        handleSignals();
    }
    LOG(debug, "Server was attempted stopped, shutting down");
    // Create guard that will forcefully kill storage if destruction takes longer
    // time than given timeout.
    vespalib::ShutdownGuard shutdownGuard(getMaxShutDownTime());
    LOG(debug, "Attempting proper shutdown");
    _process.reset();
    LOG(debug, "Completed controlled shutdown.");
    return 0;
}

} // storage

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    vespalib::SignalHandler::enable_cross_thread_stack_tracing();
    storage::StorageApp app;
    storage::sigtramp = &app;
    int retval = app.main(argc,argv);
    storage::sigtramp = nullptr;
    LOG(debug, "Exiting");
    return retval;
}
