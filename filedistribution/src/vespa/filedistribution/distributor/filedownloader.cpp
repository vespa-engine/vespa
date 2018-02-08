// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filedownloader.h"
#include "hostname.h"
#include <vespa/filedistribution/model/zkfacade.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <boost/filesystem.hpp>
#include <boost/filesystem/fstream.hpp>
#include <boost/filesystem/convenience.hpp>
#include <boost/function_output_iterator.hpp>

#include <libtorrent/alert.hpp>
#include <libtorrent/alert_types.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/bencode.hpp>

#include <iterator>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".filedownloader");

using namespace filedistribution;
namespace fs = boost::filesystem;

using libtorrent::sha1_hash;
using libtorrent::torrent_handle;

namespace {
const std::string resumeDataSuffix = ".resume";
const std::string resumeDataSuffixTemp = ".resumetemp";
const std::string newFileSuffix = ".new";

std::string
fileReferenceToString(const libtorrent::sha1_hash& fileReference) {
    std::ostringstream fileReferenceString;
    fileReferenceString <<fileReference;

    assert (fileReferenceString.str().size() == 40);
    return fileReferenceString.str();
}

libtorrent::sha1_hash
toInfoHash(const std::string& fileReference) {
    assert (fileReference.size() == 40);
    std::istringstream s(fileReference);

    sha1_hash infoHash;
    s >> infoHash;
    return infoHash;
}

void
addNewFile(const fs::path& dbPath, const fs::path& newFile) {
    LOG(debug, "Adding new file: '%s'.", newFile.string().c_str());
    const fs::path destination = dbPath / newFile.stem();

    if ( fs::exists(destination) ) {
        fs::remove_all(destination);
    }

    fs::path resumeData = destination.string() + resumeDataSuffix;
    if ( fs::exists(resumeData) ) {
        fs::remove(resumeData);
    }

    fs::rename(newFile, destination);
}

void
addNewDbFiles(const fs::path& dbPath) {
    for (fs::directory_iterator i(dbPath), end; i != end; ++i) {
        if (newFileSuffix == fs::extension(*i)) {
            addNewFile(dbPath, *i);
        }
    }
}

fs::path
resumeDataPath(const libtorrent::torrent_handle& torrent) {
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    fs::path tmp(torrent.save_path());
#pragma GCC diagnostic pop
    return tmp.string() + resumeDataSuffix;
}

fs::path
resumeDataPathTemp(const libtorrent::torrent_handle& torrent) {
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    fs::path tmp(torrent.save_path());
#pragma GCC diagnostic pop
    return tmp.string() + resumeDataSuffixTemp;
}

fs::path
getMainFile(const libtorrent::torrent_handle& handle) {
    const libtorrent::torrent_info fallback(handle.info_hash());
    auto p = handle.torrent_file();
    const libtorrent::torrent_info& info = (p ? *p : fallback);
    return info.files().num_files() == 1 ?
        info.file_at(0).path :
        info.name();
}

std::string
getMainName(const libtorrent::torrent_handle& handle) {
    const libtorrent::torrent_info fallback(handle.info_hash());
    auto p = handle.torrent_file();
    const libtorrent::torrent_info& info = (p ? *p : fallback);
    return info.name();
}

libtorrent::session_settings
createSessionSettings() {
    libtorrent::session_settings s;

    const int unlimited = -1;
    s.active_downloads = s.active_seeds = s.active_limit = unlimited;

    s.min_reconnect_time = 1; //seconds
    s.min_announce_interval = 5 * 60; //seconds
    return s;
}

} //anonymous namespace

namespace filedistribution {
    VESPA_IMPLEMENT_EXCEPTION(NoSuchTorrentException, vespalib::Exception);
}

struct FileDownloader::EventHandler
{
    FileDownloader& _fileDownloader;

    EventHandler(FileDownloader* fileDownloader)
        : _fileDownloader(*fileDownloader)
    {}

    void defaultHandler(const libtorrent::alert& alert) const {
        LOG(debug, "alert %s: %s", alert.what(), alert.message().c_str());
    }

    void operator()(const libtorrent::listen_failed_alert& alert) const {
        throw vespalib::PortListenException(alert.endpoint.port(), alert.endpoint.address().to_string(), alert.message(), VESPA_STRLOC);
    }
    void operator()(const libtorrent::fastresume_rejected_alert& alert) const {
        LOG(debug, "alert %s: %s", alert.what(), alert.message().c_str());
    }
    void operator()(const libtorrent::torrent_delete_failed_alert& alert) const {
        LOG(warning, "alert %s: %s", alert.what(), alert.message().c_str());
    }
    void operator()(const libtorrent::file_error_alert& alert) const {
        LOG(error, "alert %s: %s", alert.what(), alert.message().c_str());
    }

    void operator()(const libtorrent::torrent_finished_alert& alert) const {
        defaultHandler(alert);

        std::string fileReference =  fileReferenceToString(alert.handle.info_hash());

        LOG(debug, "File '%s' with file reference '%s' downloaded successfully.",
            getMainName(alert.handle).c_str(),
            fileReference.c_str());

        _fileDownloader.signalIfFinishedDownloading(fileReference);
        alert.handle.save_resume_data();
        _fileDownloader.didRequestSRD();
    }

