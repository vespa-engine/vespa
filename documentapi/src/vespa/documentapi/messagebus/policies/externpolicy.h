// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/routing/hop.h>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/slobrok/imirrorapi.h>
#include <vespa/documentapi/common.h>
#include <mutex>

namespace documentapi {

class MirrorAndStuff;

/**
 * This policy implements the necessary logic to communicate with an external Vespa application and resolve its list of
 * recipients using that other application's slobrok servers.
 */
class ExternPolicy : public mbus::IRoutingPolicy {
private:
    using IMirrorAPI = slobrok::api::IMirrorAPI;
    std::mutex                       _lock;
    std::unique_ptr<MirrorAndStuff>  _mirrorWithAll;
    string                           _pattern;
    string                           _session;
    string                           _error;
    uint32_t                         _offset;
    uint32_t                         _gen;
    std::vector<mbus::Hop>           _recipients;

private:
    /**
     * Returns the appropriate recipient hop. This method provides synchronized access to the internal mirror.
     *
     * @return The recipient hop to use.
     */
    mbus::Hop getRecipient();

    /**
     * Updates the list of matching recipients by querying the extern slobrok.
     */
    void update();

public:
    /**
     * Constructs a policy that will choose local services that match the slobrok pattern in which this policy occured.
     * If no local service can be found, this policy simply returns the asterisk to allow the network to choose any.
     *
     * @param param The address to use for this, if empty this will resolve to hostname.
     */
    ExternPolicy(const string &param);
    ~ExternPolicy();

    /**
     * This is a safety mechanism to allow the constructor to fail and signal that it can not be used.
     *
     * @return True if this policy can be used.
     */
    const string &getError() const { return _error; }

    /**
     * Returns the slobrok mirror api used by this policy to resolve external patterns. This is basically here just to
     * enable unit tests. If you rely on this for production code, then you need to reconsider your logic. Furthermore,
     * it deref's the content of an auto-pointer that is NULL in case broken() returns true, meaning it _will_ core.
     *
     * @return The mirror pointer.
     */
    const slobrok::api::IMirrorAPI *getMirror() const;
    void select(mbus::RoutingContext &ctx) override;
    void merge(mbus::RoutingContext &ctx) override;
};

}

