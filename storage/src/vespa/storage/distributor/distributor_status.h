#pragma once

#include <condition_variable>
#include <iosfwd>
#include <mutex>

namespace storage::framework {
class HttpUrlPath;
class StatusReporter;
}

namespace storage::distributor {

class DelegatedStatusRequest;

// TODO description
class DistributorStatus {
    const DelegatedStatusRequest& _request;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _done;

public:
    DistributorStatus(const DelegatedStatusRequest& request) noexcept
        : _request(request),
          _lock(),
          _cond(),
          _done(false)
    {}

    std::ostream& getStream();
    const framework::HttpUrlPath& getPath() const;
    const framework::StatusReporter& getReporter() const;

    void notifyCompleted();
    void waitForCompletion();
};

}
