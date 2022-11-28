// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Domain;
import aQute.bnd.version.VersionRange;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.VespaModel;
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
 * Base class for OSGi bundle validator. Uses BND library for some of the validation.
 *
 * @author bjorncs
 */
public abstract class AbstractBundleValidator extends Validator {

    protected abstract void validateManifest(DeployState state, JarFile jar, Manifest mf);
    protected abstract void validatePomXml(DeployState state, JarFile jar, Document pom);

    @Override
    public final void validate(VespaModel model, DeployState state) {
        ApplicationPackage app = state.getApplicationPackage();
        for (ComponentInfo info : app.getComponentsInfo(state.getVespaVersion())) {
            Path path = Path.fromString(info.getPathRelativeToAppDir());
            try {
                state.getDeployLogger()
                        .log(Level.FINE, String.format("Validating bundle at '%s'", path));
                JarFile jarFile = new JarFile(app.getFileReference(path));
                validateJarFile(state, jarFile);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to validate JAR file '" + path.last() + "'", e);
            }
        }
    }

    final void validateJarFile(DeployState state, JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            throw new IllegalArgumentException("Non-existing or invalid manifest in " + filename(jar));
        }
        validateManifest(state, jar, manifest);
        getPomXmlContent(state.getDeployLogger(), jar)
                .ifPresent(pom -> validatePomXml(state, jar, pom));
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

    protected final void forEachImportPackage(Manifest mf, BiConsumer<String, VersionRange> consumer) {
        Parameters importPackage = Domain.domain(mf).getImportPackage();
        importPackage.forEach((packageName, attrs) -> {
            VersionRange versionRange = attrs.getVersion() != null
                    ? VersionRange.parseOSGiVersionRange(attrs.getVersion())
                    : null;
            consumer.accept(packageName, versionRange);
        });
    }

    protected final void log(DeployState state, Level level, String fmt, Object... args) {
        state.getDeployLogger().logApplicationPackage(level, String.format(fmt, args));
    }

    private static final Pattern POM_FILE_LOCATION = Pattern.compile("META-INF/maven/.+?/.+?/pom.xml");
    public Optional<Document> getPomXmlContent(DeployLogger deployLogger, JarFile jar) {
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
                        deployLogger.log(Level.SEVERE, message);
                        throw new RuntimeException(message, e);
                    } catch (IOException e) {
                        deployLogger.log(Level.INFO,
                                String.format("Unable to read '%s' from '%s'", f.getName(), jar.getName()));
                        return null;
                    }
                });
    }
}
