// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <iostream>
#include <string>
#include <cstdlib>

#include <boost/program_options.hpp>
#include <boost/lambda/bind.hpp>
#include <boost/lambda/lambda.hpp>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/noncopyable.hpp>
#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/exception/diagnostic_information.hpp>
#include <boost/scope_exit.hpp>

#include <vespa/fastos/app.h>
#include <vespa/config-zookeepers.h>

#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/filedistribution/distributor/config-filedistributor.h>
#include <vespa/filedistribution/model/config-filereferences.h>

#include <vespa/filedistribution/distributor/filedistributortrackerimpl.h>
#include <vespa/filedistribution/distributor/filedownloadermanager.h>
#include <vespa/filedistribution/distributor/filedownloader.h>
#include <vespa/filedistribution/distributor/signalhandling.h>
#include <vespa/filedistribution/distributor/state_server_impl.h>

#include <vespa/filedistribution/model/filedistributionmodelimpl.h>
#include <vespa/filedistribution/rpc/filedistributorrpc.h>
#include <vespa/filedistribution/common/exception.h>
#include <vespa/filedistribution/common/componentsdeleter.h>

namespace {
const char* programName = "filedistributor";
}

#include <vespa/log/log.h>
LOG_SETUP(programName);

namespace ll = boost::lambda;

using namespace filedistribution;
using cloud::config::ZookeepersConfig;
using cloud::config::filedistribution::FiledistributorConfig;
using cloud::config::filedistribution::FiledistributorrpcConfig;

