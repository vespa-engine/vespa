// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 * \file document.h
 *
 * Include common client parts of document, such that clients can easily
 * just include this file to get what they need.
 *
 * This should pull in all code needed for handling:
 *    - Datatypes
 *    - Fieldvalues
 *    - Updates
 *    - Selection language
 */

#pragma once

#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/update/updates.h>
#include <vespa/document/select/parser.h>

