// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filedistributorrpc.h"
#include <mutex>

#include <vespa/fnet/frt/frt.h>
#include <vespa/frtstream/frtserverstream.h>
#include <map>

#include "fileprovider.h"
#include <vespa/filedistribution/model/filedbmodel.h>
#include <vespa/log/log.h>
LOG_SETUP(".filedistributorrpc");

using filedistribution::FileDistributorRPC;
using filedistribution::FileProvider;

namespace fs = boost::filesystem;

namespace {
typedef std::lock_guard<std::mutex> LockGuard;

struct RPCErrorCodes {
    const static uint32_t baseErrorCode = 0x10000;
    const static uint32_t baseFileProviderErrorCode = baseErrorCode + 0x1000;

    const static uint32_t unknownError = baseErrorCode + 1;
};

class QueuedRequests {
    bool _shuttingDown;

    std::mutex _mutex;
    typedef std::multimap<std::string, FRT_RPCRequest*> Map;
    Map _queuedRequests;

    template <class FUNC>
    void returnAnswer(const std::string& fileReference, FUNC func) {
        LockGuard guard(_mutex);

        typedef Map::iterator iterator;
        std::pair<iterator, iterator> range = _queuedRequests.equal_range(fileReference);

        for (iterator it(range.first); it != range.second; it++) {
            const Map::value_type & request(*it);
            LOG(info, "Returning earlier enqueued request for file reference '%s'.", request.first.c_str());
            func(*request.second);
            request.second->Return();
        }

        _queuedRequests.erase(range.first, range.second);
    }

    struct DownloadFinished {
        const std::string& _path;

        void operator()(FRT_RPCRequest& request) {
            LOG(info, "Download finished: '%s'", _path.c_str());
            frtstream::FrtServerStream requestHandler(&request);
            requestHandler <<_path;
        }

        DownloadFinished(const std::string& path)
            :_path(path)
        {}
    };

    struct DownloadFailed {
        FileProvider::FailedDownloadReason _reason;

        void operator()(FRT_RPCRequest& request) {
            LOG(info, "Download failed: '%d'", _reason);
            request.SetError(RPCErrorCodes::baseFileProviderErrorCode + _reason, "Download failed");
        }

        DownloadFailed(FileProvider::FailedDownloadReason reason)
            :_reason(reason)
        {}
    };

public:
    QueuedRequests()
        :_shuttingDown(false)
    {}

    void enqueue(const std::string& fileReference, FRT_RPCRequest* request) {
        LockGuard guard(_mutex);

        if (_shuttingDown) {
            LOG(info, "Shutdown: Aborting request for file reference '%s'.", fileReference.c_str());
            abort(request);
        } else {
            _queuedRequests.insert(std::make_pair(fileReference, request));
        }
    }

    void abort(FRT_RPCRequest* request) {
        request->SetError(FRTE_RPC_ABORT);
        request->Return();
    }

    void dequeue(const std::string& fileReference, FRT_RPCRequest* request) {
        LockGuard guard(_mutex);

        typedef Map::iterator iterator;
        std::pair<iterator, iterator> range = _queuedRequests.equal_range(fileReference);

        iterator candidate = std::find(range.first, range.second,
                std::pair<const std::string, FRT_RPCRequest*>(fileReference, request));

        if (candidate != range.second)
            _queuedRequests.erase(candidate);
    }

    void downloadFinished(const std::string& fileReference, const fs::path& path) {

        DownloadFinished handler(path.string());
        returnAnswer(fileReference, handler);
    }

    void downloadFailed(const std::string& fileReference, FileProvider::FailedDownloadReason reason) {

        DownloadFailed handler(reason);
        returnAnswer(fileReference, handler);
    }

    void shutdown() {
        LockGuard guard(_mutex);
        _shuttingDown = true;

        for (const Map::value_type& request : _queuedRequests) {
            LOG(info, "Shutdown: Aborting earlier enqueued request for file reference '%s'.", request.first.c_str());
            abort(request.second);
        }
        _queuedRequests.erase(_queuedRequests.begin(), _queuedRequests.end());
    }
};

} //anonymous namespace

class FileDistributorRPC::Server : public FRT_Invokable {
  public:
    FileProvider::SP                _fileProvider;
    std::unique_ptr<FRT_Supervisor> _supervisor;

