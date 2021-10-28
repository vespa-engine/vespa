// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <vector>

class FRT_RPCRequest;

namespace config::sentinel {

class Cmd {
public:
    using UP = std::unique_ptr<Cmd>;
    enum CmdType { LIST, RESTART, START, STOP };

    Cmd(FRT_RPCRequest *req, CmdType cmdType, const char *service = "")
      : _req(req), _cmdType(cmdType), _serviceName(service)
    {}

    CmdType type() const { return _cmdType; }
    const char *serviceName() const { return _serviceName; }

    void retError(const char *errorString) const;
    void retValue(const char *valueString) const;

    ~Cmd();
private:
    FRT_RPCRequest *_req;
    CmdType _cmdType;
    const char *_serviceName;
};

class CommandQueue
{
private:
    std::mutex _lock;
    std::vector<Cmd::UP> _queue;
public:
    CommandQueue() = default;
    ~CommandQueue() = default;

    void enqueue(Cmd::UP cmd) {
        std::lock_guard guard(_lock);
        _queue.push_back(std::move(cmd));
    }

    std::vector<Cmd::UP> drain() {
        std::vector<Cmd::UP> r;
        std::lock_guard guard(_lock);
        r.swap(_queue);
        return r;
    }

};

} // namespace config::sentinel
