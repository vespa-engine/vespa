// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.handler.threadpool.DefaultContainerThreadpool;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import org.w3c.dom.Element;

import java.util.Optional;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 */
public class ContainerThreadpool extends SimpleComponent implements ContainerThreadpoolConfig.Producer {

    private final String name;
    private final UserOptions userOptions;

    public ContainerThreadpool(String name, UserOptions userOptions) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "threadpool@" + name,
                        DefaultContainerThreadpool.class.getName(),
                        null)));
        this.name = name;
        this.userOptions = userOptions;
    }

    @Override
    public void getConfig(ContainerThreadpoolConfig.Builder builder) {
        builder.name(this.name);
        if (userOptions != null) {
            builder.maxThreads(userOptions.maxThreads);
            builder.minThreads(userOptions.minThreads);
            builder.queueSize(userOptions.queueSize);
        }
    }

    protected Optional<UserOptions> userOptions() { return Optional.ofNullable(userOptions); }
    protected boolean hasUserOptions() { return userOptions().isPresent(); }

    public static class UserOptions {
        private final int maxThreads;
        private final int minThreads;
        private final int queueSize;

        private UserOptions(int maxThreads, int minThreads, int queueSize) {
            this.maxThreads = maxThreads;
            this.minThreads = minThreads;
            this.queueSize = queueSize;
        }

        public static Optional<UserOptions> fromXml(Element xml) {
            Element element = XML.getChild(xml, "threadpool");
            if (element == null) return Optional.empty();
            return Optional.of(new UserOptions(
                    intOption(element, "max-threads"),
                    intOption(element, "min-threads"),
                    intOption(element, "queue-size")));
        }

        private static int intOption(Element element, String name) {
            return Integer.parseInt(XML.getChild(element, name).getTextContent());
        }
    }
}
