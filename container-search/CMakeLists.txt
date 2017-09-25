# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/cluster.def RENAME search.config.cluster.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/documentdb-info.def RENAME prelude.fastsearch.documentdb-info.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/emulation.def RENAME prelude.emulation.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/federation.def RENAME search.federation.federation.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/fs4.def RENAME container.search.fs4.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/index-info.def RENAME search.config.index-info.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/keyvalue.def RENAME prelude.searcher.keyvalue.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/legacy-emulation.def RENAME container.search.legacy-emulation.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/lowercasing.def RENAME search.querytransform.lowercasing.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/measure-qps.def RENAME search.statistics.measure-qps.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/page-templates.def RENAME search.pagetemplates.page-templates.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/provider.def RENAME search.federation.provider.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-binary-cache-region.def RENAME search.cache.qr-binary-cache-region.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-binary-cache.def RENAME search.cache.qr-binary-cache.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-monitor.def RENAME prelude.cluster.qr-monitor.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-quotetable.def RENAME prelude.searcher.qr-quotetable.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-start.def RENAME search.config.qr-start.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/query-profiles.def RENAME search.query.profile.config.query-profiles.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/rate-limiting.def RENAME search.config.rate-limiting.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/resolvers.def RENAME search.pagetemplates.resolvers.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/rewrites.def RENAME search.query.rewrite.rewrites.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/search-nodes.def RENAME search.config.dispatchprototype.search-nodes.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/search-with-renderer-handler.def RENAME search.handler.search-with-renderer-handler.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/searchchain-forward.def RENAME search.federation.searchchain-forward.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/semantic-rules.def RENAME prelude.semantics.semantic-rules.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/strict-contracts.def RENAME search.federation.strict-contracts.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/timing-searcher.def RENAME search.statistics.timing-searcher.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
