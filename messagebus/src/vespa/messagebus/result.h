// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "error.h"
#include <memory>

namespace mbus {

class Message;

/**
 * A Result object is used as return value when trying to send a
 * Message on a SourceSession. It says whether messagebus has accepted
 * the message or not. If messagebus accepts the message an
 * asynchronous reply will be delivered at a later time. If messagebus
 * does not accept the message, the returned Result will indicate why
 * the message was not accepted. A Result indication an error will
 * also contain the message that did not get accepted, passing it back
 * to the application. Note that Result objects have destructive copy
 * of the pointer to the Message that is handed back to the
 * application.
 **/
class Result
{
private:
    bool        _accepted;
    Error       _error;
    std::unique_ptr<Message> _msg;

public:
    /**
     * Create a Result indicating that messagebus has accepted the
     * Message.
     **/
    Result();

    /**
     * Create a Result indicating that messagebus has not accepted the
     * Message.
     *
     * @param err the reason for not accepting the Message
     * @param msg the message that did not get accepted
     **/
    Result(const Error &err, std::unique_ptr<Message> msg);


    Result(Result &&rhs) = default;
    Result &operator=(Result &&rhs) = default;
    ~Result();

    /**
     * Check if the message was accepted.
     *
     * @return true if the Message was accepted
     **/
    bool isAccepted() const { return _accepted; }

    /**
     * Obtain the error causing the message not to be accepted.
     *
     * @return error
     **/
    const Error &getError() const { return _error; }

    /**
     * If the message was not accepted, this method may be used to get
     * the Message back out. Note that this method hands the Message
     * over to the caller. Also note that copying the Result will
     * transfer the ownership of the Message to the new copy.
     *
     * @return the Message that was not accepted
     **/
    std::unique_ptr<Message> getMessage() { return std::move(_msg); }
};

} // namespace mbus

