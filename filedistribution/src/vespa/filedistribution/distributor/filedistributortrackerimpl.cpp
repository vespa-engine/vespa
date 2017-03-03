// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filedistributortrackerimpl.h"
#include <cmath>
#include <libtorrent/tracker_manager.hpp>
#include <libtorrent/torrent.hpp>
#include <vespa/filedistribution/model/filedistributionmodel.h>
#include <vespa/filedistribution/model/zkfacade.h>
#include "filedownloader.h"
#include "hostname.h"

#include <vespa/log/log.h>
LOG_SETUP(".filedistributiontrackerimpl");

using namespace filedistribution;

typedef FileDistributionModel::PeerEntries PeerEntries;

namespace asio = boost::asio;

namespace {

void
filterSelf(FileDistributionModel::PeerEntries& peers,
           const std::string& hostName,
           int port)
{
    FileDistributionModel::PeerEntries::iterator
        i = peers.begin(),
        currEnd = peers.end();

    while ( i != currEnd ) {
        //hostName is currently used in the ip field
        if (i->ip == hostName && i->port == port) {
            --currEnd;
            std::swap(*i, *currEnd);
        } else {
            ++i;
        }
    }

    peers.erase(currEnd, peers.end());
}

void resolveIPAddresses(PeerEntries& peers) {
    for (auto& p: peers) {
        try {
            p.ip = filedistribution::lookupIPAddress(p.ip);
        } catch (filedistribution::FailedResolvingHostName& e) {
            LOG(info, "Failed resolving address %s", p.ip.c_str());
        }
    }
}

struct TrackingTask : public Scheduler::Task {
    int _numTimesRescheduled;

    libtorrent::tracker_request _trackerRequest;
    boost::weak_ptr<libtorrent::torrent> _torrent;
    std::weak_ptr<FileDownloader> _downloader;
    std::shared_ptr<FileDistributionModel> _model;

    TrackingTask(Scheduler& scheduler,
                 const libtorrent::tracker_request& trackerRequest,
                 const TorrentSP & torrent,
                 const std::weak_ptr<FileDownloader>& downloader,
                 const std::shared_ptr<FileDistributionModel>& model);
    ~TrackingTask();

    //TODO: refactor
    void doHandle();
    PeerEntries getPeers(const std::shared_ptr<FileDownloader>& downloader);
    void reschedule();
};

TrackingTask::TrackingTask(Scheduler& scheduler,
                           const libtorrent::tracker_request& trackerRequest,
                           const TorrentSP & torrent,
                           const std::weak_ptr<FileDownloader>& downloader,
                           const std::shared_ptr<FileDistributionModel>& model)
    : Task(scheduler),
      _numTimesRescheduled(0),
      _trackerRequest(trackerRequest),
      _torrent(torrent),
      _downloader(downloader),
      _model(model)
{ }

TrackingTask::~TrackingTask() {}


//TODO: refactor
void
TrackingTask::doHandle() {
    if (std::shared_ptr<FileDownloader> downloader = _downloader.lock()) {
        //All torrents must be destructed before the session is destructed.
        //It's okay to prevent the torrent from expiring here
        //since the session can't be destructed while
        //we hold a shared_ptr to the downloader.
        if (TorrentSP torrent = _torrent.lock()) {
            PeerEntries peers = getPeers(downloader);

            if (!peers.empty()) {
                torrent->session().m_io_service.dispatch(
                [torrent_weak_ptr = _torrent, trackerRequest = _trackerRequest, peers = peers]() mutable {
                    if (auto torrent_sp = torrent_weak_ptr.lock()) {
                        torrent_sp->tracker_response(
                                trackerRequest,
                                libtorrent::address(),
                                std::list<libtorrent::address>(),
                                peers,
                                -1, -1, -1, -1, -1,
                                libtorrent::address(), "trackerid");
                    }
                });
            }

            if (peers.size() < 5) {
                reschedule();
            }
        }
    }
}

PeerEntries
TrackingTask::getPeers(const std::shared_ptr<FileDownloader>& downloader) {
    std::string fileReference = downloader->infoHash2FileReference(_trackerRequest.info_hash);

    const size_t recommendedMaxNumberOfPeers = 30;
    PeerEntries peers = _model->getPeers(fileReference, recommendedMaxNumberOfPeers);

    //currently, libtorrent stops working if it tries to connect to itself.
    filterSelf(peers, downloader->_hostName, downloader->_port);
    resolveIPAddresses(peers);
    for (const auto& peer: peers) {
        LOG(debug, "Returning peer with ip %s", peer.ip.c_str());
    }

    return peers;
}

void
TrackingTask::reschedule() {
    if (_numTimesRescheduled < 5) {
        double fudgeFactor = 0.1;
        schedule(boost::posix_time::seconds(static_cast<int>(
                                                    std::pow(3., _numTimesRescheduled) + fudgeFactor)));
        _numTimesRescheduled++;
    }
}

} //anonymous namespace

FileDistributorTrackerImpl::FileDistributorTrackerImpl(const std::shared_ptr<FileDistributionModel>& model) :
     _model(model)
{}

FileDistributorTrackerImpl::~FileDistributorTrackerImpl() {
    LOG(debug, "Deconstructing FileDistributorTrackerImpl");

    LockGuard guard(_mutex);
    _scheduler.reset();
}

void
FileDistributorTrackerImpl::trackingRequest(
        libtorrent::tracker_request& request,
        const TorrentSP & torrent)
{
    LockGuard guard(_mutex);

    if (torrent != TorrentSP()) {
        std::shared_ptr<TrackingTask> trackingTask(new TrackingTask(
                        *_scheduler.get(), request, torrent, _downloader, _model));

        trackingTask->scheduleNow();
    }
}

void asioWorker(asio::io_service& ioService)
{
    while (!ioService.stopped()) {
        try {
            ioService.run();
        } catch (const ZKConnectionLossException & e) {
            LOG(info, "Connection loss in asioWorker thread, resuming. %s", e.what());
        } catch (const ZKOperationTimeoutException & e) {
            LOG(warning, "Operation timed out in asioWorker thread, will do quick exit to start a clean sheet. %s", e.what());
            std::quick_exit(31);
        }
    }
}

void
FileDistributorTrackerImpl::setDownloader(const std::shared_ptr<FileDownloader>& downloader)
{
    LockGuard guard(_mutex);

    _scheduler.reset();
    _downloader = downloader;

    if (downloader) {
        _scheduler.reset(new Scheduler([] (asio::io_service& ioService) { asioWorker(ioService); }));
    }
}
