// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster_params.h"
#include <iostream>

namespace search::bmcluster {

BmClusterParams::BmClusterParams()
    : _bucket_db_stripe_bits(4),
      _disable_queue_limits_for_chained_merges(false), // Same default as in stor-server.def
      _distributor_merge_busy_wait(10), // Same default as stor_distributormanager.def
      _distributor_stripes(0),
      _doc_store_chunk_compression_level(9), // Same default as in proton.def
      _doc_store_chunk_maxbytes(65536),      // Same default as in proton.def
      _enable_distributor(false),
      _enable_service_layer(false),
      _groups(0),
      _indexing_sequencer(),
      _max_merges_per_node(16),     // Same default as in stor-server.def
      _max_merge_queue_size(1024),  // Same default as in stor-server.def
      _max_pending_idealstate_operations(100), // Same default as in stor-distributormanager.def
      _mbus_distributor_node_max_pending_count(),
      _num_nodes(1),
      _nodes_per_group(1),
      _redundancy(1),
      _response_threads(2),         // Same default as in stor-filestor.def
      _rpc_events_before_wakeup(1), // Same default as in stor-communicationmanager.def
      _rpc_network_threads(1),      // Same default as previous in stor-communicationmanager.def
      _rpc_targets_per_node(1),     // Same default as in stor-communicationmanager.def
      _skip_get_spi_bucket_info(false),
      _use_async_message_handling_on_schedule(false),
      _use_document_api(false),
      _use_message_bus(false),
      _use_storage_chain(false)
{
    recalc_nodes();
}

BmClusterParams::~BmClusterParams() = default;

bool
BmClusterParams::check() const
{
    if (_response_threads < 1) {
        std::cerr << "Too few response threads: " << _response_threads << std::endl;
        return false;
    }
    if (_rpc_network_threads < 1) {
        std::cerr << "Too few rpc network threads: " << _rpc_network_threads << std::endl;
        return false;
    }
    if (_rpc_targets_per_node < 1) {
        std::cerr << "Too few rpc targets per node: " << _rpc_targets_per_node << std::endl;
        return false;
    }
    if (_nodes_per_group < _redundancy) {
        std::cerr << "Too high redundancy " << _redundancy << " with " << _nodes_per_group << " nodes per group" << std::endl;
        return false;
    }
    return true;
}

void
BmClusterParams::recalc_nodes()
{
    _num_nodes = std::max(1u, _groups) * _nodes_per_group;
}

void
BmClusterParams::set_groups(uint32_t value)
{
    _groups = value;
    recalc_nodes();
}

void
BmClusterParams::set_nodes_per_group(uint32_t value)
{
    _nodes_per_group = value;
    recalc_nodes();
}

}
