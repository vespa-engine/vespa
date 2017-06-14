// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

/**
 *
 * @author  Bjorn Borud
 * @author arnej27959
 *
 */
public class Util {

    /**
     * We do not have direct access to the <code>gethostname</code>
     * system call, so we have to fake it.
     */
    public static String getHostName () {
        String hostname = "-";
        try {
            Process p = Runtime.getRuntime().exec("hostname");
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "UTF-8"));
            hostname = r.readLine();
            if (hostname != null) {
                return hostname;
            }
        }
        catch (java.io.IOException e) {}
        try {
            hostname = InetAddress.getLocalHost().getHostName();
            return hostname;
        }
        catch (java.net.UnknownHostException e) {}
        return "-";
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
