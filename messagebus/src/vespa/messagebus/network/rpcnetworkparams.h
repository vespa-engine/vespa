// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "identity.h"
#include <vespa/slobrok/cfg.h>
#include <vespa/vespalib/util/compressionconfig.h>

namespace mbus {

/**
 * To facilitate several configuration parameters to the {@link RPCNetwork} constructor, all parameters are
 * held by this class. This class has reasonable default values for each parameter.
 */
class RPCNetworkParams {
private:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    Identity          _identity;
    config::ConfigUri _slobrokConfig;
    int               _listenPort;
    uint32_t          _maxInputBufferSize;
    uint32_t          _maxOutputBufferSize;
    uint32_t          _numThreads;
    bool              _dispatchOnEncode;
    bool              _dispatchOnDecode;
    double            _connectionExpireSecs;
    CompressionConfig _compressionConfig;

public:
    RPCNetworkParams();
    ~RPCNetworkParams();

    /**
     * Returns the identity to use for the network.
     *
     * @return The identity.
     */
    const Identity &getIdentity() const {
        return _identity;
    }

    /**
     * Sets the identity to use for the network.
     *
     * @param identity The new identity.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setIdentity(const Identity &identity) {
        _identity = identity;
        return *this;
    }

    /**
     * Sets the identity to use for the network.
     *
     * @param identity A string representation of the identity.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setIdentity(const string &identity) {
        return setIdentity(Identity(identity));
    }

    /**
     * Returns the config id of the slobrok config.
     *
     * @return The config id.
     */
    const config::ConfigUri & getSlobrokConfig() const {
        return _slobrokConfig;
    }

    /**
     * Sets of the slobrok config.
     *
     * @param slobrokConfigId The new config.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setSlobrokConfig(const config::ConfigUri & slobrokConfig) {
        _slobrokConfig = slobrokConfig;
        return *this;
    }

    /**
     * Returns the port to listen to.
     *
     * @return The port.
     */
    int getListenPort() const {
        return _listenPort;
    }

    /**
     * Sets the port to listen to.
     *
     * @param listenPort The new port.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setListenPort(int listenPort) {
        _listenPort = listenPort;
        return *this;
    }

    /**
     * Sets number of threads for the thread pool.
     *
     * @param numThreads number of threads for thread pool
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setNumThreads(uint32_t numThreads) {
        _numThreads = numThreads;
        return *this;
    }

    uint32_t getNumThreads() const { return _numThreads; }

    /**
     * Returns the number of seconds before an idle network connection expires.
     *
     * @return The number of seconds.
     */
    double getConnectionExpireSecs() const{
        return _connectionExpireSecs;
    }

    /**
     * Sets the number of seconds before an idle network connection expires.
     *
     * @param secs The number of seconds.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setConnectionExpireSecs(double secs) {
        _connectionExpireSecs = secs;
        return *this;
    }

    /**
     * Returns the maximum input buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    uint32_t getMaxInputBufferSize() const {
        return _maxInputBufferSize;
    }

    /**
     * Sets the maximum input buffer size allowed for the underlying FNET connection. Using the value 0 means that there
     * is no limit; the connection will not free any allocated memory until it is cleaned up. This might potentially
     * save alot of allocation time.
     *
     * @param maxInputBufferSize The maximum number of bytes.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setMaxInputBufferSize(uint32_t maxInputBufferSize) {
        _maxInputBufferSize = maxInputBufferSize;
        return *this;
    }

    /**
     * Returns the maximum output buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    uint32_t getMaxOutputBufferSize() const {
        return _maxOutputBufferSize;
    }

    /**
     * Sets the maximum output buffer size allowed for the underlying FNET connection. Using the value 0 means that there
     * is no limit; the connection will not free any allocated memory until it is cleaned up. This might potentially
     * save alot of allocation time.
     *
     * @param maxOutputBufferSize The maximum number of bytes.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setMaxOutputBufferSize(uint32_t maxOutputBufferSize) {
        _maxOutputBufferSize = maxOutputBufferSize;
        return *this;
    }

    RPCNetworkParams &setCompressionConfig(CompressionConfig compressionConfig) {
        _compressionConfig = compressionConfig;
        return *this;
    }
    CompressionConfig getCompressionConfig() const { return _compressionConfig; }


    RPCNetworkParams &setDispatchOnDecode(bool dispatchOnDecode) {
        _dispatchOnDecode = dispatchOnDecode;
        return *this;
    }

    uint32_t getDispatchOnDecode() const { return _dispatchOnDecode; }

    RPCNetworkParams &setDispatchOnEncode(bool dispatchOnEncode) {
        _dispatchOnEncode = dispatchOnEncode;
        return *this;
    }

    uint32_t getDispatchOnEncode() const { return _dispatchOnEncode; }
};

}

