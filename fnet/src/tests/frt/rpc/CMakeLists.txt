# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_invoke_test_app TEST
    SOURCES
    invoke.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_invoke_test_app COMMAND fnet_invoke_test_app)
vespa_add_test(NAME fnet_invoke_test_app_xor COMMAND fnet_invoke_test_app ENVIRONMENT "CRYPTOENGINE=xor")
vespa_add_test(NAME fnet_invoke_test_app_tls COMMAND fnet_invoke_test_app ENVIRONMENT "CRYPTOENGINE=tls")
vespa_add_test(NAME fnet_invoke_test_app_tls_maybe_yes COMMAND fnet_invoke_test_app ENVIRONMENT "CRYPTOENGINE=tls_maybe_yes")
vespa_add_test(NAME fnet_invoke_test_app_tls_maybe_no COMMAND fnet_invoke_test_app ENVIRONMENT "CRYPTOENGINE=tls_maybe_no")
vespa_add_executable(fnet_detach_return_invoke_test_app TEST
    SOURCES
    detach_return_invoke.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_detach_return_invoke_test_app COMMAND fnet_detach_return_invoke_test_app)
vespa_add_executable(fnet_session_test_app TEST
    SOURCES
    session.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_session_test_app COMMAND fnet_session_test_app)
vespa_add_test(NAME fnet_session_test_app_xor COMMAND fnet_session_test_app ENVIRONMENT "CRYPTOENGINE=xor")
vespa_add_test(NAME fnet_session_test_app_tls COMMAND fnet_session_test_app ENVIRONMENT "CRYPTOENGINE=tls")
vespa_add_test(NAME fnet_session_test_app_tls_maybe_yes COMMAND fnet_session_test_app ENVIRONMENT "CRYPTOENGINE=tls_maybe_yes")
vespa_add_test(NAME fnet_session_test_app_tls_maybe_no COMMAND fnet_session_test_app ENVIRONMENT "CRYPTOENGINE=tls_maybe_no")
vespa_add_executable(fnet_sharedblob_test_app TEST
    SOURCES
    sharedblob.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_sharedblob_test_app COMMAND fnet_sharedblob_test_app)
