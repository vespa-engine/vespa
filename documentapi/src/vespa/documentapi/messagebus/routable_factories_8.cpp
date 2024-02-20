// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routable_factories_8.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/documentapi/messagebus/docapi_common.pb.h>
#include <vespa/documentapi/messagebus/docapi_feed.pb.h>
#include <vespa/documentapi/messagebus/docapi_visiting.pb.h>
#include <vespa/documentapi/messagebus/docapi_inspect.pb.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".documentapi.messagebus.routable_factories_8");

namespace documentapi::messagebus {

namespace {

// Protobuf codec helpers for common types

void set_bucket_id(protobuf::BucketId& dest, const document::BucketId& src) {
    dest.set_raw_id(src.getRawId());
}

document::BucketId get_bucket_id(const protobuf::BucketId& src) {
    return document::BucketId(src.raw_id());
}

void set_document_id(protobuf::DocumentId& dest, const document::DocumentId& src) {
    auto doc_id = src.toString();
    dest.set_id(doc_id.data(), doc_id.size());
}

document::DocumentId get_document_id(const protobuf::DocumentId& src) {
    return document::DocumentId(src.id());
}

// TODO DocumentAPI should be extended to use actual document::FieldSet enums instead of always passing strings.
void set_raw_field_set(protobuf::FieldSet& dest, vespalib::stringref src) {
    dest.set_spec(src.data(), src.size());
}

// Note: returns by ref
vespalib::stringref get_raw_field_set(const protobuf::FieldSet& src) noexcept {
    return src.spec();
}

void set_raw_selection(protobuf::DocumentSelection& dest, vespalib::stringref src) {
    dest.set_selection(src.data(), src.size());
}

// Note: returns by ref
vespalib::stringref get_raw_selection(const protobuf::DocumentSelection& src) noexcept {
    return src.selection();
}

void set_bucket_space(protobuf::BucketSpace& dest, vespalib::stringref space_name) {
    dest.set_name(space_name.data(), space_name.size());
}

// Note: returns by ref
vespalib::stringref get_bucket_space(const protobuf::BucketSpace& src) noexcept {
    return src.name();
}

void set_global_id(protobuf::GlobalId& dest, const document::GlobalId& src) {
    char tmp[document::GlobalId::LENGTH];
    memcpy(tmp, src.get(), document::GlobalId::LENGTH);
    dest.set_raw_gid(tmp, document::GlobalId::LENGTH);
}

document::GlobalId get_global_id(const protobuf::GlobalId& src) {
    if (src.raw_gid().size() != document::GlobalId::LENGTH) [[unlikely]] {
        throw document::DeserializeException(
                vespalib::make_string("Unexpected serialized protobuf GlobalId size (expected %u, was %zu)",
                                      document::GlobalId::LENGTH, src.raw_gid().size()));
    }
    return document::GlobalId(src.raw_gid().data()); // By copy
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
    return {};
}

std::shared_ptr<document::Document> get_document_or_throw(const protobuf::Document& src_doc,
                                                          const document::DocumentTypeRepo& type_repo)
{
    auto doc = get_document(src_doc, type_repo);
    if (!doc) [[unlikely]] {
        throw document::DeserializeException("Message does not contain a required document object", VESPA_STRLOC);
    }
    return doc;
}

void set_document(protobuf::Document& target_doc, const document::Document& src_doc) {
    vespalib::nbostream stream;
    src_doc.serialize(stream);
    target_doc.set_payload(stream.peek(), stream.size());
}

void set_update(protobuf::DocumentUpdate& dest, const document::DocumentUpdate& src) {
    vespalib::nbostream stream;
    src.serializeHEAD(stream);
    dest.set_payload(stream.peek(), stream.size());
}

std::shared_ptr<document::DocumentUpdate> get_update(const protobuf::DocumentUpdate& src,
                                                     const document::DocumentTypeRepo& type_repo)
{
    if (!src.payload().empty()) {
        return document::DocumentUpdate::createHEAD(
                type_repo, vespalib::nbostream(src.payload().data(), src.payload().size()));
    }
    return {};
}

std::shared_ptr<document::DocumentUpdate> get_update_or_throw(const protobuf::DocumentUpdate& src,
                                                              const document::DocumentTypeRepo& type_repo)
{
    auto upd = get_update(src, type_repo);
    if (!upd) [[unlikely]] {
        throw document::DeserializeException("Message does not contain a required document update object", VESPA_STRLOC);
    }
    return upd;
}

void log_codec_error(const char* op, const char* type, const char* msg) noexcept __attribute__((noinline));
void log_codec_error(const char* op, const char* type, const char* msg) noexcept {
    LOGBM(error, "Error during Protobuf %s for message type %s: %s", op, type, msg);
}

template <typename DocApiType, typename ProtobufType, typename EncodeFn, typename DecodeFn>
requires std::is_invocable_r_v<void, EncodeFn, const DocApiType&, ProtobufType&> &&
         std::is_invocable_r_v<std::unique_ptr<DocApiType>, DecodeFn, const ProtobufType&>
class ProtobufRoutableFactory final : public IRoutableFactory {
    EncodeFn _encode_fn;
    DecodeFn _decode_fn;
public:
    template <typename EncFn, typename DecFn>
    ProtobufRoutableFactory(EncFn&& enc_fn, DecFn&& dec_fn) noexcept
        : _encode_fn(std::forward<EncFn>(enc_fn)),
          _decode_fn(std::forward<DecFn>(dec_fn))
    {}
    ~ProtobufRoutableFactory() override = default;

