package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.util.Arrays;

/**
 * @author valerijf
 */
public class Maintainer {
    public static void main(String[] args) {
        String[] options = {"delete-old-app-data"};

        if (args.length == 0) {
            System.err.println("Please specify job name. Valid job names:");
            System.err.println(String.join(", ", options));
            return;
        }

        String jobName = args[0];
        args = Arrays.copyOfRange(args, 1, args.length);
        switch (jobName) {
            case "delete-old-app-data":
                DeleteOldAppData.main(args);
                break;

            default:
                System.err.println("Invalid job name. Valid job names:");
                System.err.println(String.join(", ", options));
                break;
        }
    }
}
