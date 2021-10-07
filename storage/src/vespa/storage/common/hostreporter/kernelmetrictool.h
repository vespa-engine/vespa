// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This file contains various tools for use by reporters when fetching os information.
 */

#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_KERNELMETRICTOOL_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_KERNELMETRICTOOL_H_

#include <vespa/vespalib/stllike/string.h>

namespace storage {
namespace kernelmetrictool {

vespalib::string readFile(const char* fileName);

vespalib::string stripWhitespace(const vespalib::string& s);

vespalib::string getLine(vespalib::stringref key,
                         vespalib::stringref content);

vespalib::string getToken(uint32_t index, const vespalib::string& line);

uint32_t getTokenCount(const vespalib::string& line);

uint64_t toLong(vespalib::stringref s, int base = 0) ;

} /* namespace kernelmetrictool */
} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_KERNELMETRICTOOL_H_ */
