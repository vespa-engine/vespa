// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file exceptions.h
 * @ingroup util
 *
 * @brief Various common exception classes.
 *
 * @author H�kon Humberset
 * @date 2004-03-31
 * @version $Id$
 */

#pragma once

#include "exception.h"
#include "stringfmt.h"
#include <memory>

namespace vespalib {

#ifdef IAM_DOXYGEN
/**
 * @class vespalib::UnsupportedOperationException
 * @brief Exception telling that the requested operation is not supported.
 **/
/**
 * @class vespalib::IllegalArgumentException
 * @brief Exception telling that illegal arguments was passed to a function.
 **/
/**
 * @class vespalib::IllegalStateException
 * @brief Exception telling that the object has an illegal state.
 **/
/**
 * @class vespalib::OverflowException
 * @brief XXX - Exception telling that some sort of overflow happened? unused
 **/
/**
 * @class vespalib::UnderflowException
 * @brief XXX - Exception telling that some sort of underflow happened? unused
 **/
/**
 * @class vespalib::FatalException
 * @brief Something went seriously wrong and the application should terminate
 **/

#else

VESPA_DEFINE_EXCEPTION(UnsupportedOperationException, Exception);
VESPA_DEFINE_EXCEPTION(IllegalArgumentException, Exception);
VESPA_DEFINE_EXCEPTION(IllegalStateException, Exception);
VESPA_DEFINE_EXCEPTION(OverflowException, Exception);
VESPA_DEFINE_EXCEPTION(UnderflowException, Exception);
VESPA_DEFINE_EXCEPTION(TimeoutException, Exception);
VESPA_DEFINE_EXCEPTION(FatalException, Exception);
VESPA_DEFINE_EXCEPTION(NetworkSetupFailureException, IllegalStateException);

#endif

//-----------------------------------------------------------------------------

/**
 * @brief Exception indicating the failure to listen for connections
 * on a socket.
 **/
class ExceptionWithPayload : public std::exception {
public:
    class Anything {
    public:
       using UP = std::unique_ptr<Anything>;
       virtual ~Anything() { }
    };
    ExceptionWithPayload(vespalib::stringref msg);
    ExceptionWithPayload(vespalib::stringref msg, Anything::UP payload);
    ExceptionWithPayload(ExceptionWithPayload &&) = default;
    ExceptionWithPayload & operator = (ExceptionWithPayload &&) = default;
    ~ExceptionWithPayload();
    void setPayload(Anything::UP payload) { _payload = std::move(payload); }
    const char * what() const noexcept override;
private:
    vespalib::string _msg;
    Anything::UP     _payload;
};

class OOMException : public ExceptionWithPayload {
public:
    OOMException(vespalib::stringref msg) : ExceptionWithPayload(msg) { }
    OOMException(vespalib::stringref msg, Anything::UP payload) : ExceptionWithPayload(msg, std::move(payload)) { }
};

/**
 * @brief Exception indicating the failure to listen for connections
 * on a socket.
 **/
class PortListenException : public Exception
{
private:
    int _port;
    vespalib::string _protocol;

    vespalib::string make_message(int port, vespalib::stringref protocol, vespalib::stringref msg);

public:
    PortListenException(int port, vespalib::stringref protocol, vespalib::stringref msg = "",
                        vespalib::stringref location = "", int skipStack = 0);
    PortListenException(int port, vespalib::stringref protocol, const Exception &cause, vespalib::stringref msg = "",
                        vespalib::stringref location = "", int skipStack = 0);
    PortListenException(PortListenException &&) = default;
    PortListenException & operator = (PortListenException &&) = default;
    PortListenException(const PortListenException &);
    PortListenException & operator = (const PortListenException &);
    ~PortListenException();
    VESPA_DEFINE_EXCEPTION_SPINE(PortListenException);
    int get_port() const { return _port; }
    const vespalib::string &get_protocol() const { return _protocol; }
};

//-----------------------------------------------------------------------------

/**
 * @brief Exception signaling that some sort of I/O error happened.
 **/
class IoException : public Exception {
public:
    enum Type { UNSPECIFIED,
                ILLEGAL_PATH, NO_PERMISSION, DISK_PROBLEM,
                INTERNAL_FAILURE, NO_SPACE, NOT_FOUND, CORRUPT_DATA,
                TOO_MANY_OPEN_FILES, DIRECTORY_HAVE_CONTENT, FILE_FULL,
                ALREADY_EXISTS };

    IoException(stringref msg, Type type, stringref location, int skipStack = 0);
    IoException(stringref msg, Type type, const Exception& cause, stringref location, int skipStack = 0);

    VESPA_DEFINE_EXCEPTION_SPINE(IoException);

    static string createMessage(stringref msg, Type type);

    Type getType() const { return _type; }

    /** Use this function as a way to map error from errno to a Type. */
    static Type getErrorType(int error);

private:
    Type _type;
};

class SilenceUncaughtException : public ExceptionWithPayload::Anything {
public:
    SilenceUncaughtException(const SilenceUncaughtException &) = delete;
    SilenceUncaughtException & operator = (const SilenceUncaughtException &) = delete;
    SilenceUncaughtException(const std::exception & e);
    ~SilenceUncaughtException();
private:
    std::terminate_handler _oldTerminate;
};

}

