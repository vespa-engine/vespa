// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablefactories50.h"
#include "routablefactories51.h"
#include "routablefactories52.h"
#include "routingpolicyfactories.h"
#include "routablerepository.h"
#include "routingpolicyrepository.h"
#include "replymerger.h"
#include <vespa/document/util/stringutil.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".documentprotocol");

using document::DocumentTypeRepo;

namespace documentapi {

const mbus::string DocumentProtocol::NAME = "document";

DocumentProtocol::DocumentProtocol(const LoadTypeSet& loadTypes,
                                   DocumentTypeRepo::SP repo,
                                   const string &configId) :
    _routingPolicyRepository(new RoutingPolicyRepository()),
    _routableRepository(new RoutableRepository(loadTypes)),
    _systemState(SystemState::newInstance("")),
    _repo(repo)
{
    // Prepare config string for routing policy factories.
    string cfg = (configId.empty() ? "client" : configId);

    // When adding factories to this list, please KEEP THEM ORDERED alphabetically like they are now.
    putRoutingPolicyFactory("AND", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::AndPolicyFactory()));
    putRoutingPolicyFactory("Content", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::ContentPolicyFactory()));
    putRoutingPolicyFactory("MessageType", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::MessageTypePolicyFactory()));
    putRoutingPolicyFactory("DocumentRouteSelector", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::DocumentRouteSelectorPolicyFactory(*_repo, cfg)));
    putRoutingPolicyFactory("Extern", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::ExternPolicyFactory()));
    putRoutingPolicyFactory("LocalService", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::LocalServicePolicyFactory()));
    putRoutingPolicyFactory("RoundRobin", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::RoundRobinPolicyFactory()));
    putRoutingPolicyFactory("Storage", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::StoragePolicyFactory()));
    putRoutingPolicyFactory("SubsetService", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::SubsetServicePolicyFactory()));
    putRoutingPolicyFactory("LoadBalancer", IRoutingPolicyFactory::SP(new RoutingPolicyFactories::LoadBalancerPolicyFactory()));

    // Prepare version specifications to use when adding routable factories.
    vespalib::VersionSpecification version50(5, 0);
    vespalib::VersionSpecification version51(5, 1);
    vespalib::VersionSpecification version52(5, 115);

    std::vector<vespalib::VersionSpecification> from50 = { version50, version51, version52 };
    std::vector<vespalib::VersionSpecification> from51 = { version51, version52 };
    std::vector<vespalib::VersionSpecification> from52 = { version52 };

    // Add 5.0 serialization
    putRoutableFactory(MESSAGE_BATCHDOCUMENTUPDATE, IRoutableFactory::SP(new RoutableFactories50::BatchDocumentUpdateMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_CREATEVISITOR, IRoutableFactory::SP(new RoutableFactories50::CreateVisitorMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_DESTROYVISITOR, IRoutableFactory::SP(new RoutableFactories50::DestroyVisitorMessageFactory()), from50);
    putRoutableFactory(MESSAGE_DOCUMENTLIST, IRoutableFactory::SP(new RoutableFactories50::DocumentListMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_DOCUMENTSUMMARY, IRoutableFactory::SP(new RoutableFactories50::DocumentSummaryMessageFactory()), from50);
    putRoutableFactory(MESSAGE_EMPTYBUCKETS, IRoutableFactory::SP(new RoutableFactories50::EmptyBucketsMessageFactory()), from50);
    putRoutableFactory(MESSAGE_GETBUCKETLIST, IRoutableFactory::SP(new RoutableFactories50::GetBucketListMessageFactory()), from50);
    putRoutableFactory(MESSAGE_GETBUCKETSTATE, IRoutableFactory::SP(new RoutableFactories50::GetBucketStateMessageFactory()), from50);
    putRoutableFactory(MESSAGE_GETDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::GetDocumentMessageFactory()), from50);
    putRoutableFactory(MESSAGE_MAPVISITOR, IRoutableFactory::SP(new RoutableFactories50::MapVisitorMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_MULTIOPERATION, IRoutableFactory::SP(new RoutableFactories50::MultiOperationMessageFactory(_repo)), from50);
    putRoutableFactory(MESSAGE_PUTDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::PutDocumentMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_QUERYRESULT, IRoutableFactory::SP(new RoutableFactories50::QueryResultMessageFactory()), from50);
    putRoutableFactory(MESSAGE_REMOVEDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::RemoveDocumentMessageFactory()), from50);
    putRoutableFactory(MESSAGE_REMOVELOCATION, IRoutableFactory::SP(new RoutableFactories50::RemoveLocationMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_SEARCHRESULT, IRoutableFactory::SP(new RoutableFactories50::SearchResultMessageFactory()), from50);
    putRoutableFactory(MESSAGE_STATBUCKET, IRoutableFactory::SP(new RoutableFactories50::StatBucketMessageFactory()), from50);
    putRoutableFactory(MESSAGE_UPDATEDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::UpdateDocumentMessageFactory(*_repo)), from50);
    putRoutableFactory(MESSAGE_VISITORINFO, IRoutableFactory::SP(new RoutableFactories50::VisitorInfoMessageFactory()), from50);
    putRoutableFactory(REPLY_BATCHDOCUMENTUPDATE, IRoutableFactory::SP(new RoutableFactories50::BatchDocumentUpdateReplyFactory()), from50);
    putRoutableFactory(REPLY_CREATEVISITOR, IRoutableFactory::SP(new RoutableFactories50::CreateVisitorReplyFactory()), from50);
    putRoutableFactory(REPLY_DESTROYVISITOR, IRoutableFactory::SP(new RoutableFactories50::DestroyVisitorReplyFactory()), from50);
    putRoutableFactory(REPLY_DOCUMENTLIST, IRoutableFactory::SP(new RoutableFactories50::DocumentListReplyFactory()), from50);
    putRoutableFactory(REPLY_DOCUMENTSUMMARY, IRoutableFactory::SP(new RoutableFactories50::DocumentSummaryReplyFactory()), from50);
    putRoutableFactory(REPLY_EMPTYBUCKETS, IRoutableFactory::SP(new RoutableFactories50::EmptyBucketsReplyFactory()), from50);
    putRoutableFactory(REPLY_GETBUCKETLIST, IRoutableFactory::SP(new RoutableFactories50::GetBucketListReplyFactory()), from50);
    putRoutableFactory(REPLY_GETBUCKETSTATE, IRoutableFactory::SP(new RoutableFactories50::GetBucketStateReplyFactory()), from50);
    putRoutableFactory(REPLY_GETDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::GetDocumentReplyFactory(*_repo)), from50);
    putRoutableFactory(REPLY_MAPVISITOR, IRoutableFactory::SP(new RoutableFactories50::MapVisitorReplyFactory()), from50);
    putRoutableFactory(REPLY_MULTIOPERATION, IRoutableFactory::SP(new RoutableFactories50::MultiOperationReplyFactory()), from50);
    putRoutableFactory(REPLY_PUTDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::PutDocumentReplyFactory()), from50);
    putRoutableFactory(REPLY_QUERYRESULT, IRoutableFactory::SP(new RoutableFactories50::QueryResultReplyFactory()), from50);
    putRoutableFactory(REPLY_REMOVEDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::RemoveDocumentReplyFactory()), from50);
    putRoutableFactory(REPLY_REMOVELOCATION, IRoutableFactory::SP(new RoutableFactories50::RemoveLocationReplyFactory()), from50);
    putRoutableFactory(REPLY_SEARCHRESULT, IRoutableFactory::SP(new RoutableFactories50::SearchResultReplyFactory()), from50);
    putRoutableFactory(REPLY_STATBUCKET, IRoutableFactory::SP(new RoutableFactories50::StatBucketReplyFactory()), from50);
    putRoutableFactory(REPLY_UPDATEDOCUMENT, IRoutableFactory::SP(new RoutableFactories50::UpdateDocumentReplyFactory()), from50);
    putRoutableFactory(REPLY_VISITORINFO, IRoutableFactory::SP(new RoutableFactories50::VisitorInfoReplyFactory()), from50);
    putRoutableFactory(REPLY_WRONGDISTRIBUTION, IRoutableFactory::SP(new RoutableFactories50::WrongDistributionReplyFactory()), from50);

    // Add 5.1 serialization
    putRoutableFactory(MESSAGE_GETDOCUMENT, IRoutableFactory::SP(new RoutableFactories51::GetDocumentMessageFactory()), from51);
    putRoutableFactory(MESSAGE_CREATEVISITOR, IRoutableFactory::SP(new RoutableFactories51::CreateVisitorMessageFactory(*_repo)), from51);
    putRoutableFactory(REPLY_DOCUMENTIGNORED, IRoutableFactory::SP(new RoutableFactories51::DocumentIgnoredReplyFactory()), from51);

    // Add 5.2 serialization
    putRoutableFactory(MESSAGE_PUTDOCUMENT, IRoutableFactory::SP(new RoutableFactories52::PutDocumentMessageFactory(*_repo)), from52);
    putRoutableFactory(MESSAGE_UPDATEDOCUMENT, IRoutableFactory::SP(new RoutableFactories52::UpdateDocumentMessageFactory(*_repo)), from52);
    putRoutableFactory(MESSAGE_REMOVEDOCUMENT, IRoutableFactory::SP(new RoutableFactories52::RemoveDocumentMessageFactory()), from52);
}

