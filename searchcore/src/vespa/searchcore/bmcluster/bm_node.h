// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/searchcore/proton/common/doctypename.h>

namespace document {

class DocumentTypeRepo;
class DocumentType;
class Field;

};

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
    std::shared_ptr<document::DocumenttypesConfig> _document_types;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    proton::DocTypeName                        _doc_type_name;
    const document::DocumentType*              _document_type;
    const document::Field&                     _field;

    BmNode(std::shared_ptr<document::DocumenttypesConfig> document_types);
public:
    virtual ~BmNode();
    virtual std::unique_ptr<SpiBmFeedHandler> make_create_bucket_feed_handler(bool skip_get_spi_bucket_info) = 0;
    virtual void start_service_layer(const BmClusterParams& params) = 0;
    virtual void wait_service_layer() = 0;
    virtual void start_distributor(const BmClusterParams& params) = 0;
    virtual void create_feed_handler(const BmClusterParams& params, BmCluster& cluster) = 0;
    virtual void shutdown_feed_handler() = 0;
    virtual void shutdown_distributor() = 0;
    virtual void shutdown_service_layer() = 0;
    virtual IBmFeedHandler* get_feed_handler() = 0;
    virtual storage::spi::PersistenceProvider *get_persistence_provider() = 0;
    static std::unique_ptr<BmNode> create(const BmClusterParams& params, std::shared_ptr<document::DocumenttypesConfig> document_types);
    const proton::DocTypeName& get_doc_type_name() const noexcept { return _doc_type_name; }
    const document::DocumentType *get_document_type() const noexcept { return _document_type; }
    const document::Field& get_field() const noexcept { return _field; }
};

}
