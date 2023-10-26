// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Gunnar Gauslaa Bergem
 * @date    2008-05-22
 * @version $Id: errorcode.h 119465 2011-04-20 15:21:46Z arnej $
 */

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace config {

class ErrorCode {
private:
    ErrorCode();
    ErrorCode(const ErrorCode &);

public:
    static const int UNKNOWN_CONFIG = 100000;
    static const int UNKNOWN_DEFINITION = UNKNOWN_CONFIG + 1;
    static const int UNKNOWN_VERSION = UNKNOWN_CONFIG + 2;
    static const int UNKNOWN_CONFIGID = UNKNOWN_CONFIG + 3;
    static const int UNKNOWN_DEF_MD5 = UNKNOWN_CONFIG + 4;
    static const int UNKNOWN_VESPA_VERSION = UNKNOWN_CONFIG + 5;

    static const int ILLEGAL_NAME = UNKNOWN_CONFIG + 100;
    static const int ILLEGAL_VERSION = UNKNOWN_CONFIG + 101;
    static const int ILLEGAL_CONFIGID = UNKNOWN_CONFIG + 102;
    static const int ILLEGAL_DEF_MD5 = UNKNOWN_CONFIG + 103;
    static const int ILLEGAL_CONFIG_MD5 = UNKNOWN_CONFIG + 104;
    static const int ILLEGAL_TIMEOUT = UNKNOWN_CONFIG + 105;
    static const int ILLEGAL_TIMESTAMP = UNKNOWN_CONFIG + 106;

    static const int ILLEGAL_NAME_SPACE = UNKNOWN_CONFIG + 108;
    static const int ILLEGAL_PROTOCOL_VERSION = UNKNOWN_CONFIG + 109;
    static const int ILLEGAL_CLIENT_HOSTNAME = UNKNOWN_CONFIG + 110;

    // hasUpdatedConfig() is true, but timestamp says the config is older than previous config
    static const int OUTDATED_CONFIG = UNKNOWN_CONFIG + 150;

    static const int INTERNAL_ERROR = UNKNOWN_CONFIG + 200;

    static const int APPLICATION_NOT_LOADED = UNKNOWN_CONFIG + 300;

    static const int INCONSISTENT_CONFIG_MD5 = UNKNOWN_CONFIG + 400;

    static vespalib::string getName(int error);
};

}

