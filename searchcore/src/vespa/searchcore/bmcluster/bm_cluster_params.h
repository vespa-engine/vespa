// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/vespalib/stllike/string.h>
#include <optional>

namespace search::bmcluster {

/*
 * Parameters for setting up a benchmark cluster.
 */
class BmClusterParams
{
    uint32_t _bucket_db_stripe_bits;
    bool     _disable_queue_limits_for_chained_merges;
    uint32_t _distributor_merge_busy_wait;
    uint32_t _distributor_stripes;
    uint32_t _doc_store_chunk_compression_level;
    uint32_t _doc_store_chunk_maxbytes;
    bool     _enable_distributor;
    bool     _enable_service_layer;
    uint32_t _groups;
    vespalib::string _indexing_sequencer;
    uint32_t _max_merges_per_node;
    uint32_t _max_merge_queue_size;
    uint32_t _max_pending_idealstate_operations;
    std::optional<uint32_t> _mbus_distributor_node_max_pending_count;
    uint32_t _num_nodes;
    uint32_t _nodes_per_group;
    uint32_t _redundancy;
    uint32_t _response_threads;
    uint32_t _rpc_events_before_wakeup;
    uint32_t _rpc_network_threads;
    uint32_t _rpc_targets_per_node;
    bool     _skip_get_spi_bucket_info;
    bool     _use_async_message_handling_on_schedule;
    bool     _use_document_api;
    bool     _use_message_bus;
    bool     _use_storage_chain;
    void recalc_nodes();
public:
    BmClusterParams();
    ~BmClusterParams();
    uint32_t get_bucket_db_stripe_bits() const { return _bucket_db_stripe_bits; }
    bool get_disable_queue_limits_for_chained_merges() const noexcept { return _disable_queue_limits_for_chained_merges; }
    uint32_t get_distributor_merge_busy_wait() const { return _distributor_merge_busy_wait; }
    uint32_t get_distributor_stripes() const { return _distributor_stripes; }
    uint32_t get_doc_store_chunk_compression_level() const noexcept { return _doc_store_chunk_compression_level; }
    uint32_t get_doc_store_chunk_maxbytes() const noexcept { return _doc_store_chunk_maxbytes; }
    bool get_enable_distributor() const { return _enable_distributor; }
    uint32_t get_groups() const noexcept { return _groups; }
    const vespalib::string & get_indexing_sequencer() const { return _indexing_sequencer; }
    uint32_t get_max_merges_per_node() const noexcept { return _max_merges_per_node; }
    uint32_t get_max_merge_queue_size() const noexcept { return _max_merge_queue_size; }
    uint32_t get_max_pending_idealstate_operations() const noexcept { return _max_pending_idealstate_operations; }
    const std::optional<uint32_t>& get_mbus_distributor_node_max_pending_count() const noexcept { return _mbus_distributor_node_max_pending_count; }
    uint32_t get_nodes_per_group() const noexcept { return _nodes_per_group; }
    uint32_t get_num_nodes() const { return _num_nodes; }
    uint32_t get_redundancy() const { return _redundancy; }
    uint32_t get_response_threads() const { return _response_threads; }
    uint32_t get_rpc_events_before_wakeup() const { return _rpc_events_before_wakeup; }
    uint32_t get_rpc_network_threads() const { return _rpc_network_threads; }
    uint32_t get_rpc_targets_per_node() const { return _rpc_targets_per_node; }
    bool get_skip_get_spi_bucket_info() const { return _skip_get_spi_bucket_info; }
    bool get_use_async_message_handling_on_schedule() const { return _use_async_message_handling_on_schedule; }
    bool get_use_document_api() const { return _use_document_api; }
    bool get_use_message_bus() const { return _use_message_bus; }
    bool get_use_storage_chain() const { return _use_storage_chain; }
    bool needs_distributor() const { return _enable_distributor || _use_document_api; }
    bool needs_message_bus() const { return _use_message_bus || _use_document_api; }
    bool needs_service_layer() const { return _enable_service_layer || _enable_distributor || _use_storage_chain || _use_message_bus || _use_document_api; }
    void set_bucket_db_stripe_bits(uint32_t value) { _bucket_db_stripe_bits = value; }
    void set_disable_queue_limits_for_chained_merges(bool value) { _disable_queue_limits_for_chained_merges = value; }
    void set_distributor_merge_busy_wait(uint32_t value) { _distributor_merge_busy_wait = value; }
    void set_distributor_stripes(uint32_t value) { _distributor_stripes = value; }
    void set_doc_store_chunk_compression_level(uint32_t value) { _doc_store_chunk_compression_level = value; }
    void set_doc_store_chunk_maxbytes(uint32_t value) { _doc_store_chunk_maxbytes = value; }
    void set_enable_distributor(bool value) { _enable_distributor = value; }
    void set_enable_service_layer(bool value) { _enable_service_layer = value; }
    void set_groups(uint32_t value);
    void set_indexing_sequencer(vespalib::stringref sequencer) { _indexing_sequencer = sequencer; }
    void set_max_merges_per_node(uint32_t value) { _max_merges_per_node = value; }
    void set_max_merge_queue_size(uint32_t value) { _max_merge_queue_size = value; }
    void set_max_pending_idealstate_operations(uint32_t value) { _max_pending_idealstate_operations = value; }
    void set_mbus_distributor_node_max_pending_count(int32_t value) { _mbus_distributor_node_max_pending_count = value; }
    void set_nodes_per_group(uint32_t value);
    void set_redundancy(uint32_t value) { _redundancy = value; }
    void set_response_threads(uint32_t threads_in) { _response_threads = threads_in; }
    void set_rpc_events_before_wakeup(uint32_t value) { _rpc_events_before_wakeup = value; }
    void set_rpc_network_threads(uint32_t threads_in) { _rpc_network_threads = threads_in; }
    void set_rpc_targets_per_node(uint32_t targets_in) { _rpc_targets_per_node = targets_in; }
    void set_skip_get_spi_bucket_info(bool value) { _skip_get_spi_bucket_info = value; }
    void set_use_async_message_handling_on_schedule(bool value) { _use_async_message_handling_on_schedule = value; }
    void set_use_document_api(bool value) { _use_document_api = value; }
    void set_use_message_bus(bool value) { _use_message_bus = value; }
    void set_use_storage_chain(bool value) { _use_storage_chain = value; }
    bool check() const;
};

}