    bool encode(const mbus::Routable& obj, vespalib::GrowableByteBuffer& out) const override {
        ::google::protobuf::Arena arena;
        auto* proto_obj = ::google::protobuf::Arena::Create<ProtobufType>(&arena);

        try {
            _encode_fn(dynamic_cast<const DocApiType&>(obj), *proto_obj);
        } catch (std::exception& e) {
            log_codec_error("encode", ProtobufType::descriptor()->name().c_str(), e.what());
            return false;
        }

        const auto sz = proto_obj->ByteSizeLong();
        assert(sz <= INT32_MAX);
        auto* buf = reinterpret_cast<uint8_t*>(out.allocate(sz));
        return proto_obj->SerializeWithCachedSizesToArray(buf);
    }

    mbus::Routable::UP decode(document::ByteBuffer& in) const override {
        ::google::protobuf::Arena arena;
        auto* proto_obj = ::google::protobuf::Arena::Create<ProtobufType>(&arena);
        const auto buf_size = in.getRemaining();
        assert(buf_size <= INT_MAX);
        bool ok = proto_obj->ParseFromArray(in.getBufferAtPos(), buf_size);
        if (!ok) [[unlikely]] {
            return {}; // Malformed protobuf payload. Caller is expected to log an error.
        }
        try {
            auto msg = _decode_fn(*proto_obj);
            if constexpr (std::is_base_of_v<DocumentMessage, DocApiType>) {
                msg->setApproxSize(buf_size); // Wire size is a proxy for in-memory size
            }
            return msg;
        } catch (std::exception& e) {
            log_codec_error("decode", ProtobufType::descriptor()->name().c_str(), e.what());
            return {};
        }
    }
};

template <typename DocApiType, typename ProtobufType, typename EncodeFn, typename DecodeFn>
auto make_codec(EncodeFn&& enc_fn, DecodeFn&& dec_fn) {
    return std::make_shared<ProtobufRoutableFactory<DocApiType, ProtobufType, EncodeFn, DecodeFn>>(
            std::forward<EncodeFn>(enc_fn), std::forward<DecodeFn>(dec_fn));
}

} // anon ns

// ---------------------------------------------
// Get request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_document_message_factory() {
    return make_codec<GetDocumentMessage, protobuf::GetDocumentRequest>(
        [](const GetDocumentMessage& src, protobuf::GetDocumentRequest& dest) {
            set_document_id(*dest.mutable_document_id(), src.getDocumentId());
            set_raw_field_set(*dest.mutable_field_set(), src.getFieldSet());
        },
        [](const protobuf::GetDocumentRequest& src) {
            return std::make_unique<GetDocumentMessage>(get_document_id(src.document_id()), get_raw_field_set(src.field_set()));
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_document_reply_factory(std::shared_ptr<const document::DocumentTypeRepo> repo) {
    return make_codec<GetDocumentReply, protobuf::GetDocumentResponse>(
        [](const GetDocumentReply& src, protobuf::GetDocumentResponse& dest) {
            if (src.hasDocument()) {
                set_document(*dest.mutable_document(), src.getDocument());
            }
            dest.set_last_modified(src.getLastModified());
        },
        [type_repo = std::move(repo)](const protobuf::GetDocumentResponse& src) {
            auto msg = std::make_unique<GetDocumentReply>();
            if (src.has_document()) {
                auto doc = get_document(src.document(), *type_repo);
                doc->setLastModified(static_cast<int64_t>(src.last_modified()));
                msg->setDocument(std::move(doc));
            }
            msg->setLastModified(src.last_modified());
            return msg;
        }
    );
}

// ---------------------------------------------
// Put request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::put_document_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo) {
    return make_codec<PutDocumentMessage, protobuf::PutDocumentRequest>(
        [](const PutDocumentMessage& src, protobuf::PutDocumentRequest& dest) {
            dest.set_force_assign_timestamp(src.getTimestamp());
            if (src.getCondition().isPresent()) {
                set_tas_condition(*dest.mutable_condition(), src.getCondition());
            }
            if (src.getDocumentSP()) [[likely]] { // This should always be present in practice
                set_document(*dest.mutable_document(), src.getDocument());
            }
            dest.set_create_if_missing(src.get_create_if_non_existent());
        },
        [type_repo = std::move(repo)](const protobuf::PutDocumentRequest& src) {
            auto msg = std::make_unique<PutDocumentMessage>();
            msg->setDocument(get_document_or_throw(src.document(), *type_repo));
            if (src.has_condition()) {
                msg->setCondition(get_tas_condition(src.condition()));
            }
            msg->setTimestamp(src.force_assign_timestamp());
            msg->set_create_if_non_existent(src.create_if_missing());
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::put_document_reply_factory() {
    return make_codec<WriteDocumentReply, protobuf::PutDocumentResponse>(
        [](const WriteDocumentReply& src, protobuf::PutDocumentResponse& dest) {
            dest.set_modification_timestamp(src.getHighestModificationTimestamp());
        },
        [](const protobuf::PutDocumentResponse& src) {
            auto msg = std::make_unique<WriteDocumentReply>(DocumentProtocol::REPLY_PUTDOCUMENT);
            msg->setHighestModificationTimestamp(src.modification_timestamp());
            return msg;
        }
    );
}

// ---------------------------------------------
// Update request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::update_document_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo) {
    return make_codec<UpdateDocumentMessage, protobuf::UpdateDocumentRequest>(
        [](const UpdateDocumentMessage& src, protobuf::UpdateDocumentRequest& dest) {
            set_update(*dest.mutable_update(), src.getDocumentUpdate());
            if (src.getCondition().isPresent()) {
                set_tas_condition(*dest.mutable_condition(), src.getCondition());
            }
            dest.set_expected_old_timestamp(src.getOldTimestamp());
            dest.set_force_assign_timestamp(src.getNewTimestamp());
        },
        [type_repo = std::move(repo)](const protobuf::UpdateDocumentRequest& src) {
            auto msg = std::make_unique<UpdateDocumentMessage>();
            msg->setDocumentUpdate(get_update_or_throw(src.update(), *type_repo));
            if (src.has_condition()) {
                msg->setCondition(get_tas_condition(src.condition()));
            }
            msg->setOldTimestamp(src.expected_old_timestamp());
            msg->setNewTimestamp(src.force_assign_timestamp());
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::update_document_reply_factory() {
    return make_codec<UpdateDocumentReply, protobuf::UpdateDocumentResponse>(
        [](const UpdateDocumentReply& src, protobuf::UpdateDocumentResponse& dest) {
            dest.set_was_found(src.wasFound());
            dest.set_modification_timestamp(src.getHighestModificationTimestamp());
        },
        [](const protobuf::UpdateDocumentResponse& src) {
            auto msg = std::make_unique<UpdateDocumentReply>();
            msg->setWasFound(src.was_found());
            msg->setHighestModificationTimestamp(src.modification_timestamp());
            return msg;
        }
    );
}

// ---------------------------------------------
// Remove request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::remove_document_message_factory() {
    return make_codec<RemoveDocumentMessage, protobuf::RemoveDocumentRequest>(
        [](const RemoveDocumentMessage& src, protobuf::RemoveDocumentRequest& dest) {
            set_document_id(*dest.mutable_document_id(), src.getDocumentId());
            if (src.getCondition().isPresent()) {
                set_tas_condition(*dest.mutable_condition(), src.getCondition());
            }
        },
        [](const protobuf::RemoveDocumentRequest& src) {
            auto msg = std::make_unique<RemoveDocumentMessage>();
            msg->setDocumentId(get_document_id(src.document_id()));
            if (src.has_condition()) {
                msg->setCondition(get_tas_condition(src.condition()));
            }
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::remove_document_reply_factory() {
    return make_codec<RemoveDocumentReply, protobuf::RemoveDocumentResponse>(
        [](const RemoveDocumentReply& src, protobuf::RemoveDocumentResponse& dest) {
            dest.set_was_found(src.wasFound());
            dest.set_modification_timestamp(src.getHighestModificationTimestamp());
        },
        [](const protobuf::RemoveDocumentResponse& src) {
            auto msg = std::make_unique<RemoveDocumentReply>();
            msg->setWasFound(src.was_found());
            msg->setHighestModificationTimestamp(src.modification_timestamp());
            return msg;
        }
    );
}

// ---------------------------------------------
// RemoveLocation request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory>
RoutableFactories80::remove_location_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo) {
    return make_codec<RemoveLocationMessage, protobuf::RemoveLocationRequest>(
        [](const RemoveLocationMessage& src, protobuf::RemoveLocationRequest& dest) {
            set_raw_selection(*dest.mutable_selection(), src.getDocumentSelection());
            set_bucket_space(*dest.mutable_bucket_space(), src.getBucketSpace());
        },
        [type_repo = std::move(repo)](const protobuf::RemoveLocationRequest& src) {
            document::BucketIdFactory factory;
            document::select::Parser parser(*type_repo, factory);
            auto msg = std::make_unique<RemoveLocationMessage>(factory, parser, get_raw_selection(src.selection()));
            msg->setBucketSpace(get_bucket_space(src.bucket_space()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::remove_location_reply_factory() {
    return make_codec<DocumentReply, protobuf::RemoveLocationResponse>(
        []([[maybe_unused]] const DocumentReply& src, [[maybe_unused]] protobuf::RemoveLocationResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::RemoveLocationResponse& src) {
            // The lack of 1-1 type mapping is pretty awkward :I
            return std::make_unique<DocumentReply>(DocumentProtocol::REPLY_REMOVELOCATION);
        }
    );
}

// ---------------------------------------------
// CreateVisitor request and response
// ---------------------------------------------

namespace {

void set_bucket_id_vector(::google::protobuf::RepeatedPtrField<protobuf::BucketId>& dest,
                          const std::vector<document::BucketId>& src)
{
    assert(src.size() <= INT_MAX);
    dest.Reserve(static_cast<int>(src.size()));
    for (const auto& bucket_id : src) {
        set_bucket_id(*dest.Add(), bucket_id);
    }
}

std::vector<document::BucketId> get_bucket_id_vector(const ::google::protobuf::RepeatedPtrField<protobuf::BucketId>& src) {
    std::vector<document::BucketId> ids;
    ids.reserve(src.size());
    for (const auto& proto_bucket : src) {
        ids.emplace_back(proto_bucket.raw_id());
    }
    return ids;
}

void set_visitor_params(::google::protobuf::RepeatedPtrField<protobuf::VisitorParameter>& dest,
                        const vdslib::Parameters& src)
{
    assert(src.size() <= INT_MAX);
    dest.Reserve(static_cast<int>(src.size()));
    for (const auto& kv : src) {
        auto* proto_kv = dest.Add();
        proto_kv->set_key(kv.first.data(), kv.first.size());
        proto_kv->set_value(kv.second.data(), kv.second.size());
    }
}

vdslib::Parameters get_visitor_params(const ::google::protobuf::RepeatedPtrField<protobuf::VisitorParameter>& src) {
    vdslib::Parameters params;
    for (const auto& proto_kv : src) {
        params.set(proto_kv.key(), proto_kv.value());
    }
    return params;
}

}

std::shared_ptr<IRoutableFactory> RoutableFactories80::create_visitor_message_factory() {
    return make_codec<CreateVisitorMessage, protobuf::CreateVisitorRequest>(
        [](const CreateVisitorMessage& src, protobuf::CreateVisitorRequest& dest) {
            dest.set_visitor_library_name(src.getLibraryName().data(), src.getLibraryName().size());
            dest.set_instance_id(src.getInstanceId().data(), src.getInstanceId().size());
            dest.set_control_destination(src.getControlDestination().data(), src.getControlDestination().size());
            dest.set_data_destination(src.getDataDestination().data(), src.getDataDestination().size());
            set_raw_selection(*dest.mutable_selection(), src.getDocumentSelection());
            dest.set_max_pending_reply_count(src.getMaximumPendingReplyCount());

            set_bucket_space(*dest.mutable_bucket_space(), src.getBucketSpace());
            set_bucket_id_vector(*dest.mutable_buckets(), src.getBuckets());

            dest.set_from_timestamp(src.getFromTimestamp());
            dest.set_to_timestamp(src.getToTimestamp());
            dest.set_visit_tombstones(src.visitRemoves());
            set_raw_field_set(*dest.mutable_field_set(), src.getFieldSet());
            dest.set_visit_inconsistent_buckets(src.visitInconsistentBuckets());
            dest.set_max_buckets_per_visitor(src.getMaxBucketsPerVisitor());

            set_visitor_params(*dest.mutable_parameters(), src.getParameters());
        },
        [](const protobuf::CreateVisitorRequest& src) {
            auto msg = std::make_unique<CreateVisitorMessage>();
            msg->setLibraryName(src.visitor_library_name());
            msg->setInstanceId(src.instance_id());
            msg->setControlDestination(src.control_destination());
            msg->setDataDestination(src.data_destination());
            msg->setDocumentSelection(get_raw_selection(src.selection()));
            msg->setMaximumPendingReplyCount(src.max_pending_reply_count());
            msg->setBucketSpace(get_bucket_space(src.bucket_space()));
            msg->setBuckets(get_bucket_id_vector(src.buckets()));
            msg->setFromTimestamp(src.from_timestamp());
            msg->setToTimestamp(src.to_timestamp());
            msg->setVisitRemoves(src.visit_tombstones());
            msg->setFieldSet(get_raw_field_set(src.field_set()));
            msg->setVisitInconsistentBuckets(src.visit_inconsistent_buckets());
            msg->setMaxBucketsPerVisitor(src.max_buckets_per_visitor());
            msg->setVisitorDispatcherVersion(50); // Hard-coded; same as for v6 serialization
            msg->setParameters(get_visitor_params(src.parameters()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::create_visitor_reply_factory() {
    return make_codec<CreateVisitorReply, protobuf::CreateVisitorResponse>(
        [](const CreateVisitorReply& src, protobuf::CreateVisitorResponse& dest) {
            set_bucket_id(*dest.mutable_last_bucket(), src.getLastBucket());
            const auto& vs = src.getVisitorStatistics();
            auto* stats = dest.mutable_statistics();
            stats->set_buckets_visited(vs.getBucketsVisited());
            stats->set_documents_visited(vs.getDocumentsVisited());
            stats->set_bytes_visited(vs.getBytesVisited());
            stats->set_documents_returned(vs.getDocumentsReturned());
            stats->set_bytes_returned(vs.getBytesReturned());
        },
        [](const protobuf::CreateVisitorResponse& src) {
            auto reply = std::make_unique<CreateVisitorReply>(DocumentProtocol::REPLY_CREATEVISITOR);
            reply->setLastBucket(get_bucket_id(src.last_bucket()));
            const auto& vs = src.statistics();
            vdslib::VisitorStatistics stats;
            stats.setBucketsVisited(vs.buckets_visited());
            stats.setDocumentsVisited(vs.documents_visited());
            stats.setBytesVisited(vs.bytes_visited());
            stats.setDocumentsReturned(vs.documents_returned());
            stats.setBytesReturned(vs.bytes_returned());
            reply->setVisitorStatistics(stats);
            return reply;
        }
    );
}

// ---------------------------------------------
// DestroyVisitor request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::destroy_visitor_message_factory() {
    return make_codec<DestroyVisitorMessage, protobuf::DestroyVisitorRequest>(
        [](const DestroyVisitorMessage& src, protobuf::DestroyVisitorRequest& dest) {
            dest.set_instance_id(src.getInstanceId().data(), src.getInstanceId().size());
        },
        [](const protobuf::DestroyVisitorRequest& src) {
            auto msg = std::make_unique<DestroyVisitorMessage>();
            msg->setInstanceId(src.instance_id());
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::destroy_visitor_reply_factory() {
    return make_codec<VisitorReply, protobuf::DestroyVisitorResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::DestroyVisitorResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::DestroyVisitorResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DESTROYVISITOR);
        }
    );
}

// ---------------------------------------------
// MapVisitor request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::map_visitor_message_factory() {
    return make_codec<MapVisitorMessage, protobuf::MapVisitorRequest>(
        [](const MapVisitorMessage& src, protobuf::MapVisitorRequest& dest) {
            set_visitor_params(*dest.mutable_data(), src.getData());
        },
        [](const protobuf::MapVisitorRequest& src) {
            auto msg = std::make_unique<MapVisitorMessage>();
            msg->setData(get_visitor_params(src.data()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::map_visitor_reply_factory() {
    return make_codec<VisitorReply, protobuf::MapVisitorResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::MapVisitorResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::MapVisitorResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_MAPVISITOR);
        }
    );
}

// ---------------------------------------------
// QueryResult request and response
// ---------------------------------------------

namespace {

void set_search_result(protobuf::SearchResult& dest, const vdslib::SearchResult& src) {
    // We treat these as opaque blobs for now. Should ideally be protobuf as well.
    vespalib::GrowableByteBuffer buf;
    src.serialize(buf);
    assert(buf.position() <= INT_MAX);
    dest.set_payload(buf.getBuffer(), buf.position());
}

void set_document_summary(protobuf::DocumentSummary& dest, const vdslib::DocumentSummary& src) {
    // We treat these as opaque blobs for now. Should ideally be protobuf as well.
    vespalib::GrowableByteBuffer buf;
    src.serialize(buf);
    assert(buf.position() <= INT_MAX);
    dest.set_payload(buf.getBuffer(), buf.position());
}

document::ByteBuffer wrap_as_buffer(std::string_view buf) {
    assert(buf.size() <= UINT32_MAX);
    return {buf.data(), static_cast<uint32_t>(buf.size())};
}

}

std::shared_ptr<IRoutableFactory> RoutableFactories80::query_result_message_factory() {
    return make_codec<QueryResultMessage, protobuf::QueryResultRequest>(
        [](const QueryResultMessage& src, protobuf::QueryResultRequest& dest) {
            set_search_result(*dest.mutable_search_result(), src.getSearchResult());
            set_document_summary(*dest.mutable_document_summary(), src.getDocumentSummary());
        },
        [](const protobuf::QueryResultRequest& src) {
            auto msg = std::make_unique<QueryResultMessage>();
            // Explicitly enforce presence of result/summary fields, as our object is not necessarily
            // well-defined if these have not been initialized.
            if (!src.has_search_result() || !src.has_document_summary()) [[unlikely]] {
                throw document::DeserializeException("Query result does not have all required fields set", VESPA_STRLOC);
            }
            {
                auto buf_view = wrap_as_buffer(src.search_result().payload()); // Must be lvalue
                msg->getSearchResult().deserialize(buf_view);
            }
            {
                auto buf_view = wrap_as_buffer(src.document_summary().payload()); // Also lvalue
                msg->getDocumentSummary().deserialize(buf_view);
            }
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::query_result_reply_factory() {
    return make_codec<VisitorReply, protobuf::QueryResultResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::QueryResultResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::QueryResultResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_QUERYRESULT);
        }
    );
}

// ---------------------------------------------
// VisitorInfo request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::visitor_info_message_factory() {
    return make_codec<VisitorInfoMessage, protobuf::VisitorInfoRequest>(
        [](const VisitorInfoMessage& src, protobuf::VisitorInfoRequest& dest) {
            set_bucket_id_vector(*dest.mutable_finished_buckets(), src.getFinishedBuckets());
            dest.set_error_message(src.getErrorMessage());
        },
        [](const protobuf::VisitorInfoRequest& src) {
            auto msg = std::make_unique<VisitorInfoMessage>();
            msg->setFinishedBuckets(get_bucket_id_vector(src.finished_buckets()));
            msg->setErrorMessage(src.error_message());
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::visitor_info_reply_factory() {
    return make_codec<VisitorReply, protobuf::VisitorInfoResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::VisitorInfoResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::VisitorInfoResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_VISITORINFO);
        }
    );
}

// ---------------------------------------------
// DocumentList request and response
// TODO deprecate
// ---------------------------------------------

std::shared_ptr<IRoutableFactory>
RoutableFactories80::document_list_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo) {
    return make_codec<DocumentListMessage, protobuf::DocumentListRequest>(
        [](const DocumentListMessage& src, protobuf::DocumentListRequest& dest) {
            set_bucket_id(*dest.mutable_bucket_id(), src.getBucketId());
            for (const auto& doc : src.getDocuments()) {
                auto* proto_entry = dest.add_entries();
                proto_entry->set_timestamp(doc.getTimestamp());
                proto_entry->set_is_tombstone(doc.isRemoveEntry());
                set_document(*proto_entry->mutable_document(), *doc.getDocument());
            }
        },
        [type_repo = std::move(repo)](const protobuf::DocumentListRequest& src) {
            auto msg = std::make_unique<DocumentListMessage>();
            msg->setBucketId(get_bucket_id(src.bucket_id()));
            for (const auto& proto_entry : src.entries()) {
                auto doc = get_document_or_throw(proto_entry.document(), *type_repo);
                msg->getDocuments().emplace_back(proto_entry.timestamp(), std::move(doc), proto_entry.is_tombstone());
            }
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::document_list_reply_factory() {
    return make_codec<VisitorReply, protobuf::DocumentListResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::DocumentListResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::DocumentListResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTLIST);
        }
    );
}

// ---------------------------------------------
// EmptyBuckets request and response
// TODO this should be deprecated
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::empty_buckets_message_factory() {
    return make_codec<EmptyBucketsMessage, protobuf::EmptyBucketsRequest>(
        [](const EmptyBucketsMessage& src, protobuf::EmptyBucketsRequest& dest) {
            set_bucket_id_vector(*dest.mutable_bucket_ids(), src.getBucketIds());
        },
        [](const protobuf::EmptyBucketsRequest& src) {
            auto msg = std::make_unique<EmptyBucketsMessage>();
            msg->setBucketIds(get_bucket_id_vector(src.bucket_ids()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::empty_buckets_reply_factory() {
    return make_codec<VisitorReply, protobuf::EmptyBucketsResponse>(
        []([[maybe_unused]] const VisitorReply& src, [[maybe_unused]] protobuf::EmptyBucketsResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::EmptyBucketsResponse& src) {
            return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_EMPTYBUCKETS); // ugh
        }
    );
}

// ---------------------------------------------
// GetBucketList request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_bucket_list_message_factory() {
    return make_codec<GetBucketListMessage, protobuf::GetBucketListRequest>(
        [](const GetBucketListMessage& src, protobuf::GetBucketListRequest& dest) {
            set_bucket_id(*dest.mutable_bucket_id(), src.getBucketId());
            set_bucket_space(*dest.mutable_bucket_space(), src.getBucketSpace());
        },
        [](const protobuf::GetBucketListRequest& src) {
            auto msg = std::make_unique<GetBucketListMessage>(get_bucket_id(src.bucket_id()));
            msg->setBucketSpace(get_bucket_space(src.bucket_space()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_bucket_list_reply_factory() {
    return make_codec<GetBucketListReply, protobuf::GetBucketListResponse>(
        [](const GetBucketListReply& src, protobuf::GetBucketListResponse& dest) {
            auto* proto_info = dest.mutable_bucket_info();
            assert(src.getBuckets().size() <= INT_MAX);
            proto_info->Reserve(static_cast<int>(src.getBuckets().size()));
            for (const auto& info : src.getBuckets()) {
                auto* entry = proto_info->Add();
                set_bucket_id(*entry->mutable_bucket_id(), info._bucket);
                entry->set_info(info._bucketInformation.data(), info._bucketInformation.size());
            }
        },
        [](const protobuf::GetBucketListResponse& src) {
            auto reply = std::make_unique<GetBucketListReply>();
            reply->getBuckets().reserve(src.bucket_info_size());
            for (const auto& proto_info : src.bucket_info()) {
                GetBucketListReply::BucketInfo info;
                info._bucket = get_bucket_id(proto_info.bucket_id());
                info._bucketInformation = proto_info.info();
                reply->getBuckets().emplace_back(std::move(info));
            }
            return reply;
        }
    );
}

// ---------------------------------------------
// GetBucketState request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_bucket_state_message_factory() {
    return make_codec<GetBucketStateMessage, protobuf::GetBucketStateRequest>(
        [](const GetBucketStateMessage& src, protobuf::GetBucketStateRequest& dest) {
            // FIXME misses bucket space, but does not seem to be in use?
            set_bucket_id(*dest.mutable_bucket_id(), src.getBucketId());
        },
        [](const protobuf::GetBucketStateRequest& src) {
            return std::make_unique<GetBucketStateMessage>(get_bucket_id(src.bucket_id()));
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::get_bucket_state_reply_factory() {
    return make_codec<GetBucketStateReply, protobuf::GetBucketStateResponse>(
        [](const GetBucketStateReply& src, protobuf::GetBucketStateResponse& dest) {
            assert(src.getBucketState().size() <= INT_MAX);
            auto* proto_states = dest.mutable_states();
            proto_states->Reserve(static_cast<int>(src.getBucketState().size()));
            for (const auto& state : src.getBucketState()) {
                auto* ps = proto_states->Add();
                if (state.getDocumentId()) {
                    set_document_id(*ps->mutable_document_id(), *state.getDocumentId());
                } else {
                    set_global_id(*ps->mutable_global_id(), state.getGlobalId());
                }
                ps->set_timestamp(state.getTimestamp());
                ps->set_is_tombstone(state.isRemoveEntry());
            }
        },
        [](const protobuf::GetBucketStateResponse& src) {
            auto reply = std::make_unique<GetBucketStateReply>();
            reply->getBucketState().reserve(src.states_size());
            for (const auto& proto_state : src.states()) {
                if (proto_state.has_document_id()) {
                    reply->getBucketState().emplace_back(get_document_id(proto_state.document_id()),
                                                         proto_state.timestamp(), proto_state.is_tombstone());
                } else {
                    reply->getBucketState().emplace_back(get_global_id(proto_state.global_id()),
                                                         proto_state.timestamp(), proto_state.is_tombstone());
                }
            }
            return reply;
        }
    );
}

// ---------------------------------------------
// StatBucket request and response
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::stat_bucket_message_factory() {
    return make_codec<StatBucketMessage, protobuf::StatBucketRequest>(
        [](const StatBucketMessage& src, protobuf::StatBucketRequest& dest) {
            set_bucket_id(*dest.mutable_bucket_id(), src.getBucketId());
            set_raw_selection(*dest.mutable_selection(), src.getDocumentSelection());
            set_bucket_space(*dest.mutable_bucket_space(), src.getBucketSpace());
        },
        [](const protobuf::StatBucketRequest& src) {
            auto msg = std::make_unique<StatBucketMessage>();
            msg->setBucketId(get_bucket_id(src.bucket_id()));
            msg->setDocumentSelection(get_raw_selection(src.selection()));
            msg->setBucketSpace(get_bucket_space(src.bucket_space()));
            return msg;
        }
    );
}

std::shared_ptr<IRoutableFactory> RoutableFactories80::stat_bucket_reply_factory() {
    return make_codec<StatBucketReply, protobuf::StatBucketResponse>(
        [](const StatBucketReply& src, protobuf::StatBucketResponse& dest) {
            dest.set_results(src.getResults());
        },
        [](const protobuf::StatBucketResponse& src) {
            auto reply = std::make_unique<StatBucketReply>();
            reply->setResults(src.results());
            return reply;
        }
    );
}

// ---------------------------------------------
// WrongDistribution response (no request type)
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::wrong_distribution_reply_factory() {
    return make_codec<WrongDistributionReply, protobuf::WrongDistributionResponse>(
        [](const WrongDistributionReply& src, protobuf::WrongDistributionResponse& dest) {
            dest.mutable_cluster_state()->set_state_string(src.getSystemState());
        },
        [](const protobuf::WrongDistributionResponse& src) {
            auto reply = std::make_unique<WrongDistributionReply>();
            reply->setSystemState(src.cluster_state().state_string());
            return reply;
        }
    );
}

// ---------------------------------------------
// DocumentIgnored response (no request type)
// ---------------------------------------------

std::shared_ptr<IRoutableFactory> RoutableFactories80::document_ignored_reply_factory() {
    return make_codec<DocumentIgnoredReply, protobuf::DocumentIgnoredResponse>(
        []([[maybe_unused]] const DocumentIgnoredReply& src, [[maybe_unused]] protobuf::DocumentIgnoredResponse& dest) {
            // no-op
        },
        []([[maybe_unused]] const protobuf::DocumentIgnoredResponse& src) {
            return std::make_unique<DocumentIgnoredReply>();
        }
    );
}

} // documentapi::messagebus
