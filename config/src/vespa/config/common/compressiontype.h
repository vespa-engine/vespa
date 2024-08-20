// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace config {

enum class CompressionType {UNCOMPRESSED, LZ4};
std::string compressionTypeToString(const CompressionType & compressionType);
CompressionType stringToCompressionType(const std::string & type);

}

