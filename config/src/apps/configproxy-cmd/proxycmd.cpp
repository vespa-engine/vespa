// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proxycmd.h"
#include <iostream>
#include <vespa/vespalib/util/stringfmt.h>

Flags::Flags(const Flags &) = default;
Flags & Flags::operator=(const Flags &) = default;
Flags::Flags()
    : method("cache"),
      args(),
      hostname("localhost"),
      portnumber(19090)
{ }
Flags::~Flags() { }

ProxyCmd::ProxyCmd(const Flags& flags)
    : _supervisor(NULL),
      _target(NULL),
      _req(NULL),
      _flags(flags)
{ }

ProxyCmd::~ProxyCmd() { }

void ProxyCmd::initRPC() {
    _supervisor = new FRT_Supervisor();
    _req = _supervisor->AllocRPCRequest();
    _supervisor->Start();
}

void ProxyCmd::invokeRPC() {
    if (_req == NULL) return;
    _target->InvokeSync(_req, 65.0);
}

void ProxyCmd::finiRPC() {
    if (_req != NULL) {
        _req->SubRef();
        _req = NULL;
    }
    if (_target != NULL) {
        _target->SubRef();
        _target = NULL;
    }
    if (_supervisor != NULL) {
        _supervisor->ShutDown(true);
        delete _supervisor;
        _supervisor = NULL;
    }
}

void ProxyCmd::printArray(FRT_Values *rvals) {
    FRT_Value &lines = rvals->GetValue(0);
    for (size_t i = 0; i < lines._string_array._len; ++i) {
        std::cout << lines._string_array._pt[i]._str << std::endl;
    }
}

vespalib::string ProxyCmd::makeSpec() {
    return vespalib::make_string("tcp/%s:%d", _flags.hostname.c_str(), _flags.portnumber);
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
    _target = _supervisor->GetTarget(spec.c_str());
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
