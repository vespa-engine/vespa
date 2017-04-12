// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <boost/tokenizer.hpp>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/configagent.h>
#include <vespa/messagebus/iconfighandler.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/vdslib/bucketdistribution.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/config/helper/configgetter.h>
#include <vespa/config/helper/configgetter.hpp>


#include "locator.h"

typedef std::map<std::string, uint32_t> ClusterMap;
using namespace config;

namespace {

    void
    processHop(const mbus::HopSpec &hop, ClusterMap &clusters)
    {
        typedef boost::char_separator<char> CharSeparator;
        typedef boost::tokenizer<CharSeparator> Tokenizer;

        int colIdx = -1;
        for (uint32_t r = 0, len = hop.getNumRecipients(); r < len; ++r) {
            Tokenizer tokens(hop.getRecipient(r), CharSeparator("/"));
            Tokenizer::iterator token = tokens.begin();
            for (uint32_t t = 0; t < 2 && token != tokens.end(); ++t, ++token) {
                // empty
            }
            if (token != tokens.end()) {
                colIdx = std::max(colIdx, atoi(&token->c_str()[1]));
            }
        }
        if (colIdx < 0) {
            throw config::InvalidConfigException(vespalib::make_string("Failed to process cluster '%s'.",
                                                                       hop.getName().c_str()));
        }
        clusters.insert(ClusterMap::value_type(hop.getName().substr(15), colIdx + 1));
    }

    void
    processTable(const mbus::RoutingTableSpec &table, ClusterMap &clusters)
    {
        clusters.clear();
        for (uint32_t i = 0, len = table.getNumHops(); i < len; ++i) {
            const mbus::HopSpec &hop = table.getHop(i);
            if (hop.getName().find("search/cluster.") == 0) {
                processHop(hop, clusters);
            }
        }
        if (clusters.empty()) {
            throw config::InvalidConfigException("No search clusters found to resolve document location for.");
        }
    }

    void
    processRouting(const mbus::RoutingSpec &routing, ClusterMap &clusters)
    {
        const mbus::RoutingTableSpec *table = NULL;
        for (uint32_t i = 0, len = routing.getNumTables(); i < len; ++i) {
            const mbus::RoutingTableSpec &ref = routing.getTable(i);
            if (ref.getProtocol() == documentapi::DocumentProtocol::NAME) {
                table = &ref;
                break;
            }
        }
        if (table == NULL) {
            throw config::InvalidConfigException("No routing table available to derive config from.");
        }
        processTable(*table, clusters);
    }

    uint32_t
    getNumColumns(const mbus::RoutingSpec &routing, const std::string &clusterName)
    {
        ClusterMap clusters;
        processRouting(routing, clusters);

        if (clusterName.empty() && clusters.size() == 1) {
            return clusters.begin()->second;
        }

        ClusterMap::iterator it = clusters.find(clusterName);
        if (it == clusters.end()) {
            std::string str = "Cluster name must be one of ";
            int i = 0, len = clusters.size();
            for (it = clusters.begin(); it != clusters.end(); ++it, ++i)
            {
                str.append("'").append(it->first).append("'");
                if (i < len - 2) {
                    str.append(", ");
                } else if (i == len - 2) {
                    str.append(" or ");
                }
            }
            str.append(".");
            throw config::InvalidConfigException(str);
        }

        return it->second;
    }
}

Locator::Locator(uint32_t numColumns) :
    _factory(),
    _numColumns(numColumns)
{
    // empty
}

void
Locator::configure(const std::string &configId, const std::string &clusterName)
{
    config::ConfigUri configUri(configId);
    // Configure by inspecting routing config.
    struct MyCB : public mbus::IConfigHandler {
        mbus::RoutingSpec mySpec;
        MyCB() : mySpec() {}
        bool setupRouting(const mbus::RoutingSpec &spec) override {
            mySpec = spec;
            return true;
        }
    } myCB;
    mbus::ConfigAgent agent(myCB);
    agent.configure(ConfigGetter<messagebus::MessagebusConfig>::getConfig(configUri.getConfigId(), configUri.getContext()));
    _numColumns = getNumColumns(myCB.mySpec, clusterName);
}

document::BucketId
Locator::getBucketId(document::DocumentId &docId)
{
    return _factory.getBucketId(docId);
}

uint32_t
Locator::getSearchColumn(document::DocumentId &docId)
{
    vdslib::BucketDistribution dist(_numColumns, 16u);
    return dist.getColumn(getBucketId(docId));
}