    void operator()(const libtorrent::save_resume_data_failed_alert& alert) const {
        LOG(warning, "save resume data failed: %s -- %s",
            alert.what(), alert.message().c_str());
        _fileDownloader.didReceiveSRD();
    }

    void operator()(const libtorrent::save_resume_data_alert& alert) const {
        defaultHandler(alert);

        fs::ofstream resumeFile(resumeDataPathTemp(alert.handle), std::ios_base::binary);
        resumeFile.unsetf(std::ios_base::skipws);
        libtorrent::bencode(std::ostream_iterator<char>(resumeFile), *alert.resume_data);
        resumeFile.close();
        fs::rename(resumeDataPathTemp(alert.handle), resumeDataPath(alert.handle));
        _fileDownloader.didReceiveSRD();
    }

    void handle(std::unique_ptr<libtorrent::alert> alert) {
        try {
            libtorrent::handle_alert<
                    libtorrent::torrent_finished_alert,
                    libtorrent::save_resume_data_alert,
                    libtorrent::save_resume_data_failed_alert,
                    libtorrent::listen_failed_alert,
                    libtorrent::file_error_alert,
                    libtorrent::fastresume_rejected_alert,
                    libtorrent::torrent_delete_failed_alert>
                dispatch(alert, *this);
        } catch (libtorrent::unhandled_alert& e) {
            LOG(debug, "alert (ignored): %s -- %s",
                    alert->what(), alert->message().c_str());
        }
    }
};

FileDownloader::LogSessionDeconstructed::~LogSessionDeconstructed()
{
    LOG(debug, "Libtorrent session closed successfully.");
}

FileDownloader::FileDownloader(const std::shared_ptr<FileDistributionTracker>& tracker,
                               const std::string& hostName, int port,
                               const fs::path& dbPath)
   : _outstanding_SRD_requests(0),
     _tracker(tracker),
     _session(tracker.get(), libtorrent::fingerprint("vp", 0, 0, 0, 0), 0),
     _closed(false),
     _dbPath(dbPath),
     _hostName(hostName),
     _port(port)
{
    if (!fs::exists(_dbPath)) {
        fs::create_directories(_dbPath);
    }
    addNewDbFiles(_dbPath);

    _session.set_settings(createSessionSettings());
    listen();
    _session.set_alert_mask(
            libtorrent::alert::error_notification |
            libtorrent::alert::status_notification);

}

void
FileDownloader::drain() {
    EventHandler eventHandler(this);
    size_t cnt = 0;
    size_t waitCount = 0;
    do {
        LOG(debug, "destructor waiting for %zu SRD alerts", _outstanding_SRD_requests.load());
        while (_session.wait_for_alert(libtorrent::milliseconds(20))) {
            std::unique_ptr<libtorrent::alert> alert = _session.pop_alert();
            eventHandler.handle(std::move(alert));
            ++cnt;
        }
        waitCount++;
    } while (!drained() && (waitCount < 1000));
    LOG(debug, "handled %zu alerts during draining.", cnt);
    if (!drained()) {
        LOG(error, "handled %zu alerts during draining. But there are still %zu left.", cnt, _outstanding_SRD_requests.load());
        LOG(error, "We have been waiting for stuff that did not happen.");
    }
}

FileDownloader::~FileDownloader() {
    assert(drained());
}

void
FileDownloader::listen() {
    boost::system::error_code ec;
    _session.listen_on(std::make_pair(_port, _port), // (min, max)
                       ec, 0, // network interface (default value)
                       libtorrent::session::listen_no_system_port); 
    if (!ec && (_session.listen_port() == _port)) {
        return;
    }
    throw vespalib::PortListenException(_port, _hostName, VESPA_STRLOC);
}

boost::optional< fs::path >
FileDownloader::pathToCompletedFile(const std::string& fileReference) const {
    libtorrent::torrent_handle torrent = _session.find_torrent(toInfoHash(fileReference));

#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    if (torrent.is_valid() && torrent.is_finished()) {
#pragma GCC diagnostic pop
        return _dbPath / fileReference / getMainFile(torrent);
    } else {
        return boost::optional< fs::path>();
    }
}


boost::optional<FileDownloader::ResumeDataBuffer>
FileDownloader::getResumeData(const std::string& fileReference) {
    LOG(debug, "Reading resume data for '%s'", fileReference.c_str());
    try {
        fs::path path = (_dbPath / fileReference).string() + resumeDataSuffix;
        if (fs::exists(path)) {
            fs::ifstream file(path, std::ios::binary);
            ResumeDataBuffer result;

            std::istream_iterator<char> iterator(file), end;
            std::copy(iterator, end, std::back_inserter(result));
            LOG(debug, "Successfully retrieved resume data for '%s'", fileReference.c_str());
            if (result.size() < 50) {
                LOG(info, "Very small resume file %zu bytes.", result.size());
            }

            return result;
        }
    } catch(...) {
        //resume data is only an optimization
        LOG(info, "Error while reading resume data for '%s'", fileReference.c_str());
    }
    return boost::optional<ResumeDataBuffer>();
}


