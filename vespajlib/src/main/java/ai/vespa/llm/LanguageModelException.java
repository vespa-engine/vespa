package ai.vespa.llm;

public class LanguageModelException extends RuntimeException {

    private final int code;

    public LanguageModelException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
