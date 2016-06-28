vespa_add_executable(searchlib_changevector_test_app TEST
    SOURCES
    changevector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_changevector_test_app COMMAND sh changevector_test.sh)
