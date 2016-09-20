// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>

namespace filedistribution {

class DirectoryGuard {
public:
    typedef std::unique_ptr<DirectoryGuard> UP;
    DirectoryGuard(Path path);
    ~DirectoryGuard();
private:
    int _fd;
};

VESPA_DEFINE_EXCEPTION(InvalidProgressException, vespalib::Exception);
VESPA_DEFINE_EXCEPTION(InvalidHostStatusException, vespalib::Exception);

class FileDBModel {
public:
    struct HostStatus {
        enum State { finished, inProgress, notStarted };

        State _state;
        size_t _numFilesToDownload;
        size_t _numFilesFinished;
    };

    FileDBModel(const FileDBModel &) = delete;
    FileDBModel & operator = (const FileDBModel &) = delete;
    FileDBModel() = default;
    virtual ~FileDBModel();

    virtual bool hasFile(const std::string& fileReference) = 0;
    virtual void addFile(const std::string& fileReference, const Buffer& buffer) = 0;
    virtual Buffer getFile(const std::string& fileReference) = 0;
    virtual void cleanFiles(const std::vector<std::string>& filesToPreserve) = 0;

    virtual void setDeployedFilesToDownload(const std::string& hostName,
            const std::string & appId,
            const std::vector<std::string> & files) = 0;
    virtual void cleanDeployedFilesToDownload(
            const std::vector<std::string> & hostsToPreserve,
            const std::string& appId) = 0;
    virtual void removeDeploymentsThatHaveDifferentApplicationId(
            const std::vector<std::string> & hostsToPreserve,
            const std::string& appId) = 0;
    virtual std::vector<std::string> getHosts() = 0;

    virtual HostStatus getHostStatus(const std::string& hostName) = 0;
    //TODO: does not really belong here, refactor.
    typedef std::vector<int8_t> Progress; // [0-100]
    virtual Progress getProgress(const std::string& fileReference,
                                 const std::vector<std::string>& hostsSortedAscending) = 0;
};

} //namespace filedistribution

