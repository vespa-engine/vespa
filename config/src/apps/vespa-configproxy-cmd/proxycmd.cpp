// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proxycmd.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <iostream>

Flags::Flags(const Flags &) = default;
Flags & Flags::operator=(const Flags &) = default;
Flags::Flags()
    : method("cache"),
      args(),
      targethost("localhost"),
      portnumber(19090)
{ }
Flags::~Flags() = default;

ProxyCmd::ProxyCmd(const Flags& flags)
    : _server(),
      _target(nullptr),
      _req(nullptr),
      _flags(flags)
{ }

ProxyCmd::~ProxyCmd() = default;

void ProxyCmd::initRPC() {
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
    _req = _server->supervisor().AllocRPCRequest();
}

void ProxyCmd::invokeRPC() {
    if (_req == nullptr) return;
    _target->InvokeSync(_req, 65.0);
}

void ProxyCmd::finiRPC() {
    if (_req != nullptr) {
        _req->internal_subref();
        _req = nullptr;
    }
    if (_target != nullptr) {
        _target->internal_subref();
        _target = NULL;
    }
    _server.reset();
}

void ProxyCmd::printArray(FRT_Values *rvals) {
    FRT_Value &lines = rvals->GetValue(0);
    for (size_t i = 0; i < lines._string_array._len; ++i) {
        std::cout << lines._string_array._pt[i]._str << std::endl;
    }
}

vespalib::string ProxyCmd::makeSpec() {
    return vespalib::make_string("tcp/%s:%d", _flags.targethost.c_str(), _flags.portnumber);
}

void ProxyCmd::autoPrint() {
    if (_req->IsError()) {
        std::cerr << "FAILURE ["<< _req->GetMethodName() <<"]: " << _req->GetErrorMessage() << std::endl;
        return;
    }
    vespalib::string retspec = _req->GetReturnSpec();
    FRT_Values *rvals = _req->GetReturn();
    if (retspec == "S") {
        printArray(rvals);
    } else if (retspec == "s") {
        std::cout << rvals->GetValue(0)._string._str << std::endl;
    } else if (retspec == "i") {
        std::cout << rvals->GetValue(0)._intval32 << std::endl;
    } else {
        _req->Print();
    }
}

int ProxyCmd::action() {
    int errors = 0;
    initRPC();
    vespalib::string spec = makeSpec();
    _target = _server->supervisor().GetTarget(spec.c_str());
    _req->SetMethodName(_flags.method.c_str());
    FRT_Values &params = *_req->GetParams();
    for (size_t i = 0; i < _flags.args.size(); ++i) {
        params.AddString(_flags.args[i].c_str(), _flags.args[i].size());
    }
    invokeRPC();
    if (_req->IsError()) ++errors;
    autoPrint();
    finiRPC();
    return errors;
}
