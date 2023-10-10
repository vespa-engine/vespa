// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_writer.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of an attribute writer and its write contexts.
 */
class AttributeWriterExplorer : public vespalib::StateExplorer {
private:
    std::shared_ptr<IAttributeWriter> _writer;

public:
    AttributeWriterExplorer(std::shared_ptr<IAttributeWriter> writer);
    ~AttributeWriterExplorer();

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}

