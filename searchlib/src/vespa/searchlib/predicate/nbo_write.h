// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/objects/nbo.h>

namespace search::predicate {

/*
 * Utility function for writing a scalar value
 * in network byte order via an BufferWriter.
 */
template <typename T>
void nbo_write(BufferWriter& writer, T value)
{
    auto value_nbo = vespalib::nbo::n2h(value);
    writer.write(&value_nbo, sizeof(value_nbo));
}

}
