# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(configserver)

vespa_install_script(src/main/sh/vespa-configserver-remove-state bin)
vespa_install_script(src/main/sh/ping-configserver libexec/vespa)
vespa_install_script(src/main/sh/start-configserver libexec/vespa)
vespa_install_script(src/main/sh/start-logd libexec/vespa)
vespa_install_script(src/main/sh/stop-configserver libexec/vespa)

install(DIRECTORY src/main/resources/logd DESTINATION conf)
install(DIRECTORY src/main/resources/configserver-app DESTINATION conf)

install(CODE "execute_process(COMMAND mkdir -p \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components)")
install(CODE "execute_process(COMMAND mkdir -p \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/config-models)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/config-model-fat.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/config-model-fat.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/configserver-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/configserver.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/orchestrator-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/orchestrator.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/service-monitor-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/service-monitor.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/application-model-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/application-model.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/node-repository-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/node-repository.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/lib/jars/zkfacade-jar-with-dependencies.jar \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components/zkfacade.jar)")
install(CODE "execute_process(COMMAND ln -snf \${CMAKE_INSTALL_PREFIX}/conf/configserver-app/components \$ENV{DESTDIR}/\${CMAKE_INSTALL_PREFIX}/lib/jars/config-models)")
