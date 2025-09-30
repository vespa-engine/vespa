// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.exportsignificance;

import org.apache.commons.cli.*;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.Locale;

/**
 * The vespa export significance tool does.
 *
 * @author johsol
 */
public class Main {

    final static String PROGRAM_NAME = "vespa-export-significance";

    public static void main(String[] args) {
        Options options = new Options()
                .addOption(Option.builder("h").longOpt("help").desc("Show help").build())
                .addOption(Option.builder("f").longOpt("field").required(true).hasArg().argName("NAME").desc("Field name (required)").build())
                .addOption(Option.builder("o").longOpt("output").hasArg().argName("OUTPUT").desc("Output json file (default: significance_model.json)").build())
                .addOption(Option.builder("l").longOpt("lang").hasArg().argName("LANG").desc("Language code (default: un)").build());

        HelpFormatter help = new HelpFormatter();
        help.setWidth(100);
        help.setLeftPadding(2);
        help.setDescPadding(2);
        help.setSyntaxPrefix("Usage: ");
        help.setLongOptSeparator("=");
        help.setOptionComparator(Comparator.comparing(
            opt -> opt.getLongOpt() == null ? opt.getOpt() : opt.getLongOpt(),
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        String header = String.join(System.lineSeparator(),
                "",
                "Exports significance model to json.",
                ""
        );

        String footer = String.join(System.lineSeparator(),
                "",
                "Examples:",
                "  " + PROGRAM_NAME + " -f title",
                "  " + PROGRAM_NAME + " -f body --lang en",
                "  " + PROGRAM_NAME + " --field=summary --lang=no",
                ""
        );

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                printHelp(help, options, header, footer);
                return;
            }

            String field = cmd.getOptionValue("f");
            String language = cmd.getOptionValue("l", "un").toLowerCase(Locale.ROOT);
            String output = cmd.getOptionValue("o", "significance_model.json");

            SignificanceExporter exporter = new SignificanceExporter();
            exporter.export(field, language, output);

        } catch (MissingOptionException | MissingArgumentException e) {
            fail(help, options, e.getMessage(), header, footer);
        } catch (ParseException e) {
            fail(help, options, "Invalid arguments: " + e.getMessage(), header, footer);
        }
    }

    private static void printHelp(HelpFormatter help, Options options, String header, String footer) {
        try (PrintWriter pw = new PrintWriter(System.out, true)) {
            help.printHelp(pw, help.getWidth(),
                    PROGRAM_NAME + " [options]", header,
                    options, help.getLeftPadding(), help.getDescPadding(), footer, true);
            pw.flush();
        }
    }

    private static void fail(HelpFormatter help, Options options, String error, String header, String footer) {
        System.err.println("Error: " + error);
        try (PrintWriter pw = new PrintWriter(System.err, true)) {
            help.printHelp(pw, help.getWidth(),
                    PROGRAM_NAME + " -f <field> [--lang en|no|un]", header,
                    options, help.getLeftPadding(), help.getDescPadding(), footer, true);
            pw.flush();
        }
        System.exit(2);
    }
}
