// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

/**
 * class representing the character normalization
 * we want to do on query and indexed text.
 * Levels are strict subsets, so doing accent
 * removal means doing codepoint normalizing
 * and case normalizing also.
 */
// TODO: Missing author
public class NormalizeLevel {

    /**
     * The current levels are as follows:
     * NONE: no changes to input text
     * CODEPOINT: convert text into Unicode
     * Normalization Form Compatibility Composition
     * LOWERCASE: also convert text into lowercase letters
     * ACCENT: do both above and remove accents on characters
     */
    public enum Level {
        NONE, CODEPOINT, LOWERCASE, ACCENT
    }

    private boolean userSpecified = false;
    private Level level = Level.ACCENT;

    /**
     * Returns whether accents should be removed from text
     */
    public boolean doRemoveAccents() { return level == Level.ACCENT; }

    /**
     * Construct a default (full) normalizelevel,
     */
    public NormalizeLevel() {}

    /**
     * Construct for a specific level, possibly user specified
     *
     * @param level which level to use
     * @param fromUser whether this was specified by the user
     */
    public NormalizeLevel(Level level, boolean fromUser) {
        this.level = level;
        this.userSpecified = fromUser;
    }

    /**
     * Change the current level to CODEPOINT as inferred
     * by other features' needs.  If the current level
     * was user specified it will not change; also this
     * will not increase the level.
     */
    public void inferCodepoint() {
        if (userSpecified) {
            // ignore inferred changes if user specified something
            return;
        }
        // do not increase level
        if (level != Level.NONE) level = Level.CODEPOINT;
    }

    /**
     * Change the current level to LOWERCASE as inferred
     * by other features' needs.  If the current level
     * was user specified it will not change; also this
     * will not increase the level.
     */
    public void inferLowercase() {
        if (userSpecified) {
            // ignore inferred changes if user specified something
            return;
        }
        // do not increase level
        if (level == Level.NONE) return;
        if (level == Level.CODEPOINT) return;

        level = Level.LOWERCASE;
    }

    public Level getLevel() {
        return level;
    }

}
