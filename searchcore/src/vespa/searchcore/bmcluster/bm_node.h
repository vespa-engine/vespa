// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <memory>
#include <vector>

namespace document {

class Bucket;
class DocumentTypeRepo;
class DocumentType;
class Field;

};

namespace storage::lib { class ClusterState; }
namespace storage::spi { struct PersistenceProvider; }

namespace search::bmcluster {

class BmCluster;
class BmClusterParams;
class BmNodeStats;
struct BmStorageLinkContext;
class IBmFeedHandler;
class IBMDistribution;

/*
 * Class representing a single benchmark node in a benchmark cluster.
 */
class BmNode {
protected:

    BmNode();
public:
    virtual ~BmNode();
    virtual void initialize_persistence_provider() = 0;
    virtual void create_bucket(const document::Bucket& bucket) = 0;
    virtual void start_service_layer(const BmClusterParams& params) = 0;
    virtual void wait_service_layer() = 0;
    virtual void start_distributor(const BmClusterParams& params) = 0;
    virtual void shutdown_distributor() = 0;
    virtual void shutdown_service_layer() = 0;
    virtual void wait_service_layer_slobrok() = 0;
    virtual void wait_distributor_slobrok() = 0;
    virtual std::shared_ptr<BmStorageLinkContext> get_storage_link_context(bool distributor) = 0;
    virtual bool has_storage_layer(bool distributor) const = 0;
    virtual storage::spi::PersistenceProvider *get_persistence_provider() = 0;
    virtual void merge_node_stats(std::vector<BmNodeStats>& node_stats, storage::lib::ClusterState &baseline_state) = 0;
    static unsigned int num_ports();
    static std::unique_ptr<BmNode> create(const vespalib::string &base_dir, int base_port, uint32_t node_idx, BmCluster& cluster, const BmClusterParams& params, std::shared_ptr<DocumenttypesConfig> document_types, int slobrok_port);
};

}