bool
FileDownloader::hasTorrent(const std::string& fileReference) const {
    return _session.find_torrent(toInfoHash(fileReference)).is_valid();
}

void
FileDownloader::addTorrent(const std::string& fileReference, const Buffer& buffer) {
    if (closed()) { return; }
    LockGuard guard(_modifyTorrentsDownloadingMutex);

    boost::optional<ResumeDataBuffer> resumeData = getResumeData(fileReference);

    if (_session.find_torrent( (toInfoHash(fileReference))).is_valid())
        return;

    libtorrent::lazy_entry entry;
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    libtorrent::lazy_bdecode(&*buffer.begin(), &*buffer.end(), entry); //out
#pragma GCC diagnostic pop

    libtorrent::add_torrent_params torrentParams;
    torrentParams.save_path = (_dbPath / fileReference).string();
    torrentParams.ti = new libtorrent::torrent_info(entry);

    torrentParams.auto_managed = false;
    torrentParams.paused = false;

    if (resumeData) {
        torrentParams.resume_data = *resumeData; //vector will be swapped
    }

    libtorrent::torrent_handle torrentHandle = _session.add_torrent(torrentParams);

    LOG(debug, "Started downloading file '%s' with file reference '%s'.",
        getMainName(torrentHandle).c_str(), fileReference.c_str());
}


void
FileDownloader::deleteTorrentData(const libtorrent::torrent_handle& torrent, LockGuard&) {
    if (torrent.is_valid()) {
        fs::path resumeFilePath = resumeDataPath(torrent);

#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
        fs::path savePath = torrent.save_path();
#pragma GCC diagnostic pop

        //the files might not exist, so ignore return value
        fs::remove_all(savePath);
        fs::remove(resumeFilePath);
    }

    _downloadFailed(fileReferenceToString(torrent.info_hash()), FileProvider::FileReferenceRemoved);
}

void
FileDownloader::removeAllTorrentsBut(const std::set<std::string> & filesToRetain) {
    if (closed()) { return; }
    LockGuard guard(_modifyTorrentsDownloadingMutex);

    std::set<std::string> currentFiles;
    std::set<sha1_hash> infoHashesToRetain;
    for (const std::string& fileReference : filesToRetain) {
        infoHashesToRetain.insert(toInfoHash(fileReference));
    }

    std::vector<torrent_handle> torrents = _session.get_torrents();

    for (torrent_handle torrent : torrents) {
        if (!infoHashesToRetain.count(torrent.info_hash())) {
            LOG(info, "Removing torrent: '%s' with file reference '%s'",
                getMainName(torrent).c_str(), fileReferenceToString(torrent.info_hash()).c_str());

            deleteTorrentData(torrent, guard);
            _session.remove_torrent(torrent);
        }
    }
}


void FileDownloader::runEventLoop() {
    EventHandler eventHandler(this);
    while ( ! closed() ) {
        try {
            if (_session.wait_for_alert(libtorrent::milliseconds(100))) {
                std::unique_ptr<libtorrent::alert> alert = _session.pop_alert();
                eventHandler.handle(std::move(alert));
            }
        } catch (const ZKConnectionLossException & e) {
            LOG(info, "Connection loss in downloader event loop, resuming. %s", e.what());
        } catch (const vespalib::PortListenException & e) {
            LOG(warning, "Failed listening to torrent port : %s", e.what());
            std::quick_exit(21);
        } catch (const boost::filesystem::filesystem_error & e) {
            LOG(warning, "Some boost file operations failed : %s", e.what());
            std::quick_exit(22);
        }
    }
    drain();
}

bool
FileDownloader::closed() const
{
    return _closed.load();
}

void
FileDownloader::close()
{
    _closed.store(true);
}

void
FileDownloader::signalIfFinishedDownloading(const std::string& fileReference) {
    boost::optional<fs::path> path = pathToCompletedFile(fileReference);

    if (path) {
        _downloadCompleted(fileReference, *path);
    }
}

std::string
FileDownloader::infoHash2FileReference(const libtorrent::sha1_hash& infoHash) {
    //TODO
    return fileReferenceToString(infoHash);
}

namespace {
int
toBytesPerSec(double MBPerSec) {
    return static_cast<int>(MBPerSec * 1024 * 1024);
}
}

void
FileDownloader::setMaxDownloadSpeed(double MBPerSec) {
    LOG(config, "Setting max download speed to %f MB/sec",  MBPerSec);
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    _session.set_download_rate_limit(toBytesPerSec(MBPerSec));
#pragma GCC diagnostic pop
}

void
FileDownloader::setMaxUploadSpeed(double MBPerSec) {
    LOG(config, "Setting max upload speed to %f MB/sec",  MBPerSec);
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    _session.set_upload_rate_limit(toBytesPerSec(MBPerSec));
#pragma GCC diagnostic pop
}
