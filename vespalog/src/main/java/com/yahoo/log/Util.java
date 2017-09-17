// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 *
 * @author Bjorn Borud
 * @author arnej27959
 *
 */
public class Util {

    public static String getHostName () {
        return getDefaults().vespaHostname();
    }

    /**
     * Emulate the getpid() system call
     **/
    public static String getPID() {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[] {"perl", "-e", "print getppid().\"\\n\";"}
            );
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            p.destroy();
            int pid = Integer.parseInt(line);
            if (pid > 0) {
                return Integer.toString(pid);
            }
        } catch(Exception e) {
            // any problem handled by return below
        }
        return "-";
    }

}
