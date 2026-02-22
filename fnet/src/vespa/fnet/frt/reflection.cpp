// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reflection.h"

#include "rpcrequest.h"
#include "supervisor.h"
#include "values.h"

FRT_Method::FRT_Method(
    const char* name, const char* paramSpec, const char* returnSpec, FRT_METHOD_PT method, FRT_Invokable* handler)
    : _hashNext(nullptr),
      _listNext(nullptr),
      _name(name),
      _paramSpec(paramSpec),
      _returnSpec(returnSpec),
      _method(method),
      _handler(handler),
      _doc(),
      _access_filter() {}

FRT_Method::~FRT_Method() = default;

void FRT_Method::SetDocumentation(FRT_Values* values) {
    _doc.resize(values->GetLength());

    FNET_DataBuffer buf(&_doc[0], _doc.size());
    values->EncodeCopy(&buf);
}

void FRT_Method::GetDocumentation(FRT_Values* values) {
    FNET_DataBuffer buf(&_doc[0], _doc.size());
    buf.FreeToData(_doc.size());
    values->DecodeCopy(&buf, _doc.size());
}

FRT_ReflectionManager::FRT_ReflectionManager() : _numMethods(0), _methods(nullptr), _methodHash() { Reset(); }

FRT_ReflectionManager::~FRT_ReflectionManager() { Reset(); }

void FRT_ReflectionManager::Reset() {
    _numMethods = 0;
    while (_methods != nullptr) {
        FRT_Method* method = _methods;
        _methods = method->GetNext();
        delete (method);
    }

    for (uint32_t i = 0; i < METHOD_HASH_SIZE; i++)
        _methodHash[i] = nullptr;
}

void FRT_ReflectionManager::AddMethod(FRT_Method* method) {
    uint32_t hash = HashStr(method->GetName(), METHOD_HASH_SIZE);
    method->_hashNext = _methodHash[hash];
    _methodHash[hash] = method;
    method->_listNext = _methods;
    _methods = method;
    _numMethods++;
}

FRT_Method* FRT_ReflectionManager::LookupMethod(const char* name) {
    if (name == nullptr) {
        return nullptr;
    }
    uint32_t    hash = HashStr(name, METHOD_HASH_SIZE);
    FRT_Method* ret = _methodHash[hash];
    while (ret != nullptr && strcmp(name, ret->GetName()) != 0)
        ret = ret->_hashNext;
    return ret;
}

void FRT_ReflectionManager::DumpMethodList(FRT_Values* target) {
    FRT_StringValue* names = target->AddStringArray(_numMethods);
    FRT_StringValue* args = target->AddStringArray(_numMethods);
    FRT_StringValue* ret = target->AddStringArray(_numMethods);
    uint32_t         idx = 0;
    for (FRT_Method* method = _methods; method != nullptr; method = method->GetNext(), idx++) {
        target->SetString(&names[idx], method->GetName());
        target->SetString(&args[idx], method->GetParamSpec());
        target->SetString(&ret[idx], method->GetReturnSpec());
    }
    assert(idx == _numMethods);
}

//------------------------------------------------------------------------

void FRT_ReflectionBuilder::Flush() {
    if (_method == nullptr)
        return;

    for (; _curArg < _argCnt; _curArg++) {
        _values->SetString(&_arg_name[_curArg], "?");
        _values->SetString(&_arg_desc[_curArg], "???");
    }
    for (; _curRet < _retCnt; _curRet++) {
        _values->SetString(&_ret_name[_curRet], "?");
        _values->SetString(&_ret_desc[_curRet], "???");
    }

    _method->SetDocumentation(_values);
    _method->SetRequestAccessFilter(std::move(_access_filter)); // May be nullptr
    _method = nullptr;
    _req->Reset();
}

FRT_ReflectionBuilder::FRT_ReflectionBuilder(FRT_Supervisor* supervisor)
    : _supervisor(supervisor),
      _lookup(supervisor->GetReflectionManager()),
      _method(nullptr),
      _req(supervisor->AllocRPCRequest()),
      _values(_req->GetReturn()),
      _argCnt(0),
      _retCnt(0),
      _curArg(0),
      _curRet(0),
      _arg_name(nullptr),
      _arg_desc(nullptr),
      _ret_name(nullptr),
      _ret_desc(nullptr),
      _access_filter() {}

FRT_ReflectionBuilder::~FRT_ReflectionBuilder() {
    Flush();
    _req->internal_subref();
}

void FRT_ReflectionBuilder::DefineMethod(
    const char* name, const char* paramSpec, const char* returnSpec, FRT_METHOD_PT method, FRT_Invokable* handler) {
    if (handler == nullptr)
        return;

    Flush();
    _method = new FRT_Method(name, paramSpec, returnSpec, method, handler);
    _lookup->AddMethod(_method);

    _argCnt = strlen(paramSpec);
    _retCnt = strlen(returnSpec);
    _curArg = 0;
    _curRet = 0;
    _values->AddString("???"); // method desc
    _values->AddString(paramSpec);
    _values->AddString(returnSpec);
    _arg_name = _values->AddStringArray(_argCnt);
    _arg_desc = _values->AddStringArray(_argCnt);
    _ret_name = _values->AddStringArray(_retCnt);
    _ret_desc = _values->AddStringArray(_retCnt);
    _access_filter.reset();
}

void FRT_ReflectionBuilder::MethodDesc(const char* desc) {
    if (_method == nullptr)
        return;

    _values->SetString(&_values->GetValue(0)._string, desc);
}

void FRT_ReflectionBuilder::ParamDesc(const char* name, const char* desc) {
    if (_method == nullptr)
        return;

    if (_curArg >= _argCnt)
        return;

    _values->SetString(&_arg_name[_curArg], name);
    _values->SetString(&_arg_desc[_curArg], desc);
    _curArg++;
}

void FRT_ReflectionBuilder::ReturnDesc(const char* name, const char* desc) {
    if (_method == nullptr)
        return;

    if (_curRet >= _retCnt)
        return;

    _values->SetString(&_ret_name[_curRet], name);
    _values->SetString(&_ret_desc[_curRet], desc);
    _curRet++;
}

void FRT_ReflectionBuilder::RequestAccessFilter(std::unique_ptr<FRT_RequestAccessFilter> access_filter) {
    if (_method == nullptr) {
        return;
    }
    _access_filter = std::move(access_filter);
}
