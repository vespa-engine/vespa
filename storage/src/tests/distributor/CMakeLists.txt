# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_distributor_gtest_runner_app TEST
    SOURCES
    blockingoperationstartertest.cpp
    btree_bucket_database_test.cpp
    bucket_db_prune_elision_test.cpp
    bucketdatabasetest.cpp
    bucketdbmetricupdatertest.cpp
    bucketgctimecalculatortest.cpp
    bucketstateoperationtest.cpp
    check_condition_test.cpp
    distributor_bucket_space_repo_test.cpp
    distributor_bucket_space_test.cpp
    distributor_host_info_reporter_test.cpp
    distributor_message_sender_stub.cpp
    distributor_stripe_pool_test.cpp
    distributor_stripe_test.cpp
    distributor_stripe_test_util.cpp
    externaloperationhandlertest.cpp
    garbagecollectiontest.cpp
    getoperationtest.cpp
    idealstatemanagertest.cpp
    joinbuckettest.cpp
    maintenancemocks.cpp
    maintenanceschedulertest.cpp
    mergelimitertest.cpp
    mergeoperationtest.cpp
    multi_thread_stripe_access_guard_test.cpp
    newest_replica_test.cpp
    node_supported_features_repo_test.cpp
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
    top_level_bucket_db_updater_test.cpp
    top_level_distributor_test.cpp
    top_level_distributor_test_util.cpp
    twophaseupdateoperationtest.cpp
    updateoperationtest.cpp
    visitoroperationtest.cpp
    DEPENDS
    storage_testcommon
    storage_testhostreporter
    vespa_storage
    GTest::gmock_main
)

set(TOTAL_SHARDS 5)
math(EXPR MAX_SHARD_INDEX "${TOTAL_SHARDS} - 1")
foreach(SHARD_INDEX RANGE ${MAX_SHARD_INDEX})
    string(REGEX MATCH "...$" FMT_SHARD_INDEX "00" ${SHARD_INDEX})
    vespa_add_test(
        NAME storage_distributor_gtest_runner_app_${FMT_SHARD_INDEX}
        COMMAND storage_distributor_gtest_runner_app
        ENVIRONMENT "GTEST_SHARD_INDEX=${SHARD_INDEX};GTEST_TOTAL_SHARDS=${TOTAL_SHARDS}"
        COST 350
    )
endforeach()
