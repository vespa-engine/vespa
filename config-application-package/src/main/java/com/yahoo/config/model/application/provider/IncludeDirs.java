// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.Xml;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.List;

/**
 * Helper methods for directories included from services.xml in a &lt;include dir=''/&gt; element.
 *
 * @author gjoranv
 * @since 5.1.19
 */
public class IncludeDirs {

    public static final String INCLUDE = "include";
    public static final String DIR = "dir";

    private IncludeDirs() {
        throw new UnsupportedOperationException(IncludeDirs.class.getName() + " cannot be instantiated!");
    }

    public static void validateIncludeDir(String dirName, FilesApplicationPackage app) {
        File file = new File(dirName);

        if (file.isAbsolute()) {
            throw new IllegalArgumentException("Cannot include directory '" + dirName +
                    "', absolute paths are not supported. Directory must reside in application package, " +
                    "and path must be given relative to application package.");
        }

        file = app.getFileReference(Path.fromString(dirName));

        if (!file.exists()) {
            throw new IllegalArgumentException("Cannot include directory '" + dirName +
                    "', as it does not exist. Directory must reside in application package, " +
                    "and path must be given relative to application package.");
        }

        if (!file.isDirectory()) {
            throw new IllegalArgumentException("Cannot include '" + dirName +
                    "', as it is not a directory. Directory must reside in application package, " +
                    "and path must be given relative to application package.");
        }
    }


    public static void validateFilesInIncludedDir(String dirName, Node parentNode, ApplicationPackage app) {
        if (! (parentNode instanceof Element)) {
            throw new IllegalStateException("The parent xml node of an include is not an Element: " + parentNode);
        }
        String parentTagName = ((Element) parentNode).getTagName();

        List<Element> includedRootElems = Xml.allElemsFromPath(app, dirName);
        for (Element includedRootElem : includedRootElems) {
            validateIncludedFile(includedRootElem, parentTagName, dirName);
        }
    }

    /**
     * @param includedRootElem  The root element of the included file
     * @param dirName  The name of the included dir
     */
    private static void validateIncludedFile(Element includedRootElem, String parentTagName, String dirName) {
        if (!parentTagName.equals(includedRootElem.getTagName())) {
            throw new IllegalArgumentException("File included from '<include dir\"" + dirName +
                    "\">' does not have <" + parentTagName + "> as root element.");
        }
        if (includedRootElem.hasAttributes()) {
            throw new IllegalArgumentException("File included from '<include dir\"" + dirName +
                    "\">' has attributes set on its root element <" + parentTagName +
                    ">. These must be set in services.xml instead.");
        }
        if (XML.getChild(includedRootElem, INCLUDE) != null) {
            throw new IllegalArgumentException("File included from '<include dir\"" + dirName +
                    "\">' has <include> subelement. Recursive inclusion is not supported.");
        }
    }

}
