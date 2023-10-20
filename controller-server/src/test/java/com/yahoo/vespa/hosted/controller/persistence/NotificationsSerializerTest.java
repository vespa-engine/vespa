// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class NotificationsSerializerTest {

    @Test
    void serialization_test() throws IOException {
        NotificationsSerializer serializer = new NotificationsSerializer();
        TenantName tenantName = TenantName.from("tenant1");
        var mail = Notification.MailContent.fromTemplate("my-template").subject("My mail subject")
                .with("string-param", "string-value").with("list-param", List.of("elem1", "elem2")).build();
        List<Notification> notifications = List.of(
                new Notification(Instant.ofEpochSecond(1234),
                        Notification.Type.applicationPackage,
                        Notification.Level.warning,
                        NotificationSource.from(TenantAndApplicationId.from(tenantName.value(), "app1")),
                        List.of("Something something deprecated...")),
                new Notification(Instant.ofEpochSecond(2345),
                        Notification.Type.deployment,
                        Notification.Level.error,
                        NotificationSource.from(new RunId(ApplicationId.from(tenantName.value(), "app1", "instance1"), DeploymentContext.systemTest, 12)),
                        "Failed to deploy", List.of("Node allocation failure"),
                        Optional.of(mail)));

        Slime serialized = serializer.toSlime(notifications);
        assertEquals("{\"notifications\":[" +
                "{" +
                "\"at\":1234000," +
                "\"type\":\"applicationPackage\"," +
                "\"level\":\"warning\"," +
                "\"title\":\"\"," +
                "\"messages\":[\"Something something deprecated...\"]," +
                "\"application\":\"app1\"" +
                "},{" +
                "\"at\":2345000," +
                "\"type\":\"deployment\"," +
                "\"level\":\"error\"," +
                "\"title\":\"Failed to deploy\"," +
                "\"messages\":[\"Node allocation failure\"]," +
                "\"application\":\"app1\"," +
                "\"instance\":\"instance1\"," +
                "\"jobId\":\"test.us-east-1\"," +
                "\"runNumber\":12," +
                "\"mail-template\":\"my-template\"," +
                "\"mail-subject\":\"My mail subject\"," +
                "\"mail-params\":{\"list-param\":[\"elem1\",\"elem2\"],\"string-param\":\"string-value\"}" +
                "}]}", new String(SlimeUtils.toJsonBytes(serialized)));

        List<Notification> deserialized = serializer.fromSlime(tenantName, serialized);
        assertEquals(notifications, deserialized);
    }

}
