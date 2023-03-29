// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace search { class AttributeVector; }

namespace proton {

class AttributeExecutor;

/**
 * Class used to explore the state of an attribute vector.
 */
class AttributeVectorExplorer : public vespalib::StateExplorer
{
private:
    std::unique_ptr<const AttributeExecutor> _executor;

    void get_state_helper(const search::AttributeVector& attr, const vespalib::slime::Inserter &inserter, bool full) const;
public:
    AttributeVectorExplorer(std::unique_ptr<AttributeExecutor> executor);

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

