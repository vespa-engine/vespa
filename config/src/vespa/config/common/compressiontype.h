// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace config {

enum class CompressionType {UNCOMPRESSED, LZ4};
vespalib::string compressionTypeToString(const CompressionType & compressionType);
CompressionType stringToCompressionType(const vespalib::string & type);

}

