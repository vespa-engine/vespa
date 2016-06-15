// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/shared_ptr.hpp>

#include "filedistributionmodel.h"
#include "zkfacade.h"

namespace filedistribution {

class ZKFileDBModel : public FileDBModel {
public:
    typedef boost::filesystem::path Path;
private:
    const boost::shared_ptr<ZKFacade> _zk;
    char getProgress(const Path& path);
    void removeDeployFileNodes(const Path& hostPath, const std::string& appId);
    void removeLegacyDeployFileNodes(const Path& hostPath);
    bool canRemoveHost(const Path& hostPath, const std::string& appId);
public:
    const static Path _root;
    const static Path _fileDBPath;
    const static Path _hostsPath;

    const static char _peerEntrySeparator = ':';

    Path getPeersPath(const std::string& fileReference) {
        return _fileDBPath / fileReference;
    }

    //overrides
    bool hasFile(const std::string& fileReference);
    void addFile(const std::string& fileReference, const Buffer& buffer);
    Move<Buffer> getFile(const std::string& fileReference);
    void cleanFiles(const std::vector<std::string>& filesToPreserve);

    void setDeployedFilesToDownload(const std::string& hostName,
            const std::string & appId,
            const std::vector<std::string> & files);
    void cleanDeployedFilesToDownload(
            const std::vector<std::string> & hostsToPreserve,
            const std::string& appId);
    void removeDeploymentsThatHaveDifferentApplicationId(
            const std::vector<std::string> & hostsToPreserve,
            const std::string& appId);
    void removeNonApplicationFiles(
            const Path & hostPath,
            const std::string& appId);
    std::vector<std::string> getHosts();
    HostStatus getHostStatus(const std::string& hostName);

    ZKFileDBModel(const boost::shared_ptr<ZKFacade>& zk);

    Progress getProgress(const std::string& fileReference,
                         const std::vector<std::string>& hostsSortedAscending);
};

} //namespace filedistribution

