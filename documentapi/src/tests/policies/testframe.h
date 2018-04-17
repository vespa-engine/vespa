// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/network/identity.h>
#include <vespa/messagebus/network/inetwork.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

using documentapi::string;

class TestFrame : public mbus::IReplyHandler {
private:
    string                         _identity;
    std::shared_ptr<mbus::Slobrok>    _slobrok;
    documentapi::LoadTypeSet            _set;
    std::shared_ptr<mbus::INetwork>   _net;
    std::shared_ptr<mbus::MessageBus> _mbus;
    mbus::Message::UP                   _msg;
    mbus::HopSpec                       _hop;
    mbus::Receptor                      _handler;

    TestFrame &operator=(const TestFrame &); // hide

public:
    /**
     * Convenience typedefs.
     */
    typedef std::map<string, uint32_t> ReplyMap;

    /**
     * Create a named test frame.
     *
     * @param identity The identity to use for the server.
     */
    TestFrame(const std::shared_ptr<const document::DocumentTypeRepo> &repo,
              const string &ident = "anonymous");

    /**
     * Create a test frame running on the same slobrok and mbus as another.
     *
     * @param frame The frame whose internals to share.
     */
    TestFrame(TestFrame &frame);

    /**
     * Cleans up allocated resources.
     */
    virtual ~TestFrame();

    /**
     * Routes the contained message based on the current setup, and returns the leaf send contexts.
     *
     * @param selected    The list to add the selected recipients to.
     * @param numExpected The expected number of contexts.
     * @return True if everything was ok.
     */
    bool select(std::vector<mbus::RoutingNode*> &selected, uint32_t numExpected);

    /**
     * Ensures that the current setup selects a given set of routes.
     *
     * @param expected A list of expected route leaf nodes.
     * @return True if everything was ok.
     */
    bool testSelect(const std::vector<string> &expected);

    /**
     * This is a convenience method for invoking {@link #assertMerge(std::map,std::vector,std::vector)} with
     * no expected value.
     *
     * @param replies        The errors to set in the leaf node replies.
     * @param expectedErrors The list of expected errors in the merged reply.
     * @return True if everything was ok.
     */
    bool testMergeError(const ReplyMap &replies, const std::vector<uint32_t> &expectedErrors);

    /**
     * This is a convenience method for invoking {@link #assertMerge(std::map,std::vector,std::vector)} with
     * no expected errors.
     *
     * @param replies       The errors to set in the leaf node replies.
     * @param allowedValues The list of allowed values in the final reply.
     * @return True if everything was ok.
     */
    bool testMergeOk(const ReplyMap &replies, const std::vector<string> &allowedValues);

    /**
     * Ensures that the current setup generates as many leaf nodes as there are members of the errors argument. Each
     * error is then given one of these errors, and the method then ensures that the single returned reply contains the
     * given list of expected errors. Finally, if the expected value argument is non-null, this method ensures that the
     * reply is a SimpleReply whose string value exists in the allowed list.
     *
     * @param replies        The errors to set in the leaf node replies.
     * @param expectedErrors The list of expected errors in the merged reply.
     * @param allowedValues  The list of allowed values in the final reply.
     * @return True if everything was ok.
     */
    bool testMerge(const ReplyMap &replies,
                   const std::vector<uint32_t> &expectedErrors,
                   const std::vector<string> &allowedValues);

    /**
     * Ensures that the current setup chooses a single recipient, and that it merges similarly to how the
     * {@link DocumentProtocol} would merge these.
     *
     * @param recipient The expected recipient.
     * @return True if everything was ok.
     */
    bool testMergeOneReply(const string &recipient);

    /**
     * Ensures that the current setup will choose the two given recipients, and that it merges similarly to how the
     * {@link DocumentProtocol} would merge these.
     *
     * @param recipientOne The first expected recipient.
     * @param recipientTwo The second expected recipient.
     */
    bool testMergeTwoReplies(const string &recipientOne, const string &recipientTwo);

    /**
     * Waits for a given service pattern to resolve to the given number of hits in the local slobrok.
     *
     * @param pattern The pattern to lookup.
     * @param cnt     The number of entries to wait for.
     * @return True if the expected number of entries was found.
     */
    bool waitSlobrok(const string &pattern, uint32_t cnt);

    /**
     * Returns the identity of this frame.
     *
     * @return The ident string.
     */
    const string &getIdentity() { return _identity; }

    /**
     * Returns the private slobrok server.
     *
     * @return The slobrok.
     */
    mbus::Slobrok &getSlobrok() { return *_slobrok; }

    /**
     * Returns the private message bus.
     *
     * @return The bus.
     */
    mbus::MessageBus &getMessageBus() { return *_mbus; }

    /**
     * Returns the private network layer.
     *
     * @return The network.
     */
    mbus::INetwork &getNetwork() { return *_net; }

    /**
     * Returns the message being tested.
     *
     * @return The message.
     */
    mbus::Message::UP getMessage() { return std::move(_msg); }

    /**
     * Sets the message being tested.
     *
     * @param msg The message to set.
     */
    mbus::Message::UP setMessage(mbus::Message::UP msg) {
        std::swap(msg, _msg);
        return std::move(msg);
    }

    /**
     * Sets the spec of the hop to test with.
     *
     * @param hop The spec to set.
     */
    void setHop(const mbus::HopSpec &hop);

    /**
     * Returns the reply receptor used by this frame. All messages tested are tagged with this receptor, so after a
     * successful select, the receptor should contain a non-null reply.
     *
     * @return The reply receptor.
     */
    mbus::Receptor &getReceptor() { return _handler; }

    void handleReply(mbus::Reply::UP reply) override;
};

class UIntList : public std::vector<uint32_t> {
public:
    UIntList &add(uint32_t err) {
        std::vector<uint32_t>::push_back(err);
        return *this;
    }
};

class StringList : public std::vector<string> {
public:
    StringList &add(const string &val) {
        std::vector<string>::push_back(val);
        return *this;
    }
};

