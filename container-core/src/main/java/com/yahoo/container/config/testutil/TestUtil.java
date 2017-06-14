// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.config.testutil;

import com.yahoo.container.core.config.HandlersConfigurerDi;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gjoranv
 */
public class TestUtil {

    public static void createComponentsConfig(String configFile,
                                              String componentsFile,
                                              String componentType) throws IOException {
        createComponentsConfig(configFile, componentsFile, componentType, false);
    }

    /**
     * Copies the component ids from another config, e.g. 'handlers' to a 'components' array in a new components file,
     * to avoid a manually written 'components' file for tests where the bundle spec is given by the component id.
     * @param configFile  Full path to the original config file, e.g. 'handlers'
     * @param componentsFile  Full path to the new 'components' file
     * @param componentType   The type of component, e.g. 'handler'
     * @param append  'true' will append to an already existing 'componentsFile'
     */
    public static void createComponentsConfig(String configFile,
                                              String componentsFile,
                                              String componentType,
                                              boolean append) throws IOException {
        StringBuilder buf = new StringBuilder();
        String line;
        int i = 0;
        if (append) {
            final Pattern p = Pattern.compile("^[a-z]+" + "\\[\\d+\\]\\.id (.+)");
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(componentsFile)), "UTF-8"));
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches() && !m.group(1).equals(HandlersConfigurerDi.RegistriesHack.class.getName())) {
                    buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                    i++;
                }
            }
            reader.close();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(configFile)), "UTF-8"));
        final Pattern component = Pattern.compile("^" + componentType + "\\[\\d+\\]\\.id (.+)");
        while ((line = reader.readLine()) != null) {
            Matcher m = component.matcher(line);
            if (m.matches()) {
                buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                i++;
            }
        }
        buf.append("components[").append(i).append("].id ").
                append(HandlersConfigurerDi.RegistriesHack.class.getName()).append("\n");
        i++;
        reader.close();
        buf.insert(0, "components["+i+"]\n");

        Writer writer = new OutputStreamWriter(new FileOutputStream(new File(componentsFile)), "UTF-8");
        writer.write(buf.toString());
        writer.flush();
        writer.close();
    }

    /**
     * Copies src file to dst file. If the dst file does not exist, it is created.
     */
    public static void copyFile(String srcName, File dstFile) throws IOException {
        InputStream src = new FileInputStream(new File(srcName));
        OutputStream dst = new FileOutputStream(dstFile);
        byte[] buf = new byte[1024];
        int len;
        while ((len = src.read(buf)) > 0) {
            dst.write(buf, 0, len);
        }
        src.close();
        dst.close();
    }
}
