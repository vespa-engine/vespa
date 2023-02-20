// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Badges {

    // https://chrishewett.com/blog/calculating-text-width-programmatically/ thank you!
    private static final String characterWidths = "[[\" \",35.156],[\"!\",39.355],[\"\\\"\",45.898],[\"#\",81.836],[\"$\",63.574],[\"%\",107.617],[\"&\",72.656],[\"'\",26.855],[\"(\",45.41],[\")\",45.41],[\"*\",63.574],[\"+\",81.836],[\",\",36.377],[\"-\",45.41],[\".\",36.377],[\"/\",45.41],[\"0\",63.574],[\"1\",63.574],[\"2\",63.574],[\"3\",63.574],[\"4\",63.574],[\"5\",63.574],[\"6\",63.574],[\"7\",63.574],[\"8\",63.574],[\"9\",63.574],[\":\",45.41],[\";\",45.41],[\"<\",81.836],[\"=\",81.836],[\">\",81.836],[\"?\",54.541],[\"@\",100],[\"A\",68.359],[\"B\",68.555],[\"C\",69.824],[\"D\",77.051],[\"E\",63.232],[\"F\",57.471],[\"G\",77.539],[\"H\",75.146],[\"I\",42.09],[\"J\",45.459],[\"K\",69.287],[\"L\",55.664],[\"M\",84.277],[\"N\",74.805],[\"O\",78.711],[\"P\",60.303],[\"Q\",78.711],[\"R\",69.531],[\"S\",68.359],[\"T\",61.621],[\"U\",73.193],[\"V\",68.359],[\"W\",98.877],[\"X\",68.506],[\"Y\",61.523],[\"Z\",68.506],[\"[\",45.41],[\"\\\\\",45.41],[\"]\",45.41],[\"^\",81.836],[\"_\",63.574],[\"`\",63.574],[\"a\",60.059],[\"b\",62.305],[\"c\",52.1],[\"d\",62.305],[\"e\",59.57],[\"f\",35.156],[\"g\",62.305],[\"h\",63.281],[\"i\",27.441],[\"j\",34.424],[\"k\",59.18],[\"l\",27.441],[\"m\",97.266],[\"n\",63.281],[\"o\",60.693],[\"p\",62.305],[\"q\",62.305],[\"r\",42.676],[\"s\",52.1],[\"t\",39.404],[\"u\",63.281],[\"v\",59.18],[\"w\",81.836],[\"x\",59.18],[\"y\",59.18],[\"z\",52.539],[\"{\",63.477],[\"|\",45.41],[\"}\",63.477],[\"~\",81.836],[\"_median\",63.281]]";
    private static final double[] widths = new double[128]; // 0-94 hold widths for corresponding chars (+32); 95 holds the fallback width.

    static {
        SlimeUtils.jsonToSlimeOrThrow(characterWidths).get()
                  .traverse((ArrayTraverser) (i, pair) -> {
                      if (i < 95)
                          assert Arrays.equals(new byte[]{(byte) (i + 32)}, pair.entry(0).asUtf8()) : i + ": " + pair.entry(0).asString();
                      else
                          assert "_median".equals(pair.entry(0).asString());

                      widths[i] = pair.entry(1).asDouble();
                  });
    }

    /** Character pixel width of a 100px size Verdana font rendering of the given code point, for code points in the range [32, 126]. */
    public static double widthOf(int codePoint) {
        return 32 <= codePoint && codePoint <= 126 ? widths[codePoint - 32] : widths[95];
    }

    /** Computes an approximate pixel width of the given size Verdana font rendering of the given string, ignoring kerning. */
    public static double widthOf(String text, int size) {
        return text.codePoints().mapToDouble(Badges::widthOf).sum() * (size - 0.5) / 100;
    }

    /** Computes an approximate pixel width of a 11px size Verdana font rendering of the given string, ignoring kerning. */
    public static double widthOf(String text) {
        return widthOf(text, 11);
    }

    static String colorOf(Run run, Optional<RunStatus> previous) {
        return switch (run.status()) {
            case running -> switch (previous.orElse(RunStatus.success)) {
                case success -> "url(#run-on-success)";
                case aborted, noTests -> "url(#run-on-warning)";
                default -> "url(#run-on-failure)";
            };
            case success -> success;
            case aborted, noTests -> warning;
            default -> failure;
        };
    }

    static String nameOf(JobType type) {
        return type.isTest() ? type.isProduction() ? "test"
                                                   : type.jobName()
                             : type.jobName().replace("production-", "");
    }

    static final double xPad = 6;
    static final double logoSize = 16;
    static final String dark = "#404040";
    static final String success = "#00f844";
    static final String running = "#ab83ff";
    static final String failure = "#bf103c";
    static final String warning = "#bd890b";

    static void addText(List<String> texts, String text, double x, double width) {
        addText(texts, text, x, width, 11);
    }

    static void addText(List<String> texts, String text, double x, double width, int size) {
        texts.add("        <text font-size='" + size + "' x='" + (x + 0.5) + "' y='" + (15) + "' fill='#000' fill-opacity='.4' textLength='" + width + "'>" + text + "</text>\n");
        texts.add("        <text font-size='" + size + "' x='" + x + "' y='" + (14) + "' fill='#fff' textLength='" + width + "'>" + text + "</text>\n");
    }

    static void addShade(List<String> sections, double x, double width) {
        sections.add("        <rect x='" + (x - 6) + "' rx='3' width='" + (width + 6) + "' height='20' fill='url(#shade)'/>\n");
    }

    static void addShadow(List<String> sections, double x) {
        sections.add("        <rect x='" + (x - 6) + "' rx='3' width='" + 8 + "' height='20' fill='url(#shadow)'/>\n");
    }

    static String historyBadge(ApplicationId id, JobStatus status, int length) {
        List<String> sections = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        double x = 0;
        String text = id.toFullString();
        double textWidth = widthOf(text);
        double dx = xPad + logoSize + xPad + textWidth + xPad;

        addShade(sections, x, dx);
        sections.add("        <rect width='" + dx + "' height='20' fill='" + dark + "'/>\n");
        addText(texts, text, x + (xPad + logoSize + dx) / 2, textWidth);
        x += dx;

        if (status.lastTriggered().isEmpty())
            return badge(sections, texts, x);

        Run lastTriggered = status.lastTriggered().get();
        List<Run> runs = status.runs().descendingMap().values().stream()
                               .filter(Run::hasEnded)
                               .skip(1)
                               .limit(length)
                               .toList();

        text = lastTriggered.id().type().jobName();
        textWidth = widthOf(text);
        dx = xPad + textWidth + xPad;
        addShade(sections, x, dx);
        sections.add("        <rect x='" + (x - 6) + "' rx='3' width='" + (dx + 6) + "' height='20' fill='" + colorOf(lastTriggered, status.lastStatus()) + "'/>\n");
        addShadow(sections, x + dx);
        addText(texts, text, x + dx / 2, textWidth);
        x += dx;

        dx = xPad * (192.0 / (32 + runs.size())); // Broader sections with shorter history.
        for (Run run : runs) {
            addShade(sections, x, dx);
            sections.add("        <rect x='" + (x - 6) + "' rx='3' width='" + (dx + 6) + "' height='20' fill='" + colorOf(run, Optional.empty()) + "'/>\n");
            addShadow(sections, x + dx);
            dx *= Math.pow(0.3, 1.0 / (runs.size() + 8)); // Gradually narrowing sections with age.
            x += dx;
        }
        Collections.reverse(sections);

        return badge(sections, texts, x);
    }

    static String overviewBadge(ApplicationId id, JobList jobs) {
        // Put production tests right after their deployments, for a more compact rendering.
        List<Run> runs = new ArrayList<>(jobs.lastTriggered().asList());
        boolean anyTest = false;
        for (int i = 0; i < runs.size(); i++) {
            Run run = runs.get(i);
            if (run.id().type().isProduction() && run.id().type().isTest()) {
                anyTest = true;
                int j = i;
                while ( ! runs.get(j - 1).id().type().zone().equals(run.id().type().zone()))
                    runs.set(j, runs.get(--j));
                runs.set(j, run);
            }
        }

        List<String> sections = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        double x = 0;
        String text = id.toFullString();
        double textWidth = widthOf(text);
        double dx = xPad + logoSize + xPad + textWidth + xPad;
        double tdx = xPad + widthOf("test");

        addShade(sections, 0, dx);
        sections.add("        <rect width='" + dx + "' height='20' fill='" + dark + "'/>\n");
        addText(texts, text, x + (xPad + logoSize + dx) / 2, textWidth);
        x += dx;

        for (int i = 0; i < runs.size(); i++) {
            Run run = runs.get(i);
            Run test = i + 1 < runs.size() ? runs.get(i + 1) : null;
            if (test == null || ! test.id().type().isTest() || ! test.id().type().isProduction())
                test = null;

            boolean isTest = run.id().type().isTest() && run.id().type().isProduction();
            text = nameOf(run.id().type());
            textWidth = widthOf(text, isTest ? 9 : 11);
            dx = xPad + textWidth + (isTest ? 0 : xPad);
            Optional<RunStatus> previous = jobs.get(run.id().job()).flatMap(JobStatus::lastStatus);

            addText(texts, text, x + (dx - (isTest ? xPad : 0)) / 2, textWidth, isTest ? 9 : 11);

            // Add "deploy" when appropriate
            if ( ! run.id().type().isTest() && anyTest) {
                String deploy = "deploy";
                textWidth = widthOf(deploy, 9);
                addText(texts, deploy, x + dx + textWidth / 2, textWidth, 9);
                dx += textWidth + xPad;
            }

            // Add shade across zone section.
            if ( ! (isTest))
                addShade(sections, x, dx + (test != null ? tdx : 0));

            // Add colored section for job ...
            if (test == null)
                sections.add("        <rect x='" + (x - 16) + "' rx='3' width='" + (dx + 16) + "' height='20' fill='" + colorOf(run, previous) + "'/>\n");
            // ... with a slant if a test is next.
            else
                sections.add("        <polygon points='" + (x - 6) + " 0 " + (x - 6) + " 20 " + (x + dx - 7) + " 20 " + (x + dx + 1) + " 0' fill='" + colorOf(run, previous) + "'/>\n");

            // Cast a shadow onto the next zone ...
            if (test == null)
                addShadow(sections, x + dx);

            x += dx;
        }
        Collections.reverse(sections);

        return badge(sections, texts, x);
    }

    static String badge(List<String> sections, List<String> texts, double width) {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='20' role='img' aria-label='Deployment Status'>\n" +
               "    <title>Deployment Status</title>\n" +
               // Lighting to give the badge a 3d look--dispersion at the top, shadow at the bottom.
               "    <linearGradient id='light' x2='0' y2='100%'>\n" +
               "        <stop offset='0'  stop-color='#fff' stop-opacity='.5'/>\n" +
               "        <stop offset='.1' stop-color='#fff' stop-opacity='.15'/>\n" +
               "        <stop offset='.9' stop-color='#000' stop-opacity='.15'/>\n" +
               "        <stop offset='1'  stop-color='#000' stop-opacity='.5'/>\n" +
               "    </linearGradient>\n" +
               // Dispersed light at the left of the badge.
               "    <linearGradient id='left-light' x2='100%' y2='0'>\n" +
               "        <stop offset='0' stop-color='#fff' stop-opacity='.3'/>\n" +
               "        <stop offset='.5' stop-color='#fff' stop-opacity='.1'/>\n" +
               "        <stop offset='1' stop-color='#fff' stop-opacity='.0'/>\n" +
               "    </linearGradient>\n" +
               // Shadow at the right of the badge.
               "    <linearGradient id='right-shadow' x2='100%' y2='0'>\n" +
               "        <stop offset='0' stop-color='#000' stop-opacity='.0'/>\n" +
               "        <stop offset='.5' stop-color='#000' stop-opacity='.1'/>\n" +
               "        <stop offset='1' stop-color='#000' stop-opacity='.3'/>\n" +
               "    </linearGradient>\n" +
               // Shadow to highlight the border between sections, without using a heavy separator.
               "    <linearGradient id='shadow' x2='100%' y2='0'>\n" +
               "        <stop offset='0' stop-color='#222' stop-opacity='.3'/>\n" +
               "        <stop offset='.625' stop-color='#555' stop-opacity='.3'/>\n" +
               "        <stop offset='.9' stop-color='#555' stop-opacity='.05'/>\n" +
               "        <stop offset='1' stop-color='#555' stop-opacity='.0'/>\n" +
               "    </linearGradient>\n" +
               // Weak shade across each panel to highlight borders further.
               "    <linearGradient id='shade' x2='100%' y2='0'>\n" +
               "        <stop offset='0' stop-color='#000' stop-opacity='.20'/>\n" +
               "        <stop offset='0.05' stop-color='#000' stop-opacity='.10'/>\n" +
               "        <stop offset='1' stop-color='#000' stop-opacity='.0'/>\n" +
               "    </linearGradient>\n" +
               // Running color sloshing back and forth on top of the failure color.
               "    <linearGradient id='run-on-failure' x1='40%' x2='80%' y2='0%'>\n" +
               "        <stop offset='0' stop-color='" + running + "' />\n" +
               "        <stop offset='1' stop-color='" + failure + "' />\n" +
               "        <animate attributeName='x1' values='-110%;150%;20%;-110%' dur='6s' repeatCount='indefinite' />\n" +
               "        <animate attributeName='x2' values='-10%;250%;120%;-10%' dur='6s' repeatCount='indefinite' />\n" +
               "    </linearGradient>\n" +
               // Running color sloshing back and forth on top of the warning color.
               "    <linearGradient id='run-on-warning' x1='40%' x2='80%' y2='0%'>\n" +
               "        <stop offset='0' stop-color='" + running + "' />\n" +
               "        <stop offset='1' stop-color='" + warning + "' />\n" +
               "        <animate attributeName='x1' values='-110%;150%;20%;-110%' dur='6s' repeatCount='indefinite' />\n" +
               "        <animate attributeName='x2' values='-10%;250%;120%;-10%' dur='6s' repeatCount='indefinite' />\n" +
               "    </linearGradient>\n" +
               // Running color sloshing back and forth on top of the success color.
               "    <linearGradient id='run-on-success' x1='40%' x2='80%' y2='0%'>\n" +
               "        <stop offset='0' stop-color='" + running + "' />\n" +
               "        <stop offset='1' stop-color='" + success + "' />\n" +
               "        <animate attributeName='x1' values='-110%;150%;20%;-110%' dur='6s' repeatCount='indefinite' />\n" +
               "        <animate attributeName='x2' values='-10%;250%;120%;-10%' dur='6s' repeatCount='indefinite' />\n" +
               "    </linearGradient>\n" +
               // Clipping to give the badge rounded corners.
               "    <clipPath id='rounded'>\n" +
               "        <rect width='" + width + "' height='20' rx='3' fill='#fff'/>\n" +
               "    </clipPath>\n" +
               // Badge section backgrounds with status colors and shades for distinction.
               "    <g clip-path='url(#rounded)'>\n" +
               String.join("", sections) +
               "        <rect width='" + 2 + "' height='20' fill='url(#left-light)'/>\n" +
               "        <rect x='" + (width - 2) + "' width='" + 2 + "' height='20' fill='url(#right-shadow)'/>\n" +
               "        <rect width='" + width + "' height='20' fill='url(#light)'/>\n" +
               "    </g>\n" +
               "    <g fill='#fff' text-anchor='middle' font-family='Verdana,Geneva,DejaVu Sans,sans-serif' text-rendering='geometricPrecision' font-size='11'>\n" +
               // The vespa.ai logo (with a slightly coloured shadow)!
               "        <svg x='" + (xPad + 0.5) + "' y='" + ((20 - logoSize) / 2 + 1) + "' width='" + logoSize + "' height='" + logoSize + "' viewBox='0 0 150 150'>\n" +
               "            <polygon fill='#402a14' fill-opacity='0.5' points='84.84 10 34.1 44.46 34.1 103.78 84.84 68.02 135.57 103.78 135.57 44.46 84.84 10'/>\n" +
               "            <polygon fill='#402a14' fill-opacity='0.5' points='84.84 68.02 84.84 10 135.57 44.46 135.57 103.78 84.84 68.02'/>\n" +
               "            <polygon fill='#061a29' fill-opacity='0.5' points='65.07 81.99 14.34 46.22 14.34 105.54 65.07 140 115.81 105.54 115.81 46.22 65.07 81.99'/>\n" +
               "            <polygon fill='#061a29' fill-opacity='0.5' points='65.07 81.99 65.07 140 14.34 105.54 14.34 46.22 65.07 81.99'/>\n" +
               "        </svg>\n" +
               "        <svg x='" + xPad + "' y='" + ((20 - logoSize) / 2) + "' width='" + logoSize + "' height='" + logoSize + "' viewBox='0 0 150 150'>\n" +
               "            <linearGradient id='yellow-shaded' x1='91.17' y1='44.83' x2='136.24' y2='73.4'  gradientUnits='userSpaceOnUse'>\n" +
               "                <stop offset='0.01' stop-color='#c6783e'/>\n" +
               "                <stop offset='0.54' stop-color='#ff9750'/>\n" +
               "            </linearGradient>\n" +
               "            <linearGradient id='blue-shaded' x1='60.71' y1='104.56' x2='-15.54' y2='63' gradientUnits='userSpaceOnUse'>\n" +
               "                <stop offset='0' stop-color='#005a8e'/>\n" +
               "                <stop offset='0.54' stop-color='#1a7db6'/>\n" +
               "            </linearGradient>\n" +
               "            <polygon fill='#ff9d4b' points='84.84 10 34.1 44.46 34.1 103.78 84.84 68.02 135.57 103.78 135.57 44.46 84.84 10'/>\n" +
               "            <polygon fill='url(#yellow-shaded)' points='84.84 68.02 84.84 10 135.57 44.46 135.57 103.78 84.84 68.02'/>\n" +
               "            <polygon fill='#1a7db6' points='65.07 81.99 14.34 46.22 14.34 105.54 65.07 140 115.81 105.54 115.81 46.22 65.07 81.99'/>\n" +
               "            <polygon fill='url(#blue-shaded)' points='65.07 81.99 65.07 140 14.34 105.54 14.34 46.22 65.07 81.99'/>\n" +
               "        </svg>\n" +
               // Application ID and job names.
               String.join("", texts) +
               "    </g>\n" +
               "</svg>\n";
    }

}