DocumentProtocol::~DocumentProtocol() { }

mbus::IRoutingPolicy::UP
DocumentProtocol::createPolicy(const mbus::string &name, const mbus::string &param) const
{
    return _routingPolicyRepository->createPolicy(name, param);
}

DocumentProtocol &
DocumentProtocol::putRoutingPolicyFactory(const string &name, IRoutingPolicyFactory::SP factory)
{
    _routingPolicyRepository->putFactory(name, factory);
    return *this;
}

mbus::Blob
DocumentProtocol::encode(const vespalib::Version &version, const mbus::Routable &routable) const
{
    mbus::Blob blob(_routableRepository->encode(version, routable));
        // When valgrind reports errors of uninitialized data being written to
        // the network, it is useful to be able to see the serialized data to
        // try to identify what bits are uninitialized.
    if (LOG_WOULD_LOG(spam)) {
        std::ostringstream message;
        document::StringUtil::printAsHex(
                message, blob.data(), blob.size());
        LOG(spam, "Encoded message of protocol %s type %u using version %s serialization:\n%s",
            routable.getProtocol().c_str(), routable.getType(),
            version.toString().c_str(), message.str().c_str());
    }
    return blob;
}

mbus::Routable::UP
DocumentProtocol::decode(const vespalib::Version &version, mbus::BlobRef data) const
{
    try {
        return _routableRepository->decode(version, data);
    } catch (vespalib::Exception &e) {
        LOG(warning, "%s", e.getMessage().c_str());
        return mbus::Routable::UP();
    }
}

