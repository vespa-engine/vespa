// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buildid.h"
#include "providerstub.h"
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <persistence/spi/types.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/document/fieldset/fieldsetrepo.h>

using document::BucketId;
using document::ByteBuffer;
using document::DocumentTypeRepo;
using document::VespaDocumentDeserializer;
using document::VespaDocumentSerializer;
using std::vector;
using vespalib::Closure;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::nbostream;

namespace storage {
namespace spi {
namespace {

LoadType defaultLoadType(0, "default");

// Serialize return values
void addResult(FRT_Values &ret, const Result &result) {
    ret.AddInt8(result.getErrorCode());
    ret.AddString(result.getErrorMessage().data(),
                  result.getErrorMessage().size());
}

void addPartitionStateListResult(FRT_Values &ret,
                                 const PartitionStateListResult &result) {
    addResult(ret, result);
    PartitionStateList states = result.getList();
    uint32_t *stateValues = ret.AddInt32Array(states.size());
    FRT_StringValue *reasons = ret.AddStringArray(states.size());
    for (size_t i = 0; i < states.size(); ++i) {
        stateValues[i] = states[i].getState();
        string reason(states[i].getReason());
        ret.SetString(&reasons[i], reason.data(), reason.size());
    }
}

void addBucketInfoResult(FRT_Values &ret, const BucketInfoResult &result) {
    addResult(ret, result);
    const BucketInfo& info = result.getBucketInfo();
    ret.AddInt32(info.getChecksum());
    ret.AddInt32(info.getDocumentCount());
    ret.AddInt32(info.getDocumentSize());
    ret.AddInt32(info.getEntryCount());
    ret.AddInt32(info.getUsedSize());
    ret.AddInt8(static_cast<uint8_t>(info.isReady()));
    ret.AddInt8(static_cast<uint8_t>(info.isActive()));
}

void addRemoveResult(FRT_Values &ret, const RemoveResult &result) {
    addResult(ret, result);
    ret.AddInt8(result.wasFound());
}

void addUpdateResult(FRT_Values &ret, const UpdateResult &result) {
    addResult(ret, result);
    ret.AddInt64(result.getExistingTimestamp());
}

void addGetResult(FRT_Values &ret, const GetResult &result) {
    addResult(ret, result);
    ret.AddInt64(result.getTimestamp());
    if (result.hasDocument()) {
        nbostream stream;
        VespaDocumentSerializer serializer(stream);
        serializer.write(result.getDocument(), document::COMPLETE);
        ret.AddData(stream.c_str(), stream.size());
    } else {
        ret.AddData(0, 0);
    }
}

void addCreateIteratorResult(FRT_Values &ret,
                             const CreateIteratorResult &result)
{
    addResult(ret, result);
    ret.AddInt64(result.getIteratorId());
}

void addIterateResult(FRT_Values &ret, const IterateResult &result)
{
    addResult(ret, result);

    const vector<DocEntry::LP> &entries = result.getEntries();
    uint64_t *timestamps = ret.AddInt64Array(entries.size());
    uint32_t *flags = ret.AddInt32Array(entries.size());
    assert(sizeof(DocEntry::SizeType) == sizeof(uint32_t));
    FRT_StringValue *doc_id_array = ret.AddStringArray(entries.size());
    FRT_DataValue *doc_array = ret.AddDataArray(entries.size());

    for (size_t i = 0; i < entries.size(); ++i) {
        string doc_id_str;
        nbostream stream;
        const DocumentId *doc_id = entries[i]->getDocumentId();
        if (doc_id) {
            doc_id_str = doc_id->toString();
        }
        const Document *doc = entries[i]->getDocument();
        if (doc) {
            VespaDocumentSerializer serializer(stream);
            serializer.write(*doc, document::COMPLETE);
        }

        timestamps[i] = entries[i]->getTimestamp();
        flags[i] = entries[i]->getFlags();
        ret.SetString(&doc_id_array[i], doc_id_str.data(), doc_id_str.size());
        ret.SetData(&doc_array[i], stream.c_str(), stream.size());
    }

    ret.AddInt8(result.isCompleted());
}

void addBucketIdListResult(FRT_Values &ret, const BucketIdListResult& result) {
    addResult(ret, result);

    size_t modified_bucket_size = result.getList().size();
    uint64_t *bucket_id = ret.AddInt64Array(modified_bucket_size);
    for (size_t i = 0; i < modified_bucket_size; ++i) {
        bucket_id[i] = result.getList()[i].getRawId();
    }
}

string getString(const FRT_StringValue &str) {
    return string(str._str, str._len);
}

string getString(const FRT_Value &value) {
    return getString(value._string);
}

Bucket getBucket(const FRT_Value &bucket_val, const FRT_Value &partition_val) {
    BucketId bucket_id(bucket_val._intval64);
    PartitionId partition_id(partition_val._intval64);
    return Bucket(bucket_id, partition_id);
}

Document::UP getDocument(const FRT_Value &val, const DocumentTypeRepo &repo) {
    nbostream stream(val._data._buf, val._data._len);
    const uint16_t version = 8;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    Document::UP doc(new Document);
    deserializer.read(*doc);
    return doc;
}

Selection getSelection(const FRT_Values &params, int i) {
    DocumentSelection doc_sel(getString(params[i]));
    Timestamp timestamp_from(params[i + 1]._intval64);
    Timestamp timestamp_to(params[i + 2]._intval64);
    FRT_Array<uint64_t> array = params[i + 3]._int64_array;
    TimestampList timestamp_subset(array._pt, array._pt + array._len);

    Selection selection(doc_sel);
    selection.setFromTimestamp(timestamp_from);
    selection.setToTimestamp(timestamp_to);
    selection.setTimestampSubset(timestamp_subset);
    return selection;
}

void addConnect(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.connect",
                    "s", "", true, func, obj);
    rb.MethodDesc("Set up connection to proxy.");
    rb.ParamDesc("build_id", "Id to make sure client and server come from the "
                 "same build.");
}

void addGetPartitionStates(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.getPartitionStates",
                    "", "bsIS", true, func, obj);
    rb.MethodDesc("???");
    rb.ReturnDesc("ret", "An array of serialized PartitionStates.");
}

void doGetPartitionStates(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &ret = *req->GetReturn();
    addPartitionStateListResult(ret, provider->getPartitionStates());
    req->Return();
}

void addInitialize(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.initialize",
                    "", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doInitialize(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &ret = *req->GetReturn();
    addResult(ret, provider->initialize());
    req->Return();
}

void addListBuckets(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.listBuckets",
                    "l", "bsL", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("bucket_ids", "An array of BucketIds.");
}

void doListBuckets(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    PartitionId partition_id(params[0]._intval64);

    FRT_Values &ret = *req->GetReturn();
    addBucketIdListResult(ret, provider->listBuckets(partition_id));
    req->Return();
}

void addSetClusterState(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.setClusterState",
                    "x", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("cluster_state", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doSetClusterState(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    vespalib::nbostream stream(params[0]._data._buf, params[0]._data._len);

    ClusterState state(stream);
    FRT_Values &ret = *req->GetReturn();
    addResult(ret, provider->setClusterState(state));
    req->Return();
}

void addSetActiveState(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.setActiveState",
                    "llb", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("bucket_state", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doSetActiveState(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    BucketInfo::ActiveState state = BucketInfo::ActiveState(params[2]._intval8);

    FRT_Values &ret = *req->GetReturn();
    addResult(ret, provider->setActiveState(bucket, state));
    req->Return();
}

void addGetBucketInfo(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.getBucketInfo",
                    "ll", "bsiiiiibb", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("checksum", "");
    rb.ReturnDesc("document_count", "");
    rb.ReturnDesc("document_size", "");
    rb.ReturnDesc("entry_count", "");
    rb.ReturnDesc("used_size", "");
    rb.ReturnDesc("ready", "");
    rb.ReturnDesc("active", "");
}

void doGetBucketInfo(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    FRT_Values &ret = *req->GetReturn();
    addBucketInfoResult(ret, provider->getBucketInfo(bucket));
    req->Return();
}

void addPut(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.put",
                    "lllx", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("timestamp", "");
    rb.ParamDesc("document", "A serialized document");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doPut(FRT_RPCRequest *req, PersistenceProvider *provider,
            const DocumentTypeRepo *repo)
{
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    Timestamp timestamp(params[2]._intval64);
    Document::SP doc(getDocument(params[3], *repo).release());

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->put(bucket, timestamp, doc, context));
    req->Return();
}

void addRemoveById(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.removeById",
                    "llls", "bsb", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("timestamp", "");
    rb.ParamDesc("document_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("existed", "");
}

void doRemoveById(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    Timestamp timestamp(params[2]._intval64);
    DocumentId id(getString(params[3]));

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addRemoveResult(ret, provider->remove(bucket, timestamp, id, context));
    req->Return();
}

void addRemoveIfFound(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.removeIfFound",
                    "llls", "bsb", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("timestamp", "");
    rb.ParamDesc("document_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("existed", "");
}

void doRemoveIfFound(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    Timestamp timestamp(params[2]._intval64);
    DocumentId id(getString(params[3]));

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addRemoveResult(ret,
                    provider->removeIfFound(bucket, timestamp, id, context));
    req->Return();
}

void addUpdate(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.update",
                    "lllx", "bsl", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("timestamp", "");
    rb.ParamDesc("document_update", "A serialized DocumentUpdate");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("existing timestamp", "");
}

void doUpdate(FRT_RPCRequest *req, PersistenceProvider *provider,
              const DocumentTypeRepo *repo)
{
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    Timestamp timestamp(params[2]._intval64);
    ByteBuffer buffer(params[3]._data._buf, params[3]._data._len);
    DocumentUpdate::SP update(new DocumentUpdate(*repo, buffer,
                                                 DocumentUpdate::
                                                 SerializeVersion::
                                                 SERIALIZE_HEAD));

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addUpdateResult(ret, provider->update(bucket, timestamp, update, context));
    req->Return();
}

void addFlush(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.flush", "ll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doFlush(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->flush(bucket, context));
    req->Return();
}

void addGet(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.get",
                    "llss", "bslx", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("field_set", "Array of fields in the set");
    rb.ParamDesc("document_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("timestamp", "");
    rb.ReturnDesc("document", "A serialized document");
}

void doGet(FRT_RPCRequest *req,
           PersistenceProvider *provider,
           const DocumentTypeRepo* repo)
{
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    document::FieldSetRepo fsr;
    document::FieldSet::UP fieldSet = fsr.parse(*repo, getString(params[2]));
    DocumentId id(getString(params[3]));

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addGetResult(ret, provider->get(bucket, *fieldSet, id, context));
    req->Return();
}

void addCreateIterator(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.createIterator",
                    "llssllLb", "bsl", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("field_set", "Field set string (comma-separated list of strings)");
    rb.ParamDesc("document_selection_string", "");
    rb.ParamDesc("timestamp_from", "");
    rb.ParamDesc("timestamp_to", "");
    rb.ParamDesc("timestamp_subset", "");
    rb.ParamDesc("includedversions", "");

    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("iterator_id", "");
}

void doCreateIterator(FRT_RPCRequest *req, PersistenceProvider *provider,
                      const DocumentTypeRepo* repo)
{
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    document::FieldSetRepo fsr;
    document::FieldSet::UP fieldSet = fsr.parse(*repo, getString(params[2]));
    Selection selection = getSelection(params, 3);
    IncludedVersions versions =
        static_cast<IncludedVersions>(params[7]._intval8);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addCreateIteratorResult(ret, provider->createIterator(
                    bucket, *fieldSet, selection, versions, context));
    req->Return();
}

void addIterate(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.iterate",
                    "ll", "bsLISXb", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("iterator_id", "");
    rb.ParamDesc("max_byte_size", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("doc_entry_timestamp", "Array of timestamps for DocEntries");
    rb.ReturnDesc("doc_entry_flags", "Array of flags for DocEntries");
    rb.ReturnDesc("doc_entry_doc_id", "Array of DocumentIds for DocEntries");
    rb.ReturnDesc("doc_entry_doc", "Array of Documents for DocEntries");
    rb.ReturnDesc("completed", "bool");
}

void doIterate(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    IteratorId id(params[0]._intval64);
    uint64_t max_byte_size = params[1]._intval64;

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addIterateResult(ret, provider->iterate(id, max_byte_size, context));
    req->Return();
}

void addDestroyIterator(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.destroyIterator",
                    "l", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("iterator_id", "");
}

void doDestroyIterator(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    IteratorId id(params[0]._intval64);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->destroyIterator(id, context));
    req->Return();
}

void addCreateBucket(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.createBucket",
                    "ll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doCreateBucket(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->createBucket(bucket, context));
    req->Return();
}

void addDeleteBucket(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.deleteBucket",
                    "ll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doDeleteBucket(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->deleteBucket(bucket, context));
    req->Return();
}

void addGetModifiedBuckets(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.getModifiedBuckets",
                    "", "bsL", true, func, obj);
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
    rb.ReturnDesc("modified_buckets_bucket_ids", "Array of bucket ids");
}

void doGetModifiedBuckets(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &ret = *req->GetReturn();
    addBucketIdListResult(ret, provider->getModifiedBuckets());
    req->Return();
}

void addSplit(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.split",
                    "llllll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("source_bucket_id", "");
    rb.ParamDesc("source_partition_id", "");
    rb.ParamDesc("target1_bucket_id", "");
    rb.ParamDesc("target1_partition_id", "");
    rb.ParamDesc("target2_bucket_id", "");
    rb.ParamDesc("target2_partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doSplit(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket source = getBucket(params[0], params[1]);
    Bucket target1 = getBucket(params[2], params[3]);
    Bucket target2 = getBucket(params[4], params[5]);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->split(source, target1, target2, context));
    req->Return();
}

void addJoin(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.join",
                    "llllll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("source1_bucket_id", "");
    rb.ParamDesc("source1_partition_id", "");
    rb.ParamDesc("source2_bucket_id", "");
    rb.ParamDesc("source2_partition_id", "");
    rb.ParamDesc("target_bucket_id", "");
    rb.ParamDesc("target_partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doJoin(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket source1 = getBucket(params[0], params[1]);
    Bucket source2 = getBucket(params[2], params[3]);
    Bucket target = getBucket(params[4], params[5]);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->join(source1, source2, target, context));
    req->Return();
}

void addMove(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.move",
                    "lll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("source_bucket_id", "");
    rb.ParamDesc("source_partition_id", "");
    rb.ParamDesc("target_partition_id", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doMove(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket source = getBucket(params[0], params[1]);
    PartitionId partition_id(params[2]._intval64);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->move(source, partition_id, context));
    req->Return();
}


void addMaintain(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.maintain",
                    "llb", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("verification_level", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doMaintain(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    MaintenanceLevel level =
        static_cast<MaintenanceLevel>(params[2]._intval8);

    FRT_Values &ret = *req->GetReturn();
    addResult(ret, provider->maintain(bucket, level));
    req->Return();
}

void addRemoveEntry(
        FRT_ReflectionBuilder &rb, FRT_METHOD_PT func, FRT_Invokable *obj) {
    rb.DefineMethod("vespa.persistence.removeEntry",
                    "lll", "bs", true, func, obj);
    rb.MethodDesc("???");
    rb.ParamDesc("bucket_id", "");
    rb.ParamDesc("partition_id", "");
    rb.ParamDesc("timestamp", "");
    rb.ReturnDesc("error_code", "");
    rb.ReturnDesc("error_message", "");
}

void doRemoveEntry(FRT_RPCRequest *req, PersistenceProvider *provider) {
    FRT_Values &params = *req->GetParams();
    Bucket bucket = getBucket(params[0], params[1]);
    Timestamp timestamp(params[2]._intval64);

    FRT_Values &ret = *req->GetReturn();
    Context context(defaultLoadType, Priority(0x80), Trace::TraceLevel(0));
    addResult(ret, provider->removeEntry(bucket, timestamp, context));
    req->Return();
}

const uint32_t magic_number = 0xf00ba2;

bool checkConnection(FNET_Connection *connection) {
    return connection && connection->GetContext()._value.INT == magic_number;
}
}  //namespace

void ProviderStub::HOOK_fini(FRT_RPCRequest *req) {
    FNET_Connection *connection = req->GetConnection();
    if (checkConnection(connection)) {
        assert(_provider.get() != 0);
        _providerCleanupTask.ScheduleNow();
    }
}

void ProviderStub::RPC_connect(FRT_RPCRequest *req) {
    FRT_Values &params = *req->GetParams();
    FNET_Connection *connection = req->GetConnection();
    if (checkConnection(connection)) {
        return;
    }
    string build_id = getString(params[0]);
    if (build_id != getBuildId()) {
        req->SetError(FRTE_RPC_METHOD_FAILED,
                      ("Wrong build id. Got '" + build_id +
                       "', required '" + getBuildId() + "'").c_str());
        return;
    } else if (_provider.get()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, "Server is already connected");
        return;
    }
    if (!connection) {
        req->SetError(FRTE_RPC_METHOD_FAILED);
        return;
    }
    connection->SetContext(FNET_Context(magic_number));
    _provider = _factory.create();
}

void ProviderStub::detachAndRun(FRT_RPCRequest *req, Closure::UP closure) {
    if (!checkConnection(req->GetConnection())) {
        req->SetError(FRTE_RPC_METHOD_FAILED);
        return;
    }
    assert(_provider.get() != 0);
    req->Detach();
    _executor.execute(makeTask(std::move(closure)));
}

void ProviderStub::RPC_getPartitionStates(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doGetPartitionStates, req, _provider.get()));
}

void ProviderStub::RPC_initialize(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doInitialize, req, _provider.get()));
}

void ProviderStub::RPC_listBuckets(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doListBuckets, req, _provider.get()));
}

void ProviderStub::RPC_setClusterState(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doSetClusterState, req, _provider.get()));
}

void ProviderStub::RPC_setActiveState(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doSetActiveState, req, _provider.get()));
}

