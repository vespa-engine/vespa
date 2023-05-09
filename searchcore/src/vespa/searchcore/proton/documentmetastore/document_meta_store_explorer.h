// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store_context.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a document meta store.
 */
class DocumentMetaStoreExplorer : public vespalib::StateExplorer
{
private:
    IDocumentMetaStoreContext::IReadGuard::SP _metaStore;

public:
    DocumentMetaStoreExplorer(IDocumentMetaStoreContext::IReadGuard::SP metaStore);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

