// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.result.PageTemplatesXmlRenderer;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * Holds all configured and built-in renderers.
 * This registry is always frozen.
 *
 * @author bratseth
 */
public final class RendererRegistry extends ComponentRegistry<com.yahoo.processing.rendering.Renderer<Result>> {

    public static final ComponentId xmlRendererId = ComponentId.fromString("XmlRenderer");
    public static final ComponentId pageRendererId = ComponentId.fromString("PageTemplatesXmlRenderer");
    public static final ComponentId jsonRendererId = ComponentId.fromString("JsonRenderer");
    public static final ComponentId defaultRendererId = jsonRendererId;
    

    /** 
     * Creates a registry containing the built-in renderers only, using a custom executor.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry:
     * Use MoreExecutors.directExecutor().
     */
    public RendererRegistry(Executor executor) {
        this(Collections.emptyList(), executor);
    }

    /** 
     * Creates a registry of the given renderers plus the built-in ones, using a custom executor.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry.
     */
    public RendererRegistry(Collection<Renderer> renderers, Executor executor) {
        // add json renderer
        Renderer jsonRenderer = new JsonRenderer(executor);
        jsonRenderer.initId(RendererRegistry.jsonRendererId);
        register(jsonRenderer.getId(), jsonRenderer);

        // Add xml renderer
        Renderer xmlRenderer = new XmlRenderer(executor);
        xmlRenderer.initId(xmlRendererId);
        register(xmlRenderer.getId(), xmlRenderer);

        // Add page templates renderer
        Renderer pageRenderer = new PageTemplatesXmlRenderer(executor);
        pageRenderer.initId(pageRendererId);
        register(pageRenderer.getId(), pageRenderer);

        // add application renderers
        for (Renderer renderer : renderers)
            register(renderer.getId(), renderer);

        freeze();
    }
    
    /** Must be called when use of this is discontinued to free the resources it has allocated */
    public void deconstruct() {
        // deconstruct the renderers which was created by this
        getRenderer(jsonRendererId.toSpecification()).deconstruct();
        getRenderer(xmlRendererId.toSpecification()).deconstruct();
        getRenderer(pageRendererId.toSpecification()).deconstruct();
    }

    /**
     * Returns the default JSON renderer
     *
     * @return the default built-in result renderer
     */
    public com.yahoo.processing.rendering.Renderer<Result> getDefaultRenderer() {
        return getComponent(jsonRendererId);
    }

    /**
     * Returns the requested renderer.
     *
     * @param format the id or format alias of the renderer to return. If null is passed the default renderer
     *               is returned
     * @throws IllegalArgumentException if the renderer cannot be resolved
     */
    public com.yahoo.processing.rendering.Renderer<Result> getRenderer(ComponentSpecification format) {
        if (format == null || format.stringValue().equals("default")) return getDefaultRenderer();
        if (format.stringValue().equals("json")) return getComponent(jsonRendererId);
        if (format.stringValue().equals("xml")) return getComponent(xmlRendererId);
        if (format.stringValue().equals("page")) return getComponent(pageRendererId);

        com.yahoo.processing.rendering.Renderer<Result> renderer = getComponent(format);
        if (renderer == null)
            throw new IllegalInputException("No renderer with id or alias '" + format + "'. " +
                                            "Available renderers are: [" + rendererNames() + "].");
        return renderer;
    }

    private String rendererNames() {
        StringBuilder r = new StringBuilder();
        for (Renderer<Result> c : allComponents()) {
            if (r.length() > 0)
                r.append(", ");
            r.append(c.getId().stringValue());
        }
        return r.toString();
    }

}
