// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "schema_index_fields.h"
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::index {

/**
 * Builder for the indexingdocument document type based on an index schema.
 **/
class DocTypeBuilder {
    const Schema &_schema;
    SchemaIndexFields _iFields;

public:
    DocTypeBuilder(const Schema & schema);
    document::DocumenttypesConfig makeConfig() const;

    static document::DocumenttypesConfig
    makeConfig(const document::DocumentType &docType);
};

}
