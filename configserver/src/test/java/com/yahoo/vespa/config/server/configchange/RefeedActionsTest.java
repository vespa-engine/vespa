// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ServiceInfo;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static com.yahoo.vespa.config.server.configchange.Utils.*;

/**
 * @author geirst
 */
public class RefeedActionsTest {

    private String toString(RefeedActions.Entry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.getDocumentType() + "." + entry.getClusterName() + ":");
        builder.append(entry.getServices().stream().
                map(ServiceInfo::getServiceName).
                sorted().
                collect(Collectors.joining(",", "[", "]")));
        builder.append(entry.getMessages().stream().
                collect(Collectors.joining(",", "[", "]")));
        return builder.toString();
    }

    @Test
    public void action_with_multiple_reasons() {
        List<RefeedActions.Entry> entries = new ConfigChangeActionsBuilder().
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(ValidationId.indexModeChange, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                build().getRefeedActions().getEntries();
        assertThat(entries.size(), is(1));
        assertThat(toString(entries.get(0)), equalTo("music.foo:[baz][change,other change]"));
    }

    @Test
    public void actions_with_multiple_services() {
        List<RefeedActions.Entry> entries = new ConfigChangeActionsBuilder().
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME_2).
                build().getRefeedActions().getEntries();
        assertThat(entries.size(), is(1));
        assertThat(toString(entries.get(0)), equalTo("music.foo:[baz,qux][change]"));
    }

    @Test
    public void actions_with_multiple_document_types() {
        List<RefeedActions.Entry> entries = new ConfigChangeActionsBuilder().
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE_2, CLUSTER, SERVICE_NAME).
                build().getRefeedActions().getEntries();
        assertThat(entries.size(), is(2));
        assertThat(toString(entries.get(0)), equalTo("book.foo:[baz][change]"));
        assertThat(toString(entries.get(1)), equalTo("music.foo:[baz][change]"));
    }

    @Test
    public void actions_with_multiple_clusters() {
        List<RefeedActions.Entry> entries = new ConfigChangeActionsBuilder().
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(ValidationId.indexModeChange, CHANGE_MSG, DOC_TYPE, CLUSTER_2, SERVICE_NAME).
                build().getRefeedActions().getEntries();
        assertThat(entries.size(), is(2));
        assertThat(toString(entries.get(0)), equalTo("music.bar:[baz][change]"));
        assertThat(toString(entries.get(1)), equalTo("music.foo:[baz][change]"));
    }

}
