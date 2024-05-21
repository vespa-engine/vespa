# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set(TOTAL_SHARDS 5)
math(EXPR MAX_SHARD_INDEX "${TOTAL_SHARDS} - 1")
foreach(SHARD_INDEX RANGE ${MAX_SHARD_INDEX})
    string(REGEX MATCH "...$" FMT_SHARD_INDEX "00" ${SHARD_INDEX})
    vespa_add_test(
        NAME searchcore_persistenceconformance_test_app_shard_${FMT_SHARD_INDEX}
        COMMAND searchcore_persistenceconformance_test_app
        ENVIRONMENT "VESPA_LOG_TARGET=file:vlog_shard_${FMT_SHARD_INDEX}.txt;GTEST_SHARD_INDEX=${SHARD_INDEX};GTEST_TOTAL_SHARDS=${TOTAL_SHARDS}"
        COST 100
    )
endforeach()
