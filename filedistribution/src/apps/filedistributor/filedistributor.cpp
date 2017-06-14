// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root

#include <vespa/filedistribution/distributor/config-filedistributor.h>
#include <vespa/filedistribution/model/config-filereferences.h>

#include <vespa/filedistribution/distributor/filedistributortrackerimpl.h>
#include <vespa/filedistribution/distributor/filedownloadermanager.h>
#include <vespa/filedistribution/distributor/signalhandling.h>
#include <vespa/filedistribution/distributor/state_server_impl.h>

#include <vespa/filedistribution/model/filedistributionmodelimpl.h>
#include <vespa/filedistribution/rpc/filedistributorrpc.h>
#include <vespa/filedistribution/common/componentsdeleter.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config-zookeepers.h>
#include <vespa/fastos/app.h>
#include <boost/program_options.hpp>

namespace {
const char* programName = "filedistributor";
}

#include <vespa/log/log.h>
LOG_SETUP(programName);

using namespace std::literals;

using namespace filedistribution;
using cloud::config::ZookeepersConfig;
using cloud::config::filedistribution::FiledistributorConfig;
using cloud::config::filedistribution::FiledistributorrpcConfig;

class FileDistributor : public config::IFetcherCallback<ZookeepersConfig>,
                        public config::IFetcherCallback<FiledistributorConfig>,
                        public config::IFetcherCallback<FiledistributorrpcConfig>,
                        public config::IGenerationCallback
{
    class Components {
        ComponentsDeleter _componentsDeleter;
    public:
        const std::shared_ptr<ZKFacade> _zk;
        const std::shared_ptr<FileDistributionModelImpl> _model;
        const std::shared_ptr<FileDistributorTrackerImpl> _tracker;
        const std::shared_ptr<FileDownloader> _downloader;
        const FileDownloaderManager::SP _manager;
        const FileDistributorRPC::SP _rpcHandler;
        const std::shared_ptr<StateServerImpl> _stateServer;

    private:
        class GuardedThread {
        public:
            GuardedThread(const GuardedThread &) = delete;
            GuardedThread & operator = (const GuardedThread &) = delete;
            GuardedThread(const std::shared_ptr<FileDownloader> & downloader) :
                _downloader(downloader),
                _thread([downloader=_downloader] () { downloader->runEventLoop(); })
            { }
            ~GuardedThread() {
                _downloader->close();
                if (_thread.joinable()) {
                    _thread.join();
                }
                if ( !_downloader->drained() ) {
                    LOG(error, "The filedownloader did not drain fully. We will just exit quickly and let a restart repair it for us.");
                    std::quick_exit(67);
                }
            }
        private:
            std::shared_ptr<FileDownloader> _downloader;
            std::thread                     _thread;
        };
        std::unique_ptr<GuardedThread> _downloaderEventLoopThread;
        config::ConfigFetcher _configFetcher;

        template <class T>
        typename std::shared_ptr<T> track(T* component) {
            return _componentsDeleter.track(component);
        }

    public:
        Components(const Components &) = delete;
        Components & operator = (const Components &) = delete;

        Components(const config::ConfigUri & configUri,
                   const ZookeepersConfig& zooKeepersConfig,
                   const FiledistributorConfig& fileDistributorConfig,
                   const FiledistributorrpcConfig& rpcConfig)
            :_zk(track(new ZKFacade(zooKeepersConfig.zookeeperserverlist, false))),
             _model(track(new FileDistributionModelImpl(fileDistributorConfig.hostname, fileDistributorConfig.torrentport, _zk))),
             _tracker(track(new FileDistributorTrackerImpl(_model))),
             _downloader(track(new FileDownloader(_tracker, fileDistributorConfig.hostname, fileDistributorConfig.torrentport, Path(fileDistributorConfig.filedbpath)))),
             _manager(track(new FileDownloaderManager(_downloader, _model))),
             _rpcHandler(track(new FileDistributorRPC(rpcConfig.connectionspec, _manager))),
             _stateServer(track(new StateServerImpl(fileDistributorConfig.stateport))),
             _downloaderEventLoopThread(),
             _configFetcher(configUri.getContext())
        {
            _downloaderEventLoopThread = std::make_unique<GuardedThread>(_downloader);
            _manager->start();
            _rpcHandler->start();

            _tracker->setDownloader(_downloader);
            _configFetcher.subscribe<FilereferencesConfig>(configUri.getConfigId(), _model.get());
            _configFetcher.start();
            updatedConfig(_configFetcher.getGeneration());
        }

        void updatedConfig(int64_t generation) {
            vespalib::ComponentConfigProducer::Config curr("filedistributor", generation);
            _stateServer->myComponents.addConfig(curr);
        }

        ~Components() {
            _configFetcher.close();
            //Do not waste time retrying zookeeper operations when going down.
            _zk->disableRetries();

            _downloaderEventLoopThread.reset();
        }

    };

    typedef std::lock_guard<std::mutex> LockGuard;
    std::mutex _configMutex;

    bool _completeReconfigurationNeeded;
    std::unique_ptr<ZookeepersConfig> _zooKeepersConfig;
    std::unique_ptr<FiledistributorConfig> _fileDistributorConfig;
    std::unique_ptr<FiledistributorrpcConfig> _rpcConfig;

    std::unique_ptr<Components> _components;
public:
    FileDistributor(const FileDistributor &) = delete;
    FileDistributor & operator = (const FileDistributor &) = delete;
    FileDistributor();
    ~FileDistributor();

    void notifyGenerationChange(int64_t generation) override {
        if (_components && ! completeReconfigurationNeeded()) {
            _components->updatedConfig(generation);
        }
    }

    //configure overrides
    void configure(std::unique_ptr<ZookeepersConfig> config) override {
        LockGuard guard(_configMutex);
        _zooKeepersConfig = std::move(config);
        _completeReconfigurationNeeded = true;
    }

    void configure(std::unique_ptr<FiledistributorConfig> config) override {
        LockGuard guard(_configMutex);
        if (_fileDistributorConfig.get() != NULL &&
            (config->torrentport != _fileDistributorConfig->torrentport ||
             config->stateport   != _fileDistributorConfig->stateport ||
             config->hostname    != _fileDistributorConfig->hostname ||
             config->filedbpath  != _fileDistributorConfig->filedbpath))
        {
            _completeReconfigurationNeeded = true;
        } else if (_components.get()) {
            configureSpeedLimits(*config);
        }
        _fileDistributorConfig = std::move(config);

    }

    void configure(std::unique_ptr<FiledistributorrpcConfig> config) override {
        LockGuard guard(_configMutex);
        _rpcConfig = std::move(config);
        _completeReconfigurationNeeded = true;
    }

    void run(const config::ConfigUri & configUri) {
        while (!askedToShutDown()) {
            clearReinitializeFlag();
            runImpl(configUri);
        }
    }

    bool isConfigComplete() {
        LockGuard guard(_configMutex);
        return (_zooKeepersConfig && _fileDistributorConfig && _rpcConfig);
    }
    void createComponents(const config::ConfigUri & configUri) {
        LockGuard guard(_configMutex);
        _components.reset(
                new Components(configUri,
                        *_zooKeepersConfig,
                        *_fileDistributorConfig,
                        *_rpcConfig));

        configureSpeedLimits(*_fileDistributorConfig);
        _completeReconfigurationNeeded = false;
    }

    bool completeReconfigurationNeeded() {
        LockGuard guard(_configMutex);
        if (_completeReconfigurationNeeded) {
            LOG(debug, "Complete reconfiguration needed");
        }
        return _completeReconfigurationNeeded;
    }

    void configureSpeedLimits(const FiledistributorConfig& config) {
        FileDownloader& downloader = *_components->_downloader;
        downloader.setMaxDownloadSpeed(config.maxdownloadspeed);
        downloader.setMaxUploadSpeed(config.maxuploadspeed);
    }

    void runImpl(const config::ConfigUri & configUri) {
        createComponents(configUri);

        // We do not want back to back reinitializing as it gives zero time for serving
        // some torrents.
        int postPoneAskedToReinitializedSecs = 50;

        while (!askedToShutDown() &&
	       (postPoneAskedToReinitializedSecs > 0 || !askedToReinitialize()) &&
	       !completeReconfigurationNeeded())
        {
            postPoneAskedToReinitializedSecs--;
            std::this_thread::sleep_for(1s);
        }
        _components.reset();
    }
};

