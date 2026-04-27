// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-proton.h>

namespace vespa::config::search::core::internal { class InternalProtonType; }
namespace proton {

/**
 * Config for the DocumentMetaStore used by StoreOnlyDocSubDB.
 */
class DocumentMetaStoreConfig {
public:
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;

private:
    bool _store_full_document_ids;

private:
    DocumentMetaStoreConfig(bool store_full_document_ids);

public:
    static DocumentMetaStoreConfig make(const ProtonConfig& cfg);
    static DocumentMetaStoreConfig make();
    void update(const DocumentMetaStoreConfig& cfg);
    bool store_full_document_ids() const { return _store_full_document_ids; }
    bool operator==(const DocumentMetaStoreConfig& rhs) const;
};

}
