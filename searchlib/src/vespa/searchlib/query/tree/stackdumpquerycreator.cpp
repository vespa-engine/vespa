// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpquerycreator.h"
#include <vespa/vespalib/objects/hexdump.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.tree.stackdumpquerycreator");

using vespalib::Issue;

namespace search::query {

void
StackDumpQueryCreatorHelper::populateMultiTerm(SimpleQueryStackDumpIterator &queryStack, QueryBuilderBase & builder, MultiTerm & mt) {
    uint32_t added(0);
    for (added = 0; (added < mt.getNumTerms()) && queryStack.next(); added++) {
        ParseItem::ItemType type = queryStack.getType();
        switch (type) {
            case ParseItem::ITEM_PURE_WEIGHTED_LONG:
                mt.addTerm(queryStack.getIntergerTerm(), queryStack.GetWeight());
                break;
            case ParseItem::ITEM_PURE_WEIGHTED_STRING:
                mt.addTerm(queryStack.getTerm(), queryStack.GetWeight());
                break;
            default:
                builder.reportError(vespalib::make_string("Got unexpected node %d for multiterm node at child term %d", type, added));
                return;
        }
    }
    if (added < mt.getNumTerms()) {
        builder.reportError(vespalib::make_string("Too few nodes(%d) for multiterm(%d)", added, mt.getNumTerms()));
    }
}

void
StackDumpQueryCreatorHelper::reportError(const SimpleQueryStackDumpIterator &queryStack, const QueryBuilderBase & builder) {
    vespalib::stringref stack = queryStack.getStack();
    Issue::report("Unable to create query tree from stack dump. Failed at position %ld out of %ld bytes %s",
               queryStack.getPosition(), stack.size(), builder.error().c_str());
    LOG(error, "got bad query stack: %s", vespalib::HexDump(stack.data(), stack.size()).toString().c_str());
}

}
