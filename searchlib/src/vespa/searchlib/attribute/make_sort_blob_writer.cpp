// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_sort_blob_writer.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>

using search::attribute::BasicType;
using search::common::FieldSortSpec;
using vespalib::IllegalArgumentException;
using vespalib::Issue;

namespace search::attribute {

std::unique_ptr<search::attribute::ISortBlobWriter>
make_sort_blob_writer(const IAttributeVector* vector, const FieldSortSpec& field_sort_spec) {
    if (vector == nullptr) {
        return {};
    }
    try {
        return vector->make_sort_blob_writer(field_sort_spec._ascending,
                                             field_sort_spec._converter.get(),
                                             field_sort_spec._missing_policy,
                                             field_sort_spec._missing_value);
    } catch (const IllegalArgumentException& e) {
        Issue::report("make_sort_blob_writer: For attribute vector %s (basic type %s): %s",
                      vector->getName().c_str(),
                      BasicType(vector->getBasicType()).asString(),
                      e.message());
        return {};
    }
}

}
