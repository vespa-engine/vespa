// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.custom.HostResources;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.flags.json.FlagData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.vespa.flags.FlagsTest.testGeneric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class PermanentFlagsTest {
    @Test
    public void testSharedHostFlag() {
        SharedHost sharedHost = new SharedHost(List.of(new HostResources(4.0, 16.0, 50.0, 0.3,
                                                                         "fast", "local", "admin",
                                                                         10, "x86_64")));
        testGeneric(PermanentFlags.SHARED_HOST, sharedHost);
    }

    @Test
    void wantedDockerTagValidation() {
        IllegalArgumentException illegalArgumentException = assertThrows(
                IllegalArgumentException.class,
                () -> wantedDockerTagFlagData("do.not.work").validate(PermanentFlags.WANTED_DOCKER_TAG.serializer()));
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid value: 'do.not.work'"));

        wantedDockerTagFlagData("a").validate(PermanentFlags.WANTED_DOCKER_TAG.serializer());
        wantedDockerTagFlagData("8.1.2").validate(PermanentFlags.WANTED_DOCKER_TAG.serializer());
    }

    private static FlagData wantedDockerTagFlagData(String value) {
        return FlagData.deserialize("""
                {
                    "id": "wanted-docker-tag",
                    "rules": [
                       {
                            "value": "%s"
                        }
                    ]
                }
                """.formatted(value));
    }

}
