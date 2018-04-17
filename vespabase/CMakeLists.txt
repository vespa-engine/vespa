# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_install_script(src/start-cbinaries.sh vespa-get-config bin)
vespa_install_script(src/start-cbinaries.sh vespa-verify-ranksetup bin)
vespa_install_script(src/start-cbinaries.sh vespa-config-status bin)
vespa_install_script(src/start-cbinaries.sh vespa-doclocator bin)
vespa_install_script(src/start-cbinaries.sh vespa-model-inspect bin)
vespa_install_script(src/start-cbinaries.sh vespa-proton-cmd bin)
vespa_install_script(src/start-cbinaries.sh vespa-route bin)
vespa_install_script(src/start-cbinaries.sh vespa-transactionlog-inspect bin)
vespa_install_script(src/start-cbinaries.sh vespa-vds-disktool bin)
vespa_install_script(src/start-cbinaries.sh vespa-distributord sbin)
vespa_install_script(src/start-cbinaries.sh vespa-dispatch sbin)
vespa_install_script(src/start-cbinaries.sh vespa-proton sbin)
vespa_install_script(src/start-cbinaries.sh vespa-storaged sbin)

vespa_install_script(src/start-tool.sh vespa-start-tool.sh bin)

vespa_install_script(src/rhel-prestart.sh vespa-prestart.sh bin)
vespa_install_script(src/common-env.sh common-env.sh libexec/vespa)
vespa_install_script(src/start-vespa-base.sh start-vespa-base.sh libexec/vespa)
vespa_install_script(src/stop-vespa-base.sh stop-vespa-base.sh libexec/vespa)
vespa_install_script(src/vespa-start-configserver.sh vespa-start-configserver bin)
vespa_install_script(src/vespa-start-services.sh vespa-start-services bin)
vespa_install_script(src/vespa-stop-configserver.sh vespa-stop-configserver bin)
vespa_install_script(src/vespa-stop-services.sh vespa-stop-services bin)

configure_file(src/vespa.service.in src/vespa.service @ONLY)
configure_file(src/vespa-configserver.service.in src/vespa-configserver.service @ONLY)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/src/vespa.service ${CMAKE_CURRENT_BINARY_DIR}/src/vespa-configserver.service DESTINATION etc/systemd/system)

install(FILES src/Defaults.pm DESTINATION lib/perl5/site_perl/Yahoo/Vespa)

configure_file(conf/default-env.txt.in conf/default-env.txt @ONLY)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/conf/default-env.txt DESTINATION conf/vespa)

