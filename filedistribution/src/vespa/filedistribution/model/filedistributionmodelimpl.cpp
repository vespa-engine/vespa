// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "filedistributionmodel.h"

#include <vector>
#include <set>
#include <string>
#include <cstdlib>

#include <boost/filesystem.hpp>
#include <zookeeper/zookeeper.h>

#include <vespa/log/log.h>
LOG_SETUP(".filedistributionmodel");

#include "zkfiledbmodel.h"
#include "deployedfilestodownload.h"
#include "filedistributionmodelimpl.h"

namespace fs = boost::filesystem;

using filedistribution::ZKFileDBModel;

namespace {
//peer format:  hostName:port


void
addPeerEntry(const std::string& peer,
    filedistribution::FileDistributionModelImpl::PeerEntries& result) {

    try {
        libtorrent::peer_entry peerEntry;
        peerEntry.pid.clear();

        std::istringstream stream(peer);
        stream.exceptions ( std::istream::failbit | std::istream::badbit );

        std::getline(stream, peerEntry.ip, ZKFileDBModel::_peerEntrySeparator);
        stream >> peerEntry.port;

        result.push_back(peerEntry);
    } catch (const std::exception&) {
        LOG(warning, "Invalid peer entry: '%s'", peer.c_str());
        //Ignore invalid peer entries
    }
}

std::vector<std::string>::iterator
prunePeers(std::vector<std::string> &peers, size_t maxPeers) {
    if (peers.size() <= maxPeers)
        return peers.end();

    assert (maxPeers < 2147483648); //due to the usage of rand()

    const size_t peersSize = peers.size();
    for (size_t i=0; i<maxPeers; ++i) {
        //i <= i + (std::rand() % ( peersSize -i )) <= peerSize - 1
        size_t candidateBetween_i_and_peerSize = i + ( std::rand() % ( peersSize -i ));
        std::swap(peers[i], peers[candidateBetween_i_and_peerSize]);
    }

    return peers.begin() + maxPeers;
}

} //anonymous namespace

namespace filedistribution {

VESPA_IMPLEMENT_EXCEPTION(NotPeer, vespalib::Exception);

}

using filedistribution::FileDistributionModelImpl;

struct FileDistributionModelImpl::DeployedFilesChangedCallback :
    public ZKFacade::NodeChangedWatcher
{
    typedef std::shared_ptr<DeployedFilesChangedCallback> SP;

    std::weak_ptr<FileDistributionModelImpl> _parent;

    DeployedFilesChangedCallback(
            const std::shared_ptr<FileDistributionModelImpl> & parent)
        :_parent(parent)
    {}

    //override
    void operator()() override {
        if (std::shared_ptr<FileDistributionModelImpl> model = _parent.lock()) {
            model->_filesToDownloadChanged();
        }
    }
};


FileDistributionModelImpl::~FileDistributionModelImpl() {
    LOG(debug, "Deconstructing FileDistributionModelImpl");
}

FileDistributionModelImpl::PeerEntries
FileDistributionModelImpl::getPeers(const std::string& fileReference, size_t maxPeers) {
    try {
        fs::path path = _fileDBModel.getPeersPath(fileReference);

        typedef std::vector<std::string> Peers;
        Peers peers = _zk->getChildren(path);
        // TODO: Take this port from some config somewhere instead of hardcoding
        addConfigServersAsPeers(peers, getenv("services__addr_configserver"), 19093);

        Peers::iterator end = prunePeers(peers, maxPeers);

        PeerEntries result;
        result.reserve(end - peers.begin());

        std::for_each(peers.begin(), end, [&] (const std::string & s) { addPeerEntry(s, result); });

        LOG(debug, "Found %zu peers for path '%s'", result.size(), path.string().c_str());
        return result;
    } catch(ZKNodeDoesNotExistsException&) {
        LOG(debug, ("No peer entries available for " + fileReference).c_str());
        return PeerEntries();
    }
}

