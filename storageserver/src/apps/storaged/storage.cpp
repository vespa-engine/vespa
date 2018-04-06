// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <csignal>
#include <vespa/persistence/spi/exceptions.h>
#include <vespa/storage/storageutil/utils.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include "forcelink.h"
#include <vespa/storageserver/app/dummyservicelayerprocess.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/shutdownguard.h>
#include <iostream>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("vds.application");

namespace storage {

namespace {

Process::UP createProcess(vespalib::stringref configId) {
    // FIXME: Rewrite parameter to config uri and pass when all subsequent configs are converted.
    config::ConfigUri uri(configId);
    std::unique_ptr<vespa::config::content::core::StorServerConfig> serverConfig = config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(uri.getConfigId(), uri.getContext());
    if (serverConfig->isDistributor) {
        return Process::UP(new DistributorProcess(configId));
    } else switch (serverConfig->persistenceProvider.type) {
        case vespa::config::content::core::StorServerConfig::PersistenceProvider::STORAGE:
        case vespa::config::content::core::StorServerConfig::PersistenceProvider::DUMMY:
            return Process::UP(new DummyServiceLayerProcess(configId));
        default:
            throw vespalib::IllegalStateException("Unknown persistence provider.", VESPA_STRLOC);
    }
}

} // End of anonymous namespace

class StorageApp : public FastOS_Application,
                   private vespalib::ProgramOptions
{
    std::string       _configId;
    bool              _showSyntax;
    uint32_t          _maxShutdownTime;
    int               _lastSignal;
    vespalib::Monitor _signalLock;
    Process::UP       _process;

public:
    StorageApp();
    ~StorageApp();

    void handleSignal(int signal) {
        LOG(info, "Got signal %d, waiting for lock", signal);
        vespalib::MonitorGuard sync(_signalLock);

        LOG(info, "Got lock for signal %d", signal);
        _lastSignal = signal;
        sync.signal();
    }
    void handleSignals();

private:
    bool Init() override;
    int Main() override;
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

StorageApp::~StorageApp() {}

bool StorageApp::Init()
{
    FastOS_Application::Init();
    setCommandLineArguments(
            FastOS_Application::_argc, FastOS_Application::_argv);
    try{
        parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        std::cerr << e.getMessage() << "\n\n";
        writeSyntaxPage(std::cerr);
        exit(EXIT_FAILURE);
    }
    if (_showSyntax) {
        writeSyntaxPage(std::cerr);
        exit(0);
    }
    return true;
}

namespace {
    storage::StorageApp *sigtramp = 0;
    uint32_t _G_signalCount = 0;

    void killHandler(int sig) {
        if (_G_signalCount == 0) {
            _G_signalCount++;
            if (sigtramp == 0) _exit(EXIT_FAILURE);
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
        sigaction (SIGTERM, &usr_action, NULL);
        sigaction (SIGINT, &usr_action, NULL);
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

int StorageApp::Main()
{
    try{
        _process = createProcess(_configId);
        _process->setupConfig(600000);
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
            LOG(debug, "Config updated. Progagating config updates");
            ResumeGuard guard(_process->getNode().pause());
            _process->updateConfig();
        }
            // Wait until we get a kill signal.
        vespalib::MonitorGuard lock(_signalLock);
        lock.wait(1000);
        handleSignals();
    }
    LOG(debug, "Server was attempted stopped, shutting down");
    // Create guard that will forcifully kill storage if destruction takes longer
    // time than given timeout.
    vespalib::ShutdownGuard shutdownGuard(_maxShutdownTime);
    LOG(debug, "Attempting proper shutdown");
    _process.reset(0);
    LOG(debug, "Completed controlled shutdown.");
    return 0;
}

} // storage

int main(int argc, char **argv)
{
    storage::StorageApp app;
    storage::sigtramp = &app;
    int retval = app.Entry(argc,argv);
    storage::sigtramp = NULL;
    LOG(debug, "Exiting");
    return retval;
}
