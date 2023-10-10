// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "errorcode.h"

namespace config {

vespalib::string ErrorCode::getName(int error) {
    switch(error) {
    case UNKNOWN_CONFIG:            return "UNKNOWN_CONFIG";
    case UNKNOWN_DEFINITION:        return "UNKNOWN_DEFINITION";
    case UNKNOWN_VERSION:           return "UNKNOWN_VERSION";
    case UNKNOWN_CONFIGID:          return "UNKNOWN_CONFIGID";
    case UNKNOWN_DEF_MD5:           return "UNKNOWN_DEF_MD5";
    case UNKNOWN_VESPA_VERSION:     return "UNKNOWN_VESPA_VERSION";
    case ILLEGAL_NAME:              return "ILLEGAL_NAME";
    case ILLEGAL_VERSION:           return "ILLEGAL_VERSION";
    case ILLEGAL_CONFIGID:          return "ILLEGAL_CONFIGID";
    case ILLEGAL_DEF_MD5:           return "ILLEGAL_DEF_MD5";
    case ILLEGAL_CONFIG_MD5:        return "ILLEGAL_CONFIG_MD5";
    case ILLEGAL_TIMEOUT:           return "ILLEGAL_TIMEOUT";
    case ILLEGAL_TIMESTAMP:         return "ILLEGAL_TIMESTAMP";
    case ILLEGAL_NAME_SPACE:        return "ILLEGAL_NAME_SPACE";
    case ILLEGAL_PROTOCOL_VERSION:  return "ILLEGAL_PROTOCOL_VERSION";
    case ILLEGAL_CLIENT_HOSTNAME:   return "ILLEGAL_CLIENT_HOSTNAME";
    case OUTDATED_CONFIG:           return "OUTDATED_CONFIG";
    case INTERNAL_ERROR:            return "INTERNAL_ERROR";
    case APPLICATION_NOT_LOADED:    return "APPLICATION_NOT_LOADED";
    case INCONSISTENT_CONFIG_MD5:   return "INCONSISTENT_CONFIG_MD5";
    default:                        return "Unknown error";
    }
}

}
