// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/filedistribution/rpc/fileprovider.h>
#include <boost/thread/barrier.hpp>

namespace filedistribution {

class MockFileProvider : public FileProvider {
    DownloadCompletedSignal _downloadCompleted;
    DownloadFailedSignal _downloadFailed;
public:
    static const std::string _queueForeverFileReference;

    boost::barrier _queueForeverBarrier;

    boost::optional<Path> getPath(const std::string& fileReference) override {
        if (fileReference == "dd") {
            return Path("direct/result/path");
        } else {
            return boost::optional<Path>();
        }
    }

    void downloadFile(const std::string& fileReference) override {
        if (fileReference == _queueForeverFileReference) {
            _queueForeverBarrier.wait();
            return;
        }

        sleep(1);
        downloadCompleted()(fileReference, "downloaded/path/" + fileReference);
    }

    DownloadCompletedSignal& downloadCompleted() override {
        return _downloadCompleted;
    }

    DownloadFailedSignal& downloadFailed() override {
        return _downloadFailed;
    }

    MockFileProvider()
        :_queueForeverBarrier(2)
    {}
};

} //namespace filedistribution

