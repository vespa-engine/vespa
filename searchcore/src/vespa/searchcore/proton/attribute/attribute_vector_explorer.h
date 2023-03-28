// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "exclusive_attribute_read_accessor.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of an attribute vector.
 */
class AttributeVectorExplorer : public vespalib::StateExplorer
{
private:
    ExclusiveAttributeReadAccessor::UP _attribute;

public:
    AttributeVectorExplorer(ExclusiveAttributeReadAccessor::UP attribute);

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

