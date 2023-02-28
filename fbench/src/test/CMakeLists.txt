# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fbench_httpclient_splitstring_app TEST
    SOURCES
    httpclient_splitstring.cpp
    DEPENDS
    fbench_util
    fbench_httpclient
)
vespa_add_test(NAME fbench_httpclient_splitstring_app COMMAND fbench_httpclient_splitstring_app)
vespa_add_executable(fbench_httpclient_app
    SOURCES
    httpclient.cpp
    DEPENDS
    fbench_util
    fbench_httpclient
)
vespa_add_executable(fbench_filereader_app TEST
    SOURCES
    filereader.cpp
    DEPENDS
    fbench_util
)
vespa_add_test(NAME fbench_filereader_app COMMAND fbench_filereader_app)
vespa_add_executable(fbench_clientstatus_app TEST
    SOURCES
    clientstatus.cpp
    DEPENDS
    fbench_util
)
vespa_add_test(NAME fbench_clientstatus_app COMMAND fbench_clientstatus_app)
