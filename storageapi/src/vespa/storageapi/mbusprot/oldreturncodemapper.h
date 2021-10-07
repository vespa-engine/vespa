// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \brief Maps return code values used between 4.2 and 5.0
 */
#pragma once

namespace storage {
namespace mbusprot {

int getOldErrorCode(api::ReturnCode::Result newErrorCode) {
    switch (newErrorCode) {
        case api::ReturnCode::OK:                 return 1;
        case api::ReturnCode::EXISTS:             return 1001;
        case api::ReturnCode::NOT_READY:          return 2000;
        case api::ReturnCode::WRONG_DISTRIBUTION: return 2001;
        case api::ReturnCode::REJECTED:           return 2002;
        case api::ReturnCode::ABORTED:            return 2003;
        case api::ReturnCode::BUCKET_NOT_FOUND:   return 2004;
        case api::ReturnCode::BUCKET_DELETED:     return 2004;
        case api::ReturnCode::TIMESTAMP_EXIST:    return 2005;
        case api::ReturnCode::UNKNOWN_COMMAND:    return 3000;
        case api::ReturnCode::NOT_IMPLEMENTED:    return 3001;
        case api::ReturnCode::ILLEGAL_PARAMETERS: return 3002;
        case api::ReturnCode::IGNORED:            return 3003;
        case api::ReturnCode::UNPARSEABLE:        return 3004;
        case api::ReturnCode::NOT_CONNECTED:      return 4000;
        case api::ReturnCode::TIMEOUT:            return 4003;
        case api::ReturnCode::BUSY:               return 4004;
        case api::ReturnCode::NO_SPACE:           return 5000;
        case api::ReturnCode::DISK_FAILURE:       return 5001;
        case api::ReturnCode::IO_FAILURE:         return 5003;
        case api::ReturnCode::INTERNAL_FAILURE:   return 6000;
        default:                             return 6001;
    }
}

api::ReturnCode::Result getNewErrorCode(int oldErrorCode) {
    switch (oldErrorCode) {
        case 1:    return api::ReturnCode::OK;
        case 1000: return api::ReturnCode::OK; // NOT_FOUND
        case 1001: return api::ReturnCode::EXISTS;
        case 2000: return api::ReturnCode::NOT_READY;
        case 2001: return api::ReturnCode::WRONG_DISTRIBUTION;
        case 2002: return api::ReturnCode::REJECTED;
        case 2003: return api::ReturnCode::ABORTED;
        case 2004: return api::ReturnCode::BUCKET_NOT_FOUND;
        case 2005: return api::ReturnCode::TIMESTAMP_EXIST;
        case 3000: return api::ReturnCode::UNKNOWN_COMMAND;
        case 3001: return api::ReturnCode::NOT_IMPLEMENTED;
        case 3002: return api::ReturnCode::ILLEGAL_PARAMETERS;
        case 3003: return api::ReturnCode::IGNORED;
        case 3004: return api::ReturnCode::UNPARSEABLE;
        case 4000: return api::ReturnCode::NOT_CONNECTED;
        case 4001: return api::ReturnCode::BUSY; // OVERLOAD;
        case 4002: return api::ReturnCode::NOT_READY; // REMOTE_DISABLED;
        case 4003: return api::ReturnCode::TIMEOUT;
        case 4004: return api::ReturnCode::BUSY;
        case 5000: return api::ReturnCode::NO_SPACE;
        case 5001: return api::ReturnCode::DISK_FAILURE;
        case 5003: return api::ReturnCode::IO_FAILURE;
        case 6000: return api::ReturnCode::INTERNAL_FAILURE;
        default:   return api::ReturnCode::INTERNAL_FAILURE;
    }
}

} // mbusprot
} // storage

