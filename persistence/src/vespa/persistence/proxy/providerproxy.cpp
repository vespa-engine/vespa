// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "providerproxy.h"
#include "buildid.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/log/log.h>
LOG_SETUP(".providerproxy");

using document::BucketId;
using document::ByteBuffer;
using document::DocumentTypeRepo;
using document::VespaDocumentDeserializer;
using document::VespaDocumentSerializer;
using vespalib::nbostream;

namespace storage {
namespace spi {
namespace {
void addBucket(FRT_Values &values, const Bucket &bucket) {
    values.AddInt64(bucket.getBucketId().getId());
    values.AddInt64(bucket.getPartition());
}

void addDocument(FRT_Values &values, const Document &doc) {
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(doc, document::COMPLETE);
    values.AddData(stream.c_str(), stream.size());
}

void addString(FRT_Values &values, const string &s) {
    values.AddString(s.data(), s.size());
}

void addSelection(FRT_Values &values, const Selection &selection) {
    addString(values, selection.getDocumentSelection().getDocumentSelection());
    values.AddInt64(selection.getFromTimestamp());
    values.AddInt64(selection.getToTimestamp());
    std::copy(selection.getTimestampSubset().begin(),
              selection.getTimestampSubset().end(),
              values.AddInt64Array(selection.getTimestampSubset().size()));
}

void addDocumentUpdate(FRT_Values &values, const DocumentUpdate &update) {
    nbostream stream;
    update.serializeHEAD(stream);
    values.AddData(stream.c_str(), stream.size());
}

Document::UP readDocument(nbostream &stream, const DocumentTypeRepo &repo) {
    const uint16_t version = 8;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    Document::UP doc(new Document);
    deserializer.read(*doc);
    return doc;
}

string getString(const FRT_StringValue &str) {
    return string(str._str, str._len);
}

string getString(const FRT_Value &value) {
    return getString(value._string);
}

template <typename ResultType>
ResultType readError(const FRT_Values &values) {
    uint8_t error_code = values[0]._intval8;
    string error_msg = getString(values[1]);
    return ResultType(Result::ErrorType(error_code), error_msg);
}

bool invokeRpc(FRT_Target *target, FRT_RPCRequest &req, const char *res_spec) {
    target->InvokeSync(&req, 0.0); // no timeout
    req.CheckReturnTypes(res_spec);
    return req.GetErrorCode() == FRTE_NO_ERROR;
}

struct RequestScopedPtr : vespalib::noncopyable {
    FRT_RPCRequest *req;
    RequestScopedPtr(FRT_RPCRequest *r) : req(r) { assert(req); }
    ~RequestScopedPtr() { req->SubRef(); }
    FRT_RPCRequest *operator->() { return req; }
    FRT_RPCRequest &operator*() { return *req; }
};
}  // namespace

template <typename ResultType>
ResultType ProviderProxy::invokeRpc_Return(FRT_RPCRequest &req,
                                           const char *res_spec) const
{
    if (!invokeRpc(_target, req, res_spec)) {


        return ResultType(Result::FATAL_ERROR,
                          vespalib::make_string("Error %s when running RPC request %s",
                                  req.GetErrorMessage(),
                                  req.GetMethodName()));
    }
    return readResult<ResultType>(*req.GetReturn());
}

template <typename ResultType>
ResultType ProviderProxy::readResult(const FRT_Values &values) const {
    if (values[0]._intval8 != Result::NONE) {
        return readError<ResultType>(values);
    }
    return readNoError<ResultType>(values);
}

template <>
Result ProviderProxy::readNoError(const FRT_Values &) const {
    return Result();
}

template <>
PartitionStateListResult
ProviderProxy::readNoError(const FRT_Values &values) const {
    FRT_LPT(uint32_t) state_array = values[2]._int32_array;
    FRT_LPT(FRT_StringValue) reason_array = values[3]._string_array;
    PartitionStateList states(state_array._len);
    for (size_t i = 0; i < state_array._len; ++i) {
        PartitionState::State state =
            static_cast<PartitionState::State>(state_array._pt[i]);
        string reason = getString(reason_array._pt[i]);
        states[i] = PartitionState(state, reason);
    }
    return PartitionStateListResult(states);
}

template <>
BucketIdListResult ProviderProxy::readNoError(const FRT_Values &values) const {
    BucketIdListResult::List list;
    for (uint32_t i = 0; i < values[2]._int64_array._len; ++i) {
        list.push_back(BucketId(values[2]._int64_array._pt[i]));
    }
    return BucketIdListResult(list);
}

template <>
BucketInfoResult ProviderProxy::readNoError(const FRT_Values &values) const {
    BucketInfo info(BucketChecksum(values[2]._intval32),
                    values[3]._intval32,
                    values[4]._intval32,
                    values[5]._intval32,
                    values[6]._intval32,
                    static_cast<BucketInfo::ReadyState>(
                            values[7]._intval8),
                    static_cast<BucketInfo::ActiveState>(
                            values[8]._intval8));
    return BucketInfoResult(info);
}

template <>
RemoveResult ProviderProxy::readNoError(const FRT_Values &values) const {
    return RemoveResult(values[2]._intval8);
}

template <>
UpdateResult ProviderProxy::readNoError(const FRT_Values &values) const {
    return UpdateResult(Timestamp(values[2]._intval64));
}

template <>
GetResult ProviderProxy::readNoError(const FRT_Values &values) const {
    nbostream stream(values[3]._data._buf, values[3]._data._len);
    if (stream.empty()) {
        return GetResult();
    }
    return GetResult(readDocument(stream, *_repo),
                     Timestamp(values[2]._intval64));
}

template <>
CreateIteratorResult ProviderProxy::readNoError(const FRT_Values &values) const
{
    return CreateIteratorResult(IteratorId(values[2]._intval64));
}

template <>
IterateResult ProviderProxy::readNoError(const FRT_Values &values) const {
    IterateResult::List result;
    assert(values[2]._int64_array._len == values[3]._int32_array._len &&
           values[2]._int64_array._len == values[4]._string_array._len &&
           values[2]._int64_array._len == values[5]._data_array._len);
    for (uint32_t i = 0; i < values[2]._int64_array._len; ++i) {
        Timestamp timestamp(values[2]._int64_array._pt[i]);
        uint32_t meta_flags = values[3]._int32_array._pt[i];
        string doc_id(getString(values[4]._string_array._pt[i]));
        nbostream stream(values[5]._data_array._pt[i]._buf,
                         values[5]._data_array._pt[i]._len);
        DocEntry::UP entry;
        if (!stream.empty()) {
            Document::UP doc = readDocument(stream, *_repo);
            entry.reset(new DocEntry(timestamp, meta_flags, std::move(doc)));
        } else if (!doc_id.empty()) {
            entry.reset(
                    new DocEntry(timestamp, meta_flags, DocumentId(doc_id)));
        } else {
            entry.reset(new DocEntry(timestamp, meta_flags));
        }
        result.push_back(std::move(entry));
    }

    return IterateResult(std::move(result), values[6]._intval8);
}

namespace {
bool shouldFailFast(uint32_t error_code) {
    return error_code != FRTE_RPC_TIMEOUT
        && error_code != FRTE_RPC_CONNECTION
        && error_code != FRTE_RPC_OVERLOAD
        && error_code != FRTE_NO_ERROR;
}
}  // namespace

ProviderProxy::ProviderProxy(const vespalib::string &connect_spec,
                             const DocumentTypeRepo &repo)
    : _supervisor(new FRT_Supervisor()),
      _target(0),
      _repo(&repo)
{
    _supervisor->Start();
    bool connected = false;
    _target = _supervisor->GetTarget(connect_spec.c_str());
    for (size_t i = 0; !connected && (i < (100 + 300)); ++i) {
        FRT_RPCRequest *req = new FRT_RPCRequest();
        req->SetMethodName("vespa.persistence.connect");
        const string build_id = getBuildId();
        req->GetParams()->AddString(build_id.data(), build_id.size());
        _target->InvokeSync(req, 5.0);
        connected = req->CheckReturnTypes("");
        uint32_t error_code = req->GetErrorCode();
        req->SubRef();
        if (!connected) {
            if (shouldFailFast(error_code)) {
                break;
            }
            _target->SubRef();
            if (i < 100) {
                FastOS_Thread::Sleep(100); // retry each 100ms for 10s
            } else {
                FastOS_Thread::Sleep(1000); // retry each 1s for 5m
            }
            _target = _supervisor->GetTarget(connect_spec.c_str());
        }
    }
    if (!connected) {
        LOG(error, "could not connect to peer");
    }
}

ProviderProxy::~ProviderProxy() {
    _target->SubRef();
    _supervisor->ShutDown(true);
}

Result ProviderProxy::initialize() {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.initialize");
    return invokeRpc_Return<Result>(*req, "bs");
}

PartitionStateListResult ProviderProxy::getPartitionStates() const {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.getPartitionStates");
    return invokeRpc_Return<PartitionStateListResult>(*req, "bsIS");
}

BucketIdListResult ProviderProxy::listBuckets(PartitionId partition) const {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.listBuckets");
    req->GetParams()->AddInt64(partition);

    return invokeRpc_Return<BucketIdListResult>(*req, "bsL");
}

Result ProviderProxy::setClusterState(const ClusterState& clusterState) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.setClusterState");

