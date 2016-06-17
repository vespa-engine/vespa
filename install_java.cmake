function(install_java_artifact NAME)
    install(FILES "${NAME}/target/${NAME}.jar" DESTINATION lib/jars/)
    if (IS_DIRECTORY "${NAME}/target/dependency")
        install(DIRECTORY "${NAME}/target/dependency" DESTINATION lib/jars/ FILES_MATCHING PATTERN "*.jar")
    endif()
endfunction()

function(install_fat_java_artifact NAME)
    install(FILES "${NAME}/target/${NAME}-jar-with-dependencies.jar" DESTINATION lib/jars/)
endfunction()

install_java_artifact(document)
install_java_artifact(searchlib)
install_java_artifact(vespajlib)

install_fat_java_artifact(application-preprocessor)
install_fat_java_artifact(component)
install_fat_java_artifact(config-bundle)
install_fat_java_artifact(config-model-api)
install_fat_java_artifact(config-model)
install_fat_java_artifact(config-provisioning)
install_fat_java_artifact(configdefinitions)
install_fat_java_artifact(container-disc)
install_fat_java_artifact(container-jersey2)
install_fat_java_artifact(container-search-and-docproc)
install_fat_java_artifact(defaults)
install_fat_java_artifact(docprocs)
install_fat_java_artifact(jdisc_core)
install_fat_java_artifact(jdisc_http_service)
install_fat_java_artifact(persistence)
install_fat_java_artifact(simplemetrics)
install_fat_java_artifact(standalone-container)
install_fat_java_artifact(vespaclient-container-plugin)

vespa_install_script(jdisc_core/src/main/perl/jdisc_logfmt bin)
install(FILES jdisc_core/src/main/perl/jdisc_logfmt.1 DESTINATION man/man1)

install(FILES
    chain/src/main/resources/configdefinitions/chains.def
    container-accesslogging/src/main/resources/configdefinitions/access-log.def
    container-core/src/main/resources/configdefinitions/application-metadata.def
    container-core/src/main/resources/configdefinitions/container-document.def
    container-core/src/main/resources/configdefinitions/container-http.def
    container-core/src/main/resources/configdefinitions/diagnostics.def
    container-core/src/main/resources/configdefinitions/health-monitor.def
    container-core/src/main/resources/configdefinitions/http-filter.def
    container-core/src/main/resources/configdefinitions/metrics-presentation.def
    container-core/src/main/resources/configdefinitions/mockservice.def
    container-core/src/main/resources/configdefinitions/qr.def
    container-core/src/main/resources/configdefinitions/qr-logging.def
    container-core/src/main/resources/configdefinitions/qr-searchers.def
    container-core/src/main/resources/configdefinitions/qr-templates.def
    container-core/src/main/resources/configdefinitions/servlet-config.def
    container-core/src/main/resources/configdefinitions/threadpool.def
    container-core/src/main/resources/configdefinitions/vip-status.def
    container-disc/src/main/resources/configdefinitions/container.jdisc.config.http-server.def
    container-disc/src/main/resources/configdefinitions/jdisc-bindings.def
    container-disc/src/main/resources/configdefinitions/jersey-connection.def
    container-disc/src/main/resources/configdefinitions/jersey-init.def
    container-disc/src/main/resources/configdefinitions/jersey-web-app-pool.def
    container-disc/src/main/resources/configdefinitions/metric-defaults.def
    container-disc/src/main/resources/configdefinitions/port-overrides.def
    container-disc/src/main/resources/configdefinitions/score-board.def
    container-di/src/main/resources/configdefinitions/bundles.def
    container-di/src/main/resources/configdefinitions/components.def
    container-di/src/main/resources/configdefinitions/jersey-bundles.def
    container-di/src/main/resources/configdefinitions/jersey-injection.def
    container-messagebus/src/main/resources/configdefinitions/container-mbus.def
    container-messagebus/src/main/resources/configdefinitions/session.def
    container-search-and-docproc/src/main/resources/configdefinitions/application-userdata.def
    container-search/src/main/resources/configdefinitions/cluster.def
    container-search/src/main/resources/configdefinitions/documentdb-info.def
    container-search/src/main/resources/configdefinitions/emulation.def
    container-search/src/main/resources/configdefinitions/federation.def
    container-search/src/main/resources/configdefinitions/fs4.def
    container-search/src/main/resources/configdefinitions/index-info.def
    container-search/src/main/resources/configdefinitions/keyvalue.def
    container-search/src/main/resources/configdefinitions/legacy-emulation.def
    container-search/src/main/resources/configdefinitions/lowercasing.def
    container-search/src/main/resources/configdefinitions/measure-qps.def
    container-search/src/main/resources/configdefinitions/page-templates.def
    container-search/src/main/resources/configdefinitions/provider.def
    container-search/src/main/resources/configdefinitions/qr-binary-cache.def
    container-search/src/main/resources/configdefinitions/qr-binary-cache-region.def
    container-search/src/main/resources/configdefinitions/qr-monitor.def
    container-search/src/main/resources/configdefinitions/qr-quotetable.def
    container-search/src/main/resources/configdefinitions/qr-start.def
    container-search/src/main/resources/configdefinitions/query-profiles.def
    container-search/src/main/resources/configdefinitions/rate-limiting.def
    container-search/src/main/resources/configdefinitions/resolvers.def
    container-search/src/main/resources/configdefinitions/rewrites.def
    container-search/src/main/resources/configdefinitions/searchchain-forward.def
    container-search/src/main/resources/configdefinitions/search-nodes.def
    container-search/src/main/resources/configdefinitions/search-with-renderer-handler.def
    container-search/src/main/resources/configdefinitions/semantic-rules.def
    container-search/src/main/resources/configdefinitions/strict-contracts.def
    container-search/src/main/resources/configdefinitions/timing-searcher.def
    docproc/src/main/resources/configdefinitions/docproc.def
    docproc/src/main/resources/configdefinitions/schemamapping.def
    docproc/src/main/resources/configdefinitions/splitter-joiner-document-processor.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.client.http-client.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.connector.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.server.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.servlet-paths.def
    jdisc_jmx_metrics/src/main/resources/configdefinitions/jmx-metric.def
    persistence/src/main/resources/configdefinitions/persistence-rpc.def
    simplemetrics/src/main/resources/configdefinitions/manager.def
    statistics/src/main/resources/configdefinitions/statistics.def
    vespaclient-core/src/main/resources/configdefinitions/feeder.def
    vespaclient-core/src/main/resources/configdefinitions/spooler.def
    DESTINATION var/db/vespa/config_server/serverdb/classes)
