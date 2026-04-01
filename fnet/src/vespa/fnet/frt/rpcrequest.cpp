// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcrequest.h"

#include "packets.h"

#include <vespa/fnet/info.h>

#include <cassert>

FRT_RPCRequest::FRT_RPCRequest()
    : _stash(),
      _context(),
      _params(_stash),
      _return(_stash),
      _completed(0),
      _errorCode(FRTE_NO_ERROR),
      _errorMessageLen(0),
      _methodNameLen(0),
      _errorMessage(nullptr),
      _methodName(nullptr),
      _detachedPT(nullptr),
      _abortHandler(nullptr),
      _returnHandler(nullptr) {}

FRT_RPCRequest::~FRT_RPCRequest() = default;

void FRT_RPCRequest::SetError(uint32_t errorCode, const char* errorMessage, uint32_t errorMessageLen) {
    _errorCode = errorCode;
    _errorMessageLen = errorMessageLen;
    _errorMessage = fnet::copyString(_stash.alloc(errorMessageLen + 1), errorMessage, errorMessageLen);
}
void FRT_RPCRequest::SetError(uint32_t errorCode, const char* errorMessage) {
    SetError(errorCode, errorMessage, strlen(errorMessage));
}

void FRT_RPCRequest::SetError(uint32_t errorCode) { SetError(errorCode, FRT_GetDefaultErrorMessage(errorCode)); }

bool FRT_RPCRequest::CheckReturnTypes(const char* types) {
    if (IsError()) {
        return false;
    }
    if (strcmp(types, GetReturnSpec()) != 0) {
        SetError(FRTE_RPC_WRONG_RETURN);
        return false;
    }
    return true;
}

void FRT_RPCRequest::SetMethodName(const char* methodName, uint32_t len) {
    _methodNameLen = len;
    _methodName = fnet::copyString(_stash.alloc(len + 1), methodName, len);
}
void FRT_RPCRequest::SetMethodName(const char* methodName) { SetMethodName(methodName, strlen(methodName)); }

bool FRT_RPCRequest::Abort() {
    if (_abortHandler == nullptr) {
        return false;
    }
    return _abortHandler->HandleAbort();
}

void FRT_RPCRequest::Return() { _returnHandler->HandleReturn(); }

FNET_Connection* FRT_RPCRequest::GetConnection() {
    if (_returnHandler == nullptr)
        return nullptr;
    return _returnHandler->GetConnection();
}

void FRT_RPCRequest::Reset() {
    _context = FNET_Context();
    _params.Reset();
    _return.Reset();
    _stash.clear();
    _errorCode = FRTE_NO_ERROR;
    _errorMessageLen = 0;
    _errorMessage = nullptr;
    _methodNameLen = 0;
    _methodName = nullptr;
    _detachedPT = nullptr;
    _completed = 0;
    _abortHandler = nullptr;
    _returnHandler = nullptr;
}

bool FRT_RPCRequest::Recycle() {
    if (count_refs() > 1 || _errorCode != FRTE_NO_ERROR)
        return false;
    Reset();
    return true;
}

void FRT_RPCRequest::Print(uint32_t indent) {
    printf("%*sFRT_RPCRequest {\n", indent, "");
    printf("%*s  method: %s\n", indent, "", (_methodName != nullptr) ? _methodName : "(N/A)");
    printf("%*s  error(%d): %s\n", indent, "", _errorCode,
           (_errorMessage != nullptr) ? _errorMessage : FRT_GetDefaultErrorMessage(_errorCode));
    printf("%*s  params:\n", indent, "");
    _params.Print(indent + 2);
    printf("%*s  return:\n", indent, "");
    _return.Print(indent + 2);
    printf("%*s}\n", indent, "");
}

FNET_Packet* FRT_RPCRequest::CreateRequestPacket(bool wantReply) {
    uint32_t flags = 0;
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
        flags |= FLAG_FRT_RPC_LITTLE_ENDIAN;

    if (wantReply)
        internal_addref();
    else
        flags |= FLAG_FRT_RPC_NOREPLY;

    return &_stash.create<FRT_RPCRequestPacket>(this, flags, true);
}

FNET_Packet* FRT_RPCRequest::CreateReplyPacket() {
    uint32_t flags = 0;
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
        flags |= FLAG_FRT_RPC_LITTLE_ENDIAN;

    if (IsError()) {
        return &_stash.create<FRT_RPCErrorPacket>(this, flags, true);
    } else {
        return &_stash.create<FRT_RPCReplyPacket>(this, flags, true);
    }
}
