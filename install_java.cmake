function(install_java_artifact NAME)
    install(FILES "${NAME}/target/${NAME}.jar" DESTINATION lib/jars/)
endfunction()

function(install_java_artifact_dependencies NAME)
    install(DIRECTORY "${NAME}/target/dependency/" DESTINATION lib/jars FILES_MATCHING PATTERN "*.jar")
endfunction()

function(install_fat_java_artifact NAME)
    install(FILES "${NAME}/target/${NAME}-jar-with-dependencies.jar" DESTINATION lib/jars/)
endfunction()

install_java_artifact(config-model-fat)
install_java_artifact(document)
install_java_artifact(jdisc_jetty)
install_java_artifact_dependencies(jdisc_jetty)
install_java_artifact_dependencies(vespa_jersey2)
install_java_artifact(vespajlib)

install_fat_java_artifact(application-preprocessor)
install_fat_java_artifact(clustercontroller-apps)
install_fat_java_artifact(clustercontroller-apputil)
install_fat_java_artifact(clustercontroller-utils)
install_fat_java_artifact(clustercontroller-core)
install_fat_java_artifact(component)
install_fat_java_artifact(config-bundle)
install_fat_java_artifact(config-model-api)
install_fat_java_artifact(config-model)
install_fat_java_artifact(config-provisioning)
install_fat_java_artifact(config-proxy)
install_fat_java_artifact(configdefinitions)
install_fat_java_artifact(configserver)
install_fat_java_artifact(container-disc)
install_fat_java_artifact(container-jersey2)
install_fat_java_artifact(container-search-and-docproc)
install_fat_java_artifact(defaults)
install_fat_java_artifact(docprocs)
install_fat_java_artifact(jdisc_core)
install_fat_java_artifact(jdisc_http_service)
install_fat_java_artifact(logserver)
install_fat_java_artifact(node-repository)
install_fat_java_artifact(orchestrator)
install_fat_java_artifact(persistence)
install_fat_java_artifact(searchlib)
install_fat_java_artifact(simplemetrics)
install_fat_java_artifact(standalone-container)
install_fat_java_artifact(vespa-http-client)
install_fat_java_artifact(vespaclient-container-plugin)
install_fat_java_artifact(vespaclient-java)
install_fat_java_artifact(zkfacade)

vespa_install_script(application-preprocessor/src/main/sh/vespa-preprocess-application bin)
vespa_install_script(jdisc_core/src/main/perl/vespa-jdisc-logfmt bin)
vespa_install_script(config-proxy/src/main/sh/vespa-config-ctl bin)
vespa_install_script(config-proxy/src/main/sh/vespa-config-loadtester bin)
vespa_install_script(config-proxy/src/main/sh/vespa-config-verification bin)
vespa_install_script(config-model/src/main/perl/vespa-deploy bin)
vespa_install_script(config-model/src/main/perl/vespa-deploy-application bin)
vespa_install_script(config-model/src/main/perl/vespa-expand-config.pl bin)
vespa_install_script(config-model/src/main/perl/vespa-replicate-log-stream bin)
vespa_install_script(config-model/src/main/sh/vespa-validate-application bin)
vespa_install_script(container-disc/src/main/sh/vespa-start-container-daemon.sh vespa-start-container-daemon bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-document-statistics.sh vespa-document-statistics bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-stat.sh vespa-stat bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-query-profile-dump-tool.sh vespa-query-profile-dump-tool bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-summary-benchmark.sh vespa-summary-benchmark bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-destination.sh vespa-destination bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-feeder.sh vespa-feeder bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-get.sh vespa-get bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-visit.sh vespa-visit bin)
vespa_install_script(vespaclient-java/src/main/sh/vespa-visit-target.sh vespa-visit-target bin)

vespa_install_script(logserver/bin/logserver-start.sh logserver-start bin)

install(DIRECTORY config-model/src/main/resources/schema DESTINATION share/vespa PATTERN ".gitignore" EXCLUDE)
install(DIRECTORY config-model/src/main/resources/schema DESTINATION share/vespa/schema/version/6.x PATTERN ".gitignore" EXCLUDE)

