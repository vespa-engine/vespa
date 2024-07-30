package ai.vespa.schemals.parser;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class acts as a proxy for the generated {@link SchemaParserLexer} from CongoCC.
 * IntelliJ syntax highlighting is lexer-based, requiring an incremental lexer extending {@link com.intellij.lexer.Lexer}.
 * This class emulates the incremental behavior by constructing a fake starting token ending at the requested startOffset.
 * I have not found a way around creating a new {@link SchemaParserLexer} object at each call to
 * start, because the CongoCC lexer builds a cache and data structures based on the string content it first receives.
 */
public class SchemaIntellijLexer extends LexerBase {
    int endOffset;
    CharSequence buffer;
    SchemaParserLexer schemaLexer;
    Token currentToken;
    boolean isInWhitespace = false;
    int whitespacePointer = 0;
    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.endOffset = endOffset;
        this.buffer = buffer;

        schemaLexer = new SchemaParserLexer(buffer);
        currentToken = new Token();
        currentToken.setBeginOffset(startOffset);
        currentToken.setEndOffset(startOffset);
        isInWhitespace = false;
        advance();
    }

    @Override
    public int getState() {
        return schemaLexer.lexicalState.ordinal();
    }

    @Override
    public @Nullable IElementType getTokenType() {
        if (getTokenStart() == endOffset) return null;
        Token.TokenType type = currentToken.getType();

        // some type keywords do not get recognized as a token type in lexer
        if (currentToken.toString().equals("int")
        ||  currentToken.toString().equals("bool")
        || currentToken.toString().equals("byte")
        || currentToken.toString().equals("position")
        || currentToken.toString().equals("predicate")) {
            type = Token.TokenType.LONG_KEYWORD;
        }
        switch (type) {
            case ALIAS:
            case ANNOTATION:
            case APPROXIMATE_THRESHOLD:
            case AS:
            case ATTRIBUTE:
            case BOLDING:
            case CONSTANT:
            case CONSTANTS:
            case DIVERSITY:
            case DOCUMENT:
            case DOCUMENT_SUMMARY:
            case FIELD:
            case FIELDS:
            case FIELDSET:
            case FIRST_PHASE:
            case FUNCTION:
            case GLOBAL_PHASE:
            case ID:
            case IGNORE_DEFAULT_RANK_FEATURES:
            case IMPORT:
            case INDEX:
            case INDEXING:
            case INHERITS:
            case INPUTS:
            case MACRO:
            case MATCH:
            case MATCH_PHASE:
            case MUTATE:
            case NORMALIZING:
            case NUM_SEARCH_PARTITIONS:
            case NUM_THREADS_PER_SEARCH:
            case ONNX_MODEL:
            case POST_FILTER_THRESHOLD:
            case QUERY_COMMAND:
            case RANK:
            case RANK_PROFILE:
            case RANK_PROPERTIES:
            case RANK_TYPE:
            case RAW_AS_BASE64_IN_SUMMARY:
            case SCHEMA:
            case SEARCH:
            case SECOND_PHASE:
            case SORTING:
            case STEMMING:
            case STRICT:
            case STRUCT:
            case STRUCT_FIELD:
            case SUMMARY:
            case SUMMARY_TO:
            case TARGET_HITS_MAX_ADJUSTMENT_FACTOR:
            case TERMWISE_LIMIT:
            case TYPE:
            case WEIGHT:
            case WEIGHTEDSET:
                return SchemaTypes.KEYWORD;
            case MAP:
            case ARRAY:
            case STRING_KEYWORD:
            case ANNOTATIONREFERENCE:
            case TENSOR_TYPE:
            case REFERENCE:
            case FLOAT_KEYWORD:
            case LONG_KEYWORD:
            case URI:
            case RAW:
                return SchemaTypes.TYPE;
            case INTEGER:
            case LONG:
            case DOUBLE:
                return SchemaTypes.NUMBER;
            case DOUBLEQUOTEDSTRING:
            case SINGLEQUOTEDSTRING:
            case URI_PATH:
                return SchemaTypes.STRING;
            case SINGLE_LINE_COMMENT:
                return SchemaTypes.COMMENT;
            case IDENTIFIER:
            case IDENTIFIER_WITH_DASH:
                return SchemaTypes.IDENTIFIER;
            case ON:
            case OFF:
            case TRUE:
            case FALSE:
                return SchemaTypes.BOOLEAN;
            default:
                return SchemaTypes.NONE;
        }
    }

    @Override
    public int getTokenStart() {
        if (isInWhitespace) {
            return whitespacePointer;
        }
        return currentToken.getBeginOffset();
    }

    @Override
    public int getTokenEnd() {
        if (isInWhitespace) {
            return whitespacePointer + 1;
        }
        return currentToken.getEndOffset();
    }

    @Override
    public void advance() {
        if (isInWhitespace) {
            whitespacePointer++;
            // when inside whitespace, currentToken holds next token that is not whitespace
            if (whitespacePointer == currentToken.getBeginOffset()) {
                isInWhitespace = false;
            }
        } else {
            int saveEndOffset = currentToken.getEndOffset();
            currentToken = schemaLexer.getNextToken(currentToken);

            if (saveEndOffset < currentToken.getBeginOffset() || currentToken.getBeginOffset() == endOffset) {
                isInWhitespace = true;
                whitespacePointer = saveEndOffset;
            }
        }
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}

