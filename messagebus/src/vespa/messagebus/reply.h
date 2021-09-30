// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "routable.h"
#include <vector>

namespace mbus {

class Message;
class Error;

/**
 * A reply is a response to a message that has been sent throught the
 * messagebus. No reply will ever exist without a corresponding message. There
 * are no error-replies defined, as errors can instead piggyback any reply by
 * the {@link #errors} member variable.
 */
class Reply : public Routable {
private:
    using MessageUP = std::unique_ptr<Message>;
    std::vector<Error>  _errors;     // A list of errors that have occured during the lifetime of this reply.
    MessageUP           _msg;        // The message to which this is a reply.
    double              _retryDelay; // How to perform resending of this.

public:
    /**
     * Convenience typedef for an auto pointer to a Reply object.
     */
    typedef std::unique_ptr<Reply> UP;

    /**
     * Constructs a new instance of this class. This object is useless until the
     * state of a real message is swapped with this using {@link
     * #swapState(Routable)}.
     */
    Reply();

    /**
     * If a reply is deleted with elements on the callstack, this destructor
     * will log an error and generate an auto-reply to avoid having the sender
     * wait indefinetly for a reply.
     */
    ~Reply() override;

    void swapState(Routable &rhs) override;
    bool isReply() const override { return true; }

    /**
     * Add an Error to this Reply
     *
     * @param error the error to add
     */
    void addError(const Error &error);

    /**
     * Returns whether or not this reply contains at least one error.
     *
     * @return True if this contains errors.
     */
    bool hasErrors() const { return ! _errors.empty(); }

    /**
     * Returns whether or not this reply contains any fatal errors.
     *
     * @return True if it contains fatal errors.
     */
    bool hasFatalErrors() const;

    /**
     * Returns the error at the given position.
     *
     * @param i The index of the error to return.
     * @return The error at the given index.
     */
    const Error &getError(uint32_t i) const;

    /**
     * Returns the number of errors that this reply contains.
     *
     * @return The number of replies.
     */
    uint32_t getNumErrors() const;

    /**
     * Attach a Message to this Reply. If a Reply contains errors, messagebus
     * will attach the original Message to the Reply before giving it to the
     * application.
     *
     * @param msg the Message to attach
     */
    void setMessage(MessageUP msg);

    /**
     * Detach the Message attached to this Reply. If a Reply contains errors,
     * messagebus will attach the original Message to the Reply before giving it
     * to the application.
     *
     * @return the detached Message
     */
    MessageUP getMessage();

    /**
     * Returns the retry request of this reply. This can be set using {@link
     * #setRetryDelay} and is an instruction to the resender logic of message
     * bus on how to perform the retry. If this value is anything other than a
     * negative number, it instructs the resender to disregard all configured
     * resending attributes and instead act according to this value.
     *
     * @return The retry request.
     */
    double getRetryDelay() const { return _retryDelay; }

    /**
     * Sets the retry delay request of this reply. If this is a negative number,
     * it will use the defaults configured in the source session.
     *
     * @param retryDelay The retry request.
     */
    void setRetryDelay(double retryDelay) { _retryDelay = retryDelay; }
};

} // namespace mbus

