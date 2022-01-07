// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import java.util.*;

/**
 * Simple command line parser, handling multiple arguments and multiple unary and binary switches starting with -.
 *
 * Terms used:
 *
 * progname -binaryswitch foo -unaryswitch argument1 argument2
 *
 * @author vegardh
 */
public class CommandLineParser {

    private static final HashSet<String> helpSwitches = new HashSet<>();

    private final List<String> inputStrings;
    private final Map<String, String> legalUnarySwitches = new HashMap<>();
    private final Map<String, String> legalBinarySwitches = new HashMap<>();
    private final List<String> unarySwitches = new ArrayList<>();
    private final Map<String, String> binarySwitches = new HashMap<>();
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> requiredUnarySwitches = new HashMap<>();
    private final Map<String, String> requiredBinarySwitches = new HashMap<>();
    private String progname = "progname";
    private String argumentExplanation;
    private int minArguments = 0;
    private int maxArguments = Integer.MAX_VALUE;
    private String helpText;
    private boolean helpSwitchUsed = false;

    static {
        helpSwitches.add("-h");
        helpSwitches.add("-help");
        helpSwitches.add("--help");
        helpSwitches.add("-?");
    }

    public CommandLineParser(String[] cmds) {
        inputStrings = Arrays.asList(cmds);
    }

    public CommandLineParser(String progname, String[] cmds) {
        this.progname=progname;
        inputStrings = Arrays.asList(cmds);
    }

    /**
     * Parses the command line
     * @throws IllegalArgumentException if a parse error occured
     */
    public void parse() {
        for (Iterator<String> it = inputStrings.iterator() ; it.hasNext() ; ) {
            String i = it.next();
            if (isHelpSwitch(i)) {
                helpSwitchUsed = true;
                usageAndThrow();
            }
            if (i.startsWith("-")) {
                if (!isLegalSwitch(i)) {
                    usageAndThrow();
                } else if (legalUnarySwitches.keySet().contains(i)) {
                    unarySwitches.add(i);
                } else if (legalBinarySwitches.keySet().contains(i)) {
                    if (!it.hasNext()) {
                        throw new IllegalArgumentException(i+ " requires value");
                    } else {
                        String val = it.next();
                        binarySwitches.put(i, val);
                    }
                }
            } else {
                arguments.add(i);
            }
        }
        if (!requiredUnarySwitches.isEmpty() && !getUnarySwitches().containsAll(requiredUnarySwitches.keySet())) {
            usageAndThrow();
        }
        if (!requiredBinarySwitches.isEmpty() && !getBinarySwitches().keySet().containsAll(requiredBinarySwitches.keySet())) {
            usageAndThrow();
        }
        if (getArguments().size()<minArguments || getArguments().size()>maxArguments) {
            usageAndThrow();
        }
    }

    private boolean isHelpSwitch(String i) {
        return helpSwitches.contains(i);
    }

    void usageAndThrow() {
        StringBuffer error_sb = new StringBuffer();
        error_sb.append("\nusage: ").append(progname).append(" ");
        if (argumentExplanation!=null) {
            error_sb.append(argumentExplanation);
        }
        if (!legalUnarySwitches.isEmpty()) error_sb.append("\nSwitches:\n");
        error_sb.append("-h This help text\n");
        for (Map.Entry<String, String> e : legalUnarySwitches.entrySet()) {
            error_sb.append(e.getKey()).append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<String, String> e : legalBinarySwitches.entrySet()) {
            error_sb.append(e.getKey()).append(" <").append(e.getValue()).append(">\n");
        }
        if (helpText!=null) {
            error_sb.append("\n").append(helpText).append("\n");
        }
        throw new IllegalArgumentException(error_sb.toString());
    }

    private boolean isLegalSwitch(String s) {
        return (legalUnarySwitches.containsKey(s) || legalBinarySwitches.containsKey(s));
    }

    /**
     * Add a legal unary switch such as "-d"
     */
    public void addLegalUnarySwitch(String s, String explanation) {
        if (legalBinarySwitches.containsKey(s)) {
            throw new IllegalArgumentException(s +" already added as a binary switch");
        }
        legalUnarySwitches.put(s, explanation);
    }

    public void addLegalUnarySwitch(String s) {
        addLegalUnarySwitch(s, null);
    }

    /**
     * Adds a required switch, such as -p
     */
    public void addRequiredUnarySwitch(String s, String explanation) {
        addLegalUnarySwitch(s, explanation);
        requiredUnarySwitches.put(s, explanation);
    }

    /**
     * Add a legal binary switch such as "-f /foo/bar"
     */
    public void addLegalBinarySwitch(String s, String explanation) {
        if (legalUnarySwitches.containsKey(s)) {
            throw new IllegalArgumentException(s +" already added as a unary switch");
        }
        legalBinarySwitches.put(s, explanation);
    }

    /**
     * Adds a legal binary switch without explanation
     */
    public void addLegalBinarySwitch(String s) {
        addLegalBinarySwitch(s, null);
    }

    /**
     * Adds a required binary switch
     */
    public void addRequiredBinarySwitch(String s, String explanation) {
        addLegalBinarySwitch(s, explanation);
        requiredBinarySwitches.put(s, explanation);
    }

    /**
     * The unary switches that were given on the command line
     */
    public List<String> getUnarySwitches() {
        return unarySwitches;
    }

    /**
     * The binary switches that were given on the command line
     */
    public Map<String, String> getBinarySwitches() {
        return binarySwitches;
    }

    /**
     * All non-switch strings that were given on the command line
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Sets the argument explanation used in printing method, i.e. "names,..."
     */
    public void setArgumentExplanation(String argumentExplanation) {
        this.argumentExplanation = argumentExplanation;
    }

    public void setExtendedHelpText(String text) {
        this.helpText=text;
    }

    public String getHelpText() {
        return helpText;
    }

    /**
     * Sets minimum number of required arguments
     */
    public void setMinArguments(int minArguments) {
        this.minArguments = minArguments;
    }

    /**
     * Sets the maximum number of allowed arguments
     */
    public void setMaxArguments(int maxArguments) {
        this.maxArguments = maxArguments;
    }

    public boolean helpSwitchUsed() {
        return helpSwitchUsed;
    }

}
