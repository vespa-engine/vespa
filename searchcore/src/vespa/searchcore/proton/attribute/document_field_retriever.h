// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/query/base.h>

namespace proton {

/**
 * Class used to retrieve a document field and populate it with the content from an attribute vector.
 */
struct DocumentFieldRetriever
{
    static void populate(search::DocumentIdT lid,
                         document::Document &doc,
                         const document::Field &field,
                         const search::attribute::IAttributeVector &attr);
};

} // namespace proton