uint32_t
DocumentProtocol::getRoutableTypes(const vespalib::Version &version, std::vector<uint32_t> &out) const
{
    return _routableRepository->getRoutableTypes(version, out);
}

DocumentProtocol &
DocumentProtocol::putRoutableFactory(uint32_t type, IRoutableFactory::SP factory,
                                     const vespalib::VersionSpecification &version)
{
    _routableRepository->putFactory(version, type, factory);
    return *this;
}

DocumentProtocol &
DocumentProtocol::putRoutableFactory(uint32_t type, IRoutableFactory::SP factory,
                                     const std::vector<vespalib::VersionSpecification> &versions)
{
    for (std::vector<vespalib::VersionSpecification>::const_iterator it = versions.begin();
         it != versions.end(); ++it)
    {
        putRoutableFactory(type, factory, *it);
    }
    return *this;
}

string
DocumentProtocol::getErrorName(uint32_t errorCode) {
    switch (errorCode) {
        case ERROR_MESSAGE_IGNORED:               return "MESSAGE_IGNORED";
        case ERROR_POLICY_FAILURE:                return "POLICY_FAILURE";
        case ERROR_DOCUMENT_NOT_FOUND:            return "DOCUMENT_NOT_FOUND";
        case ERROR_EXISTS:                        return "EXISTS";
        case ERROR_BUCKET_NOT_FOUND:              return "BUCKET_NOT_FOUND";
        case ERROR_BUCKET_DELETED:                return "BUCKET_DELETED";
        case ERROR_NOT_IMPLEMENTED:               return "NOT_IMPLEMENTED";
        case ERROR_ILLEGAL_PARAMETERS:            return "ILLEGAL_PARAMETERS";
        case ERROR_IGNORED:                       return "IGNORED";
        case ERROR_UNKNOWN_COMMAND:               return "UNKNOWN_COMMAND";
        case ERROR_UNPARSEABLE:                   return "UNPARSEABLE";
        case ERROR_NO_SPACE:                      return "NO_SPACE";
        case ERROR_INTERNAL_FAILURE:              return "INTERNAL_FAILURE";
        case ERROR_PROCESSING_FAILURE:            return "PROCESSING_FAILURE";
        case ERROR_TIMESTAMP_EXIST:               return "TIMESTAMP_EXIST";
        case ERROR_STALE_TIMESTAMP:               return "STALE_TIMESTAMP";
        case ERROR_NODE_NOT_READY:                return "NODE_NOT_READY";
        case ERROR_WRONG_DISTRIBUTION:            return "WRONG_DISTRIBUTION";
        case ERROR_REJECTED:                      return "REJECTED";
        case ERROR_ABORTED:                       return "ABORTED";
        case ERROR_BUSY:                          return "BUSY";
        case ERROR_NOT_CONNECTED:                 return "NOT_CONNECTED";
        case ERROR_DISK_FAILURE:                  return "DISK_FAILURE";
        case ERROR_IO_FAILURE:                    return "IO_FAILURE";
        case ERROR_SUSPENDED:                     return "SUSPENDED";
        case ERROR_TEST_AND_SET_CONDITION_FAILED: return "TEST_AND_SET_CONDITION_FAILED";
    }
    return mbus::ErrorCode::getName(errorCode);
}

