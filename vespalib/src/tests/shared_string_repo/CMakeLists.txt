# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_shared_string_repo_test_app TEST
    SOURCES
    shared_string_repo_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(
  NAME vespalib_shared_string_repo_test_app
  COMMAND vespalib_shared_string_repo_test_app
)
vespa_add_test(
  NAME vespalib_shared_string_repo_test_app_no_reclaim
  COMMAND vespalib_shared_string_repo_test_app
  ENVIRONMENT "VESPA_SHARED_STRING_REPO_NO_RECLAIM=true"
)
