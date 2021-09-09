/*
 * Line based variant of Apache commons-text StringComparator
 * https://github.com/apache/commons-text/blob/3b1a0a5a47ee9fa2b36f99ca28e2e1d367a10a11/src/main/java/org/apache/commons/text/diff/StringsComparator.java
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.collections.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * It is guaranteed that the comparisons will always be done as
 * {@code o1.equals(o2)} where {@code o1} belongs to the first
 * sequence and {@code o2} belongs to the second sequence. This can
 * be important if subclassing is used for some elements in the first
 * sequence and the {@code equals} method is specialized.
 * </p>
 * <p>
 * Comparison can be seen from two points of view: either as giving the smallest
 * modification allowing to transform the first sequence into the second one, or
 * as giving the longest sequence which is a subsequence of both initial
 * sequences. The {@code equals} method is used to compare objects, so any
 * object can be put into sequences. Modifications include deleting, inserting
 * or keeping one object, starting from the beginning of the first sequence.
 * </p>
 * <p>
 * This class implements the comparison algorithm, which is the very efficient
 * algorithm from Eugene W. Myers
 * <a href="http://www.cis.upenn.edu/~bcpierce/courses/dd/papers/diff.ps">
 * An O(ND) Difference Algorithm and Its Variations</a>.
 */
public class LinesComparator {

    private final List<String> left;
    private final List<String> right;
    private final int[] vDown;
    private final int[] vUp;

    private LinesComparator(List<String> left, List<String> right) {
        this.left = left;
        this.right = right;

        int size = left.size() + right.size() + 2;
        vDown = new int[size];
        vUp = new int[size];
    }

    private void buildScript(int start1, int end1, int start2, int end2, List<Pair<LineOperation, String>> result) {
        Snake middle = getMiddleSnake(start1, end1, start2, end2);

        if (middle == null
                || middle.start == end1 && middle.diag == end1 - end2
                || middle.end == start1 && middle.diag == start1 - start2) {

            int i = start1;
            int j = start2;
            while (i < end1 || j < end2) {
                if (i < end1 && j < end2 && left.get(i).equals(right.get(j))) {
                    result.add(new Pair<>(LineOperation.keep, left.get(i)));
                    ++i;
                    ++j;
                } else {
                    if (end1 - start1 > end2 - start2) {
                        result.add(new Pair<>(LineOperation.delete, left.get(i)));
                        ++i;
                    } else {
                        result.add(new Pair<>(LineOperation.insert, right.get(j)));
                        ++j;
                    }
                }
            }

        } else {
            buildScript(start1, middle.start, start2, middle.start - middle.diag, result);
            for (int i = middle.start; i < middle.end; ++i) {
                result.add(new Pair<>(LineOperation.keep, left.get(i)));
            }
            buildScript(middle.end, end1, middle.end - middle.diag, end2, result);
        }
    }

    private Snake buildSnake(final int start, final int diag, final int end1, final int end2) {
        int end = start;
        while (end - diag < end2 && end < end1 && left.get(end).equals(right.get(end - diag))) {
            ++end;
        }
        return new Snake(start, end, diag);
    }

