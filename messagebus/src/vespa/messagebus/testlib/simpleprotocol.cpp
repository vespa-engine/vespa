// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleprotocol.h"
#include "simplemessage.h"
#include "simplereply.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingcontext.h>

namespace mbus {

const string SimpleProtocol::NAME("Simple");
const uint32_t SimpleProtocol::MESSAGE(1);
const uint32_t SimpleProtocol::REPLY(2);

class AllPolicy : public IRoutingPolicy {
public:
    void select(RoutingContext &ctx) override {
        std::vector<Route> recipients;
        ctx.getMatchedRecipients(recipients);
        ctx.addChildren(recipients);
    }

    void merge(RoutingContext &ctx) override {
        SimpleProtocol::simpleMerge(ctx);
    }
};

class AllPolicyFactory : public SimpleProtocol::IPolicyFactory {
public:
    IRoutingPolicy::UP create(const string &) override {
        return IRoutingPolicy::UP(new AllPolicy());
    }
    ~AllPolicyFactory() override;
};

AllPolicyFactory::~AllPolicyFactory() = default;

class HashPolicy : public IRoutingPolicy {
public:
    void select(RoutingContext &ctx) override {
        std::vector<Route> recipients;
        ctx.getMatchedRecipients(recipients);
        if (!recipients.empty()) {
            int i = static_cast<const SimpleMessage&>(ctx.getMessage()).getHash();
            ctx.addChild(recipients[std::abs(i) % recipients.size()]);
        }
    }

    void merge(RoutingContext &ctx) override {
        SimpleProtocol::simpleMerge(ctx);
    }
};

class HashPolicyFactory : public SimpleProtocol::IPolicyFactory {
public:
    IRoutingPolicy::UP create(const string &) override {
        return IRoutingPolicy::UP(new HashPolicy());
    }
    ~HashPolicyFactory() override;
};

HashPolicyFactory::~HashPolicyFactory() = default;

SimpleProtocol::SimpleProtocol() :
    _policies()
{
    addPolicyFactory("All", IPolicyFactory::SP(new AllPolicyFactory));
    addPolicyFactory("Hash", IPolicyFactory::SP(new HashPolicyFactory));
}

SimpleProtocol::~SimpleProtocol()
{
    // empty
}

void
SimpleProtocol::addPolicyFactory(const string &name,
                                 IPolicyFactory::SP factory)
{
    _policies.insert(FactoryMap::value_type(name, factory));
}

const string &
SimpleProtocol::getName() const
{
    return NAME;
}

IRoutingPolicy::UP
SimpleProtocol::createPolicy(const string &name,
                             const string &param) const
{
    FactoryMap::const_iterator it = _policies.find(name);
    if (it != _policies.end()) {
        return it->second->create(param);
    }
    return IRoutingPolicy::UP();
}

Blob
SimpleProtocol::encode(const vespalib::Version &version, const Routable &routable) const
{
    (void)version;
    if (routable.getType() == MESSAGE) {
        string str = "M";
        str.append(static_cast<const SimpleMessage&>(routable).getValue());
        Blob ret(str.size());
        memcpy(ret.data(), str.data(), str.size());
        return ret;
    } else if (routable.getType() == REPLY) {
        string str = "R";
        str.append(static_cast<const SimpleReply&>(routable).getValue());
        Blob ret(str.size());
        memcpy(ret.data(), str.data(), str.size());
        return ret;
    } else {
        return Blob(0);
    }
}

Routable::UP
SimpleProtocol::decode(const vespalib::Version &version, BlobRef data) const
{
    (void)version;

    const char *d = data.data();
    uint32_t s = data.size();
    if (s < 1) {
        return Routable::UP(); // too short
    }
    string str(d + 1, s - 1);
    if (*d == 'M') {
        return Routable::UP(new SimpleMessage(str));
    } else if (*d == 'R') {
        return Routable::UP(new SimpleReply(str));
    } else {
        return Routable::UP(); // unknown type
    }
}

void
SimpleProtocol::simpleMerge(RoutingContext &ctx)
{
    Reply::UP ret(new EmptyReply());
    for (RoutingNodeIterator it = ctx.getChildIterator();
         it.isValid(); it.next())
    {
        const Reply &reply = it.getReplyRef();
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            ret->addError(reply.getError(i));
        }
    }
    ctx.setReply(std::move(ret));
}

} // namespace mbus
