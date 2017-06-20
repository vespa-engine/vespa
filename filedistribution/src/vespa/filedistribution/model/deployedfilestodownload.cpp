// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "deployedfilestodownload.h"
#include <vespa/filedistribution/common/logfwd.h>
#include <sstream>
#include <iterator>

using filedistribution::DeployedFilesToDownload;
using filedistribution::Path;

typedef std::vector<std::string> StringVector;

namespace filedistribution {

const Path getApplicationIdPath(const Path & parent) { return parent / "appId"; }

const std::string
readApplicationId(filedistribution::ZKFacade & zk, const Path & deployNode)
{
    if (zk.hasNode(getApplicationIdPath(deployNode))) {
        return zk.getString(getApplicationIdPath(deployNode));
    }
    return "default:default:default";
}

}

const Path
DeployedFilesToDownload::addNewDeployNode(Path parentPath, const FileReferences& files) {
    Path path = parentPath / "deploy_";

    std::ostringstream filesStream;
    if (!files.empty()) {
        filesStream << files[0];
        std::for_each(files.begin() +1, files.end(), [&](const auto & v) { filesStream << '\n' << v; });
    }
    Path retPath = _zk.createSequenceNode(path, filesStream.str().c_str(), filesStream.str().length());
    return retPath;
}

void
DeployedFilesToDownload::deleteExpiredDeployNodes(Path parentPath) {
    std::map<std::string, StringVector> childrenPerId(groupChildrenByAppId(parentPath, _zk.getChildren(parentPath)));
    for (auto & kv : childrenPerId) {
        deleteExpiredDeployNodes(parentPath, kv.second);
    }
}

std::map<std::string, StringVector>
DeployedFilesToDownload::groupChildrenByAppId(const Path & parentPath, const StringVector & children)
{
    std::map<std::string, StringVector> childrenById;
    std::for_each(std::begin(children), std::end(children),
        [&](const std::string & childName)
        {
            Path childPath = parentPath / childName;
            std::string appId(readApplicationId(_zk, childPath));
            childrenById[appId].push_back(childName);
        });
    return childrenById;
}

void
DeployedFilesToDownload::deleteExpiredDeployNodes(Path parentPath, StringVector children)
{
    if (children.size() > numberOfDeploymentsToKeepFilesFrom) {
        std::sort(children.begin(), children.end());

        size_t numberOfNodesToDelete = children.size() - numberOfDeploymentsToKeepFilesFrom;
        std::for_each(children.begin(), children.begin() + numberOfNodesToDelete,
                     [&](const std::string & s) {_zk.remove(parentPath / s); });
    }
}

void
DeployedFilesToDownload::addAppIdToDeployNode(const Path & deployNode, const std::string & appId)
{
    _zk.setData(getApplicationIdPath(deployNode), appId.c_str(), appId.length());
}

void
DeployedFilesToDownload::setDeployedFilesToDownload(
        const std::string& hostName,
        const std::string& applicationId,
        const FileReferences& files) {
    Path parentPath = getPath(hostName);
    _zk.setData(parentPath, "", 0);

    const Path deployNode(addNewDeployNode(parentPath, files));
    addAppIdToDeployNode(deployNode, applicationId);
    deleteExpiredDeployNodes(parentPath);
}

//Nothrow
template <typename INSERT_ITERATOR>
void
DeployedFilesToDownload::readDeployFile(const Path& path, INSERT_ITERATOR insertionIterator) {
    LOGFWD(debug, "Reading deploy file '%s", path.string().c_str());


    try {
        Buffer buffer(_zk.getData(path));
        std::string stringBuffer(buffer.begin(), buffer.end());
        std::istringstream stream(stringBuffer);

        typedef std::istream_iterator<std::string> iterator;
        std::copy(iterator(stream), iterator(), insertionIterator);
    } catch (const ZKNodeDoesNotExistsException& e) {
        //Node deleted, no problem.
        LOGFWD(debug, "Deploy file '%s' deleted.", path.string().c_str());
    }
}

const DeployedFilesToDownload::FileReferences
DeployedFilesToDownload::getDeployedFilesToDownload(
        const std::string& hostName,
        const ZKFacade::NodeChangedWatcherSP& watcher) {

    try {
        StringVector deployedFiles = _zk.getChildren(getPath(hostName), watcher);
        FileReferences fileReferences;

        for (StringVector::iterator i = deployedFiles.begin(); i != deployedFiles.end(); ++i) {
            readDeployFile(getPath(hostName) / *i, std::back_inserter(fileReferences));
        }

        return fileReferences;
    } catch (const ZKNodeDoesNotExistsException&) {
        //Add watch waiting for the node to appear:
        if (_zk.hasNode(getPath(hostName), watcher)) {
            return getDeployedFilesToDownload(hostName, watcher);
        } else {
            return FileReferences();
        }
    }
}

const DeployedFilesToDownload::FileReferences
DeployedFilesToDownload::getLatestDeployedFilesToDownload(const std::string& hostName)
{
    StringVector deployedFiles = _zk.getChildren(getPath(hostName));
    std::sort(deployedFiles.begin(), deployedFiles.end());

    FileReferences fileReferences;
    if (deployedFiles.empty()) {
        return fileReferences;
    } else {
        readDeployFile(getPath(hostName) / *deployedFiles.rbegin(), std::back_inserter(fileReferences));
        return fileReferences;
    }
}
