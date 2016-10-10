// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <memory>
#include <string>
#include <set>

#include <boost/signals2.hpp>

#include <libtorrent/peer.hpp>
#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>
#include "filedbmodel.h"

namespace filedistribution {

VESPA_DEFINE_EXCEPTION(NotPeer, vespalib::Exception);

class FileDistributionModel {
public:
    typedef boost::signals2::signal<void ()> FilesToDownloadChangedSignal;
    typedef std::vector<libtorrent::peer_entry> PeerEntries;

    virtual FileDBModel& getFileDBModel() = 0;

    virtual std::set<std::string> getFilesToDownload() = 0;

    virtual PeerEntries getPeers(const std::string& fileReference, size_t maxPeers) = 0;
    virtual void addPeer(const std::string& fileReference) = 0;
    virtual void removePeer(const std::string& fileReference) = 0;
    virtual void peerFinished(const std::string& fileReference) = 0; //throws NotPeer

    FileDistributionModel(const FileDistributionModel &) = delete;
    FileDistributionModel & operator = (const FileDistributionModel &) = delete;
    FileDistributionModel() = default;
    virtual ~FileDistributionModel() {}

    FilesToDownloadChangedSignal _filesToDownloadChanged;
};

} //namespace filedistribution


