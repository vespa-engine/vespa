// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "invokable.h"
#include "request_access_filter.h"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

class FRT_Values;
class FRT_Supervisor;
struct FRT_StringValue;

class FRT_Method {
    friend class FRT_ReflectionManager;

private:
    FRT_Method*                              _hashNext;      // list of methods in hash bucket
    FRT_Method*                              _listNext;      // list of all methods
    std::string                              _name;          // method name
    std::string                              _paramSpec;     // method parameter spec
    std::string                              _returnSpec;    // method return spec
    FRT_METHOD_PT                            _method;        // method pointer
    FRT_Invokable*                           _handler;       // method handler
    std::vector<char>                        _doc;           // method documentation
    std::unique_ptr<FRT_RequestAccessFilter> _access_filter; // (optional) access filter

public:
    FRT_Method(const FRT_Method&) = delete;
    FRT_Method& operator=(const FRT_Method&) = delete;
    FRT_Method(const char* name, const char* paramSpec, const char* returnSpec, FRT_METHOD_PT method,
               FRT_Invokable* handler);

    ~FRT_Method();

    FRT_Method*                    GetNext() { return _listNext; }
    const char*                    GetName() { return _name.c_str(); }
    const char*                    GetParamSpec() { return _paramSpec.c_str(); }
    const char*                    GetReturnSpec() { return _returnSpec.c_str(); }
    FRT_METHOD_PT                  GetMethod() { return _method; }
    FRT_Invokable*                 GetHandler() { return _handler; }
    const FRT_RequestAccessFilter* GetRequestAccessFilter() const noexcept { return _access_filter.get(); }
    void SetRequestAccessFilter(std::unique_ptr<FRT_RequestAccessFilter> access_filter) noexcept {
        _access_filter = std::move(access_filter);
    }
    void SetDocumentation(FRT_Values* values);
    void GetDocumentation(FRT_Values* values);
};

//------------------------------------------------------------------------

class FRT_ReflectionManager {
public:
    enum { METHOD_HASH_SIZE = 6000 };

private:
    uint32_t    _numMethods;
    FRT_Method* _methods;
    FRT_Method* _methodHash[METHOD_HASH_SIZE];

    FRT_ReflectionManager(const FRT_ReflectionManager&);
    FRT_ReflectionManager& operator=(const FRT_ReflectionManager&);

    uint32_t HashStr(const char* key, uint32_t width) {
        uint32_t             res = 0;
        unsigned const char* pt = (unsigned const char*)key;
        while (*pt != '\0') {
            res = (res << 7) + (*pt) + (res >> 25);
            pt++;
        }
        return (res % width);
    }

public:
    FRT_ReflectionManager();
    ~FRT_ReflectionManager();

    void        Reset();
    void        AddMethod(FRT_Method* method);
    FRT_Method* LookupMethod(const char* name);
    void        DumpMethodList(FRT_Values* target);
};

//------------------------------------------------------------------------

class FRT_ReflectionBuilder {
private:
    FRT_Supervisor*        _supervisor;
    FRT_ReflectionManager* _lookup;
    FRT_Method*            _method;

    // documentation variables below

    FRT_RPCRequest*                          _req;
    FRT_Values*                              _values;
    uint32_t                                 _argCnt;
    uint32_t                                 _retCnt;
    uint32_t                                 _curArg;
    uint32_t                                 _curRet;
    FRT_StringValue*                         _arg_name;
    FRT_StringValue*                         _arg_desc;
    FRT_StringValue*                         _ret_name;
    FRT_StringValue*                         _ret_desc;
    std::unique_ptr<FRT_RequestAccessFilter> _access_filter;

    FRT_ReflectionBuilder(const FRT_ReflectionBuilder&);
    FRT_ReflectionBuilder& operator=(const FRT_ReflectionBuilder&);

    void Flush();

public:
    FRT_ReflectionBuilder(FRT_Supervisor* supervisor);
    ~FRT_ReflectionBuilder();

    void DefineMethod(const char* name, const char* paramSpec, const char* returnSpec, FRT_METHOD_PT method,
                      FRT_Invokable* handler);
    void MethodDesc(const char* desc);
    void ParamDesc(const char* name, const char* desc);
    void ReturnDesc(const char* name, const char* desc);
    void RequestAccessFilter(std::unique_ptr<FRT_RequestAccessFilter> access_filter);
};
