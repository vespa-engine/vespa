// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/filedistribution/common/exception.h>
#include<boost/optional.hpp>
#include<boost/signals2.hpp>

namespace filedistribution {

class FileProvider
{
public:
    using SP = std::shared_ptr<FileProvider>;
    typedef boost::signals2::signal<void (const std::string& /* fileReference */, const Path&)> DownloadCompletedSignal;
    typedef DownloadCompletedSignal::slot_type DownloadCompletedHandler;

    enum FailedDownloadReason {
        FileReferenceDoesNotExist,
        FileReferenceRemoved
    };

    typedef boost::signals2::signal<void (const std::string& /* fileReference */, FailedDownloadReason)> DownloadFailedSignal;
    typedef DownloadFailedSignal::slot_type DownloadFailedHandler;

    virtual boost::optional<Path> getPath(const std::string& fileReference) = 0;
    virtual void downloadFile(const std::string& fileReference) = 0;

    virtual ~FileProvider() {}

    //Signals
    virtual DownloadCompletedSignal& downloadCompleted() = 0;
    virtual DownloadFailedSignal& downloadFailed() = 0;
};

} //namespace filedistribution

