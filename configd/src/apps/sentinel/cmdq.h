// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <vector>
#include <string>

class FRT_RPCRequest;

namespace config::sentinel {

class Cmd {
public:
    using UP = std::unique_ptr<Cmd>;
    enum CmdType { LIST, RESTART, START, STOP, CHECK_CONNECTIVITY };

    Cmd(FRT_RPCRequest *req, CmdType cmdType, std::string name = "", int portnum = 0)
      : _req(req), _cmdType(cmdType), _name(name), _port(portnum)
    {}

    CmdType type() const { return _cmdType; }
    // the service name or host name:
    const std::string & name() const { return _name; }
    int portNumber() const { return _port; }

    void retError(const char *errorString) const;
    void retValue(const char *valueString) const;

    ~Cmd();
private:
    FRT_RPCRequest *_req;
    CmdType _cmdType;
    std::string _name;
    int _port;
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