    vespalib::nbostream o;
    clusterState.serialize(o);
    req->GetParams()->AddData(o.c_str(), o.size());
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::setActiveState(const Bucket &bucket,
                                     BucketInfo::ActiveState newState) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.setActiveState");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt8(newState);
    return invokeRpc_Return<Result>(*req, "bs");
}

BucketInfoResult ProviderProxy::getBucketInfo(const Bucket &bucket) const {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.getBucketInfo");
    addBucket(*req->GetParams(), bucket);
    return invokeRpc_Return<BucketInfoResult>(*req, "bsiiiiibb");
}

Result ProviderProxy::put(const Bucket &bucket, Timestamp timestamp,
                          const Document::SP& doc, Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.put");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt64(timestamp);
    addDocument(*req->GetParams(), *doc);
    return invokeRpc_Return<Result>(*req, "bs");
}

RemoveResult ProviderProxy::remove(const Bucket &bucket,
                                   Timestamp timestamp,
                                   const DocumentId &id,
                                   Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.removeById");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt64(timestamp);
    addString(*req->GetParams(), id.toString());
    return invokeRpc_Return<RemoveResult>(*req, "bsb");
}

RemoveResult ProviderProxy::removeIfFound(const Bucket &bucket,
                                          Timestamp timestamp,
                                          const DocumentId &id,
                                          Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.removeIfFound");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt64(timestamp);
    addString(*req->GetParams(), id.toString());
    return invokeRpc_Return<RemoveResult>(*req, "bsb");
}

UpdateResult ProviderProxy::update(const Bucket &bucket, Timestamp timestamp,
                                   const DocumentUpdate::SP& doc_update,
                                   Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.update");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt64(timestamp);
    addDocumentUpdate(*req->GetParams(), *doc_update);
    return invokeRpc_Return<UpdateResult>(*req, "bsl");
}

Result ProviderProxy::flush(const Bucket &bucket, Context&) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.flush");
    addBucket(*req->GetParams(), bucket);
    return invokeRpc_Return<Result>(*req, "bs");
}

