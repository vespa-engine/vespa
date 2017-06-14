// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <mutex>
#include <boost/optional.hpp>

#include <libtorrent/session.hpp>

#include <vespa/filedistribution/rpc/fileprovider.h>
#include "hostname.h"
#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>
#include <vespa/filedistribution/model/filedbmodel.h>

namespace filedistribution {

VESPA_DEFINE_EXCEPTION(NoSuchTorrentException, vespalib::Exception);

class FileDownloader
{
    struct EventHandler;
    struct LogSessionDeconstructed {
        ~LogSessionDeconstructed();
    };

    std::atomic<size_t> _outstanding_SRD_requests;
    std::shared_ptr<FileDistributionTracker> _tracker;

    std::mutex _modifyTorrentsDownloadingMutex;
    typedef std::lock_guard<std::mutex> LockGuard;

    LogSessionDeconstructed _logSessionDeconstructed;
    //session is safe to use from multiple threads.
    libtorrent::session _session;
    std::atomic<bool> _closed;

    const Path _dbPath;
    typedef std::vector<char> ResumeDataBuffer;
    boost::optional<ResumeDataBuffer> getResumeData(const std::string& fileReference);

    class RemoveTorrent;

    void deleteTorrentData(const libtorrent::torrent_handle& torrent, LockGuard&);
    void listen();
    bool closed() const;
    void drain();
public:
    // accounting of save-resume-data requests:
    void didRequestSRD() { ++_outstanding_SRD_requests; }
    void didReceiveSRD() { --_outstanding_SRD_requests; }

    typedef FileProvider::DownloadCompletedSignal DownloadCompletedSignal;
    typedef FileProvider::DownloadFailedSignal DownloadFailedSignal;

    FileDownloader(const std::shared_ptr<FileDistributionTracker>& tracker,
                   const std::string& hostName, int port,
                   const Path& dbPath);
    ~FileDownloader();
    DirectoryGuard::UP getGuard() { return std::make_unique<DirectoryGuard>(_dbPath); }

    void runEventLoop();
    void addTorrent(const std::string& fileReference, const Buffer& buffer);
    bool hasTorrent(const std::string& fileReference) const;
    boost::optional<Path> pathToCompletedFile(const std::string& fileReference) const;
    void removeAllTorrentsBut(const std::set<std::string> & filesToRetain);

    void signalIfFinishedDownloading(const std::string& fileReference);

    std::string infoHash2FileReference(const libtorrent::sha1_hash& hash);
    void setMaxDownloadSpeed(double MBPerSec);
    void setMaxUploadSpeed(double MBPerSec);
    void close();
    bool drained() const { return _outstanding_SRD_requests == 0; }

    const std::string _hostName;
    const int _port;

    //signals
    DownloadCompletedSignal _downloadCompleted;
    DownloadFailedSignal _downloadFailed; //removed or error
};

} //namespace filedistribution