void ProviderStub::RPC_getBucketInfo(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doGetBucketInfo, req, _provider.get()));
}

void ProviderStub::RPC_put(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doPut, req, _provider.get(), _repo));
}

void ProviderStub::RPC_removeById(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doRemoveById, req, _provider.get()));
}

void ProviderStub::RPC_removeIfFound(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doRemoveIfFound, req, _provider.get()));
}

void ProviderStub::RPC_update(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doUpdate, req, _provider.get(), _repo));
}

void ProviderStub::RPC_flush(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doFlush, req, _provider.get()));
}

void ProviderStub::RPC_get(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doGet, req, _provider.get(), _repo));
}

void ProviderStub::RPC_createIterator(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doCreateIterator, req, _provider.get(), _repo));
}

void ProviderStub::RPC_iterate(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doIterate, req, _provider.get()));
}

void ProviderStub::RPC_destroyIterator(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doDestroyIterator, req, _provider.get()));
}

void ProviderStub::RPC_createBucket(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doCreateBucket, req, _provider.get()));
}

void ProviderStub::RPC_deleteBucket(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doDeleteBucket, req, _provider.get()));
}

void ProviderStub::RPC_getModifiedBuckets(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doGetModifiedBuckets, req, _provider.get()));
}

void ProviderStub::RPC_split(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doSplit, req, _provider.get()));
}