fs::path
FileDistributionModelImpl::getPeerEntryPath(const std::string& fileReference) {
    std::ostringstream entry;
    entry <<_hostName
          <<ZKFileDBModel::_peerEntrySeparator <<_port;

    return _fileDBModel.getPeersPath(fileReference) / entry.str();
}

void
FileDistributionModelImpl::addPeer(const std::string& fileReference) {
    fs::path path = getPeerEntryPath(fileReference);
    LOG(debug, "Adding peer '%s'", path.string().c_str());

    if (_zk->hasNode(path)) {
        LOG(info, "Retiring previous peer node owner.");
        _zk->removeIfExists(path);
    }
    _zk->addEphemeralNode(path);
}

void
FileDistributionModelImpl::removePeer(const std::string& fileReference) {
    fs::path path = getPeerEntryPath(fileReference);
    LOG(debug, "Removing peer '%s'", path.string().c_str());

    _zk->removeIfExists(path);
}

//Assumes that addPeer has been called before the torrent was started,
//so that we avoid the race condition between finishing downloading a torrent
//and setting peer status
void
FileDistributionModelImpl::peerFinished(const std::string& fileReference) {
    fs::path path = getPeerEntryPath(fileReference);
    LOG(debug, "Peer finished '%s'", path.string().c_str());

    try {
        bool mustExist = true;
        char progress = 100; //percent

        _zk->setData(path, &progress, sizeof(char), mustExist);
    } catch(ZKNodeDoesNotExistsException & e) {
        NotPeer(fileReference, e, VESPA_STRLOC);
    }
}

std::set<std::string>
FileDistributionModelImpl::getFilesToDownload() {
    DeployedFilesToDownload d(_zk.get());
    std::vector<std::string> deployed = d.getDeployedFilesToDownload(_hostName,
            DeployedFilesChangedCallback::SP(
                    new DeployedFilesChangedCallback(shared_from_this())));

    std::set<std::string> result(deployed.begin(), deployed.end());

    {
        LockGuard guard(_activeFileReferencesMutex);
        result.insert(_activeFileReferences.begin(), _activeFileReferences.end());
    }
    return result;
}

bool
FileDistributionModelImpl::updateActiveFileReferences(
    const std::vector<vespalib::string>& fileReferences) {

    std::vector<vespalib::string> sortedFileReferences(fileReferences);
    std::sort(sortedFileReferences.begin(), sortedFileReferences.end());

    LockGuard guard(_activeFileReferencesMutex);
    bool changed =
        sortedFileReferences != _activeFileReferences;

    sortedFileReferences.swap(_activeFileReferences);
    return changed;
}

void
FileDistributionModelImpl::addConfigServersAsPeers(
    std::vector<std::string> & peers, char const* envConfigServers, int port) {

    std::set<std::string> peersFromTracker(peers.begin(), peers.end());

    if (envConfigServers == NULL) {
        // Could be standalone cluster (not set for this).
        return;
    }
    std::string configServerCommaListed(envConfigServers);
    std::stringstream configserverstream(configServerCommaListed);
    std::string configserver;
    while (std::getline(configserverstream, configserver, ',')) {
         configserver += ":" + std::to_string(port);
         if (peersFromTracker.find(configserver) ==  peersFromTracker.end()) {
             peers.push_back(configserver);
             LOG(debug, "Adding configserver '%s'", configserver.c_str());
         } else {
             LOG(debug, "Configserver already added '%s'", configserver.c_str());
	 }
    }
}

void
FileDistributionModelImpl::configure(std::unique_ptr<FilereferencesConfig> config) {
    const bool changed = updateActiveFileReferences(config->filereferences);
    if (changed) {
        try {
            _filesToDownloadChanged();
        } catch (const ZKConnectionLossException & e) {
            LOG(info, "Connection loss in reconfigure of file references, resuming. %s", e.what());
        }
    }
}
