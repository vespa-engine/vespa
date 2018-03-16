// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagepolicy.h"
#include <vespa/document/base/documentid.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config/subscription/configuri.h>

#include <vespa/log/log.h>
LOG_SETUP(".storagepolicy");

using vespalib::make_string;

namespace documentapi {

StoragePolicy::StoragePolicy(const string& param)
    : ExternSlobrokPolicy(parse(param)),
      _bucketIdFactory()
{
    std::map<string, string> params(parse(param));

    if (params.find("cluster") != params.end()) {
        _clusterName = params.find("cluster")->second;
    } else {
        _error = "Required parameter clustername not set";
    }

    if (params.find("clusterconfigid") != params.end()) {
        _clusterConfigId = params.find("clusterconfigid")->second;
    }
}

namespace {
    class CallBack : public config::IFetcherCallback<storage::lib::Distribution::DistributionConfig>
    {
    public:
        CallBack(StoragePolicy & policy) : _policy(policy) { }
        void configure(std::unique_ptr<storage::lib::Distribution::DistributionConfig> config) override {
            _policy.configure(std::move(config));
        }
    private:
        StoragePolicy & _policy;
    };
}
string StoragePolicy::init()
{
    string error = ExternSlobrokPolicy::init();
    if (error.length() > 0) {
        return error;
    }

    if (!_clusterConfigId.length()) {
        _clusterConfigId = createConfigId(_clusterName);
    }

    using storage::lib::Distribution;
    config::ConfigUri uri(_clusterConfigId);
    if (!_configSources.empty()) {
        _configFetcher.reset(new config::ConfigFetcher(config::ServerSpec(_configSources)));
    } else {
        _configFetcher.reset(new config::ConfigFetcher(uri.getContext()));
    }
    _callBack = std::make_unique<CallBack>(*this);
    _configFetcher->subscribe<vespa::config::content::StorDistributionConfig>(uri.getConfigId(), static_cast<CallBack *>(_callBack.get()));
    _configFetcher->start();
    return "";
}

StoragePolicy::~StoragePolicy()
{
}

string
StoragePolicy::createConfigId(const string & clusterName) const
{
    return "storage/cluster." + clusterName;
}

string
StoragePolicy::createPattern(const string & clusterName, int distributor) const
{
    vespalib::asciistream ost;

    ost << "storage/cluster." << clusterName << "/distributor/";

    if (distributor == -1) {
        ost << '*';
    } else {
        ost << distributor;
    }
    ost << "/default";
    return ost.str();
}

void
StoragePolicy::configure(std::unique_ptr<vespa::config::content::StorDistributionConfig> config)
{
    try {
        _nextDistribution.reset(new storage::lib::Distribution(*config));
    } catch (const std::exception& e) {
        LOG(warning, "Got exception when configuring distribution, config id was %s", _clusterConfigId.c_str());
        throw e;
    }
}

void
StoragePolicy::doSelect(mbus::RoutingContext &context)
{
    const mbus::Message &msg = context.getMessage();

    int distributor = -1;

    if (_state.get()) {
        document::BucketId id;
        switch(msg.getType()) {
        case DocumentProtocol::MESSAGE_PUTDOCUMENT:
            id = _bucketIdFactory.getBucketId(static_cast<const PutDocumentMessage&>(msg).getDocument().getId());
            break;

        case DocumentProtocol::MESSAGE_GETDOCUMENT:
            id = _bucketIdFactory.getBucketId(static_cast<const GetDocumentMessage&>(msg).getDocumentId());
            break;

        case DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
            id = _bucketIdFactory.getBucketId(static_cast<const RemoveDocumentMessage&>(msg).getDocumentId());
            break;

        case DocumentProtocol::MESSAGE_UPDATEDOCUMENT:
            id = _bucketIdFactory.getBucketId(static_cast<const UpdateDocumentMessage&>(msg).getDocumentUpdate().getId());
            break;

        case DocumentProtocol::MESSAGE_STATBUCKET:
            id = static_cast<const StatBucketMessage&>(msg).getBucketId();
            break;

        case DocumentProtocol::MESSAGE_GETBUCKETLIST:
            id = static_cast<const GetBucketListMessage&>(msg).getBucketId();
            break;

        case DocumentProtocol::MESSAGE_CREATEVISITOR:
            id = static_cast<const CreateVisitorMessage&>(msg).getBuckets()[0];
            break;

        case DocumentProtocol::MESSAGE_REMOVELOCATION:
            id = static_cast<const RemoveLocationMessage&>(msg).getBucketId();
            break;

        case DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE:
            id = static_cast<const BatchDocumentUpdateMessage&>(msg).getBucketId();
            break;

        default:
            LOG(error, "Message type '%d' not supported.", msg.getType());
            return;
        }

        // _P_A_R_A_N_O_I_A_
        if (id.getRawId() == 0) {
            mbus::Reply::UP reply(new mbus::EmptyReply());
            reply->addError(mbus::Error(mbus::ErrorCode::APP_FATAL_ERROR,
                                    "No bucket id available in message."));
            context.setReply(std::move(reply));
            return;
        }

        // Pick a distributor using ideal state algorithm
        try {
                // Update distribution here, to make it not take lock in average case
            if (_nextDistribution.get() != 0) {
                _distribution = std::move(_nextDistribution);
                _nextDistribution.reset();
            }
            assert(_distribution.get());
            distributor = _distribution->getIdealDistributorNode(*_state, id);
        } catch (storage::lib::TooFewBucketBitsInUseException& e) {
            mbus::Reply::UP reply(
                    new WrongDistributionReply(_state->toString()));
            reply->addError(mbus::Error(
                    DocumentProtocol::ERROR_WRONG_DISTRIBUTION,
                    "Too few distribution bits used for given cluster state"));
            context.setReply(std::move(reply));
            return;

        } catch (storage::lib::NoDistributorsAvailableException& e) {
                // No distributors available in current cluster state. Remove
                // cluster state we cannot use and send to random target
            _state.reset();
            distributor = -1;
        }
    }

    mbus::Hop hop = getRecipient(context, distributor);

    if (distributor != -1 && !hop.hasDirectives()) {
        hop = getRecipient(context, -1);
    }

    if (hop.hasDirectives()) {
        mbus::Route route = context.getRoute();
        route.setHop(0, hop);
        context.addChild(route);
    } else {
        context.setError(
                mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE,
                make_string("Could not resolve a distributor to send to in cluster %s", _clusterName.c_str()));
    }
}

mbus::Hop
StoragePolicy::getRecipient(mbus::RoutingContext& context, int distributor)
{
    slobrok::api::IMirrorAPI::SpecList entries = lookup(context, createPattern(_clusterName, distributor));

    if (!entries.empty()) {
        return mbus::Hop::parse(entries[random() % entries.size()].second + "/default");
    }

    return mbus::Hop();
}

void
StoragePolicy::merge(mbus::RoutingContext &context)
{
    mbus::RoutingNodeIterator it = context.getChildIterator();
    mbus::Reply::UP reply = it.removeReply();

    if (reply->getType() == DocumentProtocol::REPLY_WRONGDISTRIBUTION) {
        updateStateFromReply(static_cast<WrongDistributionReply&>(*reply));
    } else if (reply->hasErrors()) {
        _state.reset();
    }

    context.setReply(std::move(reply));
}

void
StoragePolicy::updateStateFromReply(WrongDistributionReply& wdr)
{
    std::unique_ptr<storage::lib::ClusterState> newState(
            new storage::lib::ClusterState(wdr.getSystemState()));
    if (_state.get() == 0 || newState->getVersion() >= _state->getVersion()) {
        if (_state.get()) {
            wdr.getTrace().trace(1, make_string("System state changed from version %u to %u",
                                                _state->getVersion(), newState->getVersion()));
        } else {
            wdr.getTrace().trace(1, make_string("System state set to version %u", newState->getVersion()));
        }

        _state = std::move(newState);
    } else {
        wdr.getTrace().trace(1, make_string("System state cleared because system state returned had version %d, "
                                            "while old state had version %d. New states should not have a lower version than the old.",
                                            newState->getVersion(), _state->getVersion()));
        _state.reset();
    }
}

} // documentapi
