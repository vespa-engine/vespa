// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcrequest.h"
#include "packets.h"
#include <vespa/fnet/info.h>
#include <cassert>

FRT_RPCRequest::FRT_RPCRequest()
    : _stash(),
      _context(),
      _params(&_stash),
      _return(&_stash),
      _refcnt(1),
      _completed(0),
      _errorCode(FRTE_NO_ERROR),
      _errorMessageLen(0),
      _methodNameLen(0),
      _errorMessage(NULL),
      _methodName(NULL),
      _detachedPT(NULL),
      _abortHandler(NULL),
      _returnHandler(NULL),
      _cleanupHandler(NULL)
{ }

FRT_RPCRequest::~FRT_RPCRequest()
{
    assert(_refcnt == 0);
}

void
FRT_RPCRequest::SetError(uint32_t errorCode, const char *errorMessage, uint32_t errorMessageLen)
{
    _errorCode = errorCode;
    _errorMessageLen = errorMessageLen;
    _errorMessage = fnet::copyString(_stash.alloc(errorMessageLen + 1), errorMessage, errorMessageLen);
}
void
FRT_RPCRequest::SetError(uint32_t errorCode, const char *errorMessage) {
    SetError(errorCode, errorMessage, strlen(errorMessage));
}

void
FRT_RPCRequest::SetError(uint32_t errorCode) {
    SetError(errorCode, FRT_GetDefaultErrorMessage(errorCode));
}

bool
FRT_RPCRequest::CheckReturnTypes(const char *types) {
    if (IsError()) {
        return false;
    }
    if (strcmp(types, GetReturnSpec()) != 0) {
        SetError(FRTE_RPC_WRONG_RETURN);
        return false;
    }
    return true;
}

void
FRT_RPCRequest::SetMethodName(const char *methodName, uint32_t len) {
    _methodNameLen = len;
    _methodName = fnet::copyString(_stash.alloc(len + 1), methodName, len);
}
void
FRT_RPCRequest::SetMethodName(const char *methodName) {
    SetMethodName(methodName, strlen(methodName));
}

bool
FRT_RPCRequest::Abort() {
    if (_abortHandler == NULL) {
        return false;
    }
    return _abortHandler->HandleAbort();
}

void
FRT_RPCRequest::Return() {
    _returnHandler->HandleReturn();
}

FNET_Connection *
FRT_RPCRequest::GetConnection() {
    if (_returnHandler == NULL)
        return NULL;
    return _returnHandler->GetConnection();
}

void
FRT_RPCRequest::Cleanup() {
    if (_cleanupHandler != NULL) {
        _cleanupHandler->HandleCleanup();
        _cleanupHandler = NULL;
    }
}

void
FRT_RPCRequest::Reset() {
    assert(_refcnt <= 1);
    Cleanup();
    _context = FNET_Context();
    _params.Reset();
    _return.Reset();
    _stash.clear();
    _errorCode = FRTE_NO_ERROR;
    _errorMessageLen = 0;
    _errorMessage = NULL;
    _methodNameLen = 0;
    _methodName = NULL;
    _detachedPT = NULL;
    _completed = 0;
    _abortHandler = NULL;
    _returnHandler = NULL;
}


bool
FRT_RPCRequest::Recycle()
{
    if (_refcnt > 1 || _errorCode != FRTE_NO_ERROR)
        return false;
    Reset();
    return true;
}


void
FRT_RPCRequest::SubRef()
{
    assert(_refcnt > 0);
    if (vespalib::Atomic::postDec(&_refcnt) == 1) {
        Reset();
        delete this;
    }
}


void
FRT_RPCRequest::Print(uint32_t indent)
{
    printf("%*sFRT_RPCRequest {\n", indent, "");
    printf("%*s  method: %s\n", indent, "",
           (_methodName != NULL)? _methodName : "(N/A)");
    printf("%*s  error(%d): %s\n", indent, "", _errorCode,
           (_errorMessage != NULL)
           ? _errorMessage
           : FRT_GetDefaultErrorMessage(_errorCode));
    printf("%*s  params:\n", indent, "");
    _params.Print(indent + 2);
    printf("%*s  return:\n", indent, "");
    _return.Print(indent + 2);
    printf("%*s}\n", indent, "");
}


FNET_Packet *
FRT_RPCRequest::CreateRequestPacket(bool wantReply)
{
    uint32_t flags = 0;
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
        flags |= FLAG_FRT_RPC_LITTLE_ENDIAN;

    if (wantReply)
        AddRef_NoLock();
    else
        flags |= FLAG_FRT_RPC_NOREPLY;

    return &_stash.create<FRT_RPCRequestPacket>(this, flags, true);
}


FNET_Packet *
FRT_RPCRequest::CreateReplyPacket()
{
    uint32_t flags = 0;
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
        flags |= FLAG_FRT_RPC_LITTLE_ENDIAN;

    if (IsError()) {
        return &_stash.create<FRT_RPCErrorPacket>(this, flags, true);
    } else {
        return &_stash.create<FRT_RPCReplyPacket>(this, flags, true);
    }
}
