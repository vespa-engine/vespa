package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.util.Arrays;

/**
 * @author valerijf
 */
public class Maintainer {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please specify job name. Valid job names:");
            System.err.println(Arrays.asList(JobName.values()));
            return;
        }

        try {
            JobName jobName = JobName.valueOf(args[0]);
            args = Arrays.copyOfRange(args, 1, args.length);
            switch (jobName) {
                case delete_old_app_data:
                    DeleteOldAppData.main(args);
                    break;
            }

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid job name. Valid job names:");
            System.err.println(Arrays.asList(JobName.values()));
        }
    }

    private enum JobName {
        delete_old_app_data
    }
}
