// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.document.TokensMode;

/**
 * The linguistics settings which can be given for one side (indexing or searching) of a field,
 * as extracted when parsing a "linguistics" block.  Do not put advanced logic here!
 *
 * @author arnej27959
 */
public class ParsedLinguistics extends ParsedBlock {

    private String profile = null;
    private TokensMode tokens = null;
    private boolean opened = false;

    public ParsedLinguistics(String fieldName, String blockType) {
        super(fieldName, blockType);
    }

    public String profile() { return profile; }
    public TokensMode tokens() { return tokens; }

    /** Returns whether the block holding these settings has been opened. */
    public boolean isOpened() { return opened; }

    public void setProfile(String profile) {
        verifyThat(this.profile == null, "already has profile", this.profile);
        this.profile = profile;
    }

    public void setTokens(TokensMode tokens) {
        verifyThat(this.tokens == null, "already has tokens", this.tokens == null ? "" : this.tokens.getName());
        this.tokens = tokens;
    }

    /** Records that the block holding these settings is opened. There can only be one of each. */
    public void openBlock() {
        verifyThat(! opened, "is given more than once");
        opened = true;
    }

    /** Verifies that no profile is set in this, for the given reason. */
    public void verifyNoProfile(String reason) {
        verifyThat(profile == null, reason);
    }

    /** Sets any setting not set in this from the given settings. */
    public void mergeDefaultsFrom(ParsedLinguistics other) {
        if (profile == null) profile = other.profile();
        if (tokens == null) tokens = other.tokens();
    }

}
