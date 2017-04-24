// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN

#include <vespa/filedistribution/distributor/filedownloader.h>
#include <vespa/filedistribution/distributor/filedistributortrackerimpl.h>

#include <fstream>

#include <boost/test/unit_test.hpp>
#include <boost/filesystem.hpp>
#include <boost/filesystem/fstream.hpp>

#include <vespa/filedistribution/manager/createtorrent.h>
#include <vespa/filedistribution/common/componentsdeleter.h>

namespace fs = boost::filesystem;

using namespace filedistribution;

namespace {
const std::string localHost("localhost");
const int uploaderPort = 9113;
const int downloaderPort = 9112;

#if 0
std::shared_ptr<FileDownloader>
createDownloader(ComponentsDeleter& deleter,
                 int port, const fs::path& downloaderPath,
                 const std::shared_ptr<FileDistributionModel>& model)
{
    std::shared_ptr<FileDistributorTrackerImpl> tracker(deleter.track(new FileDistributorTrackerImpl(model)));
    std::shared_ptr<FileDownloader> downloader(deleter.track(new FileDownloader(tracker,
                            localHost, port, downloaderPath)));

    tracker->setDownloader(downloader);
    return downloader;
}
#endif

} //anonymous namespace

class MockFileDistributionModel : public FileDistributionModel {
    FileDBModel& getFileDBModel() override {
        abort();
    }

    std::set<std::string> getFilesToDownload() override {
        return std::set<std::string>();
    }

    PeerEntries getPeers(const std::string& , size_t) override {
         PeerEntries peers(2);
        peers[0].ip = localHost;
        peers[0].port = uploaderPort;

        peers[1].ip = localHost;
        peers[1].port = downloaderPort;

        return peers;
    }

    void addPeer(const std::string&) override {}
    void removePeer(const std::string&) override {}
    void peerFinished(const std::string&) override {}
};


#if 0
BOOST_AUTO_TEST_CASE(fileDownloaderTest) {
    fs::path testPath = "/tmp/filedownloadertest";
    fs::remove_all(testPath);

    fs::path downloaderPath = testPath / "downloader";
    fs::path uploaderPath   = testPath / "uploader";

    const std::string fileReference = "0123456789012345678901234567890123456789";
    const std::string fileToSend = "filetosend.txt";

    fs::create_directories(downloaderPath);
    fs::create_directories(uploaderPath / fileReference);

    fs::path fileToUploadPath = uploaderPath / fileReference / "filetosend.txt";

    {
    fs::ofstream stream(fileToUploadPath);
    stream <<"Hello, world!" <<std::endl;
    }

    CreateTorrent createTorrent(fileToUploadPath);
    Buffer buffer(createTorrent.bencode());

    ComponentsDeleter deleter;

    std::shared_ptr<FileDistributionModel> model(deleter.track(new MockFileDistributionModel()));
    std::shared_ptr<FileDownloader> downloader =
        createDownloader(deleter, downloaderPort, downloaderPath, model);

    std::shared_ptr<FileDownloader> uploader =
        createDownloader(deleter, uploaderPort, uploaderPath, model);

    std::thread uploaderThread( [uploader] () { uploader->runEventLoop(); });
    std::thread downloaderThread( [downloader] () { downloader->runEventLoop(); });

    uploader->addTorrent(fileReference, buffer);
    downloader->addTorrent(fileReference, buffer);

    sleep(5);
    BOOST_CHECK(fs::exists(downloaderPath / fileReference / fileToSend));

    uploaderThread.interrupt();
    uploaderThread.join();

    downloaderThread.interrupt();
    downloaderThread.join();

    fs::remove_all(testPath);
}
#endif

//TODO: cleanup
libtorrent::sha1_hash
toInfoHash(const std::string& fileReference) {
    assert (fileReference.size() == 40);
    std::istringstream s(fileReference);

    libtorrent::sha1_hash infoHash;
    s >> infoHash;
    return infoHash;
}

BOOST_AUTO_TEST_CASE(test_filereference_infohash_conversion) {
    const std::string fileReference = "3a281c905c9b6ebe4d969037a198454fedefbdf3";

    libtorrent::sha1_hash infoHash = toInfoHash(fileReference);

    std::ostringstream fileReferenceString;
    fileReferenceString <<infoHash;

    BOOST_CHECK(fileReference == fileReferenceString.str());

    std::cout <<fileReference <<std::endl <<fileReferenceString.str() <<std::endl;
}
