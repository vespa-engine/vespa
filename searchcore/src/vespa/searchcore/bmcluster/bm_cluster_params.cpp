// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster_params.h"
#include <iostream>

namespace search::bmcluster {

BmClusterParams::BmClusterParams()
    : _bucket_db_stripe_bits(0),
      _distributor_stripes(0),
      _enable_distributor(false),
      _enable_service_layer(false),
      _indexing_sequencer(),
      _num_nodes(1),
      _response_threads(2),         // Same default as in stor-filestor.def
      _rpc_events_before_wakeup(1), // Same default as in stor-communicationmanager.def
      _rpc_network_threads(1),      // Same default as previous in stor-communicationmanager.def
      _rpc_targets_per_node(1),     // Same default as in stor-communicationmanager.def
      _skip_communicationmanager_thread(false), // Same default as in stor-communicationmanager.def
      _skip_get_spi_bucket_info(false),
      _use_async_message_handling_on_schedule(false),
      _use_document_api(false),
      _use_message_bus(false),
      _use_storage_chain(false)
{
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
    return true;
}

}
