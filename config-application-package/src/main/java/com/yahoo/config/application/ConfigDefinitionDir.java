// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.model.application.provider.Bundle;

import java.io.*;
import java.util.List;

/**
 * A @{link ConfigDefinitionDir} contains a set of config definitions. New definitions may be added,
 * but they cannot conflict with the existing ones.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ConfigDefinitionDir {
    private final File defDir;

    public ConfigDefinitionDir(File defDir) {
        this.defDir = defDir;
    }

    public void addConfigDefinitionsFromBundle(Bundle bundle, List<Bundle> bundlesAdded) {
        try {
            checkAndCopyUserDefs(bundle, bundlesAdded);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to add config definitions from bundle " + bundle.getFile().getAbsolutePath(), e);
        }
    }

    private void checkAndCopyUserDefs(Bundle bundle, List<Bundle> bundlesAdded) throws IOException {
        for (Bundle.DefEntry def : bundle.getDefEntries()) {
            checkUserDefConflict(bundle, def, bundlesAdded);
            String defFilename = def.defNamespace + "." + def.defName + ".def";
            OutputStream out = new FileOutputStream(new File(defDir, defFilename));
            out.write(def.contents.getBytes());
            out.close();
        }
    }

    private void checkUserDefConflict(Bundle bundle, Bundle.DefEntry userDef, List<Bundle> bundlesAdded) {
        final String defName = userDef.defName;
        final String defNamespace = userDef.defNamespace;
        File[] builtinDefsWithSameName = defDir.listFiles((dir, name) ->    name.matches(defName + ".def")
                                                                         || name.matches(defNamespace + "." + defName + ".def"));
        if (builtinDefsWithSameName != null && builtinDefsWithSameName.length > 0) {
            String message = "a built-in config definition (" + getFilePathsCommaSeparated(builtinDefsWithSameName) + ")";
            for (Bundle b : bundlesAdded) {
                for (Bundle.DefEntry defEntry : b.getDefEntries()) {
                    if (defEntry.defName.equals(defName) && defEntry.defNamespace.equals(defNamespace)) {
                        message = "the same config definition in the bundle '" + b.getFile().getName() + "'";
                    }
                }
            }
            throw new IllegalArgumentException("The config definition with name '" + defNamespace + "." + defName +
                    "' contained in the bundle '" + bundle.getFile().getName() +
                    "' conflicts with " + message + ". Please choose a different name.");
        }
    }

    private String getFilePathsCommaSeparated(File[] files) {
        StringBuilder sb = new StringBuilder();
        if (files.length > 0) {
            sb.append(files[0].getAbsolutePath());
            for (int i = 1; i < files.length; i++) {
                sb.append(", ");
                sb.append(files[i].getAbsolutePath());
            }
        }
        return sb.toString();
    }
}
