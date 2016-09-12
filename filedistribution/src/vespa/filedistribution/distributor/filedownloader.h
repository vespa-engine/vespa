// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <boost/thread/mutex.hpp>
#include <boost/filesystem/path.hpp>
#include <boost/optional.hpp>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/indexed_by.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/ordered_index.hpp>

#include <libtorrent/session.hpp>

#include <vespa/filedistribution/rpc/fileprovider.h>
#include "hostname.h"
#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exceptionrethrower.h>
#include <vespa/filedistribution/common/exception.h>
#include <vespa/filedistribution/model/filedbmodel.h>

namespace filedistribution {

struct NoSuchTorrentException : public Exception {};

struct FailedListeningException : public Exception {
    FailedListeningException(const std::string& hostName, int port, const std::string & message) {
        *this << errorinfo::HostName(hostName) << errorinfo::Port(port) << errorinfo::TorrentMessage(message);
    }
    FailedListeningException(const std::string& hostName, int port) {
        *this <<errorinfo::HostName(hostName) << errorinfo::Port(port);
    }
};

class FileDownloader
{
    struct EventHandler;
    struct LogSessionDeconstructed {
        ~LogSessionDeconstructed();
    };

    size_t _outstanding_SRD_requests;
    std::shared_ptr<FileDistributionTracker> _tracker;

    boost::mutex _modifyTorrentsDownloadingMutex;
    typedef boost::lock_guard<boost::mutex> LockGuard;

    LogSessionDeconstructed _logSessionDeconstructed;
    //session is safe to use from multiple threads.
    libtorrent::session _session;

    const boost::filesystem::path _dbPath;
    typedef std::vector<char> ResumeDataBuffer;
    boost::optional<ResumeDataBuffer> getResumeData(const std::string& fileReference);

    class RemoveTorrent;

    void deleteTorrentData(const libtorrent::torrent_handle& torrent, LockGuard&);
    void listen();
public:
    // accounting of save-resume-data requests:
    void didRequestSRD() { ++_outstanding_SRD_requests; }
    void didReceiveSRD() { --_outstanding_SRD_requests; }

    typedef FileProvider::DownloadCompletedSignal DownloadCompletedSignal;
    typedef FileProvider::DownloadFailedSignal DownloadFailedSignal;

    FileDownloader(const std::shared_ptr<FileDistributionTracker>& tracker,
                   const std::string& hostName, int port,
                   const boost::filesystem::path& dbPath,
                   const std::shared_ptr<ExceptionRethrower>& exceptionRethrower);
    ~FileDownloader();
    DirectoryGuard::UP getGuard() { return std::make_unique<DirectoryGuard>(_dbPath); }

    void runEventLoop();
    void addTorrent(const std::string& fileReference, const Buffer& buffer);
    bool hasTorrent(const std::string& fileReference) const;
    boost::optional<boost::filesystem::path> pathToCompletedFile(const std::string& fileReference) const;
    void removeAllTorrentsBut(const std::set<std::string> & filesToRetain);

    void signalIfFinishedDownloading(const std::string& fileReference);

    std::string infoHash2FileReference(const libtorrent::sha1_hash& hash);
    void setMaxDownloadSpeed(double MBPerSec);
    void setMaxUploadSpeed(double MBPerSec);

    const std::shared_ptr<ExceptionRethrower> _exceptionRethrower;

    const std::string _hostName;
    const int _port;

    //signals
    DownloadCompletedSignal _downloadCompleted;
    DownloadFailedSignal _downloadFailed; //removed or error
};

} //namespace filedistribution