GetResult ProviderProxy::get(const Bucket &bucket,
                             const document::FieldSet& fieldSet,
                             const DocumentId &doc_id,
                             Context&) const
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.get");
    document::FieldSetRepo repo;
    addBucket(*req->GetParams(), bucket);
    addString(*req->GetParams(), repo.serialize(fieldSet));
    addString(*req->GetParams(), doc_id.toString());
    return invokeRpc_Return<GetResult>(*req, "bslx");
}

CreateIteratorResult ProviderProxy::createIterator(const Bucket &bucket,
                                                   const document::FieldSet& fieldSet,
                                                   const Selection &select,
                                                   IncludedVersions versions,
                                                   Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.createIterator");
    addBucket(*req->GetParams(), bucket);

    document::FieldSetRepo repo;
    addString(*req->GetParams(), repo.serialize(fieldSet));
    addSelection(*req->GetParams(), select);
    req->GetParams()->AddInt8(versions);
    return invokeRpc_Return<CreateIteratorResult>(*req, "bsl");
}

IterateResult ProviderProxy::iterate(IteratorId id,
                                     uint64_t max_byte_size,
                                     Context&) const
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.iterate");
    req->GetParams()->AddInt64(id);
    req->GetParams()->AddInt64(max_byte_size);
    return invokeRpc_Return<IterateResult>(*req, "bsLISXb");
}

Result ProviderProxy::destroyIterator(IteratorId id, Context&) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.destroyIterator");
    req->GetParams()->AddInt64(id);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::createBucket(const Bucket &bucket, Context&) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.createBucket");
    addBucket(*req->GetParams(), bucket);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::deleteBucket(const Bucket &bucket, Context&) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.deleteBucket");
    addBucket(*req->GetParams(), bucket);
    return invokeRpc_Return<Result>(*req, "bs");
}

BucketIdListResult ProviderProxy::getModifiedBuckets() const {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.getModifiedBuckets");
    return invokeRpc_Return<BucketIdListResult>(*req, "bsL");
}

Result ProviderProxy::split(const Bucket &source,
                            const Bucket &target1,
                            const Bucket &target2,
                            Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.split");
    addBucket(*req->GetParams(), source);
    addBucket(*req->GetParams(), target1);
    addBucket(*req->GetParams(), target2);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::join(const Bucket &source1,
                           const Bucket &source2,
                           const Bucket &target,
                           Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.join");
    addBucket(*req->GetParams(), source1);
    addBucket(*req->GetParams(), source2);
    addBucket(*req->GetParams(), target);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::move(const Bucket &source,
                           PartitionId target,
                           Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.move");
    addBucket(*req->GetParams(), source);
    req->GetParams()->AddInt64(target);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::maintain(const Bucket &bucket, MaintenanceLevel level) {
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.maintain");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt8(level);
    return invokeRpc_Return<Result>(*req, "bs");
}

Result ProviderProxy::removeEntry(const Bucket &bucket, Timestamp timestamp,
                                  Context&)
{
    RequestScopedPtr req(_supervisor->AllocRPCRequest());
    req->SetMethodName("vespa.persistence.removeEntry");
    addBucket(*req->GetParams(), bucket);
    req->GetParams()->AddInt64(timestamp);
    return invokeRpc_Return<Result>(*req, "bs");
}

}  // namespace spi
}  // namespace storage
