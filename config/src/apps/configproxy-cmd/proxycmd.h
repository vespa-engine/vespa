// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/frt.h>

struct Flags {
    vespalib::string method;
    std::vector<vespalib::string> args;
    vespalib::string hostname;
    int portnumber;
    Flags(const Flags &);
    Flags & operator=(const Flags &);
    Flags();
    ~Flags();
};

class ProxyCmd
{
private:
    FRT_Supervisor *_supervisor;
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


