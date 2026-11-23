// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_document_meta_store_lid_space_flush_target.h"

namespace proton {

namespace {

const std::string shrink_document_meta_store_lid_space_trace_span_name("Shrink document meta store lid space");
const std::string shrink_document_meta_store_name("documentmetastore.shrink");

} // namespace

ShrinkDocumentMetaStoreLidSpaceFlushTarget::ShrinkDocumentMetaStoreLidSpaceFlushTarget(
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, SerialNum flushedSerialNum,
    Time last_flush_time, std::shared_ptr<ICompactableLidSpace> target)
    : ShrinkLidSpaceFlushTarget(shrink_document_meta_store_name, Type::GC, Component::ATTRIBUTE,
                                std::move(doc_type_and_subdb_name_in), flushedSerialNum, last_flush_time,
                                std::move(target)) {
}

ShrinkDocumentMetaStoreLidSpaceFlushTarget::~ShrinkDocumentMetaStoreLidSpaceFlushTarget() = default;

[[nodiscard]] std::string_view ShrinkDocumentMetaStoreLidSpaceFlushTarget::trace_span_name() const noexcept {
    return shrink_document_meta_store_lid_space_trace_span_name;
}

} // namespace proton
