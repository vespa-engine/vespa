# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_distributor_gtest_runner_app TEST
    SOURCES
    blockingoperationstartertest.cpp
    btree_bucket_database_test.cpp
    bucket_db_prune_elision_test.cpp
    bucketdatabasetest.cpp
    bucketdbmetricupdatertest.cpp
    bucketdbupdatertest.cpp
    bucketgctimecalculatortest.cpp
    bucketstateoperationtest.cpp
    distributor_bucket_space_test.cpp
    distributor_host_info_reporter_test.cpp
    distributor_message_sender_stub.cpp
    distributortest.cpp
    distributortestutil.cpp
    externaloperationhandlertest.cpp
    garbagecollectiontest.cpp
    getoperationtest.cpp
    gtest_runner.cpp
    idealstatemanagertest.cpp
    joinbuckettest.cpp
    maintenanceschedulertest.cpp
    mergelimitertest.cpp
    mergeoperationtest.cpp
    nodeinfotest.cpp
    nodemaintenancestatstrackertest.cpp
    operation_sequencer_test.cpp
    operationtargetresolvertest.cpp
    ownership_transfer_safe_time_point_calculator_test.cpp
    pendingmessagetrackertest.cpp
    persistence_metrics_set_test.cpp
    putoperationtest.cpp
    read_for_write_visitor_operation_test.cpp
    removebucketoperationtest.cpp
    removelocationtest.cpp
    removeoperationtest.cpp
    simplebucketprioritydatabasetest.cpp
    simplemaintenancescannertest.cpp
    splitbuckettest.cpp
    statecheckerstest.cpp
    statoperationtest.cpp
    statusreporterdelegatetest.cpp
    throttlingoperationstartertest.cpp
    twophaseupdateoperationtest.cpp
    updateoperationtest.cpp
    visitoroperationtest.cpp
    DEPENDS
    storage_testcommon
    storage_testhostreporter
    storage_distributor
    GTest::GTest
)

vespa_add_test(
    NAME storage_distributor_gtest_runner_app
    COMMAND storage_distributor_gtest_runner_app
    COST 350
)
