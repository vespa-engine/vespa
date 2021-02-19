// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization7.h"
#include "serializationhelper.h"
#include "protobuf_includes.h"

#include <vespa/document/update/documentupdate.h>
#include <vespa/document/util/bufferexceptions.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::mbusprot {

ProtocolSerialization7::ProtocolSerialization7(std::shared_ptr<const document::DocumentTypeRepo> repo)
    : ProtocolSerialization(),
      _repo(std::move(repo))
{
}

namespace {

void set_bucket(protobuf::Bucket& dest, const document::Bucket& src) {
    dest.set_raw_bucket_id(src.getBucketId().getRawId());
    dest.set_space_id(src.getBucketSpace().getId());
}

void set_bucket_id(protobuf::BucketId& dest, const document::BucketId& src) {
    dest.set_raw_id(src.getRawId());
}

document::BucketId get_bucket_id(const protobuf::BucketId& src) {
    return document::BucketId(src.raw_id());
}

void set_bucket_space(protobuf::BucketSpace& dest, const document::BucketSpace& src) {
    dest.set_space_id(src.getId());
}

document::BucketSpace get_bucket_space(const protobuf::BucketSpace& src) {
    return document::BucketSpace(src.space_id());
}

void set_bucket_info(protobuf::BucketInfo& dest, const api::BucketInfo& src) {
    dest.set_last_modified_timestamp(src.getLastModified());
    dest.set_legacy_checksum(src.getChecksum());
    dest.set_doc_count(src.getDocumentCount());
    dest.set_total_doc_size(src.getTotalDocumentSize());
    dest.set_meta_count(src.getMetaCount());
    dest.set_used_file_size(src.getUsedFileSize());
    dest.set_active(src.isActive());
    dest.set_ready(src.isReady());
}

document::Bucket get_bucket(const protobuf::Bucket& src) {
    return document::Bucket(document::BucketSpace(src.space_id()),
                            document::BucketId(src.raw_bucket_id()));
}

api::BucketInfo get_bucket_info(const protobuf::BucketInfo& src) {
    api::BucketInfo info;
    info.setLastModified(src.last_modified_timestamp());
    info.setChecksum(src.legacy_checksum());
    info.setDocumentCount(src.doc_count());
    info.setTotalDocumentSize(src.total_doc_size());
    info.setMetaCount(src.meta_count());
    info.setUsedFileSize(src.used_file_size());
    info.setActive(src.active());
    info.setReady(src.ready());
    return info;
}

documentapi::TestAndSetCondition get_tas_condition(const protobuf::TestAndSetCondition& src) {
    return documentapi::TestAndSetCondition(src.selection());
}

void set_tas_condition(protobuf::TestAndSetCondition& dest, const documentapi::TestAndSetCondition& src) {
    dest.set_selection(src.getSelection().data(), src.getSelection().size());
}

std::shared_ptr<document::Document> get_document(const protobuf::Document& src_doc,
                                                 const document::DocumentTypeRepo& type_repo)
{
    if (!src_doc.payload().empty()) {
        vespalib::nbostream doc_buf(src_doc.payload().data(), src_doc.payload().size());
        return std::make_shared<document::Document>(type_repo, doc_buf);
    }
    return std::shared_ptr<document::Document>();
}

void set_update(protobuf::Update& dest, const document::DocumentUpdate& src) {
    vespalib::nbostream stream;
    src.serializeHEAD(stream);
    dest.set_payload(stream.peek(), stream.size());
}

std::shared_ptr<document::DocumentUpdate> get_update(const protobuf::Update& src,
                                                     const document::DocumentTypeRepo& type_repo)
{
    if (!src.payload().empty()) {
        return document::DocumentUpdate::createHEAD(
                type_repo, vespalib::nbostream(src.payload().data(), src.payload().size()));
    }
    return std::shared_ptr<document::DocumentUpdate>();
}

void write_request_header(vespalib::GrowableByteBuffer& buf, const api::StorageCommand& cmd) {
    protobuf::RequestHeader hdr; // Arena alloc not needed since there are no nested messages
    hdr.set_message_id(cmd.getMsgId());
    hdr.set_priority(cmd.getPriority());
    hdr.set_source_index(cmd.getSourceIndex());

    uint8_t dest[128]; // Only primitive fields, should be plenty large enough.
    auto encoded_size = static_cast<uint32_t>(hdr.ByteSizeLong());
    assert(encoded_size <= sizeof(dest));
    [[maybe_unused]] bool ok = hdr.SerializeWithCachedSizesToArray(dest);
    assert(ok);
    buf.putInt(encoded_size);
    buf.putBytes(reinterpret_cast<const char*>(dest), encoded_size);
}

void write_response_header(vespalib::GrowableByteBuffer& buf, const api::StorageReply& reply) {
    protobuf::ResponseHeader hdr; // Arena alloc not needed since there are no nested messages
    const auto& result = reply.getResult();
    hdr.set_return_code_id(static_cast<uint32_t>(result.getResult()));
    if (!result.getMessage().empty()) {
        hdr.set_return_code_message(result.getMessage().data(), result.getMessage().size());
    }
    hdr.set_message_id(reply.getMsgId());
    hdr.set_priority(reply.getPriority());

    const auto header_size = hdr.ByteSizeLong();
    assert(header_size <= UINT32_MAX);
    buf.putInt(static_cast<uint32_t>(header_size));

    auto* dest_buf = reinterpret_cast<uint8_t*>(buf.allocate(header_size));
    [[maybe_unused]] bool ok = hdr.SerializeWithCachedSizesToArray(dest_buf);
    assert(ok);
}

void decode_request_header(document::ByteBuffer& buf, protobuf::RequestHeader& hdr) {
    auto hdr_len = static_cast<uint32_t>(SerializationHelper::getInt(buf));
    if (hdr_len > buf.getRemaining()) {
        throw document::BufferOutOfBoundsException(buf.getPos(), hdr_len);
    }
    bool ok = hdr.ParseFromArray(buf.getBufferAtPos(), hdr_len);
    if (!ok) {
        throw vespalib::IllegalArgumentException("Malformed protobuf request header");
    }
    buf.incPos(hdr_len);
}

void decode_response_header(document::ByteBuffer& buf, protobuf::ResponseHeader& hdr) {
    auto hdr_len = static_cast<uint32_t>(SerializationHelper::getInt(buf));
    if (hdr_len > buf.getRemaining()) {
        throw document::BufferOutOfBoundsException(buf.getPos(), hdr_len);
    }
    bool ok = hdr.ParseFromArray(buf.getBufferAtPos(), hdr_len);
    if (!ok) {
        throw vespalib::IllegalArgumentException("Malformed protobuf response header");
    }
    buf.incPos(hdr_len);
}

} // anonymous namespace

