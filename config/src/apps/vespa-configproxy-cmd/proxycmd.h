// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

class FRT_Target;
class FRT_RPCRequest;
class FRT_Values;

namespace fnet::frt { class StandaloneFRT; }

struct Flags {
    vespalib::string method;
    std::vector<vespalib::string> args;
    vespalib::string targethost;
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
    vespalib::string makeSpec();
    void autoPrint();
public:
    ProxyCmd(const Flags& flags);
    virtual ~ProxyCmd();
    int action();
};


