// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <libtorrent/session.hpp>
#include <libtorrent/torrent.hpp>

#include <boost/thread.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/asio/io_service.hpp>
#include <boost/asio/deadline_timer.hpp>

#include <vespa/filedistribution/model/filedistributionmodel.h>
#include <vespa/filedistribution/common/exceptionrethrower.h>
#include "scheduler.h"

namespace filedistribution {
class FileDistributionModel;
class FileDownloader;

class FileDistributorTrackerImpl : public FileDistributionTracker  {
    const boost::shared_ptr<ExceptionRethrower>  _exceptionRethrower;
    const boost::shared_ptr<FileDistributionModel> _model;

    typedef boost::lock_guard<boost::mutex> LockGuard;
    boost::mutex _mutex;
    boost::weak_ptr<FileDownloader> _downloader;

    //Use separate worker thread to avoid potential deadlock
    //between tracker requests and files to download changed requests.
    boost::scoped_ptr<Scheduler> _scheduler;
public:
    FileDistributorTrackerImpl(const boost::shared_ptr<FileDistributionModel>& model,
                               const boost::shared_ptr<ExceptionRethrower>& exceptionRethrower);

    virtual ~FileDistributorTrackerImpl();

    //overrides
    void trackingRequest(libtorrent::tracker_request& request,
        const boost::shared_ptr<libtorrent::torrent> & torrent);

    void setDownloader(const boost::shared_ptr<FileDownloader>& downloader);
};

} //namespace filedistribution

