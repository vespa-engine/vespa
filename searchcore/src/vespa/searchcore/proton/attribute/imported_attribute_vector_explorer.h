// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace search::attribute { class ImportedAttributeVector; }

namespace proton {

/**
 * Class used to explore the state of an imported attribute vector.
 */
class ImportedAttributeVectorExplorer : public vespalib::StateExplorer
{
private:
    std::shared_ptr<search::attribute::ImportedAttributeVector> _attr;

public:
    ImportedAttributeVectorExplorer(std::shared_ptr<search::attribute::ImportedAttributeVector> attr);

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

}
