// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

import com.yahoo.io.IOUtils;
import com.yahoo.language.Language;
import sentencepiece.SentencepieceModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A SentencePiece model
 *
 * @author bratseth
 */
final class Model {

    final Path source;
    final Language language;
    final float minScore;
    final float maxScore;
    final Trie tokens = new Trie();
    final Map<Integer, Token> tokenId2Token = new HashMap<>();


    Model(Language language, Path path) {
        try {
            this.source = path;
            this.language = language;
            var sp = SentencepieceModel.ModelProto.parseFrom(IOUtils.readFileBytes(path.toFile()));
            float minScore = Float.MAX_VALUE;
            float maxScore = Float.MIN_VALUE;
            for (int i = 0; i < sp.getPiecesCount(); i++) {
                var piece = sp.getPieces(i);
                var type = toTokenType(piece.getType());
                var word = piece.getPiece();
                tokens.add(type, i, word, piece.getScore());
                tokenId2Token.put(i, new Token(word, type));
                minScore = Math.min(piece.getScore(), minScore);
                maxScore = Math.max(piece.getScore(), maxScore);
            }
            this.minScore = minScore;
            this.maxScore = maxScore;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read a SentencePiece model from " + path, e);
        }
    }

    private static TokenType toTokenType(SentencepieceModel.ModelProto.SentencePiece.Type type) {
        switch (type) {
            case USER_DEFINED : return TokenType.userDefined;
            case UNKNOWN : return TokenType.unknown;
            case NORMAL : return TokenType.text;
            case CONTROL : return TokenType.control;
            case UNUSED : return TokenType.unused;
            default : throw new IllegalArgumentException("Unknown token type " + type);
        }
    }

    @Override
    public String toString() {
        return "SentencePiece model for " + language + ": '" + source + "'";
    }

    record Token(String text, TokenType type) { }

}
