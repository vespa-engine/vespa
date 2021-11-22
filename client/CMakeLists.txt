# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

add_custom_command(OUTPUT go/bin/vespa
                   COMMAND make -C go)
add_custom_target(vespa-cli ALL true
                  SOURCES go/bin/vespa)
vespa_install_script(go/bin/vespa bin)
