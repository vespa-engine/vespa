// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <libtorrent/session.hpp>
#include <libtorrent/torrent.hpp>

#include <vespa/filedistribution/model/filedistributionmodel.h>
#include "scheduler.h"
#include <mutex>

namespace filedistribution {
class FileDistributionModel;
class FileDownloader;

using TorrentSP = boost::shared_ptr<libtorrent::torrent>;

class FileDistributorTrackerImpl : public FileDistributionTracker  {
    const std::shared_ptr<FileDistributionModel> _model;

    typedef std::lock_guard<std::mutex> LockGuard;
    std::mutex _mutex;
    std::weak_ptr<FileDownloader> _downloader;

    //Use separate worker thread to avoid potential deadlock
    //between tracker requests and files to download changed requests.
    std::unique_ptr<Scheduler> _scheduler;
public:
    FileDistributorTrackerImpl(const std::shared_ptr<FileDistributionModel>& model);

    virtual ~FileDistributorTrackerImpl();

    //overrides
    void trackingRequest(libtorrent::tracker_request& request, const TorrentSP & torrent) override;

    void setDownloader(const std::shared_ptr<FileDownloader>& downloader);
};

} //namespace filedistribution

