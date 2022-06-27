// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/iprotocol.h>
#include <map>

namespace mbus {

class SimpleProtocol : public IProtocol {
public:
    /**
     * Defines a policy factory interface that tests can use to register arbitrary policies with this protocol.
     */
    class IPolicyFactory {
    public:
        /**
         * Convenience typedefs.
         */
        typedef std::shared_ptr<IPolicyFactory> SP;

        /**
         * Required for inheritance.
         */
        virtual ~IPolicyFactory() { }

        /**
         * Creates a new isntance of the routing policy that this factory encapsulates.
         *
         * @param param The param for the policy constructor.
         * @return The routing policy created.
         */
        virtual IRoutingPolicy::UP create(const string &param) = 0;
    };

private:
    typedef std::map<string, IPolicyFactory::SP> FactoryMap;
    FactoryMap _policies;

public:
    static const string NAME;
    static const uint32_t MESSAGE;
    static const uint32_t REPLY;

    /**
     * Constructs a new simple protocol. This registers policy factories for both {@link SimpleAllPolicy} and
     * {@link SimpleHashPolicy}.
     */
    SimpleProtocol();

    ~SimpleProtocol();

    /**
     * Registers a policy factory with this protocol under a given name. Whenever a policy is requested that
     * matches this name, the factory is invoked.
     *
     * @param name    The name of the policy.
     * @param factory The policy factory.
     */
    void addPolicyFactory(const string &name,
                          IPolicyFactory::SP factory);

    /**
     * Common merge logic that can be used for any simple policy. It all errors across all replies into
     * a new {@link EmptyReply}.
     *
     * @param ctx The routing context whose children to merge.
     */
    static void simpleMerge(RoutingContext &ctx);

    const string & getName() const override;
    IRoutingPolicy::UP createPolicy(const string &name, const string &param) const override;
    Blob encode(const vespalib::Version &version, const Routable &routable) const override;
    Routable::UP decode(const vespalib::Version &version, BlobRef data) const override;
};

} // namespace mbus
