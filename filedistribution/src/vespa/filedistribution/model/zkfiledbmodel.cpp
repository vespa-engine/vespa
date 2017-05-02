// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filedistributionmodel.h"
#include "zkfacade.h"
#include "zkfiledbmodel.h"
#include "deployedfilestodownload.h"
#include <vespa/filedistribution/common/logfwd.h>
#include <sys/file.h>
#include <ostream>
#include <algorithm>

namespace fs = boost::filesystem;

namespace filedistribution {

namespace {

fs::path
createPath(const std::string& fileReference) {
    return ZKFileDBModel::_fileDBPath / fileReference;
}

void
createNode(const fs::path & path, ZKFacade& zk) {
    if (!zk.hasNode(path))
        zk.setData(path, "", 0);
}

bool
isEntryForHost(const std::string& host, const std::string& peerEntry) {
    return host.size() < peerEntry.size() &&
        std::equal(host.begin(), host.end(), peerEntry.begin()) &&
        peerEntry[host.size()] == ZKFileDBModel::_peerEntrySeparator;
}

std::vector<std::string>
getSortedChildren(ZKFacade& zk, const Path& path) {
    std::vector<std::string> children = zk.getChildren(path);
    std::sort(children.begin(), children.end());
    return children;
}

} //anonymous namespace

VESPA_IMPLEMENT_EXCEPTION(InvalidProgressException, vespalib::Exception);
VESPA_IMPLEMENT_EXCEPTION(InvalidHostStatusException, vespalib::Exception);

const Path ZKFileDBModel::_root = "/vespa/filedistribution";
const Path ZKFileDBModel::_fileDBPath = _root / "files";
const Path ZKFileDBModel::_hostsPath = _root / "hosts";

bool
ZKFileDBModel::hasFile(const std::string& fileReference) {
    return _zk->hasNode(createPath(fileReference));
}

void
ZKFileDBModel::addFile(const std::string& fileReference, const Buffer& buffer) {
    return _zk->setData(createPath(fileReference), buffer);
}

Buffer
ZKFileDBModel::getFile(const std::string& fileReference) {
    try {
        return _zk->getData(createPath(fileReference));
    } catch(const ZKNodeDoesNotExistsException & e) {
        throw FileDoesNotExistException(fileReference, e, VESPA_STRLOC);
    }
}

void
ZKFileDBModel::setDeployedFilesToDownload(
        const std::string& hostName,
        const std::string& appId,
        const std::vector<std::string>& files) {
    DeployedFilesToDownload d(_zk.get());
    d.setDeployedFilesToDownload(hostName, appId, files);
}

void
ZKFileDBModel::cleanDeployedFilesToDownload(
        const std::vector<std::string>& hostsToPreserve,
        const std::string& appId) {

    std::vector<std::string> allHosts = getHosts();
    std::set<std::string> toPreserve(hostsToPreserve.begin(), hostsToPreserve.end());

    for (auto & host : allHosts) {
        Path hostPath = _hostsPath / host;
        try {
            removeLegacyDeployFileNodes(hostPath);
            // If this host is NOT part of hosts to deploy to
            if (toPreserve.find(host) == toPreserve.end()) {
                removeDeployFileNodes(hostPath, appId);
                if (canRemoveHost(hostPath, appId)) {
                    _zk->remove(hostPath);
                }
            }
        } catch (const ZKNodeDoesNotExistsException& e) {
            LOGFWD(debug, "Host '%s' changed. Not touching", hostPath.string().c_str());
        }
    }
}

void
ZKFileDBModel::removeDeploymentsThatHaveDifferentApplicationId(
        const std::vector<std::string>& hostsToPreserve,
        const std::string& appId) {

    std::vector<std::string> allHosts = getHosts();
    std::set<std::string> toPreserve(hostsToPreserve.begin(), hostsToPreserve.end());

    for (auto & host : allHosts) {
        Path hostPath = _hostsPath / host;
        try {
            if (toPreserve.find(host) != toPreserve.end()) {
                removeNonApplicationFiles(hostPath, appId);
            }
        } catch (const ZKNodeDoesNotExistsException& e) {
            LOGFWD(debug, "Host '%s' changed. Not touching", hostPath.string().c_str());
        }
    }
}


// Delete files which do not belong to this application.
void
ZKFileDBModel::removeNonApplicationFiles(const Path & hostPath, const std::string& appId)
{
    std::vector<std::string> deployNodes = _zk->getChildren(hostPath);
        for (auto & deployNode : deployNodes) {
            Path deployNodePath = hostPath / deployNode;
            std::string applicationId(readApplicationId(*_zk, deployNodePath));
            if (appId != applicationId) {
                _zk->remove(deployNodePath);
            }
        }
}


void
ZKFileDBModel::removeLegacyDeployFileNodes(const Path & hostPath)
{
    std::vector<std::string> deployNodes = _zk->getChildren(hostPath);
    for (auto & deployNode : deployNodes) {
        Path deployNodePath = hostPath / deployNode;
        std::string applicationId(readApplicationId(*_zk, deployNodePath));
        size_t numParts = std::count(applicationId.begin(), applicationId.end(), ':');
        // If we have an id with 3 colons, it is a legacy id and can be deleted.
        if (numParts == 3) {
            _zk->remove(deployNodePath);
        }
    }
}

void
ZKFileDBModel::removeDeployFileNodes(const Path & hostPath, const std::string& appId) {
    std::vector<std::string> deployNodes = _zk->getChildren(hostPath);
    for (auto & deployNode : deployNodes) {
        Path deployNodePath = hostPath / deployNode;
        std::string applicationId(readApplicationId(*_zk, deployNodePath));
        if (appId == applicationId) {
            _zk->remove(deployNodePath);
        }
    }
}

bool
ZKFileDBModel::canRemoveHost(const Path & hostPath, const std::string& appId) {
    std::vector<std::string> deployNodes = _zk->getChildren(hostPath);
    for (auto & deployNode : deployNodes) {
        Path deployNodePath = hostPath / deployNode;
        std::string applicationId(readApplicationId(*_zk, deployNodePath));
        if (appId != applicationId) {
            return false;
        }
    }
    return true;
}

std::vector<std::string>
ZKFileDBModel::getHosts() {
    try {
        return _zk->getChildren(_hostsPath);
    } catch(ZKNodeDoesNotExistsException&) {
        LOGFWD(debug, "No files to be distributed.");
        return std::vector<std::string>();
    }
}

namespace {
const ZKFileDBModel::Progress::value_type notStarted = 101;
};

//TODO: Refactor
ZKFileDBModel::HostStatus
ZKFileDBModel::getHostStatus(const std::string& hostName) {
    typedef std::vector<std::string> PeerEntries;

    DeployedFilesToDownload d(_zk.get());
    DeployedFilesToDownload::FileReferences filesToDownload = d.getLatestDeployedFilesToDownload(hostName);

    HostStatus hostStatus;
    hostStatus._state = HostStatus::notStarted;
    hostStatus._numFilesToDownload = filesToDownload.size();
    hostStatus._numFilesFinished = 0;

    for (const std::string & file : filesToDownload) {
        Path path = getPeersPath(file);

        const PeerEntries peerEntries = getSortedChildren(*_zk, path);
        PeerEntries::const_iterator candidate =
            std::lower_bound(peerEntries.begin(), peerEntries.end(), hostName);

        if (candidate != peerEntries.end() && isEntryForHost(hostName, *candidate)) {
            char fileProgressPercentage = getProgress(path / (*candidate));
            if (fileProgressPercentage == 100) {
                hostStatus._numFilesFinished++;
            } else if (fileProgressPercentage != notStarted) {
                hostStatus._state = HostStatus::inProgress;
            }

            candidate++;
            if (candidate != peerEntries.end() && isEntryForHost(hostName, *candidate))
                throw InvalidHostStatusException(path.string(), VESPA_STRLOC);
        }
    }


    if (hostStatus._numFilesToDownload ==  hostStatus._numFilesFinished) {
        hostStatus._state = HostStatus::finished;
    }

    return hostStatus;
}

void
ZKFileDBModel::cleanFiles(const std::vector<std::string>& filesToPreserve) {
    _zk->retainOnly(_fileDBPath, filesToPreserve);
}

ZKFileDBModel::ZKFileDBModel(const std::shared_ptr<ZKFacade>& zk)
    : _zk(zk)
{
    createNode(_root, *_zk);
    createNode(_fileDBPath, *_zk);
    createNode(_hostsPath, *_zk);
}

ZKFileDBModel::~ZKFileDBModel() {}

char
ZKFileDBModel::getProgress(const Path& path) {
    try {
        Buffer buffer(_zk->getData(path));
        if (buffer.size() == 1)
            return buffer[0];
        else if (buffer.size() == 0)
            return 0;
        else {
            throw InvalidProgressException(path.string(), VESPA_STRLOC);
        }
    } catch (ZKNodeDoesNotExistsException& e) {
        //progress information deleted
        return notStarted;
    }
}

ZKFileDBModel::Progress
ZKFileDBModel::getProgress(const std::string& fileReference,
                           const std::vector<std::string>& hostsSortedAscending) {
    Path path = getPeersPath(fileReference);

    Progress progress;
    progress.reserve(hostsSortedAscending.size());

    typedef std::vector<std::string> PeerEntries;
    const PeerEntries peerEntries = getSortedChildren(*_zk, path);

    PeerEntries::const_iterator current = peerEntries.begin();
    for (const std::string& host : hostsSortedAscending) {
        PeerEntries::const_iterator candidate =
            std::lower_bound(current, peerEntries.end(), host);

        ZKFileDBModel::Progress::value_type hostProgress = notStarted;
        if (candidate != peerEntries.end()) {
            current = candidate;
            if (isEntryForHost(host, *current))
                hostProgress = getProgress(path / (*candidate));
        }
        progress.push_back(hostProgress);
    }
    return progress;
}

FileDBModel::~FileDBModel() {}

DirectoryGuard::DirectoryGuard(Path path) :
    _fd(-1)
{
    _fd = open(path.c_str(), O_RDONLY);
    assert(_fd != -1);
    int retval = flock(_fd, LOCK_EX);
    while ((retval != 0) && (errno == EINTR)) {
        std::cout << "Got interupted while flock'ing ' " << path << std::endl;
        retval = flock(_fd, LOCK_EX);
    }
    assert(retval == 0);
}

DirectoryGuard::~DirectoryGuard() {
    if (_fd != -1) {
        int retval = flock(_fd, LOCK_UN);
        assert(retval == 0);
        retval = close(_fd);
        assert(retval ==0);
    }
}

}
