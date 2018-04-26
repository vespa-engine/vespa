// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import java.io.File;
import java.util.Map;

import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;

/**
 * A standalone tool for dumping query profile properties
 *
 * @author bratseth
 */
public class DumpTool {

    /** Creates and returns a dump from some parameters */
    public String resolveAndDump(String... args) {
        if (args.length == 0 || args[0].startsWith("-")) {
            StringBuilder result = new StringBuilder();
            result.append("Dumps all resolved query profile properties for a set of dimension values\n");
            result.append("USAGE: dump [query-profile] [dir]? [parameters]?\n");
            result.append("  and [query-profile] is the name of the query profile to dump the values of\n");
            result.append("  and [dir] is a path to an application package or query profile directory. Default: current dir\n");
            result.append("  and   [parameters] is the http request encoded dimension keys used during resolving. Default: none\n");
            result.append("Examples:\n");
            result.append("  dump default\n");
            result.append("  - dumps the 'default' profile non-variant values in the current dir\n");
            result.append("  dump default x=x1&y=y1\n");
            result.append("  - dumps the 'default' profile resolved with dimensions values x=x1 and y=y1 in the current dir\n");
            result.append("  dump default myapppackage\n");
            result.append("  - dumps the 'default' profile non-variant values in myapppackage/search/query-profiles\n");
            result.append("  dump default dev/myprofiles x=x1&y=y1\n");
            result.append("  - dumps the 'default' profile resolved with dimensions values x=x1 and y=y1 in dev/myprofiles\n");
            return result.toString();
        }

        // Find what the arguments means
        if (args.length >= 3) {
            return dump(args[0], args[1], args[2]);
        }
        else if (args.length == 2) {
            if (args[1].contains("="))
                return dump(args[0], "", args[1]);
            else
                return dump(args[0], args[1],"");
        }
        else { // args.length=1
            return dump(args[0], "", "");
        }
    }

    private String dump(String profileName,String dir,String parameters) {
        // Import profiles
        if (dir.isEmpty())
            dir = ".";
        File dirInAppPackage = new File(dir, "search/query-profiles");
        if (dirInAppPackage.exists())
            dir = dirInAppPackage.getPath();
        QueryProfileXMLReader reader = new QueryProfileXMLReader();
        QueryProfileRegistry registry = reader.read(dir);
        registry.freeze();

        // Dump (through query to get wiring & parameter parsing done easily)
        Query query = new Query("?" + parameters, registry.compile().findQueryProfile(profileName));
        Map<String,Object> properties = query.properties().listProperties();

        // Create result
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,Object> property : properties.entrySet()) {
            b.append(property.getKey());
            b.append("=");
            b.append(property.getValue().toString());
            b.append("\n");
        }
        return b.toString();
    }

    public static void main(String... args) {
        try {
            System.out.print(new DumpTool().resolveAndDump(args));
        }
        catch (Exception e) {
            System.err.println(Exceptions.toMessageString(e));
        }
    }

}
