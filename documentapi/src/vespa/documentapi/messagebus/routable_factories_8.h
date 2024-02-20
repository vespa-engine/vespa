// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iroutablefactory.h"

namespace document { class DocumentTypeRepo; }

namespace documentapi::messagebus {

/**
 * Implementation of MessageBus message request/response serialization built around Protocol Buffers.
 */
class RoutableFactories80 {
public:
    RoutableFactories80() = delete;

    // CRUD messages

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> put_document_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo);
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> put_document_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_document_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_document_reply_factory(std::shared_ptr<const document::DocumentTypeRepo> repo);

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> remove_document_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> remove_document_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> update_document_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo);
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> update_document_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> remove_location_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo);
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> remove_location_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> document_list_message_factory(std::shared_ptr<const document::DocumentTypeRepo> repo);
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> document_list_reply_factory();

    // Visitor-related messages

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> create_visitor_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> create_visitor_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> destroy_visitor_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> destroy_visitor_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> empty_buckets_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> empty_buckets_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> map_visitor_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> map_visitor_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> query_result_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> query_result_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> visitor_info_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> visitor_info_reply_factory();

    // Inspection-related messages

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_bucket_list_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_bucket_list_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_bucket_state_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> get_bucket_state_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> stat_bucket_message_factory();
    [[nodiscard]] static std::shared_ptr<IRoutableFactory> stat_bucket_reply_factory();

    // Polymorphic reply messages

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> wrong_distribution_reply_factory();

    [[nodiscard]] static std::shared_ptr<IRoutableFactory> document_ignored_reply_factory();
};

}
