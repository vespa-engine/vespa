// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ExportPackages.Parameter;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ExportPackageParserTest {
    private final Parameter versionParameter = new Parameter("version", "1.2.3.sample");

    @Test
    public void require_that_package_is_parsed_correctly() {
        List<Export> exports = ExportPackageParser.parseExports("sample.exported.package");

        assertThat(exports.size(), is(1));
        assertThat(exports.get(0).getParameters(), empty());
        assertThat(exports.get(0).getPackageNames(), contains("sample.exported.package"));
    }

    @Test
    public void require_that_version_is_parsed_correctly() {
        List<Export> exports = ExportPackageParser.parseExports("com.yahoo.sample.exported.package;version=\"1.2.3.sample\"");

        assertThat(exports.size(), is(1));
        Export export = exports.get(0);
        assertThat(export.getPackageNames(), contains("com.yahoo.sample.exported.package"));
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter)));
    }

    @Test
    public void require_that_multiple_packages_with_same_parameters_is_parsed_correctly() {
        List<Export> exports = ExportPackageParser.parseExports("exported.package1;exported.package2;version=\"1.2.3.sample\"");

        assertThat(exports.size(), is(1));
        Export export = exports.get(0);
        assertThat(export.getPackageNames(), contains("exported.package1", "exported.package2"));
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter)));
    }

    @Test
    public void require_that_spaces_between_separators_are_allowed() {
        List<Export> exports = ExportPackageParser.parseExports("exported.package1  ,  exported.package2 ; version   = \"1.2.3.sample\"  ");

        assertThat(exports.size(), is(2));
        Export export = exports.get(0);
        assertThat(export.getPackageNames(), contains("exported.package1"));
        export = exports.get(1);
        assertThat(export.getPackageNames(), contains("exported.package2"));
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void require_that_multiple_parameters_for_a_package_is_parsed_correctly() {
        List<Export> exports = ExportPackageParser.parseExports("exported.package;version=\"1.2.3.sample\";param2=true");

        assertThat(exports.size(), is(1));
        Export export = exports.get(0);
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter), parameterMatching("param2", "true")));
    }

    @Test
    public void require_that_multiple_exports_are_parsed_correctly() {
        List<Export> exports = ExportPackageParser.parseExports("exported.package1,exported.package2");
        assertThat(exports.size(), is(2));
        Export export = exports.get(0);
        assertThat(export.getPackageNames(), contains("exported.package1"));
        assertThat(export.getParameters(), empty());
        export = exports.get(1);
        assertThat(export.getPackageNames(), contains("exported.package2"));
        assertThat(export.getParameters(), empty());

        exports = ExportPackageParser.parseExports("exported.package1;version=\"1.2.3.sample\",exported.package2");
        assertThat(exports.size(), is(2));
        export = exports.get(0);
        assertThat(export.getPackageNames(), contains("exported.package1"));
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter)));
        export = exports.get(1);
        assertThat(export.getPackageNames(), contains("exported.package2"));
        assertThat(export.getParameters(), empty());

        exports = ExportPackageParser.parseExports("exported.package1,exported.package2;version=\"1.2.3.sample\"");
        assertThat(exports.size(), is(2));
        export = exports.get(0);
        assertThat(export.getPackageNames(), contains("exported.package1"));
        assertThat(export.getParameters(), empty());
        export = exports.get(1);
        assertThat(export.getPackageNames(), contains("exported.package2"));
        assertThat(export.getParameters(), contains(parameterMatching(versionParameter)));
    }

    @Test
    public void require_that_long_string_literals_do_not_cause_stack_overflow_error() {
        //From jersey-server-1.13.jar
        String exportHeader = "com.sun.jersey.server.impl.wadl;uses:=\"com.sun.jersey.api.model,com.sun.resea"
                + "rch.ws.wadl,com.sun.jersey.api.wadl.config,com.sun.jersey.server.wadl,com.sun."
                + "jersey.api.core,javax.xml.bind,javax.ws.rs.core,com.sun.jersey.server.impl.uri"
                + ",com.sun.jersey.core.spi.factory,com.sun.jersey.server.impl.model.method,com.s"
                + "un.jersey.api.uri,com.sun.jersey.core.header,com.sun.jersey.spi.dispatch,javax"
                + ".ws.rs,com.sun.jersey.spi.resource\";version=\"1.13.0\",com.sun.jersey.server."
                + "impl.model.parameter.multivalued;uses:=\"com.sun.jersey.spi,javax.ws.rs.core,c"
                + "om.sun.jersey.api.container,com.sun.jersey.impl,javax.xml.parsers,org.xml.sax,"
                + "javax.xml.transform,javax.xml.bind.annotation,javax.xml.transform.sax,com.sun."
                + "jersey.spi.inject,javax.xml.bind,javax.ws.rs.ext,com.sun.jersey.api.model,com."
                + "sun.jersey.core.reflection,javax.ws.rs,com.sun.jersey.core.spi.component,com.s"
                + "un.jersey.core.header\";version=\"1.13.0\",com.sun.jersey.server.impl.model.pa"
                + "rameter;uses:=\"com.sun.jersey.api.model,com.sun.jersey.core.spi.component,com"
                + ".sun.jersey.server.impl.model.parameter.multivalued,com.sun.jersey.spi.inject,"
                + "com.sun.jersey.api,com.sun.jersey.api.core,com.sun.jersey.server.impl.inject,j"
                + "avax.ws.rs.core,javax.ws.rs,com.sun.jersey.core.header,com.sun.jersey.api.repr"
                + "esentation\";version=\"1.13.0\",com.sun.jersey.server.impl.application;uses:="
                + "\"com.sun.jersey.core.spi.component,com.sun.jersey.api.core,com.sun.jersey.spi"
                + ",com.sun.jersey.spi.inject,javax.ws.rs.core,com.sun.jersey.api.container,javax"
                + ".ws.rs.ext,com.sun.jersey.spi.container,com.sun.jersey.core.reflection,com.sun"
                + ".jersey.api.model,com.sun.jersey.impl,com.sun.jersey.spi.dispatch,com.sun.jers"
                + "ey.server.impl.model,com.sun.jersey.server.impl.wadl,com.sun.jersey.server.imp"
                + "l.uri,com.sun.jersey.core.spi.factory,com.sun.jersey.api.uri,com.sun.jersey.se"
                + "rver.impl.uri.rules,com.sun.jersey.spi.uri.rules,com.sun.jersey.server.spi.com"
                + "ponent,com.sun.jersey.core.util,com.sun.jersey.core.header,com.sun.jersey.core"
                + ".spi.component.ioc,javax.ws.rs,com.sun.jersey.server.impl,com.sun.jersey.serve"
                + "r.wadl,com.sun.jersey.server.impl.inject,com.sun.jersey.server.impl.component,"
                + "com.sun.jersey.spi.monitoring,com.sun.jersey.server.impl.monitoring,com.sun.je"
                + "rsey.api.container.filter,com.sun.jersey.server.impl.model.parameter.multivalu"
                + "ed,com.sun.jersey.server.impl.model.parameter,com.sun.jersey.server.impl.templ"
                + "ate,com.sun.jersey.spi.template,com.sun.jersey.server.impl.resource,com.sun.je"
                + "rsey.server.impl.modelapi.annotation,com.sun.jersey.server.impl.container.filt"
                + "er,com.sun.jersey.server.impl.modelapi.validation,com.sun.jersey.api,com.sun.j"
                + "ersey.spi.service\";version=\"1.13.0\",com.sun.jersey.server.impl.component;us"
                + "es:=\"com.sun.jersey.api.model,com.sun.jersey.core.spi.component,com.sun.jerse"
                + "y.server.spi.component,com.sun.jersey.api.core,com.sun.jersey.core.spi.compone"
                + "nt.ioc,com.sun.jersey.server.impl.inject,com.sun.jersey.server.impl.resource,c"
                + "om.sun.jersey.api.container,com.sun.jersey.core.reflection,com.sun.jersey.spi."
                + "inject\";version=\"1.13.0\",com.sun.jersey.server.impl.provider;uses:=\"com.su"
                + "n.jersey.core.spi.factory,com.sun.jersey.api.container,com.sun.jersey.api.core"
                + ",javax.ws.rs.core\";version=\"1.13.0\",com.sun.jersey.server.impl.template;use"
                + "s:=\"com.sun.jersey.core.spi.component,com.sun.jersey.api.view,com.sun.jersey."
                + "spi.template,javax.ws.rs.core,com.sun.jersey.core.header,com.sun.jersey.server"
                + ".impl.model.method,com.sun.jersey.spi.dispatch,com.sun.jersey.api.uri,javax.ws"
                + ".rs,com.sun.jersey.spi.inject,javax.ws.rs.ext,com.sun.jersey.server.impl.uri.r"
                + "ules,com.sun.jersey.server.probes,com.sun.jersey.core.reflection,com.sun.jerse"
                + "y.spi.uri.rules,com.sun.jersey.spi.container,com.sun.jersey.api.core\";version"
                + "=\"1.13.0\",com.sun.jersey.server.osgi;uses:=\"com.sun.jersey.server.impl.prov"
                + "ider,org.osgi.framework,javax.ws.rs.ext\";version=\"1.13.0\",com.sun.jersey.se"
                + "rver.wadl.generators.resourcedoc.model;uses:=\"javax.xml.bind.annotation,javax"
                + ".xml.namespace\";version=\"1.13.0\",com.sun.jersey.server.impl.resource;uses:="
                + "\"com.sun.jersey.api.model,com.sun.jersey.core.spi.component,com.sun.jersey.se"
                + "rver.spi.component,com.sun.jersey.api.core,com.sun.jersey.api.container,javax."
                + "ws.rs,com.sun.jersey.server.impl.inject,com.sun.jersey.core.spi.component.ioc,"
                + "javax.ws.rs.core\";version=\"1.13.0\",com.sun.jersey.server.impl.monitoring;us"
                + "es:=\"com.sun.jersey.spi.monitoring,com.sun.jersey.spi.service,com.sun.jersey."
                + "api.model,com.sun.jersey.spi.container,javax.ws.rs.ext,com.sun.jersey.core.spi"
                + ".component\";version=\"1.13.0\",com.sun.jersey.server.impl.modelapi.annotation"
                + ";uses:=\"com.sun.jersey.api.model,javax.ws.rs.core,javax.ws.rs,com.sun.jersey."
                + "core.reflection,com.sun.jersey.core.header,com.sun.jersey.impl\";version=\"1.1"
                + "3.0\",com.sun.jersey.server.impl.container;uses:=\"com.sun.jersey.server.impl."
                + "application,com.sun.jersey.spi.container,com.sun.jersey.api.container\";versio"
                + "n=\"1.13.0\",com.sun.jersey.server.wadl;uses:=\"javax.ws.rs.core,com.sun.resea"
                + "rch.ws.wadl,javax.xml.namespace,com.sun.jersey.api.model,javax.xml.bind,javax."
                + "ws.rs,com.sun.jersey.server.wadl.generators,com.sun.jersey.server.impl.modelap"
                + "i.annotation,com.sun.jersey.server.impl\";version=\"1.13.0\",com.sun.jersey.se"
                + "rver.impl.model.method.dispatch;uses:=\"com.sun.jersey.api.model,com.sun.jerse"
                + "y.api.core,com.sun.jersey.spi.container,com.sun.jersey.server.impl.inject,com."
                + "sun.jersey.api,javax.ws.rs.core,com.sun.jersey.core.spi.factory,com.sun.jersey"
                + ".spi.inject,com.sun.jersey.spi.dispatch,com.sun.jersey.core.spi.component,java"
                + "x.ws.rs,com.sun.jersey.server.impl.model.parameter.multivalued,com.sun.jersey."
                + "api.representation,com.sun.jersey.api.container\";version=\"1.13.0\",com.sun.j"
                + "ersey.server.impl;uses:=\"javax.naming,com.sun.jersey.api.core,com.sun.jersey."
                + "core.header,javax.ws.rs.core,com.sun.jersey.server.impl.model,com.sun.jersey.s"
                + "pi.container\";version=\"1.13.0\",com.sun.jersey.server.wadl.generators.resour"
                + "cedoc;uses:=\"com.sun.jersey.api.model,com.sun.jersey.server.wadl.generators.r"
                + "esourcedoc.model,com.sun.jersey.server.wadl.generators.resourcedoc.xhtml,com.s"
                + "un.research.ws.wadl,javax.xml.namespace,com.sun.jersey.server.wadl,javax.xml.b"
                + "ind,javax.ws.rs.core\";version=\"1.13.0\",com.sun.jersey.server.impl.container"
                + ".httpserver;uses:=\"com.sun.net.httpserver,com.sun.jersey.spi.container,javax."
                + "ws.rs.core,com.sun.jersey.core.header,com.sun.jersey.api.container,com.sun.jer"
                + "sey.core.util,com.sun.jersey.api.core\";version=\"1.13.0\",com.sun.jersey.serv"
                + "er.impl.container.filter;uses:=\"com.sun.jersey.api.model,com.sun.jersey.spi.c"
                + "ontainer,com.sun.jersey.core.spi.component,com.sun.jersey.api.core,javax.ws.rs"
                + ",com.sun.jersey.server.impl.uri,javax.ws.rs.core\";version=\"1.13.0\",com.sun."
                + "jersey.server.wadl.generators.resourcedoc.xhtml;uses:=\"javax.xml.bind,javax.x"
                + "ml.namespace,javax.xml.bind.annotation\";version=\"1.13.0\",com.sun.jersey.ser"
                + "ver.impl.uri.rules;uses:=\"com.sun.jersey.spi.uri.rules,com.sun.jersey.api.uri"
                + ",com.sun.jersey.api.core,com.sun.jersey.server.impl.model.method,com.sun.jerse"
                + "y.spi.dispatch,com.sun.jersey.core.header,javax.ws.rs.core,com.sun.jersey.api."
                + "model,com.sun.jersey.server.probes,com.sun.jersey.core.reflection,com.sun.jers"
                + "ey.server.impl.template,com.sun.jersey.spi.monitoring,com.sun.jersey.api,com.s"
                + "un.jersey.spi.container,com.sun.jersey.server.impl.uri,javax.ws.rs,com.sun.jer"
                + "sey.api.container,com.sun.jersey.server.impl.inject,com.sun.jersey.spi.inject,"
                + "com.sun.jersey.server.impl.uri.rules.automata\";version=\"1.13.0\",com.sun.jer"
                + "sey.server.spi.component;uses:=\"com.sun.jersey.spi.inject,com.sun.jersey.api."
                + "model,com.sun.jersey.core.spi.component,com.sun.jersey.api.core,com.sun.jersey"
                + ".server.impl.inject,com.sun.jersey.api.container,com.sun.jersey.core.spi.compo"
                + "nent.ioc\";version=\"1.13.0\",com.sun.jersey.server.probes;version=\"1.13.0\","
                + "com.sun.jersey.server.wadl.generators;uses:=\"com.sun.research.ws.wadl,javax.x"
                + "ml.bind.annotation,com.sun.jersey.api.model,com.sun.jersey.server.wadl,javax.x"
                + "ml.bind,javax.ws.rs.core,com.sun.jersey.api,javax.xml.namespace,javax.xml.tran"
                + "sform,javax.xml.transform.stream\";version=\"1.13.0\",com.sun.jersey.server.im"
                + "pl.modelapi.validation;uses:=\"com.sun.jersey.api.model,javax.ws.rs,com.sun.je"
                + "rsey.impl,com.sun.jersey.api.core,com.sun.jersey.core.reflection,javax.ws.rs.c"
                + "ore\";version=\"1.13.0\",com.sun.jersey.server.impl.model.method;uses:=\"com.s"
                + "un.jersey.api.container,com.sun.jersey.spi.dispatch,com.sun.jersey.api.uri,com"
                + ".sun.jersey.api.model,com.sun.jersey.server.impl.container.filter,com.sun.jers"
                + "ey.impl,com.sun.jersey.spi.container,com.sun.jersey.spi.inject,com.sun.jersey."
                + "api.core,javax.ws.rs.core,com.sun.jersey.core.header\";version=\"1.13.0\",com."
                + "sun.jersey.server.impl.model;uses:=\"javax.ws.rs,com.sun.jersey.impl,com.sun.j"
                + "ersey.api.container,com.sun.jersey.core.header,com.sun.jersey.core.header.read"
                + "er,com.sun.jersey.api.core,javax.ws.rs.core,com.sun.jersey.server.impl.model.m"
                + "ethod,com.sun.jersey.server.impl.container.filter,com.sun.jersey.api.model,com"
                + ".sun.jersey.server.impl.wadl,com.sun.jersey.spi.monitoring,com.sun.jersey.serv"
                + "er.impl.uri,com.sun.jersey.spi.container,com.sun.jersey.server.impl.inject,com"
                + ".sun.jersey.spi.inject,com.sun.jersey.api.uri,com.sun.jersey.core.spi.componen"
                + "t,com.sun.jersey.server.impl.uri.rules,com.sun.jersey.server.impl.template,com"
                + ".sun.jersey.api.view,com.sun.jersey.spi.uri.rules\";version=\"1.13.0\",com.sun"
                + ".jersey.server.impl.uri.rules.automata;uses:=\"com.sun.jersey.server.impl.uri,"
                + "com.sun.jersey.spi.uri.rules,com.sun.jersey.server.impl.uri.rules,com.sun.jers"
                + "ey.api.uri\";version=\"1.13.0\",com.sun.jersey.server.impl.uri;uses:=\"com.sun"
                + ".jersey.api.uri,javax.ws.rs.core\";version=\"1.13.0\",com.sun.jersey.server.im"
                + "pl.inject;uses:=\"com.sun.jersey.api.core,com.sun.jersey.spi.inject,javax.ws.r"
                + "s,com.sun.jersey.api.container,com.sun.jersey.api.model,com.sun.jersey.core.sp"
                + "i.component,com.sun.jersey.core.spi.factory\";version=\"1.13.0\",com.sun.jerse"
                + "y.spi.scanning;uses:=\"org.objectweb.asm,com.sun.jersey.core.reflection,com.su"
                + "n.jersey.core.spi.scanning,javax.ws.rs,javax.ws.rs.ext\";version=\"1.13.0\",co"
                + "m.sun.jersey.spi.resource;uses:=\"com.sun.jersey.server.impl.resource,com.sun."
                + "jersey.server.spi.component\";version=\"1.13.0\",com.sun.jersey.spi.template;u"
                + "ses:=\"com.sun.jersey.api.view,javax.ws.rs.core,com.sun.jersey.api.container\""
                + ";version=\"1.13.0\",com.sun.jersey.spi.dispatch;uses:=\"com.sun.jersey.api.cor"
                + "e\";version=\"1.13.0\",com.sun.jersey.spi.uri.rules;uses:=\"com.sun.jersey.api"
                + ".core,com.sun.jersey.api.model,com.sun.jersey.spi.container,com.sun.jersey.api"
                + ".uri\";version=\"1.13.0\",com.sun.jersey.spi.container;uses:=\"javax.ws.rs,com"
                + ".sun.jersey.api.representation,com.sun.jersey.core.header,com.sun.jersey.spi,j"
                + "avax.ws.rs.core,com.sun.jersey.api.container,com.sun.jersey.api.core,com.sun.j"
                + "ersey.core.util,com.sun.jersey.core.header.reader,com.sun.jersey.server.impl,c"
                + "om.sun.jersey.core.reflection,javax.ws.rs.ext,com.sun.jersey.server.impl.model"
                + ",com.sun.jersey.api,com.sun.jersey.api.uri,com.sun.jersey.core.spi.factory,com"
                + ".sun.jersey.spi.monitoring,com.sun.jersey.api.model,com.sun.jersey.core.spi.co"
                + "mponent,com.sun.jersey.server.impl.application,com.sun.jersey.impl,com.sun.jer"
                + "sey.spi.inject,com.sun.jersey.spi.dispatch,com.sun.jersey.server.impl.inject,c"
                + "om.sun.jersey.core.spi.component.ioc,com.sun.jersey.spi.service\";version=\"1."
                + "13.0\",com.sun.jersey.spi.monitoring;uses:=\"com.sun.jersey.api.model,com.sun."
                + "jersey.spi.container,javax.ws.rs.ext\";version=\"1.13.0\",com.sun.jersey.api;u"
                + "ses:=\"javax.ws.rs,javax.ws.rs.core,com.sun.jersey.core.header,com.sun.jersey."
                + "core.spi.factory\";version=\"1.13.0\",com.sun.jersey.api.core;uses:=\"javax.ws"
                + ".rs.core,com.sun.jersey.core.spi.scanning,com.sun.jersey.api.model,com.sun.jer"
                + "sey.api.uri,javax.ws.rs,com.sun.jersey.core.header,com.sun.jersey.api.represen"
                + "tation,com.sun.jersey.core.util,javax.ws.rs.ext,com.sun.jersey.api.container,c"
                + "om.sun.jersey.spi.scanning,com.sun.jersey.spi.container,com.sun.jersey.server."
                + "impl.application\";version=\"1.13.0\",com.sun.jersey.api.wadl.config;uses:=\"c"
                + "om.sun.jersey.server.wadl,com.sun.jersey.api.core,com.sun.jersey.core.reflecti"
                + "on,com.sun.jersey.server.wadl.generators\";version=\"1.13.0\",com.sun.jersey.a"
                + "pi.model;uses:=\"javax.ws.rs.core,com.sun.jersey.spi.container\";version=\"1.1"
                + "3.0\",com.sun.jersey.api.view;version=\"1.13.0\",com.sun.jersey.api.container."
                + "filter;uses:=\"javax.ws.rs,com.sun.jersey.spi.container,javax.ws.rs.core,com.s"
                + "un.jersey.api.container,com.sun.jersey.api.core,com.sun.jersey.core.util,com.s"
                + "un.jersey.core.header,com.sun.jersey.api.representation,com.sun.jersey.api.mod"
                + "el,javax.annotation.security\";version=\"1.13.0\",com.sun.jersey.api.container"
                + ";uses:=\"com.sun.jersey.api.core,com.sun.jersey.spi.container,com.sun.jersey.s"
                + "pi.service,com.sun.jersey.core.spi.component.ioc\";version=\"1.13.0\",com.sun."
                + "jersey.api.container.httpserver;uses:=\"com.sun.net.httpserver,com.sun.jersey."
                + "api.core,com.sun.jersey.api.container,com.sun.jersey.core.spi.component.ioc\";"
                + "version=\"1.13.0\",com.sun.research.ws.wadl;uses:=\"javax.xml.bind.annotation,"
                + "javax.xml.bind.annotation.adapters,javax.xml.namespace\";version=\"1.13.0\"";
        ExportPackageParser.parseExports(exportHeader);
    }

    private static TypeSafeMatcher<Parameter> parameterMatching(final String name, final String value) {
        return new TypeSafeMatcher<Parameter>() {
            @Override
            protected boolean matchesSafely(Parameter parameter) {
                return parameter.getName().equals(name) && parameter.getValue().equals(value);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Parameter with name ").appendValue(name).appendText(" with value ").appendValue(value);
            }
        };
    }

    private static TypeSafeMatcher<Parameter> parameterMatching(final Parameter param) {
        return parameterMatching(param.getName(), param.getValue());
    }
}
