// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Disable warnings emitted by protoc generated
// TODO move into own forwarding header file
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wsuggest-override"

#include "protocolserialization7.h"
#include "serializationhelper.h"
#include "storageapi.pb.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/util/bufferexceptions.h>

#pragma GCC diagnostic pop

namespace storage::mbusprot {

ProtocolSerialization7::ProtocolSerialization7(const std::shared_ptr<const document::DocumentTypeRepo>& repo,
                                                   const documentapi::LoadTypeSet& loadTypes)
    : ProtocolSerialization6_0(repo, loadTypes)
{
}

namespace {

void set_bucket(protobuf::Bucket& dest, const document::Bucket& src) {
    dest.set_raw_bucket_id(src.getBucketId().getRawId());
    dest.set_space_id(src.getBucketSpace().getId());
}

void set_bucket_info(protobuf::BucketInfo& dest, const api::BucketInfo& src) {
    auto* info = dest.mutable_info_v1();
    info->set_last_modified_timestamp(src.getLastModified());
    info->set_checksum(src.getChecksum());
    info->set_doc_count(src.getDocumentCount());
    info->set_total_doc_size(src.getTotalDocumentSize());
    info->set_meta_count(src.getMetaCount());
    info->set_used_file_size(src.getUsedFileSize());
    info->set_active(src.isActive());
    info->set_ready(src.isReady());
}

document::Bucket get_bucket(const protobuf::Bucket& src) {
    return document::Bucket(document::BucketSpace(src.space_id()),
                            document::BucketId(src.raw_bucket_id()));
}

api::BucketInfo get_bucket_info(const protobuf::BucketInfo& src) {
    if (!src.has_info_v1()) {
        return {};
    }
    api::BucketInfo info;
    const auto& s = src.info_v1();
    info.setLastModified(s.last_modified_timestamp());
    info.setChecksum(s.checksum());
    info.setDocumentCount(s.doc_count());
    info.setTotalDocumentSize(s.total_doc_size());
    info.setMetaCount(s.meta_count());
    info.setUsedFileSize(s.used_file_size());
    info.setActive(s.active());
    info.setReady(s.ready());
    return info;
}

documentapi::TestAndSetCondition get_tas_condition(const protobuf::TestAndSetCondition& src) {
    return documentapi::TestAndSetCondition(src.selection());
}

void set_tas_condition(protobuf::TestAndSetCondition& dest, const documentapi::TestAndSetCondition& src) {
    dest.set_selection(src.getSelection().data(), src.getSelection().size());
}

// TODO add test with unset doc field in root proto
std::shared_ptr<document::Document> get_document(const protobuf::Document& src_doc,
                                                 const document::DocumentTypeRepo& type_repo)
{
    if (!src_doc.payload().empty()) {
        document::ByteBuffer doc_buf(src_doc.payload().data(), src_doc.payload().size());
        return std::make_shared<document::Document>(type_repo, doc_buf);
    }
    return std::shared_ptr<document::Document>();
}

void write_request_header(vespalib::GrowableByteBuffer& buf, const api::StorageCommand& cmd) {
    protobuf::RequestHeader hdr;
    hdr.set_message_id(cmd.getMsgId());
    hdr.set_priority(cmd.getPriority());
    hdr.set_source_index(cmd.getSourceIndex());
    hdr.set_loadtype_id(cmd.getLoadType().getId());

    char dest[128]; // Only primitive fields, should be plenty large enough.
    auto encoded_size = static_cast<uint32_t>(hdr.ByteSizeLong());
    bool ok = hdr.SerializeToArray(dest, sizeof(dest));
    assert(ok); // TODO
    buf.putInt(encoded_size);
    buf.putBytes(dest, encoded_size);
}

void write_response_header(vespalib::GrowableByteBuffer& buf, const api::StorageReply& reply) {
    protobuf::ResponseHeader hdr;
    const auto& result = reply.getResult();
    hdr.set_return_code_id(static_cast<uint32_t>(result.getResult()));
    if (!result.getMessage().empty()) {
        hdr.set_return_code_message(result.getMessage().data(), result.getMessage().size());
    }
    hdr.set_message_id(reply.getMsgId());
    hdr.set_priority(reply.getPriority());

    std::string encoded; // TODO wrap in zero copy buffers!
    bool ok = hdr.SerializeToString(&encoded);
    assert(ok); // TODO
    buf.putInt(static_cast<uint32_t>(encoded.size()));
    buf.putBytes(encoded.data(), static_cast<uint32_t>(encoded.size()));
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
        std::string encoded; // TODO wrap in zero copy buffers!
        bool ok = _proto_obj->SerializeToString(&encoded);
        assert(ok); // TODO
        _out_buf.putBytes(encoded.data(), encoded.size());
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
    const documentapi::LoadTypeSet& _load_types;
public:
    RequestDecoder(document::ByteBuffer& in_buf, const documentapi::LoadTypeSet& load_types)
        : _arena(),
          _proto_obj(::google::protobuf::Arena::Create<ProtobufType>(&_arena)),
          _load_types(load_types)
    {
        decode_request_header(in_buf, _hdr);
        bool ok = _proto_obj->ParseFromArray(in_buf.getBufferAtPos(), in_buf.getRemaining()); // FIXME size handling
        if (!ok) {
            throw vespalib::IllegalArgumentException("Malformed protobuf request payload");
        }
    }

    void transfer_meta_information_to(api::StorageCommand& dest) {
        dest.forceMsgId(_hdr.message_id());
        dest.setPriority(static_cast<uint8_t>(_hdr.priority()));
        dest.setSourceIndex(static_cast<uint16_t>(_hdr.source_index()));
        dest.setLoadType(_load_types[_hdr.loadtype_id()]);
    }

    ProtobufType& request() noexcept { return *_proto_obj; }
    const ProtobufType& request() const noexcept { return *_proto_obj; }
};

template <typename ProtobufType>
void transfer_bucket_info_response_fields_from_proto_to_msg(api::BucketInfoReply& dest, const ProtobufType& src) {
    if (src.has_bucket_info()) {
        dest.setBucketInfo(get_bucket_info(src.bucket_info()));
    }
    if (src.has_remapped_bucket_id()) {
        dest.remapBucketId(document::BucketId(src.remapped_bucket_id().raw_id()));
    }
}

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
        bool ok = _proto_obj->ParseFromArray(in_buf.getBufferAtPos(), in_buf.getRemaining()); // FIXME size handling
        if (!ok) {
            throw vespalib::IllegalArgumentException("Malformed protobuf response payload");
        }
    }

