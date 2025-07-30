package ai.vespa.schemals.context;

public class InvalidContextException extends Exception {
    public InvalidContextException() {
        super();
    }

    public InvalidContextException(String message) {
        super(message);
    }
}
