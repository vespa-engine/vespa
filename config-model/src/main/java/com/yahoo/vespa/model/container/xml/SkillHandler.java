// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.util.List;
import java.util.stream.Stream;

import static com.yahoo.container.bundle.BundleInstantiationSpecification.fromSearchAndDocproc;

/**
 * The handler serving the schemas skill, bound at /skills/v1 on the same hosts and ports as search.
 * Added to container clusters with &lt;schemas/&gt; in &lt;skills&gt;.
 *
 * @author ingrid
 */
class SkillHandler extends Handler {

    // Class names, not class references, as the package is not exported from its bundle
    static final String HANDLER_CLASSNAME = "ai.vespa.search.skill.SkillHandler";
    static final String SCHEMA_SKILL_CLASSNAME = "ai.vespa.search.skill.SchemaSkill";
    static final BundleInstantiationSpecification SCHEMA_SKILL_SPEC = fromSearchAndDocproc(SCHEMA_SKILL_CLASSNAME);
    static final String PATH = "/skills/v1";

    /** Creates the handler, bound where search is in the given cluster, and not at all if search has no bindings. */
    SkillHandler(ApplicationContainerCluster cluster) {
        super(new ComponentModel(fromSearchAndDocproc(HANDLER_CLASSNAME), null));
        bindings(searchBindings(cluster)).forEach(this::addServerBindings);
    }

    private static List<BindingPattern> searchBindings(ApplicationContainerCluster cluster) {
        return cluster.getHandlers().stream()
                      .filter(handler -> handler.getClassId().getName().equals(SearchHandler.HANDLER_CLASSNAME))
                      .flatMap(handler -> handler.getServerBindings().stream())
                      .toList();
    }

    /** Returns this handler's bindings: at PATH, on each of the schemes, hosts and ports of the given bindings. */
    private static List<BindingPattern> bindings(List<BindingPattern> servedAt) {
        return servedAt.stream()
                       .map(binding -> binding.scheme() + "://" + binding.host() + binding.port().map(port -> ":" + port).orElse(""))
                       .distinct()
                       .flatMap(root -> Stream.<BindingPattern>of(SystemBindingPattern.fromPattern(root + PATH),
                                                                  SystemBindingPattern.fromPattern(root + PATH + "/*")))
                       .toList();
    }

}
