// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "compressiontype.h"

namespace config {

vespalib::string
compressionTypeToString(const CompressionType & compressionType)
{
    switch (compressionType) {
        case CompressionType::UNCOMPRESSED:
            return "UNCOMPRESSED";
        default:
            return "LZ4";
    }
}

CompressionType
stringToCompressionType(const vespalib::string & type)
{
    if (type.compare("UNCOMPRESSED") == 0) {
        return CompressionType::UNCOMPRESSED;
    } else {
        return CompressionType::LZ4;
    }
}

}
