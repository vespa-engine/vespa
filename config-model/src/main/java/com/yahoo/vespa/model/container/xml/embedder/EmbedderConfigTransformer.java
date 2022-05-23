package com.yahoo.vespa.model.container.xml.embedder;


import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A specific embedder to component configuration transformer.
 *
 * @author lesters
 */
public class EmbedderConfigTransformer {

    private final Document doc = XML.getDocumentBuilder().newDocument();

    private final String id;
    private final String className;
    private final String bundle;
    private final String def;
    private final Map<String, EmbedderOption> options = new HashMap<>();

    public EmbedderConfigTransformer(Element spec, boolean hosted) {
        this(spec, hosted, null, null);
    }

    public EmbedderConfigTransformer(Element spec, boolean hosted, String defaultBundle, String defaultDef) {
        id = spec.getAttribute("id");
        className = spec.hasAttribute("class") ? spec.getAttribute("class") : id;
        bundle = spec.hasAttribute("bundle") ? spec.getAttribute("bundle") : defaultBundle;
        def = spec.hasAttribute("def") ? spec.getAttribute("def") : defaultDef;

        if (className == null || className.length() == 0) {
            throw new IllegalArgumentException("Embedder class is empty");
        }
        if (this.bundle == null || this.bundle.length() == 0) {
            throw new IllegalArgumentException("Embedder configuration requires a bundle name");
        }
        if (this.def == null || this.def.length() == 0) {
            throw new IllegalArgumentException("Embedder configuration requires a config definition name");
        }
    }

    Element createComponentConfig(DeployState deployState) {
        checkRequiredOptions();

        Element component = doc.createElement("component");
        component.setAttribute("id", id);
        component.setAttribute("class", className);
        component.setAttribute("bundle", bundle);

        if (options.size() > 0) {
            Element config = doc.createElement("config");
            config.setAttribute("name", def);
            for (Map.Entry<String, EmbedderOption> entry : options.entrySet()) {
                entry.getValue().toElement(deployState, config);
            }
            component.appendChild(config);
        }

        return component;
    }

    // TODO: support nested options
    void addOption(Element elem) {
        String name = elem.getTagName();

        EmbedderOption.Builder builder = new EmbedderOption.Builder();
        builder.name(name);
        builder.value(elem.getTextContent());
        builder.attributes(elem);

        if (options.containsKey(name)) {
            builder.required(options.get(name).required());
            builder.optionTransformer(options.get(name).optionTransformer());
        }
        options.put(name, builder.build());
    }

    void addOption(EmbedderOption option) {
        options.put(option.name(), option);
    }

    private void checkRequiredOptions() {
        List<String> missingOptions = new ArrayList<>();
        for (EmbedderOption option : options.values()) {
            if ( ! option.isSet()) {
                missingOptions.add(option.name());
            }
        }
        if (missingOptions.size() > 0) {
            throw new IllegalArgumentException("Embedder '" + className + "' requires options for " + missingOptions);
        }
    }


}
