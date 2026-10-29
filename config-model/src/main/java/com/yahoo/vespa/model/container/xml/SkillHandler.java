// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.yahoo.container.bundle.BundleInstantiationSpecification.fromSearchAndDocproc;

/**
 * The handler serving an agent skill describing the schemas of the application.
 * Added to all container clusters with search.
 *
 * @author ingrid
 */
class SkillHandler {

    // Class names, not class references, as the package is not exported from its bundle
    static final String HANDLER_CLASSNAME = "ai.vespa.search.skill.SkillHandler";
    static final String SKILL_CLASSNAME = "ai.vespa.search.skill.SchemaSkill";
    static final String PATH = "/skill/v1";

    private SkillHandler() { }

    /**
     * Returns the handler, bound to the same schemes, hosts and ports as the given search handler bindings,
     * so the skill is served where search is, and not at all if search has no bindings.
     */
    static Handler create(Collection<BindingPattern> searchBindings) {
        Handler handler = new Handler(new ComponentModel(fromSearchAndDocproc(HANDLER_CLASSNAME), null));
        bindings(searchBindings).forEach(handler::addServerBindings);
        return handler;
    }

    /** Returns the component rendering the skill. */
    static Component<?, ?> createSkill() {
        return new Component<>(new ComponentModel(fromSearchAndDocproc(SKILL_CLASSNAME), null));
    }

    /**
     * Throws if a skill handler declared by the application has a binding outside /skill/v1,
     * as its routes are fixed: requests to other paths would get 404.
     */
    static void validateBindings(Handler handler) {
        for (BindingPattern binding : handler.getServerBindings()) {
            if ( ! binding.path().equals(PATH) && ! binding.path().startsWith(PATH + "/"))
                throw new IllegalArgumentException("Handler '" + handler.getComponentId() + "' has binding '" + binding.patternString() +
                                                   "', but it can only be bound to " + PATH + " and " + PATH + "/*");
        }
    }

    static boolean isSkillHandler(Handler handler) {
        return handler.getClassId().getName().equals(HANDLER_CLASSNAME);
    }

    static boolean isSkill(Component<?, ?> component) {
        return component.getClassId().getName().equals(SKILL_CLASSNAME);
    }

    static List<BindingPattern> bindings(Collection<BindingPattern> searchBindings) {
        return searchBindings.stream()
                             .map(binding -> binding.scheme() + "://" + binding.host() + binding.port().map(port -> ":" + port).orElse(""))
                             .distinct()
                             .flatMap(root -> Stream.<BindingPattern>of(SystemBindingPattern.fromPattern(root + PATH),
                                                                        SystemBindingPattern.fromPattern(root + PATH + "/*")))
                             .toList();
    }

}