    QueuedRequests _queuedRequests;

    boost::signals2::scoped_connection _downloadCompletedConnection;
    boost::signals2::scoped_connection _downloadFailedConnection;

    void queueRequest(const std::string& fileReference, FRT_RPCRequest* request);
    void defineMethods();

    Server(const Server &) = delete;
    Server & operator = (const Server &) = delete;
    Server(int listen_port, const FileProvider::SP & provider);
    void start(const FileDistributorRPC::SP & parent);
    ~Server();

    void waitFor(FRT_RPCRequest*);
};

FileDistributorRPC::
Server::Server(int listen_port, const FileProvider::SP & provider)
    :_fileProvider(provider),
     _supervisor(new FRT_Supervisor())
{
    defineMethods();
    _supervisor->Listen(listen_port);
    _supervisor->Start();
}


FileDistributorRPC::Server::~Server() {
    _queuedRequests.shutdown();

    const bool waitForFinished = true;
    _supervisor->ShutDown(waitForFinished);
}

void
FileDistributorRPC::Server::start(const FileDistributorRPC::SP & parent) {
    _downloadCompletedConnection =
        _fileProvider->downloadCompleted().connect(FileProvider::DownloadCompletedSignal::slot_type(
                        [&] (const std::string &file, const fs::path& path) { _queuedRequests.downloadFinished(file, path); })
                .track_foreign(parent));

    _downloadFailedConnection =
        _fileProvider->downloadFailed().connect(FileProvider::DownloadFailedSignal::slot_type(
                        [&] (const std::string& file, FileProvider::FailedDownloadReason reason) { _queuedRequests.downloadFailed(file, reason); })
                .track_foreign(parent));


}

void
FileDistributorRPC::
Server::queueRequest(const std::string& fileReference, FRT_RPCRequest* request) {
    _queuedRequests.enqueue( fileReference, request );
    try {
        _fileProvider->downloadFile(fileReference);
    } catch(...) {
        _queuedRequests.dequeue(fileReference, request);
        throw;
    }
}

void
FileDistributorRPC::Server::defineMethods() {
    const bool instant = true;
    FRT_ReflectionBuilder builder(_supervisor.get());
    builder.DefineMethod("waitFor", "s", "s", instant,
        FRT_METHOD(Server::waitFor), this);
}

void
FileDistributorRPC::Server::waitFor(FRT_RPCRequest* request) {
    try {
        frtstream::FrtServerStream requestHandler(request);
        std::string fileReference;
        requestHandler >> fileReference;
        boost::optional<fs::path> path = _fileProvider->getPath(fileReference);
        if (path) {
            LOG(debug, "Returning request for file reference '%s'.", fileReference.c_str());
            requestHandler << path->string();
        } else {
            LOG(debug, "Enqueuing file request for file reference '%s'.", fileReference.c_str());
            request->Detach();
            queueRequest(fileReference, request);
        }
    } catch (const FileDoesNotExistException&) {
        LOG(warning, "Received a request for a file reference that does not exist in zookeeper.");
        request->SetError(RPCErrorCodes::baseFileProviderErrorCode + FileProvider::FileReferenceDoesNotExist,
                          "No such file reference");
        request->Return();
    } catch (const std::exception& e) {
        LOG(error, "An exception occurred while calling the rpc method waitFor:%s", e.what());
        request->SetError(RPCErrorCodes::unknownError, e.what());
        request->Return(); //the request might be detached.
    }
}

FileDistributorRPC::FileDistributorRPC(const std::string& connectionSpec,
                                       const FileProvider::SP & provider)
    :_server(new Server(get_port(connectionSpec), provider))
{}

void
FileDistributorRPC::start()
{
    _server->start(shared_from_this());
}

int
FileDistributorRPC::get_port(const std::string &spec)
{
    const char *port = (spec.data() + spec.size());
    while ((port > spec.data()) && (port[-1] >= '0') && (port[-1] <= '9')) {
        --port;
    }
    return atoi(port);
}

filedistribution::FileDistributorRPC::~FileDistributorRPC()
{
    LOG(debug, "Deconstructing FileDistributorRPC");
}
