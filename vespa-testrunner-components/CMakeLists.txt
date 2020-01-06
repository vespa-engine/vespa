# Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_java_artifact(vespa-testrunner-components)
install_fat_java_artifact(vespa-testrunner-components)
install_config_definition(src/main/resources/configdefinitions/test-runner.def com.yahoo.vespa.hosted.testrunner.test-runner.def)
