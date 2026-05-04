// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_meta_store_config.h"

#include <vespa/config-proton.h>

namespace proton {

DocumentMetaStoreConfig::DocumentMetaStoreConfig(bool store_full_document_ids)
    : _store_full_document_ids(store_full_document_ids) {
}

DocumentMetaStoreConfig DocumentMetaStoreConfig::make(const ProtonConfig& cfg) {
    return {cfg.storeFullDocumentIds};
}

DocumentMetaStoreConfig DocumentMetaStoreConfig::make() {
    return {false};
}

void DocumentMetaStoreConfig::update(const DocumentMetaStoreConfig& cfg) {
    _store_full_document_ids = cfg._store_full_document_ids;
}

bool DocumentMetaStoreConfig::operator==(const DocumentMetaStoreConfig& rhs) const {
    return _store_full_document_ids == rhs._store_full_document_ids;
}

} // namespace proton
