// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>
#include <vector>

class FRT_Target;
class FRT_RPCRequest;
class FRT_Values;

namespace fnet::frt { class StandaloneFRT; }

struct Flags {
    std::string method;
    std::vector<std::string> args;
    std::string targethost;
    int portnumber;
    Flags(const Flags &);
    Flags & operator=(const Flags &);
    Flags();
    ~Flags();
};

class ProxyCmd
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT> _server;
    FRT_Target     *_target;
    FRT_RPCRequest *_req;
    Flags           _flags;

    void initRPC();
    void invokeRPC();
    void finiRPC();
    void printArray(FRT_Values *rvals);
    std::string makeSpec();
    void autoPrint();
public:
    ProxyCmd(const Flags& flags);
    virtual ~ProxyCmd();
    int action();
};


