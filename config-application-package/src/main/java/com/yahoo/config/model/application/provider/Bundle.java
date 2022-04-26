// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.collections.Tuple2;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * A Bundle represents an OSGi bundle inside the model, and provides utilities
 * for accessing resources within that bundle.
 *
 * @author Tony Vaagenes, Ulf Lilleengen
 * @since 5.1
 */
public class Bundle {
    private static final Logger log = Logger.getLogger(Bundle.class.getName());
    private static final String DEFPATH = "configdefinitions/"; // path inside jar file
    private final File bundleFile;
    private final JarFile jarFile;
    private final List<DefEntry> defEntries;

    public Bundle(JarFile jarFile, File bundleFile) {
        this.jarFile = jarFile;
        this.bundleFile = bundleFile;
        defEntries = findDefEntries();
    }

    public static List<Bundle> getBundles(File bundleDir) {
        try {
            List<Bundle> bundles =  new ArrayList<>();
            for (File bundleFile : getBundleFiles(bundleDir)) {
                JarFile jarFile;
                try {
                    jarFile = new JarFile(bundleFile);
                } catch (ZipException e) {
                    throw new IllegalArgumentException("Error opening jar file '" + bundleFile.getName() +
                            "'. Please check that this is a valid jar file");
                }
                bundles.add(new Bundle(jarFile, bundleFile));
            }
            return bundles;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static List<File> getBundleFiles(File bundleDir) {
        if (!bundleDir.isDirectory()) {
            return new ArrayList<>();
        }
        return Arrays.asList(bundleDir.listFiles((dir, name) -> name.endsWith(".jar")));
    }

    public List<DefEntry> getDefEntries() {
        return Collections.unmodifiableList(defEntries);
    }

    /**
     * Returns a list of all .def-file entries in this Component.
     * @return  A list of .def-file entries.
     */
    private List<DefEntry> findDefEntries() {
        List<DefEntry> defEntries = new ArrayList<>();

        ZipEntry defDir = jarFile.getEntry(DEFPATH);

        if ((defDir == null) || !defDir.isDirectory())
            return defEntries;

        for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.endsWith(".def")) {
                if (name.matches("^" + DEFPATH + ".*\\.def$")) {
                    defEntries.add(new DefEntry(this, entry));
                } else {
                    log.info("Config definition file '" + name + "' in component '" + jarFile.getName() +
                            "' will not be used. Files must reside in the '" + DEFPATH +
                            "' directory in the .jar file");

                }
            }
        }
        return defEntries;
    }

    public JarFile getJarFile() {
        return jarFile;
    }

    public File getFile() {
        return bundleFile;
    }

    /**
     * Represents a def-file inside a Component. Immutable.
     */
    public static class DefEntry {

        private final Bundle bundle;
        private final ZipEntry zipEntry;
        public final String defName;  // Without version number and suffix.
        public final String defNamespace;
        public final String contents;

        /**
         * @param bundle      The bundle this def entry belongs to.
         * @param zipEntry    The ZipEntry representing the def-file.
         */
        public DefEntry(Bundle bundle, ZipEntry zipEntry) {
            this.bundle = bundle;
            this.zipEntry = zipEntry;

            String entryName = zipEntry.getName();
            Tuple2<String, String> nameAndNamespace = ConfigUtils.getNameAndNamespaceFromString(entryName.substring(DEFPATH.length(), entryName.indexOf(".def")));

            defName = nameAndNamespace.first;
            defNamespace = getNamespace();
            if (defNamespace.isEmpty())
                throw new IllegalArgumentException("Config definition '" + defName + "' is missing a package (or namespace)");
            contents = getContents();
        }

        /**
         * Returns the namespace of the .def-file, as given by the "namespace=" statement inside the given entry.
         * @return  The namespace string, or "" (empty string) if no namespace exists
         */
        private String getNamespace() {
            return ConfigUtils.getDefNamespace(getReader());
        }

        private String getContents() {
            StringBuilder ret = new StringBuilder("");
            BufferedReader reader = new BufferedReader(getReader());
            try {
                String str = reader.readLine();
                while (str != null){
                    ret.append(str);
                    str = reader.readLine();
                    if (str != null) {
                        ret.append("\n");
                    }
                }
                reader.close();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed reading contents of def-file '" + defName +
                        ".def in component " + bundle.jarFile.getName(),e);
            }
            return ret.toString();
        }

        public Reader getReader() {
            if (zipEntry == null) {
                return new StringReader("");
            }
            try {
                return new InputStreamReader(bundle.jarFile.getInputStream(zipEntry), StandardCharsets.UTF_8);
            }  catch (IOException e) {
                throw new IllegalArgumentException("IOException", e);
            }
        }

    }
}
