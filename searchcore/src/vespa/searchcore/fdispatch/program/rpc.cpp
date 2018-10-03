// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc.h"
#include <vespa/searchcore/fdispatch/search/engine_base.h>
#include <vespa/searchcore/fdispatch/search/datasetcollection.h>

void
FastS_fdispatch_RPC::RegisterMethods(FRT_ReflectionBuilder *rb)
{
    FastS_RPC::RegisterMethods(rb);
    //------------------------------------------------------------------
    rb->DefineMethod("fs.admin.enableEngine", "s", "i",
                     FRT_METHOD(FastS_fdispatch_RPC::RPC_EnableEngine), this);
    rb->MethodDesc("Enable the given engine (clear badness).");
    rb->ParamDesc("name",  "engine name");
    rb->ReturnDesc("count", "number of engines affected");
    //------------------------------------------------------------------
    rb->DefineMethod("fs.admin.disableEngine", "s", "i",
                     FRT_METHOD(FastS_fdispatch_RPC::RPC_DisableEngine), this);
    rb->MethodDesc("Disable the given engine (mark as admin bad).");
    rb->ParamDesc("name",  "engine name");
    rb->ReturnDesc("count", "number of engines affected");
}


void
FastS_fdispatch_RPC::RPC_GetNodeType(FRT_RPCRequest *req)
{
    req->GetReturn()->AddString("dispatch");
}

namespace {

template<class FUN>
struct ExecuteWhenEqualName_t {
    FUN _successFun;
    const char* _targetName;
    uint32_t _cnt;

    ExecuteWhenEqualName_t(const char* targetName, FUN successFun)
        : _successFun(successFun),
          _targetName(targetName),
          _cnt(0)
    {}

    void operator()(FastS_EngineBase* engine) {
        if (strcmp(engine->GetName(), _targetName) ==  0 ) {
            _cnt++;
            _successFun(engine);
        }
    }
};

template <class FUN>
ExecuteWhenEqualName_t<FUN>
ExecuteWhenEqualName(const char* targetName, FUN successFun) {
    return ExecuteWhenEqualName_t<FUN>(targetName, successFun);
}


} //anonymous namespace

void
FastS_fdispatch_RPC::RPC_EnableEngine(FRT_RPCRequest *req)
{
    const char *name = req->GetParams()->GetValue(0)._string._str;
    FastS_DataSetCollection *dsc = GetAppCtx()->GetDataSetCollection();
    uint32_t cnt = 0;

    for (uint32_t i = 0; i < dsc->GetMaxNumDataSets(); i++) {
        FastS_DataSetBase *ds;
        FastS_PlainDataSet *ds_plain;
        if ((ds = dsc->PeekDataSet(i)) == NULL ||
            (ds_plain = ds->GetPlainDataSet()) == NULL)
            continue;

        cnt += ds_plain->ForEachEngine(
                ExecuteWhenEqualName(name,
                        std::mem_fun( &FastS_EngineBase::ClearBad )))
                       ._cnt;
    }

    dsc->subRef();
    req->GetReturn()->AddInt32(cnt);
}


void
FastS_fdispatch_RPC::RPC_DisableEngine(FRT_RPCRequest *req)
{
    const char *name = req->GetParams()->GetValue(0)._string._str;
    FastS_DataSetCollection *dsc = GetAppCtx()->GetDataSetCollection();
    uint32_t cnt = 0;

    for (uint32_t i = 0; i < dsc->GetMaxNumDataSets(); i++) {
        FastS_DataSetBase *ds;
        FastS_PlainDataSet *ds_plain;
        if ((ds = dsc->PeekDataSet(i)) == NULL ||
            (ds_plain = ds->GetPlainDataSet()) == NULL)
            continue;

        uint32_t badness = FastS_EngineBase::BAD_ADMIN;
        cnt += ds_plain->ForEachEngine(
                ExecuteWhenEqualName(name,
                        std::bind2nd(
                                std::mem_fun( &FastS_EngineBase::MarkBad ),
                                badness)))
               ._cnt;
    }
    dsc->subRef();
    req->GetReturn()->AddInt32(cnt);
}
