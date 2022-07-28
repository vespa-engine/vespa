// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.test.TotalOrderTester;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.yahoo.config.provision.ApplicationId.from;
import static com.yahoo.config.provision.ApplicationId.global;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ulf Lilleengen
 * @author vegard
 * @since 5.1
 */
public class ApplicationIdTest {

    @Test
    void require_that_application_id_is_set() {
        ApplicationId app = applicationId("application");
        assertEquals("application", app.application().value());
        app = from("tenant", "application", "instance");
        assertEquals("tenant", app.tenant().value());
        assertEquals("application", app.application().value());
        assertEquals("instance", app.instance().value());
    }

    @Test
    void require_that_equals_and_hashcode_behaves_correctly() {
        assertEquals(Set.of(from("tenant1", "name1", "instance1"),
                                                      from("tenant2", "name1", "instance1"),
                                                      from("tenant1", "name2", "instance1"),
                                                      from("tenant1", "name1", "instance2"),
                                                      applicationId("name1"),
                                                      applicationId("name2")),
                               new HashSet<>(List.of(from("tenant1", "name1", "instance1"),
                                                      from("tenant2", "name1", "instance1"),
                                                      from("tenant1", "name2", "instance1"),
                                                      from("tenant1", "name1", "instance2"),
                                                      applicationId("name1"),
                                                      applicationId("name2"))));
    }

    @Test
    void require_that_value_format_is_correct() {
        ApplicationId id1 = applicationId("foo");
        ApplicationId id2 = applicationId("bar");
        ApplicationId id3 = from("tenant", "baz", "bim");
        assertEquals("default:foo:default", id1.serializedForm());
        assertEquals("default:bar:default", id2.serializedForm());
        assertEquals("tenant:baz:bim", id3.serializedForm());
    }

    @Test
    void require_string_formats_are_correct() {
        ApplicationId id1 = applicationId("foo");
        ApplicationId id2 = from("bar", "baz", "default");
        ApplicationId id3 = from("tenant", "baz", "bim");
        assertEquals("default.foo", id1.toShortString());
        assertEquals("default.foo.default", id1.toFullString());
        assertEquals("bar.baz", id2.toShortString());
        assertEquals("bar.baz.default", id2.toFullString());
        assertEquals("tenant.baz.bim", id3.toShortString());
        assertEquals("tenant.baz.bim", id3.toFullString());
    }

    @Test
    void require_that_idstring_can_be_parsed() {
        ApplicationId id = ApplicationId.fromSerializedForm("ten:foo:bim");
        assertEquals("ten", id.tenant().value());
        assertEquals("foo", id.application().value());
        assertEquals("bim", id.instance().value());
    }

    @Test
    void require_that_invalid_idstring_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.fromSerializedForm("foo:baz");
        });
    }

    @Test
    void require_that_defaults_are_given() {
        ApplicationId id1 = applicationId("foo");
        assertEquals("default", id1.tenant().value());
        assertEquals("default", id1.instance().value());
    }

    @Test
    void require_that_compare_to_is_correct() {
        new TotalOrderTester<ApplicationId>()
                               .theseObjects(from("tenant1", "name1", "instance1"),
                                                      from("tenant1", "name1", "instance1"))
                               .areLessThan(from("tenant2", "name1", "instance1"))
                               .areLessThan(from("tenant2", "name2", "instance1"))
                               .areLessThan(from("tenant2", "name2", "instance2"))
                               .testOrdering();
    }

    @Test
    void require_that_instance_from_config_is_correct() {
        ApplicationIdConfig.Builder builder = new ApplicationIdConfig.Builder();
        builder.tenant("a");
        builder.application("b");
        builder.instance("c");
        ApplicationId applicationId = from(new ApplicationIdConfig(builder));
        assertEquals("a", applicationId.tenant().value());
        assertEquals("b", applicationId.application().value());
        assertEquals("c", applicationId.instance().value());
    }

    @Test
    void require_that_global_is_special() {
        assertEquals(global(), global());
        assertNotEquals(global(), from("hosted-vespa", "routing", "default"));
        assertEquals(global().serializedForm(), from("hosted-vespa", "routing", "default").serializedForm());
    }

    private ApplicationId applicationId(String applicationName) {
        return from(TenantName.defaultName(), ApplicationName.from(applicationName), InstanceName.defaultName());
    }

}
