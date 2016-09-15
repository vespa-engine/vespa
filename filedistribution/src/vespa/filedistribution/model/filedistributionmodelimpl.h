// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/enable_shared_from_this.hpp>

#include "filedistributionmodel.h"
#include <vespa/filedistribution/model/config-filereferences.h>
#include "zkfacade.h"
#include "zkfiledbmodel.h"
#include <vespa/filedistribution/common/exceptionrethrower.h>
#include <vespa/config/config.h>

using cloud::config::filedistribution::FilereferencesConfig;

namespace filedistribution {

class FileDistributionModelImpl : public FileDistributionModel,
                                  public config::IFetcherCallback<FilereferencesConfig>,
                                  public boost::enable_shared_from_this<FileDistributionModelImpl>
{
    struct DeployedFilesChangedCallback;

    const std::string _hostName;
    const int _port;

    const boost::shared_ptr<ZKFacade> _zk;
    ZKFileDBModel _fileDBModel;

    boost::mutex _activeFileReferencesMutex;
    typedef boost::lock_guard<boost::mutex> LockGuard;
    std::vector<vespalib::string> _activeFileReferences;

    const boost::shared_ptr<ExceptionRethrower> _exceptionRethrower;

    bool /*changed*/
    updateActiveFileReferences(const std::vector<vespalib::string>& fileReferences);

    ZKFacade::Path getPeerEntryPath(const std::string& fileReference);
public:
    FileDistributionModelImpl(const std::string& hostName, int port,
                              const boost::shared_ptr<ZKFacade>& zk,
                              const boost::shared_ptr<ExceptionRethrower>& exceptionRethrower)
        :_hostName(hostName),
         _port(port),
         _zk(zk),
         _fileDBModel(_zk),
         _exceptionRethrower(exceptionRethrower)
    {
        /* Hack: Force the first call to updateActiveFileReferences to return changed=true
           when the file references config is empty.
           This ensures that the "deployed files to download" nodes in zookeeper are read at start up.
        */
        _activeFileReferences.push_back("force-initial-files-to-download-changed-signal");
    }

    ~FileDistributionModelImpl();

    //overrides FileDistributionModel
    FileDBModel& getFileDBModel() {
        return _fileDBModel;
    }

    std::set<std::string> getFilesToDownload();

    PeerEntries getPeers(const std::string& fileReference, size_t maxPeers);
    void addPeer(const std::string& fileReference);
    void removePeer(const std::string& fileReference);
    void peerFinished(const std::string& fileReference);
    void addConfigServersAsPeers(std::vector<std::string>& peers, char const* envConfigServer, int port);

    //Overrides Subscriber
    void configure(std::unique_ptr<FilereferencesConfig> config);
};

} //namespace filedistribution