FileDistributor::FileDistributor()
    : _configMutex(),
      _completeReconfigurationNeeded(false),
      _zooKeepersConfig(),
      _fileDistributorConfig(),
      _rpcConfig(),
      _components()
{ }
FileDistributor::~FileDistributor() { }

class FileDistributorApplication : public FastOS_Application {
    const config::ConfigUri _configUri;
public:
    FileDistributorApplication(const config::ConfigUri & configUri);

    int Main() override;
};

namespace {

struct ProgramOptionException {
    std::string _msg;
    ProgramOptionException(const std::string & msg)
        : _msg(msg)
    {}
};

bool exists(const std::string& optionName, const boost::program_options::variables_map& map) {
    return map.find(optionName) != map.end();
}

void ensureExists(const std::string& optionName, const boost::program_options::variables_map& map ) {
    if (!exists(optionName, map)) {
        throw ProgramOptionException("Error: Missing option " + optionName);
    }
}

} //anonymous namespace

FileDistributorApplication::FileDistributorApplication(const config::ConfigUri & configUri)
    :_configUri(configUri) {
}

int
FileDistributorApplication::Main() {
    try {
        FileDistributor distributor;

        config::ConfigFetcher configFetcher(_configUri.getContext());
        configFetcher.subscribe<ZookeepersConfig>(_configUri.getConfigId(), &distributor);
        configFetcher.subscribe<FiledistributorConfig>(_configUri.getConfigId(), &distributor);
        configFetcher.subscribe<FiledistributorrpcConfig>(_configUri.getConfigId(), &distributor);
        configFetcher.subscribeGenerationChanges(&distributor);
        configFetcher.start();

        while (! distributor.isConfigComplete() ) {
            std::this_thread::sleep_for(10ms);
        }
        distributor.run(_configUri);

        EV_STOPPING(programName, "Clean exit");
        return 0;
    } catch (const FileDoesNotExistException & e) {
        EV_STOPPING(programName, e.what());
        return 1;
    } catch (const ZKNodeDoesNotExistsException & e) {
        EV_STOPPING(programName, e.what());
        return 2;
    } catch (const ZKSessionExpired & e) {
        EV_STOPPING(programName, e.what());
        return 3;
    } catch (const config::ConfigTimeoutException & e) {
        EV_STOPPING(programName, e.what());
        return 4;
    } catch (const vespalib::PortListenException & e) {
        EV_STOPPING(programName, e.what());
        return 5;
    } catch (const ZKConnectionLossException & e) {
        EV_STOPPING(programName, e.what());
        return 6;
    } catch (const ZKFailedConnecting & e) {
        EV_STOPPING(programName, e.what());
        return 7;
    } catch (const config::InvalidConfigException & e) {
        EV_STOPPING(programName, e.what());
        return 8;
    } catch (const ZKOperationTimeoutException & e) {
        EV_STOPPING(programName, e.what());
        return 9;
    } catch (const ZKGenericException & e) {
        EV_STOPPING(programName, e.what());
        return 99;
    }
}

int
executeApplication(int argc, char** argv) {
    const char
        *configId("configid"),
        *help("help");

    namespace po = boost::program_options;
    po::options_description description;
    description.add_options()
        (configId, po::value<std::string > (), "id to request config for")
        (help, "help");

    try {
        po::variables_map values;
        po::store(po::parse_command_line(argc, argv, description), values);

        if (exists(help, values)) {
            std::cout <<description;
            return 0;
        }
        ensureExists(configId, values);

        FileDistributorApplication application(values[configId].as<std::string > ());
        return application.Entry(argc, argv);

    } catch(ProgramOptionException& e) {
        std::cerr <<e._msg <<std::endl;
        return -1;
    }
}

namespace {

class InitSignals {
public:
    InitSignals() { initSignals(); }
};

InitSignals _G_initSignals __attribute__ ((init_priority (101)));

}

int
main(int argc, char** argv) {
    if (askedToShutDown()) { return 0; }
    EV_STARTED(programName);

    std::srand(std::time(0));
    filedistribution::ZKLogging loggingGuard;

    return executeApplication(argc, argv);
}