void ProviderStub::RPC_join(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doJoin, req, _provider.get()));
}

void ProviderStub::RPC_move(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doMove, req, _provider.get()));
}

void ProviderStub::RPC_maintain(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doMaintain, req, _provider.get()));
}

void ProviderStub::RPC_removeEntry(FRT_RPCRequest *req) {
    detachAndRun(req, makeClosure(doRemoveEntry, req, _provider.get()));
}

void ProviderStub::SetupRpcCalls() {
    FRT_ReflectionBuilder rb(&_supervisor);
    addConnect(rb, FRT_METHOD(ProviderStub::RPC_connect), this);
    addInitialize(
            rb, FRT_METHOD(ProviderStub::RPC_initialize), this);
    addGetPartitionStates(
            rb, FRT_METHOD(ProviderStub::RPC_getPartitionStates), this);
    addListBuckets(rb, FRT_METHOD(ProviderStub::RPC_listBuckets), this);
    addSetClusterState(rb, FRT_METHOD(ProviderStub::RPC_setClusterState), this);
    addSetActiveState(
            rb, FRT_METHOD(ProviderStub::RPC_setActiveState), this);
    addGetBucketInfo(rb, FRT_METHOD(ProviderStub::RPC_getBucketInfo), this);
    addPut(rb, FRT_METHOD(ProviderStub::RPC_put), this);
    addRemoveById(rb, FRT_METHOD(ProviderStub::RPC_removeById), this);
    addRemoveIfFound(rb, FRT_METHOD(ProviderStub::RPC_removeIfFound), this);
    addUpdate(rb, FRT_METHOD(ProviderStub::RPC_update), this);
    addFlush(rb, FRT_METHOD(ProviderStub::RPC_flush), this);
    addGet(rb, FRT_METHOD(ProviderStub::RPC_get), this);
    addCreateIterator(rb, FRT_METHOD(ProviderStub::RPC_createIterator), this);
    addIterate(rb, FRT_METHOD(ProviderStub::RPC_iterate), this);
    addDestroyIterator(
            rb, FRT_METHOD(ProviderStub::RPC_destroyIterator), this);
    addCreateBucket(rb, FRT_METHOD(ProviderStub::RPC_createBucket), this);
    addDeleteBucket(rb, FRT_METHOD(ProviderStub::RPC_deleteBucket), this);
    addGetModifiedBuckets(
            rb, FRT_METHOD(ProviderStub::RPC_getModifiedBuckets), this);
    addSplit(rb, FRT_METHOD(ProviderStub::RPC_split), this);
    addJoin(rb, FRT_METHOD(ProviderStub::RPC_join), this);
    addMove(rb, FRT_METHOD(ProviderStub::RPC_move), this);
    addMaintain(rb, FRT_METHOD(ProviderStub::RPC_maintain), this);
    addRemoveEntry(rb, FRT_METHOD(ProviderStub::RPC_removeEntry), this);
}

ProviderStub::ProviderStub(int port, uint32_t threads,
                           const document::DocumentTypeRepo &repo,
                           PersistenceProviderFactory &factory)
    : _supervisor(),
      _executor(threads, 256*1024),
      _repo(&repo),
      _factory(factory),
      _provider(),
      _providerCleanupTask(_supervisor.GetScheduler(), _executor, _provider)
{
    SetupRpcCalls();
    _supervisor.SetSessionFiniHook(FRT_METHOD(ProviderStub::HOOK_fini), this);
    _supervisor.Start();
    _supervisor.Listen(port);
}

ProviderStub::~ProviderStub() {
    _supervisor.ShutDown(true);
    sync();
}

}  // namespace spi
}  // namespace storage
