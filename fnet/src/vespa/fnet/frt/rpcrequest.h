// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "error.h"
#include "values.h"

#include <vespa/fnet/context.h>
#include <vespa/vespalib/util/ref_counted.h>
#include <vespa/vespalib/util/stash.h>

#include <atomic>

class FNET_Packet;

class FRT_IAbortHandler {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IAbortHandler() = default;

    virtual bool HandleAbort() = 0;
};

class FRT_IReturnHandler {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IReturnHandler() = default;

    virtual void             HandleReturn() = 0;
    virtual FNET_Connection* GetConnection() = 0;
};

class FRT_RPCRequest : public vespalib::enable_ref_counted {
private:
    using Stash = vespalib::Stash;
    Stash            _stash;
    FNET_Context     _context;
    FRT_Values       _params;
    FRT_Values       _return;
    std::atomic<int> _completed;
    uint32_t         _errorCode;
    uint32_t         _errorMessageLen;
    uint32_t         _methodNameLen;
    char*            _errorMessage;
    char*            _methodName;

    bool*               _detachedPT;
    FRT_IAbortHandler*  _abortHandler;
    FRT_IReturnHandler* _returnHandler;

public:
    FRT_RPCRequest(const FRT_RPCRequest&) = delete;
    FRT_RPCRequest& operator=(const FRT_RPCRequest&) = delete;
    FRT_RPCRequest();
    ~FRT_RPCRequest();

    void Reset();
    bool Recycle();

    void DiscardBlobs() {
        _params.DiscardBlobs();
        _return.DiscardBlobs();
    }

    void         SetContext(FNET_Context context) { _context = context; }
    FNET_Context GetContext() { return _context; }

    Stash& getStash() { return _stash; }

    FRT_Values* GetParams() { return &_params; }
    FRT_Values* GetReturn() { return &_return; }

    const char* GetParamSpec() {
        const char* spec = _params.GetTypeString();
        return (spec != nullptr) ? spec : "";
    }
    const char* GetReturnSpec() {
        const char* spec = _return.GetTypeString();
        return (spec != nullptr) ? spec : "";
    }

    bool GetCompletionToken() { return (_completed.fetch_add(1) == 0); }

    void SetError(uint32_t errorCode, const char* errorMessage, uint32_t errorMessageLen);
    void SetError(uint32_t errorCode, const char* errorMessage);
    void SetError(uint32_t errorCode);

    bool        IsError() { return (_errorCode != FRTE_NO_ERROR); }
    uint32_t    GetErrorCode() { return _errorCode; }
    uint32_t    GetErrorMessageLen() { return _errorMessageLen; }
    const char* GetErrorMessage() { return _errorMessage; }

    bool CheckReturnTypes(const char* types);

    void SetMethodName(const char* methodName, uint32_t len);
    void SetMethodName(const char* methodName);

    uint32_t    GetMethodNameLen() const { return _methodNameLen; }
    const char* GetMethodName() const { return _methodName; }

    void Print(uint32_t indent = 0);

    FNET_Packet* CreateRequestPacket(bool wantReply);
    FNET_Packet* CreateReplyPacket();

    void            SetDetachedPT(bool* detachedPT) { _detachedPT = detachedPT; }
    FRT_RPCRequest* Detach() {
        *_detachedPT = true;
        return this;
    }

    void SetAbortHandler(FRT_IAbortHandler* handler) { _abortHandler = handler; }
    void SetReturnHandler(FRT_IReturnHandler* handler) { _returnHandler = handler; }

    bool             Abort();
    void             Return();
    FNET_Connection* GetConnection();
};
