// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcrequest.h"
#include "packets.h"
#include <vespa/fnet/info.h>
#include <cassert>

FRT_RPCRequest::FRT_RPCRequest()
    : _tub(),
      _context(),
      _params(&_tub),
      _return(&_tub),
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
{
}


FRT_RPCRequest::~FRT_RPCRequest()
{
    assert(_refcnt == 0);
}

void
FRT_RPCRequest::SetError(uint32_t errorCode, const char *errorMessage, uint32_t errorMessageLen)
{
    _errorCode = errorCode;
    _errorMessageLen = errorMessageLen;
    _errorMessage = _tub.CopyString(errorMessage,
                                    errorMessageLen);
}
void
FRT_RPCRequest::SetError(uint32_t errorCode, const char *errorMessage) {
    SetError(errorCode, errorMessage, strlen(errorMessage));
}

void
FRT_RPCRequest::SetError(uint32_t errorCode)
{
    SetError(errorCode, FRT_GetDefaultErrorMessage(errorCode));
}

void
FRT_RPCRequest::Reset()
{
    assert(_refcnt <= 1);
    Cleanup();
    _context = FNET_Context();
    _params.Reset();
    _return.Reset();
    _tub.Reset();
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

    return new (&_tub) FRT_RPCRequestPacket(this, flags, true);
}


FNET_Packet *
FRT_RPCRequest::CreateReplyPacket()
{
    uint32_t flags = 0;
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
        flags |= FLAG_FRT_RPC_LITTLE_ENDIAN;

    if (IsError())
        return new (&_tub) FRT_RPCErrorPacket(this, flags, true);
    else
        return new (&_tub) FRT_RPCReplyPacket(this, flags, true);
}
