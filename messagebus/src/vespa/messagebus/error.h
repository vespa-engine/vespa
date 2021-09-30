// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"

#ifdef Error
#undef Error
#endif

namespace mbus {

/**
 * An Error contains an error code (@ref ErrorCode) combined with an
 * error message.
 **/
class Error
{
private:
    uint32_t  _code;
    string    _msg;
    string    _service;

public:
    /**
     * Create an error with error code NONE and an empty message. This
     * constructor is not intended for application use, but is needed
     * for standard library containers.
     **/
    Error();
    ~Error();

    /**
     * Create a new error with the given code and message
     *
     * @param c error code
     * @param m error message
     * @param s error service
     **/
    Error(uint32_t c, vespalib::stringref m, vespalib::stringref s = "");

    /**
     * Obtain the error code of this error.
     *
     * @return error code
     **/
    uint32_t getCode() const { return _code; }

    /**
     * Obtain the error message of this error.
     *
     * @return error message
     **/
    const string &getMessage() const { return _msg; }

    /**
     * Obtain the service string of this error.
     *
     * @return service string
     **/
    const string &getService() const { return _service; }

    /**
     * Obtain a string representation of this error.
     *
     * @return string representation
     **/
    string toString() const;
};

} // namespace mbus

