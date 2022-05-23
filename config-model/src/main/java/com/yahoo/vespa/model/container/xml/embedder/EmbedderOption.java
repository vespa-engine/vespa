package com.yahoo.vespa.model.container.xml.embedder;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


/**
 * Holds options for embedder configuration. This includes code for handling special
 * options such as model specifiers.
 *
 * @author lesters
 */
public class EmbedderOption {

    public static final OptionTransformer defaultOptionTransformer = new OptionTransformer();

    private final String name;
    private final boolean required;
    private final String value;
    private final Map<String, String> attributes;
    private final OptionTransformer optionTransformer;
    private final boolean set;

    private EmbedderOption(Builder builder) {
        this.name = builder.name;
        this.required = builder.required;
        this.value = builder.value;
        this.attributes = builder.attributes;
        this.optionTransformer = builder.optionTransformer;
        this.set = builder.set;
    }

    public void toElement(DeployState deployState, Element parent) {
        optionTransformer.transform(deployState, parent, this);
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public boolean required() {
        return required;
    }

    public OptionTransformer optionTransformer() {
        return optionTransformer;
    }

    public boolean isSet() {
        return set;
    }

    /**
     * Basic option transformer. No special handling of options.
     */
    public static class OptionTransformer {
        public void transform(DeployState deployState, Element parent, EmbedderOption option) {
            createElement(parent, option.name(), option.value());
        }

        public static Element createElement(Element parent, String name, String value) {
            Element element = parent.getOwnerDocument().createElement(name);
            element.setTextContent(value);
            parent.appendChild(element);
            return element;
        }
    }

    /**
     * Transforms model options of type <x id="..." url="..." path="..." /> to the
     * required fields in the config definition.
     */
    public static class ModelOptionTransformer extends OptionTransformer {

        private final String pathField;
        private final String urlField;

        public ModelOptionTransformer(String pathField, String urlField) {
            super();
            this.pathField = pathField;
            this.urlField = urlField;
        }

        @Override
        public void transform(DeployState deployState, Element parent, EmbedderOption option) {
            String id = option.attributes.get("id");
            String url = option.attributes.get("url");
            String path = option.attributes.get("path");

            // Always use path if it is set
            if (path != null && path.length() > 0) {
                createElement(parent, pathField, path);
                createElement(parent, urlField, "");
                return;
            }

            // Only use the id if we're on cloud
            if (deployState.isHosted() && id != null && id.length() > 0) {
                createElement(parent, urlField, EmbedderConfig.modelIdToUrl(id));
                createElement(parent, pathField, createDummyPath(deployState));
                return;
            }

            // Otherwise, use url
            if (url != null && url.length() > 0) {
                createElement(parent, urlField, url);
                createElement(parent, pathField, createDummyPath(deployState));
                return;
            }

            if ( ! deployState.isHosted() && id != null && id.length() > 0) {
                throw new IllegalArgumentException("Model option 'id' is not valid here");
            }
            throw new IllegalArgumentException("Model option requires either a 'path' or a 'url' attribute");
        }

        private String createDummyPath(DeployState deployState) {
            // For now, until we have optional config parameters, return services.xml as it is guaranteed to exist
            return "services.xml";
        }

    }

    public static class Builder {
        private String name = "";
        private boolean required = false;
        private String value = "";
        private Map<String, String> attributes = Map.of();
        private OptionTransformer optionTransformer = defaultOptionTransformer;
        private boolean set = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            this.set = true;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder attributes(Element element) {
            NamedNodeMap map = element.getAttributes();
            if (map.getLength() > 0) {
                this.attributes = new HashMap<>(map.getLength());
                for (int i = 0; i < map.getLength(); ++i) {
                    String attr = map.item(i).getNodeName();
                    attributes.put(attr, element.getAttribute(attr));
                }
            }
            return this;
        }

        public Builder optionTransformer(OptionTransformer optionTransformer) {
            this.optionTransformer = optionTransformer;
            return this;
        }

        public EmbedderOption build() {
            return new EmbedderOption(this);
        }

    }

}
