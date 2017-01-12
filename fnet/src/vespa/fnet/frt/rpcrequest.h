// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "values.h"
#include "error.h"
#include <vespa/fnet/context.h>

#include <vespa/vespalib/util/atomic.h>

class FNETConnection;
class FNET_Packet;

class FRT_IAbortHandler
{
public:

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IAbortHandler(void) {}

    virtual bool HandleAbort() = 0;
};


class FRT_IReturnHandler
{
public:

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IReturnHandler(void) {}

    virtual void HandleReturn() = 0;
    virtual FNET_Connection *GetConnection() = 0;
};


class FRT_ICleanupHandler
{
public:

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_ICleanupHandler(void) {}

    virtual void HandleCleanup() = 0;
};


class FRT_RPCRequest
{
private:
    FRT_MemoryTub _tub;
    FNET_Context  _context;
    FRT_Values    _params;
    FRT_Values    _return;
    int           _refcnt;
    int           _completed;
    uint32_t      _errorCode;
    uint32_t      _errorMessageLen;
    uint32_t      _methodNameLen;
    char         *_errorMessage;
    char         *_methodName;

    bool                *_detachedPT;
    FRT_IAbortHandler   *_abortHandler;
    FRT_IReturnHandler  *_returnHandler;
    FRT_ICleanupHandler *_cleanupHandler;

    FRT_RPCRequest(const FRT_RPCRequest &);
    FRT_RPCRequest &operator=(const FRT_RPCRequest &);

public:
    FRT_RPCRequest();
    ~FRT_RPCRequest();

    void Reset();
    bool Recycle();

    void DiscardBlobs()
    {
        _params.DiscardBlobs();
        _return.DiscardBlobs();
    }

    void AddRef_NoLock() { _refcnt++; }  // be very carefull
    void AddRef() { vespalib::Atomic::postInc(&_refcnt); }
    void SubRef();

    void SetContext(FNET_Context context) { _context = context; }
    FNET_Context GetContext() { return _context; }

    FRT_MemoryTub *GetMemoryTub() { return &_tub; }

    FRT_Values *GetParams() { return &_params; }
    FRT_Values *GetReturn() { return &_return; }

    const char *GetParamSpec()
    {
        const char *spec = _params.GetTypeString();
        return (spec != NULL) ? spec : "";
    }
    const char *GetReturnSpec()
    {
        const char *spec = _return.GetTypeString();
        return (spec != NULL) ? spec : "";
    }

    bool GetCompletionToken() { return (vespalib::Atomic::postInc(&_completed) == 0); }

    void SetError(uint32_t errorCode, const char *errorMessage, uint32_t errorMessageLen);
    void SetError(uint32_t errorCode, const char *errorMessage);
    void SetError(uint32_t errorCode);

    bool IsError() { return (_errorCode != FRTE_NO_ERROR); }
    uint32_t GetErrorCode() { return _errorCode; }
    uint32_t GetErrorMessageLen() { return _errorMessageLen; }
    const char *GetErrorMessage() { return _errorMessage; }

    bool CheckReturnTypes(const char *types) {
        if (IsError()) {
            return false;
        }
        if (strcmp(types, GetReturnSpec()) != 0) {
            SetError(FRTE_RPC_WRONG_RETURN);
            return false;
        }
        return true;
    }

    void SetMethodName(const char *methodName, uint32_t len)
    {
        _methodNameLen = len;
        _methodName = _tub.CopyString(methodName, len);
    }
    void SetMethodName(const char *methodName)
    { SetMethodName(methodName, strlen(methodName)); }

    uint32_t GetMethodNameLen() { return _methodNameLen; }
    const char *GetMethodName() { return _methodName; }

    void Print(uint32_t indent = 0);

    FNET_Packet *CreateRequestPacket(bool wantReply);
    FNET_Packet *CreateReplyPacket();

    void SetDetachedPT(bool *detachedPT) { _detachedPT = detachedPT; }
    void Detach() { *_detachedPT = true; }

    void SetAbortHandler(FRT_IAbortHandler *handler)
    { _abortHandler = handler; }
    void SetReturnHandler(FRT_IReturnHandler *handler)
    { _returnHandler = handler; }
    void SetCleanupHandler(FRT_ICleanupHandler *handler)
    { _cleanupHandler = handler; }

    bool Abort()
    {
        if (_abortHandler == NULL) {
            return false;
        }
        return _abortHandler->HandleAbort();
    }

    void Return() {
        _returnHandler->HandleReturn();
    }

    FNET_Connection *GetConnection()
    {
        if (_returnHandler == NULL)
            return NULL;
        return _returnHandler->GetConnection();
    }

    void Cleanup()
    {
        if (_cleanupHandler != NULL) {
            _cleanupHandler->HandleCleanup();
            _cleanupHandler = NULL;
        }
    }
};