install(FILES jdisc_core/src/main/perl/vespa-jdisc-logfmt.1 DESTINATION man/man1)

install(FILES
    config-model-fat/src/main/resources/config-models.xml
    node-repository/src/main/config/node-repository.xml
    DESTINATION conf/configserver-app)

install(FILES
    chain/src/main/resources/configdefinitions/chains.def
    config-provisioning/src/main/resources/configdefinitions/flavors.def
    configdefinitions/src/vespa/application-id.def
    configdefinitions/src/vespa/attributes.def
    configdefinitions/src/vespa/cluster-info.def
    configdefinitions/src/vespa/cluster-list.def
    configdefinitions/src/vespa/configserver.def
    configdefinitions/src/vespa/fleetcontroller.def
    configdefinitions/src/vespa/ilscripts.def
    configdefinitions/src/vespa/indexschema.def
    configdefinitions/src/vespa/lb-services.def
    configdefinitions/src/vespa/load-type.def
    configdefinitions/src/vespa/messagetyperouteselectorpolicy.def
    configdefinitions/src/vespa/model.def
    configdefinitions/src/vespa/persistence.def
    configdefinitions/src/vespa/rank-profiles.def
    configdefinitions/src/vespa/routing-provider.def
    configdefinitions/src/vespa/routing.def
    configdefinitions/src/vespa/sentinel.def
    configdefinitions/src/vespa/slobroks.def
    configdefinitions/src/vespa/specialtokens.def
    configdefinitions/src/vespa/stor-devices.def
    configdefinitions/src/vespa/stor-distribution.def
    configdefinitions/src/vespa/stor-filestor.def
    configdefinitions/src/vespa/stor-memfilepersistence.def
    configdefinitions/src/vespa/summary.def
    configdefinitions/src/vespa/summarymap.def
    configdefinitions/src/vespa/upgrading.def
    configdefinitions/src/vespa/ymon.def
    configdefinitions/src/vespa/zookeeper-server.def
    configdefinitions/src/vespa/zookeepers.def
    configdefinitions/src/vespa/dispatch.def
    container-accesslogging/src/main/resources/configdefinitions/access-log.def
    container-core/src/main/resources/configdefinitions/application-metadata.def
    container-core/src/main/resources/configdefinitions/container-document.def
    container-core/src/main/resources/configdefinitions/container-http.def
    container-core/src/main/resources/configdefinitions/diagnostics.def
    container-core/src/main/resources/configdefinitions/health-monitor.def
    container-core/src/main/resources/configdefinitions/http-filter.def
    container-core/src/main/resources/configdefinitions/metrics-presentation.def
    container-core/src/main/resources/configdefinitions/mockservice.def
    container-core/src/main/resources/configdefinitions/qr-logging.def
    container-core/src/main/resources/configdefinitions/qr-searchers.def
    container-core/src/main/resources/configdefinitions/qr-templates.def
    container-core/src/main/resources/configdefinitions/qr.def
    container-core/src/main/resources/configdefinitions/servlet-config.def
    container-core/src/main/resources/configdefinitions/threadpool.def
    container-core/src/main/resources/configdefinitions/vip-status.def
    container-di/src/main/resources/configdefinitions/bundles.def
    container-di/src/main/resources/configdefinitions/components.def
    container-di/src/main/resources/configdefinitions/jersey-bundles.def
    container-di/src/main/resources/configdefinitions/jersey-injection.def
    container-disc/src/main/resources/configdefinitions/container.jdisc.config.http-server.def
    container-disc/src/main/resources/configdefinitions/jdisc-bindings.def
    container-disc/src/main/resources/configdefinitions/jersey-connection.def
    container-disc/src/main/resources/configdefinitions/jersey-init.def
    container-disc/src/main/resources/configdefinitions/jersey-web-app-pool.def
    container-disc/src/main/resources/configdefinitions/metric-defaults.def
    container-disc/src/main/resources/configdefinitions/score-board.def
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
    container-search/src/main/resources/configdefinitions/qr-binary-cache-region.def
    container-search/src/main/resources/configdefinitions/qr-binary-cache.def
    container-search/src/main/resources/configdefinitions/qr-monitor.def
    container-search/src/main/resources/configdefinitions/qr-quotetable.def
    container-search/src/main/resources/configdefinitions/qr-start.def
    container-search/src/main/resources/configdefinitions/query-profiles.def
    container-search/src/main/resources/configdefinitions/rate-limiting.def
    container-search/src/main/resources/configdefinitions/resolvers.def
    container-search/src/main/resources/configdefinitions/rewrites.def
    container-search/src/main/resources/configdefinitions/search-nodes.def
    container-search/src/main/resources/configdefinitions/search-with-renderer-handler.def
    container-search/src/main/resources/configdefinitions/searchchain-forward.def
    container-search/src/main/resources/configdefinitions/semantic-rules.def
    container-search/src/main/resources/configdefinitions/strict-contracts.def
    container-search/src/main/resources/configdefinitions/timing-searcher.def
    docproc/src/main/resources/configdefinitions/docproc.def
    docproc/src/main/resources/configdefinitions/schemamapping.def
    docproc/src/main/resources/configdefinitions/splitter-joiner-document-processor.def
    document/src/vespa/document/config/documentmanager.def
    document/src/vespa/document/config/documenttypes.def
    documentapi/src/main/resources/configdefinitions/documentrouteselectorpolicy.def
    fileacquirer/src/main/resources/configdefinitions/filedistributorrpc.def
    filedistribution/src/vespa/filedistribution/distributor/filedistributor.def
    filedistribution/src/vespa/filedistribution/model/filereferences.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.client.http-client.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.connector.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.server.def
    jdisc_http_service/src/main/resources/configdefinitions/jdisc.http.servlet-paths.def
    logd/src/main/resources/configdefinitions/logd.def
    messagebus/src/main/config/messagebus.def
    metrics/src/vespa/metrics/metricsmanager.def
    orchestrator/src/main/resources/configdefinitions/orchestrator.def
    persistence/src/main/resources/configdefinitions/persistence-rpc.def
    searchcore/src/vespa/searchcore/config/fdispatchrc.def
    searchcore/src/vespa/searchcore/config/partitions.def
    searchcore/src/vespa/searchcore/config/proton.def
    searchcore/src/vespa/searchcore/config/ranking-constants.def
    searchlib/src/vespa/searchlib/config/translogserver.def
    searchsummary/src/vespa/searchsummary/config/juniperrc.def
    simplemetrics/src/main/resources/configdefinitions/manager.def
    statistics/src/main/resources/configdefinitions/statistics.def
    storage/src/vespa/storage/bucketdb/stor-bucket-init.def
    storage/src/vespa/storage/bucketdb/stor-bucketdb.def
    storage/src/vespa/storage/config/rpc-provider.def
    storage/src/vespa/storage/config/stor-bouncer.def
    storage/src/vespa/storage/config/stor-bucketmover.def
    storage/src/vespa/storage/config/stor-communicationmanager.def
    storage/src/vespa/storage/config/stor-distributormanager.def
    storage/src/vespa/storage/config/stor-integritychecker.def
    storage/src/vespa/storage/config/stor-messageforwarder.def
    storage/src/vespa/storage/config/stor-opslogger.def
    storage/src/vespa/storage/config/stor-prioritymapping.def
    storage/src/vespa/storage/config/stor-server.def
    storage/src/vespa/storage/config/stor-status.def
    storage/src/vespa/storage/config/stor-visitordispatcher.def
    storage/src/vespa/storage/visiting/stor-visitor.def
    vsm/src/vespa/vsm/config/vsm.def
    vsm/src/vespa/vsm/config/vsmfields.def
    vsm/src/vespa/vsm/config/vsmsummary.def
    vespaclient-core/src/main/resources/configdefinitions/feeder.def
    vespaclient-core/src/main/resources/configdefinitions/spooler.def
    docker-api/src/main/resources/configdefinitions/docker.def
    DESTINATION var/db/vespa/config_server/serverdb/classes)
