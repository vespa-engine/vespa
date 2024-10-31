package ai.vespa.lemminx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * LemminX isn't quite able to use .rng files from inside a jar for validation.
 * We embed them in the fat-jar and unpack them to the server installation location on start.
 * This is similar to the documentation fetching in schema language-server.
 */
public class UnpackRNGFiles {
    public static void unpackRNGFiles(Path serverPath) throws IOException {
        Files.createDirectories(serverPath.resolve("resources").resolve("schema")); // mkdir -p
        final String basePath = "resources/schema/";

        var resources = Thread.currentThread().getContextClassLoader().getResources(basePath);

        if (!resources.hasMoreElements()) {
            throw new IOException("Could not find RNG files in jar file!");
        }

        URL resourceURL = resources.nextElement();

        if (!resourceURL.getProtocol().equals("jar")) {
            throw new IOException("Unhandled protocol for resource " + resourceURL.toString());
        }

        String jarPath = resourceURL.getPath().substring(5, resourceURL.getPath().indexOf('!'));
        try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".rng") && entry.getName().startsWith("resources/schema")) {
                    Path writePath = serverPath.resolve(entry.getName());
                    try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(entry.getName())) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                        Files.write(writePath, content.getBytes(), StandardOpenOption.CREATE);
                    } catch (Exception ex) {
                        // Ignore: unwanted .rng file
                    }
                }
            }
        }
    }
}
