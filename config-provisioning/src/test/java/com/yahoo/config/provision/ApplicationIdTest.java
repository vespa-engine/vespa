// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import static org.junit.Assert.assertEquals;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.test.TotalOrderTester;
import org.junit.Test;
import com.google.common.testing.EqualsTester;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Ulf Lilleengen
 * @author vegard
 * @since 5.1
 */
public class ApplicationIdTest {

    ApplicationId idFrom(String tenant, String name, String instance) {
        ApplicationId.Builder b = new ApplicationId.Builder();
        b.tenant(tenant);
        b.applicationName(name);
        b.instanceName(instance);
        return b.build();
    }

    @Test
    public void require_that_application_id_is_set() {
        ApplicationId app = applicationId("application");
        assertEquals("application", app.application().value());
        app = idFrom("tenant", "application", "instance");
        assertEquals("tenant", app.tenant().value());
        assertEquals("application", app.application().value());
        assertEquals("instance", app.instance().value());
    }

    @Test
    public void require_that_equals_and_hashcode_behaves_correctly() {
        new EqualsTester()
                .addEqualityGroup(idFrom("tenant1", "name1", "instance1"),
                                  idFrom("tenant1", "name1", "instance1"))
                .addEqualityGroup(idFrom("tenant2", "name1", "instance1"))
                .addEqualityGroup(idFrom("tenant1", "name2", "instance1"))
                .addEqualityGroup(idFrom("tenant1", "name1", "instance2"))
                .addEqualityGroup(applicationId("onlyName1"))
                .addEqualityGroup(applicationId("onlyName2"))
                .testEquals();
    }

    @Test
    public void require_that_value_format_is_correct() {
        ApplicationId id1 = applicationId("foo");
        ApplicationId id2 = applicationId("bar");
        ApplicationId id3 = idFrom("tenant", "baz", "bim");
        assertThat(id1.serializedForm(), is("default:foo:default"));
        assertThat(id2.serializedForm(), is("default:bar:default"));
        assertThat(id3.serializedForm(), is("tenant:baz:bim"));
    }

    @Test
    public void require_string_formats_are_correct() {
        ApplicationId id1 = applicationId("foo");
        ApplicationId id2 = idFrom("bar", "baz", "default");
        ApplicationId id3 = idFrom("tenant", "baz", "bim");
        assertThat(id1.toShortString(), is("default.foo"));
        assertThat(id1.toFullString(), is("default.foo.default"));
        assertThat(id2.toShortString(), is("bar.baz"));
        assertThat(id2.toFullString(), is("bar.baz.default"));
        assertThat(id3.toShortString(), is("tenant.baz.bim"));
        assertThat(id3.toFullString(), is("tenant.baz.bim"));
    }

    @Test
    public void require_that_idstring_can_be_parsed() {
        ApplicationId id = ApplicationId.fromSerializedForm("ten:foo:bim");
        assertThat(id.tenant().value(), is("ten"));
        assertThat(id.application().value(), is("foo"));
        assertThat(id.instance().value(), is("bim"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_invalid_idstring_throws_exception() {
        ApplicationId.fromSerializedForm("foo:baz");
    }

    @Test
    public void require_that_defaults_are_given() {
        ApplicationId id1 = applicationId("foo");
        assertThat(id1.tenant().value(), is("default"));
        assertThat(id1.instance().value(), is("default"));
    }

    @Test
    public void require_that_compare_to_is_correct() {
        new TotalOrderTester<ApplicationId>()
                .theseObjects(idFrom("tenant1", "name1", "instance1"),
                              idFrom("tenant1", "name1", "instance1"))
                 .areLessThan(idFrom("tenant2", "name1", "instance1"))
                 .areLessThan(idFrom("tenant2", "name2", "instance1"))
                 .areLessThan(idFrom("tenant2", "name2", "instance2"))
                .testOrdering();
    }

    @Test
    public void require_that_instance_from_config_is_correct() {
        ApplicationIdConfig.Builder builder = new ApplicationIdConfig.Builder();
        builder.tenant("a");
        builder.application("b");
        builder.instance("c");
        ApplicationId applicationId = new ApplicationId(new ApplicationIdConfig(builder));
        assertEquals("a", applicationId.tenant().value());
        assertEquals("b", applicationId.application().value());
        assertEquals("c", applicationId.instance().value());
    }

    private ApplicationId applicationId(String applicationName) {
        return ApplicationId.from(TenantName.defaultName(),
                                  ApplicationName.from(applicationName), InstanceName.defaultName());
    }

}
