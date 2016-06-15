// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/utility.hpp>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/vdslib/bucketdistribution.h>
#include <vespa/vespalib/util/sync.h>

namespace documentapi {

/**
 * This policy implements the logic to select recipients for a single search column.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 * @version $Id$
 */
class SearchColumnPolicy : public boost::noncopyable, public mbus::IRoutingPolicy {
private:
    typedef std::map<uint32_t, vdslib::BucketDistribution> DistributionCache;

    vespalib::Lock            _lock;
    document::BucketIdFactory _factory;
    DistributionCache         _distributions;
    uint32_t                  _maxOOS;

    /**
     * Returns the recipient index for the given bucket id. This updates the shared internal distribution map, so it
     * needs to be synchronized.
     *
     * @param bucketId      The bucket whose recipient to return.
     * @param numRecipients The number of recipients being distributed to.
     * @return The recipient to use.
     */
    uint32_t getRecipient(const document::BucketId &bucketId, uint32_t numRecipients);

public:
    /**
     * Constructs a new policy object for the given parameter string. The string can be null or empty, which is a
     * request to not allow any bad columns.
     *
     * @param param The maximum number of allowed bad columns.
     */
    SearchColumnPolicy(const string &param);

    /**
     * Destructor.
     *
     * Frees all allocated resources.
     */
    virtual ~SearchColumnPolicy();

    // Inherit doc from IRoutingPolicy.
    virtual void select(mbus::RoutingContext &context);

    // Inherit doc from IRoutingPolicy.
    virtual void merge(mbus::RoutingContext &context);
};

}