template <typename ProtobufType>
class BaseEncoder {
    vespalib::GrowableByteBuffer& _out_buf;
    ::google::protobuf::Arena     _arena;
    ProtobufType*                 _proto_obj;
public:
    explicit BaseEncoder(vespalib::GrowableByteBuffer& out_buf)
        : _out_buf(out_buf),
          _arena(),
          _proto_obj(::google::protobuf::Arena::Create<ProtobufType>(&_arena))
    {
    }

    void encode() {
        assert(_proto_obj != nullptr);
        const auto sz = _proto_obj->ByteSizeLong();
        assert(sz <= UINT32_MAX);
        auto* buf = reinterpret_cast<uint8_t*>(_out_buf.allocate(sz));
        [[maybe_unused]] bool ok = _proto_obj->SerializeWithCachedSizesToArray(buf);
        assert(ok);
        _proto_obj = nullptr;
    }
protected:
    vespalib::GrowableByteBuffer& buffer() noexcept { return _out_buf; }

    // Precondition: encode() is not called
    ProtobufType& proto_obj() noexcept { return *_proto_obj; }
    const ProtobufType& proto_obj() const noexcept { return *_proto_obj; }
};

template <typename ProtobufType>
class RequestEncoder : public BaseEncoder<ProtobufType> {
public:
    RequestEncoder(vespalib::GrowableByteBuffer& out_buf, const api::StorageCommand& cmd)
        : BaseEncoder<ProtobufType>(out_buf)
    {
        write_request_header(out_buf, cmd);
    }

    // Precondition: encode() is not called
    ProtobufType& request() noexcept { return this->proto_obj(); }
    const ProtobufType& request() const noexcept { return this->proto_obj(); }
};

template <typename ProtobufType>
class ResponseEncoder : public BaseEncoder<ProtobufType> {
public:
    ResponseEncoder(vespalib::GrowableByteBuffer& out_buf, const api::StorageReply& reply)
        : BaseEncoder<ProtobufType>(out_buf)
    {
        write_response_header(out_buf, reply);
    }

    // Precondition: encode() is not called
    ProtobufType& response() noexcept { return this->proto_obj(); }
    const ProtobufType& response() const noexcept { return this->proto_obj(); }
};

template <typename ProtobufType>
class RequestDecoder {
    protobuf::RequestHeader         _hdr;
    ::google::protobuf::Arena       _arena;
    ProtobufType*                   _proto_obj;
public:
    RequestDecoder(document::ByteBuffer& in_buf)
        : _arena(),
          _proto_obj(::google::protobuf::Arena::Create<ProtobufType>(&_arena))
    {
        decode_request_header(in_buf, _hdr);
        assert(in_buf.getRemaining() <= INT_MAX);
        bool ok = _proto_obj->ParseFromArray(in_buf.getBufferAtPos(), in_buf.getRemaining());
        if (!ok) {
            throw vespalib::IllegalArgumentException(
                    vespalib::make_string("Malformed protobuf request payload for %s",
                                          ProtobufType::descriptor()->full_name().c_str()));
        }
    }

    void transfer_meta_information_to(api::StorageCommand& dest) {
        dest.forceMsgId(_hdr.message_id());
        dest.setPriority(static_cast<uint8_t>(_hdr.priority()));
        dest.setSourceIndex(static_cast<uint16_t>(_hdr.source_index()));
    }

    ProtobufType& request() noexcept { return *_proto_obj; }
    const ProtobufType& request() const noexcept { return *_proto_obj; }
};

template <typename ProtobufType>
class ResponseDecoder {
    protobuf::ResponseHeader  _hdr;
    ::google::protobuf::Arena _arena;
    ProtobufType*             _proto_obj;
public:
    explicit ResponseDecoder(document::ByteBuffer& in_buf)
        : _arena(),
          _proto_obj(::google::protobuf::Arena::Create<ProtobufType>(&_arena))
    {
        decode_response_header(in_buf, _hdr);
        assert(in_buf.getRemaining() <= INT_MAX);
        bool ok = _proto_obj->ParseFromArray(in_buf.getBufferAtPos(), in_buf.getRemaining());
        if (!ok) {
            throw vespalib::IllegalArgumentException(
                    vespalib::make_string("Malformed protobuf response payload for %s",
                                          ProtobufType::descriptor()->full_name().c_str()));
        }
    }

    void transfer_meta_information_to(api::StorageReply& dest) {
        dest.forceMsgId(_hdr.message_id());
        dest.setPriority(static_cast<uint8_t>(_hdr.priority()));
        dest.setResult(api::ReturnCode(static_cast<api::ReturnCode::Result>(_hdr.return_code_id()),
                                       _hdr.return_code_message()));
    }

    ProtobufType& response() noexcept { return *_proto_obj; }
    const ProtobufType& response() const noexcept { return *_proto_obj; }
};

template <typename ProtobufType, typename Func>
void encode_request(vespalib::GrowableByteBuffer& out_buf, const api::StorageCommand& msg, Func&& f) {
    RequestEncoder<ProtobufType> enc(out_buf, msg);
    f(enc.request());
    enc.encode();
}

