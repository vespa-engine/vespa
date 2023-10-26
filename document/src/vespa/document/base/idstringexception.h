// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/exception.h>

namespace document {

/**
 * \class document::IdParseException
 * \ingroup base
 *
 * \brief Exception used to indicate failure to parse a %document identifier
 * URI.
 */

VESPA_DEFINE_EXCEPTION(IdParseException, vespalib::Exception);

}
