// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Base class for OSGi bundle validator.
 *
 * @author bjorncs
 */
public abstract class AbstractBundleValidator implements Validator {

    protected interface JarContext {
        void illegal(String error);
        void illegal(String error, Throwable cause);
        DeployState deployState();
        static JarContext of(Context context) {
            return new JarContext() {
                @Override public void illegal(String error) { context.illegal(error); }
                @Override public void illegal(String error, Throwable cause) { context.illegal(error, cause); }
                @Override public DeployState deployState() { return context.deployState(); }
            };
        }
    }

    protected abstract void validateManifest(JarContext context, JarFile jar, Manifest mf);
    protected abstract void validatePomXml(JarContext context, JarFile jar, Document pom);

    @Override
    public final void validate(Context context) {
        ApplicationPackage app = context.deployState().getApplicationPackage();
        for (ComponentInfo info : app.getComponentsInfo(context.deployState().getVespaVersion())) {
            Path path = Path.fromString(info.getPathRelativeToAppDir());
            try {
                context.deployState().getDeployLogger()
                        .log(Level.FINE, String.format("Validating bundle at '%s'", path));
                JarFile jarFile = new JarFile(app.getFileReference(path));
                validateJarFile(JarContext.of(context), jarFile);
            } catch (IOException e) {
                context.illegal("Failed to validate JAR file '" + path.last() + "'", e);
            }
        }
    }

    final void validateJarFile(JarContext context, JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            context.illegal("Non-existing or invalid manifest in " + filename(jar));
        }
        validateManifest(context, jar, manifest);
        getPomXmlContent(context::illegal, context.deployState().getDeployLogger(), jar).ifPresent(pom -> validatePomXml(context, jar, pom));
    }

    protected final String filename(JarFile jarFile) { return Paths.get(jarFile.getName()).getFileName().toString(); }

    protected final void forEachPomXmlElement(Document pom, String xpath, Consumer<Element> consumer) throws XPathExpressionException {
        NodeList dependencies = (NodeList) XPathFactory.newDefaultInstance().newXPath()
                .compile("/project/" + xpath)
                .evaluate(pom, XPathConstants.NODESET);
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element element = (Element) dependencies.item(i);
            consumer.accept(element);
        }
    }

    protected final void forEachImportPackage(Manifest mf, Consumer<String> consumer) {
        String importPackage = mf.getMainAttributes().getValue("Import-Package");
        ImportPackageInfo importPackages = new ImportPackageInfo(importPackage);

        for (String packageName : importPackages.packages()) {
            consumer.accept(packageName);
        }
    }

    protected final void log(DeployState state, Level level, String fmt, Object... args) {
        state.getDeployLogger().logApplicationPackage(level, String.format(fmt, args));
    }

    private static final Pattern POM_FILE_LOCATION = Pattern.compile("META-INF/maven/.+?/.+?/pom.xml");
    public Optional<Document> getPomXmlContent(BiConsumer<String, Throwable> context, DeployLogger logger, JarFile jar) {
        return jar.stream()
                .filter(f -> POM_FILE_LOCATION.matcher(f.getName()).matches())
                .findFirst()
                .map(f -> {
                    try {
                        String text = new String(jar.getInputStream(f).readAllBytes());
                        return XML.getDocumentBuilder(false)
                                .parse(new InputSource(new StringReader(text)));
                    } catch (SAXException e) {
                        String message = String.format("Unable to parse pom.xml from %s", filename(jar));
                        logger.log(Level.SEVERE, message);
                        context.accept(message, e);
                    } catch (IOException e) {
                        logger.log(Level.INFO,
                                String.format("Unable to read '%s' from '%s'", f.getName(), jar.getName()));
                    }
                    return null;
                });
    }
}