class FileDistributor : public config::IFetcherCallback<ZookeepersConfig>,
                        public config::IFetcherCallback<FiledistributorConfig>,
                        public config::IFetcherCallback<FiledistributorrpcConfig>,
                        public config::IGenerationCallback,
                        boost::noncopyable
{
    class Components : boost::noncopyable {
        ComponentsDeleter _componentsDeleter;
    public:
        const boost::shared_ptr<ZKFacade> _zk;
        const boost::shared_ptr<FileDistributionModelImpl> _model;
        const boost::shared_ptr<FileDistributorTrackerImpl> _tracker;
        const boost::shared_ptr<FileDownloader> _downloader;
        const boost::shared_ptr<FileDownloaderManager> _manager;
        const boost::shared_ptr<FileDistributorRPC> _rpcHandler;
        const boost::shared_ptr<StateServerImpl> _stateServer;

    private:
        boost::thread _downloaderEventLoopThread;
        config::ConfigFetcher _configFetcher;


        template <class T>
        typename boost::shared_ptr<T> track(T* component) {
            return _componentsDeleter.track(component);
        }

    public:

        Components(const boost::shared_ptr<ExceptionRethrower>& exceptionRethrower,
                   const config::ConfigUri & configUri,
                   const ZookeepersConfig& zooKeepersConfig,
                   const FiledistributorConfig& fileDistributorConfig,
                   const FiledistributorrpcConfig& rpcConfig)
            :_zk(track(new ZKFacade(zooKeepersConfig.zookeeperserverlist, exceptionRethrower))),
             _model(track(new FileDistributionModelImpl(
                                     fileDistributorConfig.hostname,
                                     fileDistributorConfig.torrentport,
                                     _zk,
                                     exceptionRethrower))),
             _tracker(track(new FileDistributorTrackerImpl(_model, exceptionRethrower))),
             _downloader(track(new FileDownloader(
                                     _tracker,
                                     fileDistributorConfig.hostname,
                                     fileDistributorConfig.torrentport,
                                     boost::filesystem::path(fileDistributorConfig.filedbpath),
                                     exceptionRethrower))),
             _manager(track(new FileDownloaderManager(_downloader, _model))),
             _rpcHandler(track(new FileDistributorRPC(rpcConfig.connectionspec, _manager))),
             _stateServer(track(new StateServerImpl(fileDistributorConfig.stateport))),
             _downloaderEventLoopThread(
                    ll::bind(&FileDownloader::runEventLoop, _downloader.get())),
             _configFetcher(configUri.getContext())

        {
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

            _downloaderEventLoopThread.interrupt();
            _downloaderEventLoopThread.join();
        }

    };

    typedef boost::lock_guard<boost::mutex> LockGuard;
    boost::mutex _configMutex;

    bool _completeReconfigurationNeeded;
    std::unique_ptr<ZookeepersConfig> _zooKeepersConfig;
    std::unique_ptr<FiledistributorConfig> _fileDistributorConfig;
    std::unique_ptr<FiledistributorrpcConfig> _rpcConfig;

    boost::shared_ptr<ExceptionRethrower> _exceptionRethrower;
    std::unique_ptr<Components> _components;
public:
    FileDistributor()
        : _configMutex(),
          _completeReconfigurationNeeded(false),
          _zooKeepersConfig(),
          _fileDistributorConfig(),
          _rpcConfig(),
          _exceptionRethrower(),
          _components()
    { }

    void notifyGenerationChange(int64_t generation) {
        if (_components && ! completeReconfigurationNeeded()) {
            _components->updatedConfig(generation);
        }
    }

    //configure overrides
    void configure(std::unique_ptr<ZookeepersConfig> config) {
        LockGuard guard(_configMutex);
        _zooKeepersConfig = std::move(config);
        _completeReconfigurationNeeded = true;
    }

    void configure(std::unique_ptr<FiledistributorConfig> config) {
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

    void configure(std::unique_ptr<FiledistributorrpcConfig> config) {
        LockGuard guard(_configMutex);
        _rpcConfig = std::move(config);
        _completeReconfigurationNeeded = true;
    }

    void run(const config::ConfigUri & configUri) {
        while (!askedToShutDown()) {
            clearReinitializeFlag();
            _exceptionRethrower.reset(new ExceptionRethrower());
            runImpl(configUri);

            if (_exceptionRethrower->exceptionStored())
                _exceptionRethrower->rethrow();
        }
    }

    static void ensureExceptionsStored(const boost::shared_ptr<ExceptionRethrower>& exceptionRethrower) {
        //TODO: this is somewhat hackish, refactor to eliminate this later.
        LOG(debug, "Waiting for shutdown");
        for (int i=0;
             i<50 && !exceptionRethrower.unique();
                ++i) {
            boost::this_thread::sleep(boost::posix_time::milliseconds(100));
        }
        LOG(debug, "Done waiting for shutdown");

        if (!exceptionRethrower.unique()) {
            EV_STOPPING(programName, "Forced termination");
            kill(getpid(), SIGKILL);
        }
    }

    void createComponents(const boost::shared_ptr<ExceptionRethrower>& exceptionRethrower, const config::ConfigUri & configUri) {
        LockGuard guard(_configMutex);
        _components.reset(
                new Components(exceptionRethrower,
                        configUri,
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

    //avoid warning due to scope exit macro
#pragma GCC diagnostic ignored "-Wshadow"
    void runImpl(const config::ConfigUri & configUri) {

        BOOST_SCOPE_EXIT((&_components)(&_exceptionRethrower)) {
            _components.reset();
            //Ensures that any exception stored during destruction will be available when returning.
            ensureExceptionsStored(_exceptionRethrower);
        } BOOST_SCOPE_EXIT_END

        createComponents(_exceptionRethrower, configUri);

        // We do not want back to back reinitializing as it gives zero time for serving
        // some torrents.
        int postPoneAskedToReinitializedSecs = 50;

        while (!askedToShutDown() &&
	       (postPoneAskedToReinitializedSecs > 0 || !askedToReinitialize()) &&
	       !completeReconfigurationNeeded() &&
	       !_exceptionRethrower->exceptionStored()) {
	  postPoneAskedToReinitializedSecs--;
	  boost::this_thread::sleep(boost::posix_time::seconds(1));
        }
    }
};

//TODO: use pop in gcc 4.6
#pragma GCC diagnostic warning "-Wshadow"

class FileDistributorApplication : public FastOS_Application {
    const config::ConfigUri _configUri;
public:
    FileDistributorApplication(const config::ConfigUri & configUri);

    //overrides
    int Main();
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

void ensureExists(const std::string& optionName, const boost::program_options::variables_map& map \
                  ) {
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

        distributor.run(_configUri);

        EV_STOPPING(programName, "Clean exit");
        return 0;
    } catch(const FileDoesNotExistException & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 1;
    } catch(const ZKNodeDoesNotExistsException & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 2;
    } catch(const ZKSessionExpired & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 3;
    } catch(const config::ConfigTimeoutException & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 4;
    } catch(const FailedListeningException & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 5;
    } catch(const ZKGenericException & e) {
        std::string s = boost::diagnostic_information(e);
        EV_STOPPING(programName, s.c_str());
        return 99;
    } catch(const boost::unknown_exception & e) {
        std::string s = boost::diagnostic_information(e);
        LOG(warning, "Caught '%s'", s.c_str());
        EV_STOPPING(programName, s.c_str());
        return 255;
#if 0
    /*
     These are kept hanging around for reference as to how it was when we just held our ears
     singing "na, na, na, na..." no matter if the sun was shining or if the world imploded.
    */
    } catch(const boost::exception& e) {
        std::string s = boost::diagnostic_information(e);
        LOG(error, "Caught '%s'", s.c_str());
        EV_STOPPING(programName, s.c_str());
        return -1;
    } catch(const std::string& msg) {
        std::string s = "Error: " + msg;
        LOG(error, "Caught '%s'", s.c_str());
        EV_STOPPING(programName, s.c_str());
        return -1;
#endif
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
        po::store(
                po::parse_command_line(argc, argv, description),
                values);

        if (exists(help, values)) {
            std::cout <<description;
            return 0;
        }
        ensureExists(configId, values);

        FileDistributorApplication application(
                values[configId].as<std::string > ());
        return application.Entry(argc, argv);

    } catch(ProgramOptionException& e) {
        std::cerr <<e._msg <<std::endl;
        return -1;
    }
}

int
main(int argc, char** argv) {
    EV_STARTED(programName);
    initSignals();

    std::srand(std::time(0));
    filedistribution::ZKLogging loggingGuard;

    return executeApplication(argc, argv);
}