    ProtobufType& response() noexcept { return *_proto_obj; }
    const ProtobufType& response() const noexcept { return *_proto_obj; }
};

template <typename ProtobufType, typename Func>
void encode_bucket_request(vespalib::GrowableByteBuffer& out_buf, const api::BucketCommand& msg, Func&& f) {
    RequestEncoder<ProtobufType> enc(out_buf, msg);
    set_bucket(*enc.request().mutable_bucket(), msg.getBucket());
    f(enc.request());
    enc.encode();
}

template <typename ProtobufType, typename Func>
void encode_bucket_response(vespalib::GrowableByteBuffer& out_buf, const api::BucketReply& reply, Func&& f) {
    ResponseEncoder<ProtobufType> enc(out_buf, reply);
    auto& res = enc.response();
    if (reply.hasBeenRemapped()) {
        res.mutable_remapped_bucket_id()->set_raw_id(reply.getBucketId().getRawId());
    }
    f(res);
    enc.encode();
}

// TODO implement in terms of encode_bucket_response
template <typename ProtobufType, typename Func>
void encode_bucket_info_response(vespalib::GrowableByteBuffer& out_buf, const api::BucketInfoReply& reply, Func&& f) {
    ResponseEncoder<ProtobufType> enc(out_buf, reply);
    auto& res = enc.response();
    if (reply.hasBeenRemapped()) {
        res.mutable_remapped_bucket_id()->set_raw_id(reply.getBucketId().getRawId());
    }
    set_bucket_info(*res.mutable_bucket_info(), reply.getBucketInfo());
    f(res);
    enc.encode();
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageCommand>
ProtocolSerialization7::decode_bucket_request(document::ByteBuffer& in_buf, Func&& f) const {
    RequestDecoder<ProtobufType> dec(in_buf, loadTypes());
    const auto& req = dec.request();
    if (!req.has_bucket()) {
        throw vespalib::IllegalArgumentException("Malformed protocol buffer request; no bucket"); // TODO proto type name?
    }
    const auto bucket = get_bucket(req.bucket());
    auto cmd = f(req, bucket);
    dec.transfer_meta_information_to(*cmd);
    return cmd;
}

template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageReply>
ProtocolSerialization7::decode_bucket_response(document::ByteBuffer& in_buf, Func&& f) const {
    ResponseDecoder<ProtobufType> dec(in_buf);
    const auto& res = dec.response();
    auto reply = f(res);
    if (res.has_remapped_bucket_id()) {
        reply->remapBucketId(document::BucketId(res.remapped_bucket_id().raw_id()));
    }
    return reply;
}

// TODO implement this in terms of decode_bucket_response
template <typename ProtobufType, typename Func>
std::unique_ptr<api::StorageReply>
ProtocolSerialization7::decode_bucket_info_response(document::ByteBuffer& in_buf, Func&& f) const {
    ResponseDecoder<ProtobufType> dec(in_buf);
    const auto& res = dec.response();
    auto reply = f(res);
    transfer_bucket_info_response_fields_from_proto_to_msg(*reply, res);
    return reply;
}

// TODO document protobuf ducktyping assumptions

namespace {
// Inherit from known base class just to avoid having to template this. We don't care about its subtype anyway.
void no_op_encode([[maybe_unused]] ::google::protobuf::Message&) {
    // nothing to do here.
}

void set_document_if_present(protobuf::Document& target_doc, const document::Document* src_doc) {
    if (src_doc) {
        vespalib::nbostream stream;
        src_doc->serialize(stream);
        target_doc.set_payload(stream.peek(), stream.size());
    }
}

}

// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::PutCommand& msg) const {
    encode_bucket_request<protobuf::PutRequest>(buf, msg, [&](auto& req) {
        req.set_new_timestamp(msg.getTimestamp());
        req.set_expected_old_timestamp(msg.getUpdateTimestamp());
        if (msg.getCondition().isPresent()) {
            set_tas_condition(*req.mutable_condition(), msg.getCondition());
        }
        set_document_if_present(*req.mutable_document(), msg.getDocument().get());
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::PutReply& msg) const {
    encode_bucket_info_response<protobuf::PutResponse>(buf, msg, [&](auto& res) {
        res.set_was_found(msg.wasFound());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodePutCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::PutRequest>(buf, [&](auto& req, auto& bucket) {
        auto document = get_document(req.document(), getTypeRepo());
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

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::UpdateCommand& msg) const {
    encode_bucket_request<protobuf::UpdateRequest>(buf, msg, [&](auto& req) {
        auto* update = msg.getUpdate().get();
        if (update) {
            // TODO move out
            vespalib::nbostream stream;
            update->serializeHEAD(stream);
            req.mutable_update()->set_payload(stream.peek(), stream.size());
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
        // TODO move out
        std::shared_ptr<document::DocumentUpdate> update;
        if (req.has_update() && !req.update().payload().empty()) {
            update = document::DocumentUpdate::createHEAD(getTypeRepo(), vespalib::nbostream(
                    req.update().payload().data(), req.update().payload().size()));
        }
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

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetCommand& msg) const {
    encode_bucket_request<protobuf::GetRequest>(buf, msg, [&](auto& req) {
        auto doc_id = msg.getDocumentId().toString();
        req.set_document_id(doc_id.data(), doc_id.size());
        req.set_before_timestamp(msg.getBeforeTimestamp());
        if (!msg.getFieldSet().empty()) {
            req.set_field_set(msg.getFieldSet().data(), msg.getFieldSet().size());
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetReply& msg) const {
    encode_bucket_info_response<protobuf::GetResponse>(buf, msg, [&](auto& res) {
        // FIXME this will always create an empty document field!
        set_document_if_present(*res.mutable_document(), msg.getDocument().get());
        res.set_last_modified_timestamp(msg.getLastModifiedTimestamp());
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeGetCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::GetRequest>(buf, [&](auto& req, auto& bucket) {
        document::DocumentId doc_id(vespalib::stringref(req.document_id().data(), req.document_id().size()));
        return std::make_unique<api::GetCommand>(bucket, std::move(doc_id),
                                                 req.field_set(), req.before_timestamp());
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeGetReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_info_response<protobuf::GetResponse>(buf, [&](auto& res) {
        try {
            auto document = get_document(res.document(), getTypeRepo());
            return std::make_unique<api::GetReply>(static_cast<const api::GetCommand&>(cmd),
                                                   std::move(document), res.last_modified_timestamp());
        } catch (std::exception& e) {
            auto reply = std::make_unique<api::GetReply>(static_cast<const api::GetCommand&>(cmd),
                                                         std::shared_ptr<document::Document>(), 0u);
            reply->setResult(api::ReturnCode(api::ReturnCode::UNPARSEABLE, e.what()));
            return reply;
        }
    });
}

// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::RevertCommand& msg) const {
    encode_bucket_request<protobuf::RevertRequest>(buf, msg, [&](auto& req) {
        auto* tokens = req.mutable_revert_tokens();
        assert(msg.getRevertTokens().size() < INT_MAX);
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

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::MergeBucketCommand& msg) const {
    encode_bucket_request<protobuf::MergeBucketRequest>(buf, msg, [&](auto& req) {
        // TODO dedupe
        for (const auto& src_node : msg.getNodes()) {
            auto* dest_node = req.add_nodes();
            dest_node->set_index(src_node.index);
            dest_node->set_source_only(src_node.sourceOnly);
        }
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
        using Node = api::MergeBucketCommand::Node;
        std::vector<Node> nodes;
        nodes.reserve(req.nodes_size());
        for (const auto& node : req.nodes()) {
            nodes.emplace_back(node.index(), node.source_only());
        }
        std::vector<uint16_t> chain;
        chain.reserve(req.node_chain_size());
        for (uint16_t node : req.node_chain()) {
            chain.emplace_back(node);
        }

        auto cmd = std::make_unique<api::MergeBucketCommand>(bucket, std::move(nodes), req.max_timestamp());
        cmd->setClusterStateVersion(req.cluster_state_version());
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

namespace {

void set_global_id(protobuf::GlobalId& dest, const document::GlobalId& src) {
    static_assert(document::GlobalId::LENGTH == 12);
    uint64_t lo64;
    uint32_t hi32;
    memcpy(&lo64, src.get() + sizeof(uint32_t), sizeof(uint64_t));
    memcpy(&hi32, src.get(), sizeof(uint32_t));
    dest.set_hi_32(hi32);
    dest.set_lo_64(lo64);
}

document::GlobalId get_global_id(const protobuf::GlobalId& src) {
    static_assert(document::GlobalId::LENGTH == 12);
    const uint64_t lo64 = src.lo_64();
    const uint32_t hi32 = src.hi_32();

    char buf[document::GlobalId::LENGTH];
    memcpy(buf, &hi32, sizeof(uint32_t));
    memcpy(buf + sizeof(uint32_t), &lo64, sizeof(uint64_t));
    return document::GlobalId(buf);
}

void set_diff_entry(protobuf::MetaDiffEntry& dest, const api::GetBucketDiffCommand::Entry& src) {
    dest.set_timestamp(src._timestamp);
    set_global_id(*dest.mutable_gid(), src._gid);
    dest.set_header_size(src._headerSize);
    dest.set_body_size(src._bodySize);
    dest.set_flags(src._flags);
    dest.set_has_mask(src._flags);
}

api::GetBucketDiffCommand::Entry get_diff_entry(const protobuf::MetaDiffEntry& src) {
    api::GetBucketDiffCommand::Entry e;
    e._timestamp = src.timestamp();
    e._gid = get_global_id(src.gid()); // TODO need presence check?
    e._headerSize = src.header_size();
    e._bodySize = src.body_size();
    e._flags = src.flags();
    e._hasMask = src.has_mask(); // TODO rename, ambiguous :I
    return e;
}

} // anynomous namespace

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetBucketDiffCommand& msg) const {
    encode_bucket_request<protobuf::GetBucketDiffRequest>(buf, msg, [&](auto& req) {
        // TODO dedupe
        for (const auto& src_node : msg.getNodes()) {
            auto* dest_node = req.add_nodes();
            dest_node->set_index(src_node.index);
            dest_node->set_source_only(src_node.sourceOnly);
        }
        req.set_max_timestamp(msg.getMaxTimestamp());
        for (const auto& diff_entry : msg.getDiff()) {
            set_diff_entry(*req.add_diff(), diff_entry);
        }
    });
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::GetBucketDiffReply& msg) const {
    encode_bucket_response<protobuf::GetBucketDiffResponse>(buf, msg, [&](auto& res) {
        for (const auto& diff_entry : msg.getDiff()) {
            set_diff_entry(*res.add_diff(), diff_entry);
        }
    });
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeGetBucketDiffCommand(BBuf& buf) const {
    return decode_bucket_request<protobuf::GetBucketDiffRequest>(buf, [&](auto& req, auto& bucket) {
        // TODO dedupe
        using Node = api::MergeBucketCommand::Node;
        std::vector<Node> nodes;
        nodes.reserve(req.nodes_size());
        for (const auto& node : req.nodes()) {
            nodes.emplace_back(node.index(), node.source_only());
        }
        auto cmd = std::make_unique<api::GetBucketDiffCommand>(bucket, std::move(nodes), req.max_timestamp());
        auto& diff = cmd->getDiff(); // TODO refactor
        diff.reserve(req.diff_size());
        for (const auto& diff_entry : req.diff()) {
            diff.emplace_back(get_diff_entry(diff_entry));
        }
        return cmd;
    });
}

api::StorageReply::UP ProtocolSerialization7::onDecodeGetBucketDiffReply(const SCmd& cmd, BBuf& buf) const {
    return decode_bucket_response<protobuf::GetBucketDiffResponse>(buf, [&](auto& res) {
        auto reply = std::make_unique<api::GetBucketDiffReply>(static_cast<const api::GetBucketDiffCommand&>(cmd));
        // TODO dedupe
        auto& diff = reply->getDiff(); // TODO refactor
        // FIXME why does the ctor copy the diff from the command? remove entirely?
        diff.clear();
        diff.reserve(res.diff_size());
        for (const auto& diff_entry : res.diff()) {
            diff.emplace_back(get_diff_entry(diff_entry));
        }
        return reply;
    });
}

/*

// -----------------------------------------------------------------

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::Command& msg) const {
    (void)buf;
    (void)msg;
}

void ProtocolSerialization7::onEncode(GBBuf& buf, const api::Reply& msg) const {
    (void)buf;
    (void)msg;
}

api::StorageCommand::UP ProtocolSerialization7::onDecodeCommand(BBuf& buf) const {
    (void)buf;
    return api::StorageCommand::UP();
}

api::StorageReply::UP ProtocolSerialization7::onDecodeReply(const SCmd& cmd, BBuf& buf) const {
    (void)cmd;
    (void)buf;
    return api::StorageReply::UP();
}
 */

/*
 * TODO extend testing of:
 *   - bucket info in responses
 *   - bucket remapping in responses
 */

}