void
DocumentProtocol::merge(mbus::RoutingContext &ctx)
{
    std::set<uint32_t> mask;
    merge(ctx, mask);
}

void
DocumentProtocol::merge(mbus::RoutingContext& ctx,
                        const std::set<uint32_t>& mask)
{
    ReplyMerger rm;
    uint32_t idx = 0;
    for (mbus::RoutingNodeIterator it = ctx.getChildIterator();
         it.isValid(); it.next(), ++idx)
    {
        if (mask.find(idx) != mask.end()) {
            continue;
        }
        rm.merge(idx, it.getReplyRef());
    }
    assert(idx != 0);
    ReplyMerger::Result res(rm.mergedReply());
    if (res.isSuccessful()) {
        const uint32_t okIdx = res.getSuccessfulReplyIndex();
        ctx.setReply(ctx.getChildIterator().skip(okIdx).removeReply()); 
    } else {
        assert(res.hasGeneratedReply());
        ctx.setReply(mbus::Reply::UP(res.releaseGeneratedReply().release()));
    }
}

bool
DocumentProtocol::hasOnlyErrorsOfType(const mbus::Reply &reply, uint32_t errCode)
{
    for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
        if (reply.getError(i).getCode() != errCode) {
            return false;
        }
    }
    return true;
}

}