template <typename ProtobufType, typename Func>
void encode_response(vespalib::GrowableByteBuffer& out_buf, const api::StorageReply& reply, Func&& f) {
    ResponseEncoder<ProtobufType> enc(out_buf, reply);
    auto& res = enc.response();
    f(res);
    enc.encode();
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageCommand>
ProtocolSerialization7::decode_request(document::ByteBuffer& in_buf, Func&& f) const {
    RequestDecoder<ProtobufType> dec(in_buf);
    const auto& req = dec.request();
    auto cmd = f(req);
    dec.transfer_meta_information_to(*cmd);
    return cmd;
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageReply>
ProtocolSerialization7::decode_response(document::ByteBuffer& in_buf, Func&& f) const {
    ResponseDecoder<ProtobufType> dec(in_buf);
    const auto& res = dec.response();
    auto reply = f(res);
    dec.transfer_meta_information_to(*reply);
    return reply;
}

template <typename ProtobufType, typename Func>
void encode_bucket_request(vespalib::GrowableByteBuffer& out_buf, const api::BucketCommand& msg, Func&& f) {
    encode_request<ProtobufType>(out_buf, msg, [&](ProtobufType& req) {
        set_bucket(*req.mutable_bucket(), msg.getBucket());
        f(req);
    });
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageCommand>
ProtocolSerialization7::decode_bucket_request(document::ByteBuffer& in_buf, Func&& f) const {
    return decode_request<ProtobufType>(in_buf, [&](const ProtobufType& req) {
        if (!req.has_bucket()) {
            throw vespalib::IllegalArgumentException(
                    vespalib::make_string("Malformed protocol buffer request for %s; no bucket",
                                          ProtobufType::descriptor()->full_name().c_str()));
        }
        const auto bucket = get_bucket(req.bucket());
        return f(req, bucket);
    });
}

template <typename ProtobufType, typename Func>
void encode_bucket_response(vespalib::GrowableByteBuffer& out_buf, const api::BucketReply& reply, Func&& f) {
    encode_response<ProtobufType>(out_buf, reply, [&](ProtobufType& res) {
        if (reply.hasBeenRemapped()) {
            set_bucket_id(*res.mutable_remapped_bucket_id(), reply.getBucketId());
        }
        f(res);
    });
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageReply>
ProtocolSerialization7::decode_bucket_response(document::ByteBuffer& in_buf, Func&& f) const {
    return decode_response<ProtobufType>(in_buf, [&](const ProtobufType& res) {
        auto reply = f(res);
        if (res.has_remapped_bucket_id()) {
            reply->remapBucketId(get_bucket_id(res.remapped_bucket_id()));
        }
        return reply;
    });
}

template <typename ProtobufType, typename Func>
void encode_bucket_info_response(vespalib::GrowableByteBuffer& out_buf, const api::BucketInfoReply& reply, Func&& f) {
    encode_bucket_response<ProtobufType>(out_buf, reply, [&](ProtobufType& res) {
        set_bucket_info(*res.mutable_bucket_info(), reply.getBucketInfo());
        f(res);
    });
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageReply>
ProtocolSerialization7::decode_bucket_info_response(document::ByteBuffer& in_buf, Func&& f) const {
    return decode_bucket_response<ProtobufType>(in_buf, [&](const ProtobufType& res) {
        auto reply = f(res);
        reply->setBucketInfo(get_bucket_info(res.bucket_info())); // If not present, default of all zeroes is correct
        return reply;
    });
}

// TODO document protobuf ducktyping assumptions

namespace {
// Inherit from known base class just to avoid having to template this. We don't care about its subtype anyway.
void no_op_encode([[maybe_unused]] ::google::protobuf::Message&) {
    // nothing to do here.
}

void set_document(protobuf::Document& target_doc, const document::Document& src_doc) {
    vespalib::nbostream stream;
    src_doc.serialize(stream);
    target_doc.set_payload(stream.peek(), stream.size());
}

}

// -----------------------------------------------------------------
// Put
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::PutCommand& msg) const {
    encode_bucket_request<protobuf::PutRequest>(buf, msg, [&](auto& req) {
        req.set_new_timestamp(msg.getTimestamp());
        req.set_expected_old_timestamp(msg.getUpdateTimestamp());
        if (msg.getCondition().isPresent()) {
            set_tas_condition(*req.mutable_condition(), msg.getCondition());
        }
        if (msg.getDocument()) {
            set_document(*req.mutable_document(), *msg.getDocument());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::PutReply& msg) const {
    encode_bucket_info_response<protobuf::PutResponse>(buf, msg, [&](auto& res) {
        res.set_was_found(msg.wasFound());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodePutCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::PutRequest>(buf, [&](auto& req, auto& bucket) {
        auto document = get_document(req.document(), type_repo());
        auto cmd = std::make_unique<api::PutCommand>(bucket, std::move(document), req.new_timestamp());
        cmd->setUpdateTimestamp(req.expected_old_timestamp());
        if (req.has_condition()) {
            cmd->setCondition(get_tas_condition(req.condition()));
        }
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodePutReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::PutResponse>(buf, [&](auto& res) {
        return std::make_unique<api::PutReply>(static_cast<const api::PutCommand&>(cmd), res.was_found());
    });
}

// -----------------------------------------------------------------
// Update
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::UpdateCommand& msg) const {
    encode_bucket_request<protobuf::UpdateRequest>(buf, msg, [&](auto& req) {
        auto* update = msg.getUpdate().get();
        if (update) {
            set_update(*req.mutable_update(), *update);
        }
        req.set_new_timestamp(msg.getTimestamp());
        req.set_expected_old_timestamp(msg.getOldTimestamp());
        if (msg.getCondition().isPresent()) {
            set_tas_condition(*req.mutable_condition(), msg.getCondition());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::UpdateReply& msg) const {
    encode_bucket_info_response<protobuf::UpdateResponse>(buf, msg, [&](auto& res) {
        res.set_updated_timestamp(msg.getOldTimestamp());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeUpdateCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::UpdateRequest>(buf, [&](auto& req, auto& bucket) {
        auto update = get_update(req.update(), type_repo());
        auto cmd = std::make_unique<api::UpdateCommand>(bucket, std::move(update), req.new_timestamp());
        cmd->setOldTimestamp(req.expected_old_timestamp());
        if (req.has_condition()) {
            cmd->setCondition(get_tas_condition(req.condition()));
        }
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeUpdateReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::UpdateResponse>(buf, [&](auto& res) {
        return std::make_unique<api::UpdateReply>(static_cast<const api::UpdateCommand&>(cmd),
                                                  res.updated_timestamp());
    });
}

// -----------------------------------------------------------------
// Remove
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RemoveCommand& msg) const {
    encode_bucket_request<protobuf::RemoveRequest>(buf, msg, [&](auto& req) {
        auto doc_id_str = msg.getDocumentId().toString();
        req.set_document_id(doc_id_str.data(), doc_id_str.size());
        req.set_new_timestamp(msg.getTimestamp());
        if (msg.getCondition().isPresent()) {
            set_tas_condition(*req.mutable_condition(), msg.getCondition());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RemoveReply& msg) const {
    encode_bucket_info_response<protobuf::RemoveResponse>(buf, msg, [&](auto& res) {
        res.set_removed_timestamp(msg.getOldTimestamp());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeRemoveCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::RemoveRequest>(buf, [&](auto& req, auto& bucket) {
        document::DocumentId doc_id(vespalib::stringref(req.document_id().data(), req.document_id().size()));
        auto cmd = std::make_unique<api::RemoveCommand>(bucket, doc_id, req.new_timestamp());
        if (req.has_condition()) {
            cmd->setCondition(get_tas_condition(req.condition()));
        }
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeRemoveReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::RemoveResponse>(buf, [&](auto& res) {
        return std::make_unique<api::RemoveReply>(static_cast<const api::RemoveCommand&>(cmd),
                                                  res.removed_timestamp());
    });
}

// -----------------------------------------------------------------
// Get
// -----------------------------------------------------------------

namespace {

protobuf::GetRequest_InternalReadConsistency read_consistency_to_protobuf(api::InternalReadConsistency consistency) {
    switch (consistency) {
    case api::InternalReadConsistency::Strong: return protobuf::GetRequest_InternalReadConsistency_Strong;
    case api::InternalReadConsistency::Weak:   return protobuf::GetRequest_InternalReadConsistency_Weak;
    default: return protobuf::GetRequest_InternalReadConsistency_Strong;
    }
}

api::InternalReadConsistency read_consistency_from_protobuf(protobuf::GetRequest_InternalReadConsistency consistency) {
    switch (consistency) {
    case protobuf::GetRequest_InternalReadConsistency_Strong: return api::InternalReadConsistency::Strong;
    case protobuf::GetRequest_InternalReadConsistency_Weak:   return api::InternalReadConsistency::Weak;
    default: return api::InternalReadConsistency::Strong;
    }
}

}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetCommand& msg) const {
    encode_bucket_request<protobuf::GetRequest>(buf, msg, [&](auto& req) {
        auto doc_id = msg.getDocumentId().toString();
        req.set_document_id(doc_id.data(), doc_id.size());
        req.set_before_timestamp(msg.getBeforeTimestamp());
        if (!msg.getFieldSet().empty()) {
            req.set_field_set(msg.getFieldSet().data(), msg.getFieldSet().size());
        }
        req.set_internal_read_consistency(read_consistency_to_protobuf(msg.internal_read_consistency()));
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetReply& msg) const {
    encode_bucket_info_response<protobuf::GetResponse>(buf, msg, [&](auto& res) {
        if (msg.getDocument()) {
            set_document(*res.mutable_document(), *msg.getDocument());
        }
        if (!msg.is_tombstone()) {
            res.set_last_modified_timestamp(msg.getLastModifiedTimestamp());
        } else {
            // This field will be ignored by older versions, making the behavior as if
            // a timestamp of zero was returned for tombstones, as it the legacy behavior.
            res.set_tombstone_timestamp(msg.getLastModifiedTimestamp());
            // Will not be encoded onto the wire, but we include it here to hammer down the
            // point that it's intentional to have the last modified time appear as a not
            // found document for older versions.
            res.set_last_modified_timestamp(0);
        }
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeGetCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::GetRequest>(buf, [&](auto& req, auto& bucket) {
        document::DocumentId doc_id(vespalib::stringref(req.document_id().data(), req.document_id().size()));
        auto op = std::make_unique<api::GetCommand>(bucket, std::move(doc_id),
                                                    req.field_set(), req.before_timestamp());
        op->set_internal_read_consistency(read_consistency_from_protobuf(req.internal_read_consistency()));
        return op;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeGetReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::GetResponse>(buf, [&](auto& res) {
        try {
            auto document = get_document(res.document(), type_repo());
            const bool is_tombstone = (res.tombstone_timestamp() != 0);
            const auto effective_timestamp = (is_tombstone ? res.tombstone_timestamp()
                                                           : res.last_modified_timestamp());
            return std::make_unique<api::GetReply>(static_cast<const api::GetCommand&>(cmd),
                                                   std::move(document), effective_timestamp,
                                                   false, is_tombstone);
        } catch (std::exception& e) {
            auto reply = std::make_unique<api::GetReply>(static_cast<const api::GetCommand&>(cmd),
                                                         std::shared_ptr<document::Document>(), 0u);
            reply->setResult(api::ReturnCode(api::ReturnCode::UNPARSEABLE, e.what()));
            return reply;
        }
    });
}

// -----------------------------------------------------------------
// Revert
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RevertCommand& msg) const {
    encode_bucket_request<protobuf::RevertRequest>(buf, msg, [&](auto& req) {
        auto* tokens = req.mutable_revert_tokens();
        assert(msg.getRevertTokens().size() <= INT_MAX);
        tokens->Reserve(static_cast<int>(msg.getRevertTokens().size()));
        for (auto token : msg.getRevertTokens()) {
            tokens->Add(token);
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RevertReply& msg) const {
    encode_bucket_info_response<protobuf::RevertResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeRevertCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::RevertRequest>(buf, [&](auto& req, auto& bucket) {
        std::vector<api::Timestamp> tokens;
        tokens.reserve(req.revert_tokens_size());
        for (auto token : req.revert_tokens()) {
            tokens.emplace_back(api::Timestamp(token));
        }
        return std::make_unique<api::RevertCommand>(bucket, std::move(tokens));
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeRevertReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::RevertResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::RevertReply>(static_cast<const api::RevertCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// RemoveLocation
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RemoveLocationCommand& msg) const {
    encode_bucket_request<protobuf::RemoveLocationRequest>(buf, msg, [&](auto& req) {
        req.set_document_selection(msg.getDocumentSelection().data(), msg.getDocumentSelection().size());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RemoveLocationReply& msg) const {
    encode_bucket_info_response<protobuf::RemoveLocationResponse>(buf, msg, [&](auto& res) {
        res.mutable_stats()->set_documents_removed(msg.documents_removed());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeRemoveLocationCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::RemoveLocationRequest>(buf, [&](auto& req, auto& bucket) {
        return std::make_unique<api::RemoveLocationCommand>(req.document_selection(), bucket);
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeRemoveLocationReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::RemoveLocationResponse>(buf, [&](auto& res) {
        uint32_t documents_removed = (res.has_stats() ? res.stats().documents_removed() : 0u);
        return std::make_unique<api::RemoveLocationReply>(
                static_cast<const api::RemoveLocationCommand&>(cmd),
                documents_removed);
    });
}

// -----------------------------------------------------------------
// DeleteBucket
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::DeleteBucketCommand& msg) const {
    encode_bucket_request<protobuf::DeleteBucketRequest>(buf, msg, [&](auto& req) {
        set_bucket_info(*req.mutable_expected_bucket_info(), msg.getBucketInfo());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::DeleteBucketReply& msg) const {
    encode_bucket_info_response<protobuf::DeleteBucketResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeDeleteBucketCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::DeleteBucketRequest>(buf, [&](auto& req, auto& bucket) {
        auto cmd = std::make_unique<api::DeleteBucketCommand>(bucket);
        if (req.has_expected_bucket_info()) {
            cmd->setBucketInfo(get_bucket_info(req.expected_bucket_info()));
        }
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeDeleteBucketReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::DeleteBucketResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::DeleteBucketReply>(static_cast<const api::DeleteBucketCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// CreateBucket
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::CreateBucketCommand& msg) const {
    encode_bucket_request<protobuf::CreateBucketRequest>(buf, msg, [&](auto& req) {
        req.set_create_as_active(msg.getActive());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::CreateBucketReply& msg) const {
    encode_bucket_info_response<protobuf::CreateBucketResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeCreateBucketCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::CreateBucketRequest>(buf, [&](auto& req, auto& bucket) {
        auto cmd = std::make_unique<api::CreateBucketCommand>(bucket);
        cmd->setActive(req.create_as_active());
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeCreateBucketReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::CreateBucketResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::CreateBucketReply>(static_cast<const api::CreateBucketCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// MergeBucket
// -----------------------------------------------------------------

namespace {

void set_merge_nodes(::google::protobuf::RepeatedPtrField<protobuf::MergeNode>& dest,
                     const std::vector<api::MergeBucketCommand::Node>& src)
{
    dest.Reserve(src.size());
    for (const auto& src_node : src) {
        auto* dest_node = dest.Add();
        dest_node->set_index(src_node.index);
        dest_node->set_source_only(src_node.sourceOnly);
    }
}

std::vector<api::MergeBucketCommand::Node> get_merge_nodes(
        const ::google::protobuf::RepeatedPtrField<protobuf::MergeNode>& src)
{
    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.reserve(src.size());
    for (const auto& node : src) {
        nodes.emplace_back(node.index(), node.source_only());
    }
    return nodes;
}

}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::MergeBucketCommand& msg) const {
    encode_bucket_request<protobuf::MergeBucketRequest>(buf, msg, [&](auto& req) {
        set_merge_nodes(*req.mutable_nodes(), msg.getNodes());
        req.set_max_timestamp(msg.getMaxTimestamp());
        req.set_cluster_state_version(msg.getClusterStateVersion());
        for (uint16_t chain_node : msg.getChain()) {
            req.add_node_chain(chain_node);
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::MergeBucketReply& msg) const {
    encode_bucket_response<protobuf::MergeBucketResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeMergeBucketCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::MergeBucketRequest>(buf, [&](auto& req, auto& bucket) {
        auto nodes = get_merge_nodes(req.nodes());
        auto cmd = std::make_unique<api::MergeBucketCommand>(bucket, std::move(nodes), req.max_timestamp());
        cmd->setClusterStateVersion(req.cluster_state_version());
        std::vector<uint16_t> chain;
        chain.reserve(req.node_chain_size());
        for (uint16_t node : req.node_chain()) {
            chain.emplace_back(node);
        }
        cmd->setChain(std::move(chain));
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeMergeBucketReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::MergeBucketResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::MergeBucketReply>(static_cast<const api::MergeBucketCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// GetBucketDiff
// -----------------------------------------------------------------

namespace {

void set_global_id(protobuf::GlobalId& dest, const document::GlobalId& src) {
    static_assert(document::GlobalId::LENGTH == 12);
    uint64_t lo64;
    uint32_t hi32;
    memcpy(&lo64, src.get(), sizeof(uint64_t));
    memcpy(&hi32, src.get() + sizeof(uint64_t), sizeof(uint32_t));
    dest.set_lo_64(lo64);
    dest.set_hi_32(hi32);
}

document::GlobalId get_global_id(const protobuf::GlobalId& src) {
    static_assert(document::GlobalId::LENGTH == 12);
    const uint64_t lo64 = src.lo_64();
    const uint32_t hi32 = src.hi_32();

    char buf[document::GlobalId::LENGTH];
    memcpy(buf, &lo64, sizeof(uint64_t));
    memcpy(buf + sizeof(uint64_t), &hi32, sizeof(uint32_t));
    return document::GlobalId(buf);
}

void set_diff_entry(protobuf::MetaDiffEntry& dest, const api::GetBucketDiffCommand::Entry& src) {
    dest.set_timestamp(src._timestamp);
    set_global_id(*dest.mutable_gid(), src._gid);
    dest.set_header_size(src._headerSize);
    dest.set_body_size(src._bodySize);
    dest.set_flags(src._flags);
    dest.set_presence_mask(src._hasMask);
}

api::GetBucketDiffCommand::Entry get_diff_entry(const protobuf::MetaDiffEntry& src) {
    api::GetBucketDiffCommand::Entry e;
    e._timestamp = src.timestamp();
    e._gid = get_global_id(src.gid());
    e._headerSize = src.header_size();
    e._bodySize = src.body_size();
    e._flags = src.flags();
    e._hasMask = src.presence_mask();
    return e;
}

void fill_proto_meta_diff(::google::protobuf::RepeatedPtrField<protobuf::MetaDiffEntry>& dest,
                          const std::vector<api::GetBucketDiffCommand::Entry>& src) {
    for (const auto& diff_entry : src) {
        set_diff_entry(*dest.Add(), diff_entry);
    }
}

void fill_api_meta_diff(std::vector<api::GetBucketDiffCommand::Entry>& dest,
                        const ::google::protobuf::RepeatedPtrField<protobuf::MetaDiffEntry>& src) {
    // FIXME GetBucketDiffReply ctor copies the diff from the request for some reason
    // TODO verify this isn't actually used anywhere and remove this "feature".
    dest.clear();
    dest.reserve(src.size());
    for (const auto& diff_entry : src) {
        dest.emplace_back(get_diff_entry(diff_entry));
    }
}

} // anonymous namespace

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetBucketDiffCommand& msg) const {
    encode_bucket_request<protobuf::GetBucketDiffRequest>(buf, msg, [&](auto& req) {
        set_merge_nodes(*req.mutable_nodes(), msg.getNodes());
        req.set_max_timestamp(msg.getMaxTimestamp());
        fill_proto_meta_diff(*req.mutable_diff(), msg.getDiff());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetBucketDiffReply& msg) const {
    encode_bucket_response<protobuf::GetBucketDiffResponse>(buf, msg, [&](auto& res) {
        fill_proto_meta_diff(*res.mutable_diff(), msg.getDiff());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeGetBucketDiffCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::GetBucketDiffRequest>(buf, [&](auto& req, auto& bucket) {
        auto nodes = get_merge_nodes(req.nodes());
        auto cmd = std::make_unique<api::GetBucketDiffCommand>(bucket, std::move(nodes), req.max_timestamp());
        fill_api_meta_diff(cmd->getDiff(), req.diff());
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeGetBucketDiffReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::GetBucketDiffResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::GetBucketDiffReply>(static_cast<const api::GetBucketDiffCommand&>(cmd));
        fill_api_meta_diff(reply->getDiff(), res.diff());
        return reply;
    });
}

// -----------------------------------------------------------------
// ApplyBucketDiff
// -----------------------------------------------------------------

namespace {

void fill_api_apply_diff_vector(std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
                                const ::google::protobuf::RepeatedPtrField<protobuf::ApplyDiffEntry>& src)
{
    // We use the same approach as the legacy protocols here in that we pre-reserve and
    // directly write into the vector. This avoids having to ensure all buffer management is movable.
    size_t n_entries = src.size();
    diff.resize(n_entries);
    for (size_t i = 0; i < n_entries; ++i) {
        auto& proto_entry = src.Get(i);
        auto& dest = diff[i];
        dest._entry = get_diff_entry(proto_entry.entry_meta());
        dest._docName = proto_entry.document_id();
        // TODO consider making buffers std::strings instead to avoid explicit zeroing-on-resize overhead
        dest._headerBlob.resize(proto_entry.header_blob().size());
        memcpy(dest._headerBlob.data(), proto_entry.header_blob().data(), proto_entry.header_blob().size());
        dest._bodyBlob.resize(proto_entry.body_blob().size());
        memcpy(dest._bodyBlob.data(), proto_entry.body_blob().data(), proto_entry.body_blob().size());
    }
}

void fill_proto_apply_diff_vector(::google::protobuf::RepeatedPtrField<protobuf::ApplyDiffEntry>& dest,
                                  const std::vector<api::ApplyBucketDiffCommand::Entry>& src)
{
    dest.Reserve(src.size());
    for (const auto& entry : src) {
        auto* proto_entry = dest.Add();
        set_diff_entry(*proto_entry->mutable_entry_meta(), entry._entry);
        proto_entry->set_document_id(entry._docName.data(), entry._docName.size());
        proto_entry->set_header_blob(entry._headerBlob.data(), entry._headerBlob.size());
        proto_entry->set_body_blob(entry._bodyBlob.data(), entry._bodyBlob.size());
    }
}

} // anonymous namespace

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::ApplyBucketDiffCommand& msg) const {
    encode_bucket_request<protobuf::ApplyBucketDiffRequest>(buf, msg, [&](auto& req) {
        set_merge_nodes(*req.mutable_nodes(), msg.getNodes());
        req.set_max_buffer_size(0x400000); // Unused, GC soon.
        fill_proto_apply_diff_vector(*req.mutable_entries(), msg.getDiff());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::ApplyBucketDiffReply& msg) const {
    encode_bucket_response<protobuf::ApplyBucketDiffResponse>(buf, msg, [&](auto& res) {
        fill_proto_apply_diff_vector(*res.mutable_entries(), msg.getDiff());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeApplyBucketDiffCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::ApplyBucketDiffRequest>(buf, [&](auto& req, auto& bucket) {
        auto nodes = get_merge_nodes(req.nodes());
        auto cmd = std::make_unique<api::ApplyBucketDiffCommand>(bucket, std::move(nodes));
        fill_api_apply_diff_vector(cmd->getDiff(), req.entries());
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeApplyBucketDiffReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::ApplyBucketDiffResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(static_cast<const api::ApplyBucketDiffCommand&>(cmd));
        fill_api_apply_diff_vector(reply->getDiff(), res.entries());
        return reply;
    });
}

// -----------------------------------------------------------------
// RequestBucketInfo
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RequestBucketInfoCommand& msg) const {
    encode_request<protobuf::RequestBucketInfoRequest>(buf, msg, [&](auto& req) {
        set_bucket_space(*req.mutable_bucket_space(), msg.getBucketSpace());
        auto& buckets = msg.getBuckets();
        if (!buckets.empty()) {
            auto* proto_buckets = req.mutable_explicit_bucket_set();
            for (const auto& b : buckets) {
                set_bucket_id(*proto_buckets->add_bucket_ids(), b);
            }
        } else {
            auto* all_buckets = req.mutable_all_buckets();
            auto cluster_state = msg.getSystemState().toString();
            all_buckets->set_distributor_index(msg.getDistributor());
            all_buckets->set_cluster_state(cluster_state.data(), cluster_state.size());
            all_buckets->set_distribution_hash(msg.getDistributionHash().data(), msg.getDistributionHash().size());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RequestBucketInfoReply& msg) const {
    encode_response<protobuf::RequestBucketInfoResponse>(buf, msg, [&](auto& res) {
        auto* proto_info = res.mutable_bucket_infos();
        proto_info->Reserve(msg.getBucketInfo().size());
        for (const auto& entry : msg.getBucketInfo()) {
            auto* bucket_and_info = proto_info->Add();
            bucket_and_info->set_raw_bucket_id(entry._bucketId.getRawId());
            set_bucket_info(*bucket_and_info->mutable_bucket_info(), entry._info);
        }
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeRequestBucketInfoCommand(BBuf& buf) const {
    return decode_request<protobuf::RequestBucketInfoRequest>(buf, [&](auto& req) {
        auto bucket_space = get_bucket_space(req.bucket_space());
        if (req.has_explicit_bucket_set()) {
            const uint32_t n_buckets = req.explicit_bucket_set().bucket_ids_size();
            std::vector<document::BucketId> buckets(n_buckets);
            const auto& proto_buckets = req.explicit_bucket_set().bucket_ids();
            for (uint32_t i = 0; i < n_buckets; ++i) {
                buckets[i] = get_bucket_id(proto_buckets.Get(i));
            }
            return std::make_unique<api::RequestBucketInfoCommand>(bucket_space, std::move(buckets));
        } else if (req.has_all_buckets()) {
            const auto& all_req = req.all_buckets();
            return std::make_unique<api::RequestBucketInfoCommand>(
                    bucket_space, all_req.distributor_index(),
                    lib::ClusterState(all_req.cluster_state()), all_req.distribution_hash());
        } else {
            throw vespalib::IllegalArgumentException("RequestBucketInfo does not have any applicable fields set");
        }
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeRequestBucketInfoReply(const SCmd& cmd, BBuf& buf) const {
    return decode_response<protobuf::RequestBucketInfoResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::RequestBucketInfoReply>(static_cast<const api::RequestBucketInfoCommand&>(cmd));
        auto& dest_entries = reply->getBucketInfo();
        uint32_t n_entries = res.bucket_infos_size();
        dest_entries.resize(n_entries);
        for (uint32_t i = 0; i < n_entries; ++i) {
            const auto& proto_entry = res.bucket_infos(i);
            dest_entries[i]._bucketId = document::BucketId(proto_entry.raw_bucket_id());
            dest_entries[i]._info     = get_bucket_info(proto_entry.bucket_info());
        }
        return reply;
    });
}

// -----------------------------------------------------------------
// NotifyBucketChange
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::NotifyBucketChangeCommand& msg) const {
    encode_bucket_request<protobuf::NotifyBucketChangeRequest>(buf, msg, [&](auto& req) {
        set_bucket_info(*req.mutable_bucket_info(), msg.getBucketInfo());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::NotifyBucketChangeReply& msg) const {
    encode_response<protobuf::NotifyBucketChangeResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeNotifyBucketChangeCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::NotifyBucketChangeRequest>(buf, [&](auto& req, auto& bucket) {
        auto bucket_info = get_bucket_info(req.bucket_info());
        return std::make_unique<api::NotifyBucketChangeCommand>(bucket, bucket_info);
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeNotifyBucketChangeReply(const SCmd& cmd, BBuf& buf) const {
    return decode_response<protobuf::NotifyBucketChangeResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::NotifyBucketChangeReply>(static_cast<const api::NotifyBucketChangeCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// SplitBucket
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::SplitBucketCommand& msg) const {
    encode_bucket_request<protobuf::SplitBucketRequest>(buf, msg, [&](auto& req) {
        req.set_min_split_bits(msg.getMinSplitBits());
        req.set_max_split_bits(msg.getMaxSplitBits());
        req.set_min_byte_size(msg.getMinByteSize());
        req.set_min_doc_count(msg.getMinDocCount());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::SplitBucketReply& msg) const {
    encode_bucket_response<protobuf::SplitBucketResponse>(buf, msg, [&](auto& res) {
        for (const auto& split_info : msg.getSplitInfo()) {
            auto* proto_info = res.add_split_info();
            proto_info->set_raw_bucket_id(split_info.first.getRawId());
            set_bucket_info(*proto_info->mutable_bucket_info(), split_info.second);
        }
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeSplitBucketCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::SplitBucketRequest>(buf, [&](auto& req, auto& bucket) {
        auto cmd = std::make_unique<api::SplitBucketCommand>(bucket);
        cmd->setMinSplitBits(static_cast<uint8_t>(req.min_split_bits()));
        cmd->setMaxSplitBits(static_cast<uint8_t>(req.max_split_bits()));
        cmd->setMinByteSize(req.min_byte_size());
        cmd->setMinDocCount(req.min_doc_count());
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeSplitBucketReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::SplitBucketResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::SplitBucketReply>(static_cast<const api::SplitBucketCommand&>(cmd));
        auto& dest_info = reply->getSplitInfo();
        dest_info.reserve(res.split_info_size());
        for (const auto& proto_info : res.split_info()) {
            dest_info.emplace_back(document::BucketId(proto_info.raw_bucket_id()),
                                   get_bucket_info(proto_info.bucket_info()));
        }
        return reply;
    });
}

// -----------------------------------------------------------------
// JoinBuckets
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::JoinBucketsCommand& msg) const {
    encode_bucket_request<protobuf::JoinBucketsRequest>(buf, msg, [&](auto& req) {
        for (const auto& source : msg.getSourceBuckets()) {
            set_bucket_id(*req.add_source_buckets(), source);
        }
        req.set_min_join_bits(msg.getMinJoinBits());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::JoinBucketsReply& msg) const {
    encode_bucket_info_response<protobuf::JoinBucketsResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeJoinBucketsCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::JoinBucketsRequest>(buf, [&](auto& req, auto& bucket) {
        auto cmd = std::make_unique<api::JoinBucketsCommand>(bucket);
        auto& entries = cmd->getSourceBuckets();
        for (const auto& proto_bucket : req.source_buckets()) {
            entries.emplace_back(get_bucket_id(proto_bucket));
        }
        cmd->setMinJoinBits(static_cast<uint8_t>(req.min_join_bits()));
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeJoinBucketsReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::JoinBucketsResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::JoinBucketsReply>(static_cast<const api::JoinBucketsCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// SetBucketState
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::SetBucketStateCommand& msg) const {
    encode_bucket_request<protobuf::SetBucketStateRequest>(buf, msg, [&](auto& req) {
        auto state = (msg.getState() == api::SetBucketStateCommand::BUCKET_STATE::ACTIVE
                      ? protobuf::SetBucketStateRequest_BucketState_Active
                      : protobuf::SetBucketStateRequest_BucketState_Inactive);
        req.set_state(state);
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::SetBucketStateReply& msg) const {
    // SetBucketStateReply is _technically_ a BucketInfoReply, but the legacy protocol impls
    // do _not_ encode bucket info as part of the wire format (and it's not used on the distributor),
    // so we follow that here and only encode remapping information.
    encode_bucket_response<protobuf::SetBucketStateResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeSetBucketStateCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::SetBucketStateRequest>(buf, [&](auto& req, auto& bucket) {
        auto state = (req.state() == protobuf::SetBucketStateRequest_BucketState_Active
                      ? api::SetBucketStateCommand::BUCKET_STATE::ACTIVE
                      : api::SetBucketStateCommand::BUCKET_STATE::INACTIVE);
        return std::make_unique<api::SetBucketStateCommand>(bucket, state);
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeSetBucketStateReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::SetBucketStateResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::SetBucketStateReply>(static_cast<const api::SetBucketStateCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// CreateVisitor
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::CreateVisitorCommand& msg) const {
    encode_request<protobuf::CreateVisitorRequest>(buf, msg, [&](auto& req) {
        set_bucket_space(*req.mutable_bucket_space(), msg.getBucketSpace());
        for (const auto& bucket : msg.getBuckets()) {
            set_bucket_id(*req.add_buckets(), bucket);
        }

        auto* ctrl_meta = req.mutable_control_meta();
        ctrl_meta->set_library_name(msg.getLibraryName().data(), msg.getLibraryName().size());
        ctrl_meta->set_instance_id(msg.getInstanceId().data(), msg.getInstanceId().size());
        ctrl_meta->set_visitor_command_id(msg.getVisitorCmdId());
        ctrl_meta->set_control_destination(msg.getControlDestination().data(), msg.getControlDestination().size());
        ctrl_meta->set_data_destination(msg.getDataDestination().data(), msg.getDataDestination().size());
        ctrl_meta->set_queue_timeout(vespalib::count_ms(msg.getQueueTimeout()));
        ctrl_meta->set_max_pending_reply_count(msg.getMaximumPendingReplyCount());
        ctrl_meta->set_max_buckets_per_visitor(msg.getMaxBucketsPerVisitor());

        auto* constraints = req.mutable_constraints();
        constraints->set_document_selection(msg.getDocumentSelection().data(), msg.getDocumentSelection().size());
        constraints->set_from_time_usec(msg.getFromTime());
        constraints->set_to_time_usec(msg.getToTime());
        constraints->set_visit_inconsistent_buckets(msg.visitInconsistentBuckets());
        constraints->set_visit_removes(msg.visitRemoves());
        constraints->set_field_set(msg.getFieldSet().data(), msg.getFieldSet().size());

        for (const auto& param : msg.getParameters()) {
            auto* proto_param = req.add_client_parameters();
            proto_param->set_key(param.first.data(), param.first.size());
            proto_param->set_value(param.second.data(), param.second.size());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::CreateVisitorReply& msg) const {
    encode_response<protobuf::CreateVisitorResponse>(buf, msg, [&](auto& res) {
        auto& stats = msg.getVisitorStatistics();
        auto* proto_stats = res.mutable_visitor_statistics();
        proto_stats->set_buckets_visited(stats.getBucketsVisited());
        proto_stats->set_documents_visited(stats.getDocumentsVisited());
        proto_stats->set_bytes_visited(stats.getBytesVisited());
        proto_stats->set_documents_returned(stats.getDocumentsReturned());
        proto_stats->set_bytes_returned(stats.getBytesReturned());
        proto_stats->set_second_pass_documents_returned(stats.getSecondPassDocumentsReturned());
        proto_stats->set_second_pass_bytes_returned(stats.getSecondPassBytesReturned());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeCreateVisitorCommand(BBuf& buf) const {
    return decode_request<protobuf::CreateVisitorRequest>(buf, [&](auto& req) {
        auto bucket_space = get_bucket_space(req.bucket_space());
        auto& ctrl_meta   = req.control_meta();
        auto& constraints = req.constraints();
        auto cmd = std::make_unique<api::CreateVisitorCommand>(bucket_space, ctrl_meta.library_name(),
                                                               ctrl_meta.instance_id(), constraints.document_selection());
        for (const auto& proto_bucket : req.buckets()) {
            cmd->getBuckets().emplace_back(get_bucket_id(proto_bucket));
        }

        cmd->setVisitorCmdId(ctrl_meta.visitor_command_id());
        cmd->setControlDestination(ctrl_meta.control_destination());
        cmd->setDataDestination(ctrl_meta.data_destination());
        cmd->setMaximumPendingReplyCount(ctrl_meta.max_pending_reply_count());
        cmd->setQueueTimeout(std::chrono::milliseconds(ctrl_meta.queue_timeout()));
        cmd->setMaxBucketsPerVisitor(ctrl_meta.max_buckets_per_visitor());
        cmd->setVisitorDispatcherVersion(50); // FIXME this magic number is lifted verbatim from the 5.1 protocol impl

        for (const auto& proto_param : req.client_parameters()) {
            cmd->getParameters().set(proto_param.key(), proto_param.value());
        }

        cmd->setFromTime(constraints.from_time_usec());
        cmd->setToTime(constraints.to_time_usec());
        cmd->setVisitRemoves(constraints.visit_removes());
        cmd->setFieldSet(constraints.field_set());
        cmd->setVisitInconsistentBuckets(constraints.visit_inconsistent_buckets());
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeCreateVisitorReply(const SCmd& cmd, BBuf& buf) const {
    return decode_response<protobuf::CreateVisitorResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::CreateVisitorReply>(static_cast<const api::CreateVisitorCommand&>(cmd));
        vdslib::VisitorStatistics vs;
        const auto& proto_stats = res.visitor_statistics();
        vs.setBucketsVisited(proto_stats.buckets_visited());
        vs.setDocumentsVisited(proto_stats.documents_visited());
        vs.setBytesVisited(proto_stats.bytes_visited());
        vs.setDocumentsReturned(proto_stats.documents_returned());
        vs.setBytesReturned(proto_stats.bytes_returned());
        vs.setSecondPassDocumentsReturned(proto_stats.second_pass_documents_returned());
        vs.setSecondPassBytesReturned(proto_stats.second_pass_bytes_returned());
        reply->setVisitorStatistics(vs);
        return reply;
    });
}

// -----------------------------------------------------------------
// DestroyVisitor
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::DestroyVisitorCommand& msg) const {
    encode_request<protobuf::DestroyVisitorRequest>(buf, msg, [&](auto& req) {
        req.set_instance_id(msg.getInstanceId().data(), msg.getInstanceId().size());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::DestroyVisitorReply& msg) const {
    encode_response<protobuf::DestroyVisitorResponse>(buf, msg, no_op_encode);
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeDestroyVisitorCommand(BBuf& buf) const {
    return decode_request<protobuf::DestroyVisitorRequest>(buf, [&](auto& req) {
        return std::make_unique<api::DestroyVisitorCommand>(req.instance_id());
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeDestroyVisitorReply(const SCmd& cmd, BBuf& buf) const {
    return decode_response<protobuf::DestroyVisitorResponse>(buf, [&]([[maybe_unused]] auto& res) {
        return std::make_unique<api::DestroyVisitorReply>(static_cast<const api::DestroyVisitorCommand&>(cmd));
    });
}

// -----------------------------------------------------------------
// StatBucket
// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::StatBucketCommand& msg) const {
    encode_bucket_request<protobuf::StatBucketRequest>(buf, msg, [&](auto& req) {
        req.set_document_selection(msg.getDocumentSelection().data(), msg.getDocumentSelection().size());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::StatBucketReply& msg) const {
    encode_bucket_response<protobuf::StatBucketResponse>(buf, msg, [&](auto& res) {
        res.set_results(msg.getResults().data(), msg.getResults().size());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeStatBucketCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::StatBucketRequest>(buf, [&](auto& req, auto& bucket) {
        return std::make_unique<api::StatBucketCommand>(bucket, req.document_selection());
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeStatBucketReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::StatBucketResponse>(buf, [&](auto& res) {
        return std::make_unique<api::StatBucketReply>(static_cast<const api::StatBucketCommand&>(cmd), res.results());
    });
}

} // storage::mbusprot
