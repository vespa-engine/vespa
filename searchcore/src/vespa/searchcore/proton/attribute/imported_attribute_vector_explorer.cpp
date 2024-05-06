// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_vector_explorer.h"
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/memoryusage.h>

using search::StateExplorerUtils;
using search::attribute::ImportedAttributeVector;
using namespace vespalib::slime;

namespace proton {

ImportedAttributeVectorExplorer::ImportedAttributeVectorExplorer(std::shared_ptr<ImportedAttributeVector> attr)
    : _attr(std::move(attr))
{
}

void
ImportedAttributeVectorExplorer::get_state(const vespalib::slime::Inserter &inserter, bool) const
{
    Cursor &object = inserter.insertObject();
    auto memory_usage = _attr->get_memory_usage();
    StateExplorerUtils::memory_usage_to_slime(memory_usage, object.setObject("cacheMemoryUsage"));
}

}
