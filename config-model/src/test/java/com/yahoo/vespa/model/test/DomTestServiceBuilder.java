// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import org.w3c.dom.Element;

/**
 * Builders for test services
 */
public class DomTestServiceBuilder {


    static class SimpleServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<SimpleService> {
        int i;

        public SimpleServiceBuilder(int i) {
            this.i = i;
        }

        @Override
        protected SimpleService doBuild(DeployState deployState, AbstractConfigProducer parent, Element spec) {
            return new SimpleService(parent, "simpleservice." + i);
        }
    }

    static class ApiServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ApiService> {
        int i;

        public ApiServiceBuilder(int i) {
            this.i = i;
        }

        @Override
        protected ApiService doBuild(DeployState deployState, AbstractConfigProducer parent, Element spec) {
            return new ApiService(parent, "apiservice." + i);
        }
    }

    static class ParentServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ParentService> {
        int i;

        public ParentServiceBuilder(int i) {
            this.i = i;
        }

        @Override
        protected ParentService doBuild(DeployState deployState, AbstractConfigProducer parent, Element spec) {
            return new ParentService(parent, "parentservice." + i, spec);
        }
    }

}
