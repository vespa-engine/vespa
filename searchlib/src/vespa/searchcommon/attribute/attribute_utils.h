// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

class Config;

/**
 * Returns whether the given attribute vector is updateable only in-memory.
 *
 * For most attributes this is true.
 * The data stored in the attribute is equal to the data stored in the field value in the document.
 *
 * For predicate and reference attributes this is false.
 * The original data is transformed (lossy) before it is stored in the attribute.
 * During update we also need to update the field value in the document.
 *
 * For struct field attributes this is false.
 * A struct field attribute typically represents a sub-field of a more complex field (e.g. map of struct or array of struct).
 * During update the complex field is first updated in the document,
 * then the struct field attribute is updated based on the new content of the complex field.
 */
bool isUpdateableInMemoryOnly(const vespalib::string &attrName, const Config &cfg);

bool isStructFieldAttribute(const vespalib::string &attrName);

}
