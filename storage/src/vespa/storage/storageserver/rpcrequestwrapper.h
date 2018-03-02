// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

class FRT_RPCRequest;

namespace storage {

/**
 * This event wraps a request received from a remote rpc client.
 **/
class RPCRequestWrapper {
public:
    enum ErrorCode {
        ERR_HANDLE_NOT_CONNECTED = 75000, // > 0xffff
        ERR_HANDLE_GONE          = 75001,
        ERR_REQUEST_DELETED      = 75002,
        ERR_HANDLE_DISABLED      = 75003,
        ERR_NODE_SHUTTING_DOWN   = 75004,
        ERR_BAD_REQUEST          = 75005
    };

    RPCRequestWrapper(FRT_RPCRequest *req);
    ~RPCRequestWrapper();

    /**
     * @return request parameter data
     **/
    const char *getParam() const;

    /**
     * @return request parameter length
     **/
    uint32_t getParamLen() const;

    /**
     * Return data for this request.
     *
     * @param pt return data
     * @param len return data length
     **/
    void returnData(const char *pt, uint32_t len);

    /**
     * Return an error for this request
     *
     * @param errorCode numeric error code
     * @param errorMessage human readable error message
     **/
    void returnError(uint32_t errorCode, const char *errorMessage);

    const char *getMethodName() const;
    void addReturnString(const char *str, uint32_t len=0);
    void addReturnInt(uint32_t value);
    void returnRequest();

    /**
     * Discard any large blobs from the underlying rpc request. This
     * may be done after interpreting any parameters in order to save
     * memory on the server.
     **/
    void discardBlobs();

private:
    RPCRequestWrapper(const RPCRequestWrapper &);
    RPCRequestWrapper &operator=(const RPCRequestWrapper &);

    FRT_RPCRequest      *_req;             // underlying RPC request
};

} // namespace storage
