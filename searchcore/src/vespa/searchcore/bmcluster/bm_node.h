// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/searchcore/proton/common/doctypename.h>

namespace document {

class DocumentTypeRepo;
class DocumentType;
class Field;

};

namespace document::internal { class InternalDocumenttypesType; }

namespace storage::spi { struct PersistenceProvider; }

namespace search::bmcluster {

class BmCluster;
class BmClusterParams;
class IBmFeedHandler;
class SpiBmFeedHandler;

/*
 * Class representing a single benchmark node in a benchmark cluster.
 */
class BmNode {
protected:

    BmNode();
public:
    virtual ~BmNode();
    virtual void initialize_persistence_provider()= 0;
    virtual std::unique_ptr<SpiBmFeedHandler> make_create_bucket_feed_handler(bool skip_get_spi_bucket_info) = 0;
    virtual void start_service_layer(const BmClusterParams& params) = 0;
    virtual void wait_service_layer() = 0;
    virtual void start_distributor(const BmClusterParams& params) = 0;
    virtual void create_feed_handler(const BmClusterParams& params, BmCluster& cluster) = 0;
    virtual void shutdown_feed_handler() = 0;
    virtual void shutdown_distributor() = 0;
    virtual void shutdown_service_layer() = 0;
    virtual void wait_service_layer_slobrok(BmCluster& cluster) = 0;
    virtual void wait_distributor_slobrok(BmCluster& cluster) = 0;
    virtual IBmFeedHandler* get_feed_handler() = 0;
    virtual storage::spi::PersistenceProvider *get_persistence_provider() = 0;
    static unsigned int num_ports();
    static std::unique_ptr<BmNode> create(const vespalib::string &base_dir, int base_port, int node_idx, const BmClusterParams& params, std::shared_ptr<const document::internal::InternalDocumenttypesType> document_types, int slobrok_port);
};

}
