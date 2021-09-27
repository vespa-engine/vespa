
vespa_add_executable(eval_dense_hamming_distance_test_app TEST
    SOURCES
    dense_hamming_distance_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_dense_hamming_distance_test_app COMMAND eval_dense_hamming_distance_test_app)
