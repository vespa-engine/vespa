// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filedistributionmodel.h"
#include "config-filereferences.h"
#include "zkfacade.h"
#include "zkfiledbmodel.h"
#include <vespa/config/config.h>

using cloud::config::filedistribution::FilereferencesConfig;

namespace filedistribution {

class FileDistributionModelImpl : public FileDistributionModel,
                                  public config::IFetcherCallback<FilereferencesConfig>,
                                  public std::enable_shared_from_this<FileDistributionModelImpl>
{
    struct DeployedFilesChangedCallback;

    const std::string _hostName;
    const int _port;

    const std::shared_ptr<ZKFacade> _zk;
    ZKFileDBModel _fileDBModel;

    std::mutex _activeFileReferencesMutex;
    typedef std::lock_guard<std::mutex> LockGuard;
    std::vector<vespalib::string> _activeFileReferences;

    bool /*changed*/
    updateActiveFileReferences(const std::vector<vespalib::string>& fileReferences);

    Path getPeerEntryPath(const std::string& fileReference);
public:
    FileDistributionModelImpl(const std::string& hostName, int port, const std::shared_ptr<ZKFacade>& zk)
        :_hostName(hostName),
         _port(port),
         _zk(zk),
         _fileDBModel(_zk)
    {
        /* Hack: Force the first call to updateActiveFileReferences to return changed=true
           when the file references config is empty.
           This ensures that the "deployed files to download" nodes in zookeeper are read at start up.
        */
        _activeFileReferences.push_back("force-initial-files-to-download-changed-signal");
    }

    ~FileDistributionModelImpl();

    //overrides FileDistributionModel
    FileDBModel& getFileDBModel() override {
        return _fileDBModel;
    }

    std::set<std::string> getFilesToDownload() override;

    PeerEntries getPeers(const std::string& fileReference, size_t maxPeers) override;
    void addPeer(const std::string& fileReference) override;
    void removePeer(const std::string& fileReference) override;
    void peerFinished(const std::string& fileReference) override;
    void addConfigServersAsPeers(std::vector<std::string>& peers, char const* envConfigServer, int port);

    //Overrides Subscriber
    void configure(std::unique_ptr<FilereferencesConfig> config) override;
};

} //namespace filedistribution

