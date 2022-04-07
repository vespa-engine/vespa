// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.application.pkg.ZipEntries.ZipEntryWithContent;

/**
 * @author freva
 */
public class ApplicationPackageDiff {

    public static byte[] diffAgainstEmpty(ApplicationPackage right) {
        byte[] emptyZip = new byte[]{80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        return diff(new ApplicationPackage(emptyZip), right);
    }

    public static byte[] diff(ApplicationPackage left, ApplicationPackage right) {
        return diff(left, right, 10 << 20, 1 << 20, 10 << 20);
    }

    static byte[] diff(ApplicationPackage left, ApplicationPackage right, int maxFileSizeToDiff, int maxDiffSizePerFile, int maxTotalDiffSize) {
        if (Arrays.equals(left.zippedContent(), right.zippedContent())) return "No diff\n".getBytes(StandardCharsets.UTF_8);

        Map<String, ZipEntryWithContent> leftContents = readContents(left, maxFileSizeToDiff);
        Map<String, ZipEntryWithContent> rightContents = readContents(right, maxFileSizeToDiff);

        StringBuilder sb = new StringBuilder();
        List<String> files = Stream.of(leftContents, rightContents)
                .flatMap(contents -> contents.keySet().stream())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        for (String file : files) {
            if (sb.length() > maxTotalDiffSize)
                sb.append("--- ").append(file).append('\n').append("Diff skipped: Total diff size >").append(maxTotalDiffSize).append("B)\n\n");
            else
                diff(Optional.ofNullable(leftContents.get(file)), Optional.ofNullable(rightContents.get(file)), maxDiffSizePerFile)
                        .ifPresent(diff -> sb.append("--- ").append(file).append('\n').append(diff).append('\n'));
        }

        return (sb.length() == 0 ? "No diff\n" : sb.toString()).getBytes(StandardCharsets.UTF_8);
    }

    private static Optional<String> diff(Optional<ZipEntryWithContent> left, Optional<ZipEntryWithContent> right, int maxDiffSizePerFile) {
        Optional<byte[]> leftContent = left.flatMap(ZipEntryWithContent::content);
        Optional<byte[]> rightContent = right.flatMap(ZipEntryWithContent::content);
        if (leftContent.isPresent() && rightContent.isPresent() && Arrays.equals(leftContent.get(), rightContent.get()))
            return Optional.empty();

        if (Stream.of(left, right).flatMap(Optional::stream).anyMatch(entry -> entry.content().isEmpty()))
            return Optional.of(String.format("Diff skipped: File too large (%s -> %s)\n",
                    left.map(e -> e.size() + "B").orElse("new file"), right.map(e -> e.size() + "B").orElse("file deleted")));

        if (Stream.of(leftContent, rightContent).flatMap(Optional::stream).anyMatch(c -> isBinary(c)))
            return Optional.of(String.format("Diff skipped: File is binary (%s -> %s)\n",
                    left.map(e -> e.size() + "B").orElse("new file"), right.map(e -> e.size() + "B").orElse("file deleted")));

        return LinesComparator.diff(
                leftContent.map(c -> lines(c)).orElseGet(List::of),
                rightContent.map(c -> lines(c)).orElseGet(List::of))
                .map(diff -> diff.length() > maxDiffSizePerFile ? "Diff skipped: Diff too large (" + diff.length() + "B)\n" : diff);
    }

    private static Map<String, ZipEntryWithContent> readContents(ApplicationPackage app, int maxFileSizeToDiff) {
        return ZipEntries.from(app.zippedContent(), entry -> true, maxFileSizeToDiff, false).asList().stream()
                         .collect(Collectors.toMap(ZipEntryWithContent::name, e -> e));
    }

    private static List<String> lines(byte[] data) {
        List<String> lines = new ArrayList<>(Math.min(16, data.length / 100));
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines;
    }

    private static boolean isBinary(byte[] data) {
        if (data.length == 0) return false;

        int lengthToCheck = Math.min(data.length, 10000);
        int ascii = 0;

        for (int i = 0; i < lengthToCheck; i++) {
            byte b = data[i];
            if (b < 0x9) return true;

            // TAB, newline/line feed, carriage return
            if (b == 0x9 || b == 0xA || b == 0xD) ascii++;
            else if (b >= 0x20 && b <= 0x7E) ascii++;
        }

        return (double) ascii / lengthToCheck < 0.95;
    }
}
