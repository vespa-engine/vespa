// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.FixedBucketSpaces;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Responsible for parsing the command line arguments and presenting the help page
 *
 * @author bjorncs
 */
public class CommandLineOptions {

    private static final String HELP_OPTION = "help";
    private static final String DUMP_OPTION = "dump";
    private static final String ROUTE_OPTION = "route";
    private static final String USER_OPTION = "user";
    private static final String GROUP_OPTION = "group";
    private static final String BUCKET_OPTION = "bucket";
    private static final String GID_OPTION = "gid";
    private static final String DOCUMENT_OPTION = "document";
    private static final String BUCKET_SPACE_OPTION = "bucketspace";

    private final Options options = createOptions();

    @SuppressWarnings("AccessStaticViaInstance")
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .hasArg(false)
                .desc("Show this syntax page.")
                .longOpt(HELP_OPTION)
                .build());

        options.addOption(Option.builder("d")
                .hasArg(false)
                .desc("Dump list of documents for all buckets matching the selection command.")
                .longOpt(DUMP_OPTION)
                .build());

        options.addOption(Option.builder("r")
                .hasArg(true)
                .desc("Route to send the messages to, usually the name of the storage cluster.")
                .argName("route")
                .longOpt(ROUTE_OPTION)
                .build());

        options.addOption(Option.builder("s")
                .hasArg(true)
                .desc("Stat buckets within the given bucket space. If not provided, '" + FixedBucketSpaces.defaultSpace() + "' is used.")
                .argName("space")
                .longOpt(BUCKET_SPACE_OPTION)
                .build());

        // A group of mutually exclusive options for user, group, bucket, gid and document.
        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRequired(false);

        optionGroup.addOption(Option.builder("u")
                .hasArg(true)
                .desc("Dump list of buckets that can contain the given user.")
                .argName("userid")
                .longOpt(USER_OPTION)
                .build());

        optionGroup.addOption(Option.builder("g")
                .hasArg(true)
                .desc("Dump list of buckets that can contain the given group.")
                .argName("groupid")
                .longOpt(GROUP_OPTION)
                .build());

        optionGroup.addOption(Option.builder("b")
                .hasArg(true)
                .desc("Dump list of buckets that are contained in the given bucket, or that contain it.")
                .argName("bucketid")
                .longOpt(BUCKET_OPTION)
                .build());

        optionGroup.addOption(Option.builder("l")
                .hasArg(true)
                .desc("Dump information about one specific document, as given by the GID (implies --dump).")
                .argName("globalid")
                .longOpt(GID_OPTION)
                .build());

        optionGroup.addOption(Option.builder("o")
                .hasArg(true)
                .desc("Dump information about one specific document (implies --dump).")
                .argName("docid")
                .longOpt(DOCUMENT_OPTION)
                .build());

        options.addOptionGroup(optionGroup);
        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("vespa-stat [options]",
                "Fetch statistics about a specific user, group, bucket, gid or document.", options, "", false);
    }

    public ClientParameters parseCommandLineArguments(String[] args) {
        try {
            CommandLineParser clp = new DefaultParser();
            CommandLine cl = clp.parse(options, args);
            ClientParameters.Builder builder = new ClientParameters.Builder();

            builder.setHelp(cl.hasOption(HELP_OPTION));
            builder.setDumpData(cl.hasOption(DUMP_OPTION));
            builder.setRoute(cl.getOptionValue(ROUTE_OPTION, "default"));
            builder.setBucketSpace(cl.getOptionValue(BUCKET_SPACE_OPTION, FixedBucketSpaces.defaultSpace()));

            if (cl.hasOption(USER_OPTION)) {
                builder.setSelectionType(ClientParameters.SelectionType.USER);
                builder.setId(cl.getOptionValue(USER_OPTION));
            } else if (cl.hasOption(GROUP_OPTION)) {
                builder.setSelectionType(ClientParameters.SelectionType.GROUP);
                builder.setId(cl.getOptionValue(GROUP_OPTION));
            } else if (cl.hasOption(BUCKET_OPTION)) {
                builder.setSelectionType(ClientParameters.SelectionType.BUCKET);
                builder.setId(cl.getOptionValue(BUCKET_OPTION));
            } else if (cl.hasOption(GID_OPTION)) {
                builder.setSelectionType(ClientParameters.SelectionType.GID);
                builder.setId(cl.getOptionValue(GID_OPTION));
                builder.setDumpData(true);
            } else if (cl.hasOption(DOCUMENT_OPTION)) {
                builder.setSelectionType(ClientParameters.SelectionType.DOCUMENT);
                builder.setId(cl.getOptionValue(DOCUMENT_OPTION));
                builder.setDumpData(true);
            } else if (!cl.hasOption(HELP_OPTION)) {
                throw new IllegalArgumentException("Must specify one of 'user', 'group', 'bucket', 'document' or 'gid'.");
            }

            return builder.build();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
