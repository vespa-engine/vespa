package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JobQueueSerializerTest {

    // TODO: Remove when this legacy is gone.
    @Test
    public void testLegacyDeserialisation() {
        new JobQueueSerializer().fromJson("[\"tenant:app:default\", \"tenant:papp:default\"]".getBytes());
    }

    @Test
    public void testSerialization() {
        ApplicationId appId1 = ApplicationId.from("tenant", "app1", "default");
        ApplicationId appId2 = ApplicationId.from("tenant", "app2", "default");

        List<DeploymentQueue.Triggering> original = Arrays.asList(new DeploymentQueue.Triggering(appId1, Instant.EPOCH, true, false, false),
                                                                  new DeploymentQueue.Triggering(appId2, Instant.now(), false, false, true));

        JobQueueSerializer serializer = new JobQueueSerializer();

        assertEquals(original, serializer.fromJson(serializer.toJson(original)));
    }

}