    private Snake getMiddleSnake(final int start1, final int end1, final int start2, final int end2) {
        final int m = end1 - start1;
        final int n = end2 - start2;
        if (m == 0 || n == 0) {
            return null;
        }

        final int delta = m - n;
        final int sum = n + m;
        final int offset = (sum % 2 == 0 ? sum : sum + 1) / 2;
        vDown[1 + offset] = start1;
        vUp[1 + offset] = end1 + 1;

        for (int d = 0; d <= offset; ++d) {
            // Down
            for (int k = -d; k <= d; k += 2) {
                // First step

                final int i = k + offset;
                if (k == -d || k != d && vDown[i - 1] < vDown[i + 1]) {
                    vDown[i] = vDown[i + 1];
                } else {
                    vDown[i] = vDown[i - 1] + 1;
                }

                int x = vDown[i];
                int y = x - start1 + start2 - k;

                while (x < end1 && y < end2 && left.get(x).equals(right.get(y))) {
                    vDown[i] = ++x;
                    ++y;
                }
                // Second step
                if (delta % 2 != 0 && delta - d <= k && k <= delta + d) {
                    if (vUp[i - delta] <= vDown[i]) { // NOPMD
                        return buildSnake(vUp[i - delta], k + start1 - start2, end1, end2);
                    }
                }
            }

            // Up
            for (int k = delta - d; k <= delta + d; k += 2) {
                // First step
                final int i = k + offset - delta;
                if (k == delta - d || k != delta + d && vUp[i + 1] <= vUp[i - 1]) {
                    vUp[i] = vUp[i + 1] - 1;
                } else {
                    vUp[i] = vUp[i - 1];
                }

                int x = vUp[i] - 1;
                int y = x - start1 + start2 - k;
                while (x >= start1 && y >= start2 && left.get(x).equals(right.get(y))) {
                    vUp[i] = x--;
                    y--;
                }
                // Second step
                if (delta % 2 == 0 && -d <= k && k <= d) {
                    if (vUp[i] <= vDown[i + delta]) { // NOPMD
                        return buildSnake(vUp[i], k + start1 - start2, end1, end2);
                    }
                }
            }
        }

        // this should not happen
        throw new RuntimeException("Internal Error");
    }

    private static class Snake {
        private final int start;
        private final int end;
        private final int diag;

        private Snake(int start, int end, int diag) {
            this.start = start;
            this.end = end;
            this.diag = diag;
        }
    }

    private enum LineOperation {
        keep("  "), delete("- "), insert("+ ");
        private final String prefix;
        LineOperation(String prefix) {
            this.prefix = prefix;
        }
    }

    /** @return line-based diff in unified format. Empty contents are identical. */
    public static Optional<String> diff(List<String> left, List<String> right) {
        List<Pair<LineOperation, String>> changes = new ArrayList<>(Math.max(left.size(), right.size()));
        new LinesComparator(left, right).buildScript(0, left.size(), 0, right.size(), changes);

        // After we have a list of keep, delete, insert for each line from left and right input, generate a unified
        // diff by printing all delete and insert operations with contextLines of keep lines before and after.
        // Make sure the change windows are non-overlapping by continuously growing the window
        int contextLines = 3;
        List<int[]> changeWindows = new ArrayList<>();
        int[] last = null;
        for (int i = 0, leftIndex = 0, rightIndex = 0; i < changes.size(); i++) {
            if (changes.get(i).getFirst() == LineOperation.keep) {
                leftIndex++;
                rightIndex++;
                continue;
            }

            // We found a new change and it is too far away from the previous change to be combined into the same window
            if (last == null || i - last[1] > contextLines) {
                last = new int[]{Math.max(i - contextLines, 0), Math.min(i + contextLines + 1, changes.size()), Math.max(leftIndex - contextLines, 0), Math.max(rightIndex - contextLines, 0)};
                changeWindows.add(last);
            } else // otherwise, extend the previous change window
                last[1] = Math.min(i + contextLines + 1, changes.size());

            if (changes.get(i).getFirst() == LineOperation.delete) leftIndex++;
            else rightIndex++;
        }
        if (changeWindows.isEmpty()) return Optional.empty();

        StringBuilder sb = new StringBuilder();
        for (int[] changeWindow: changeWindows) {
            int start = changeWindow[0], end = changeWindow[1], leftIndex = changeWindow[2], rightIndex = changeWindow[3];
            Map<LineOperation, Long> counts = IntStream.range(start, end)
                    .mapToObj(i -> changes.get(i).getFirst())
                    .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
            sb.append("@@ -").append(leftIndex + 1).append(',').append(end - start - counts.getOrDefault(LineOperation.insert, 0L))
                    .append(" +").append(rightIndex + 1).append(',').append(end - start - counts.getOrDefault(LineOperation.delete, 0L)).append(" @@\n");
            for (int i = start; i < end; i++)
                sb.append(changes.get(i).getFirst().prefix).append(changes.get(i).getSecond()).append('\n');
        }
        return Optional.of(sb.toString());
    }
}
