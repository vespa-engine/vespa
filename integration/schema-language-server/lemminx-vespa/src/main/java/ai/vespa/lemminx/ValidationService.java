package ai.vespa.lemminx;

import java.io.PrintStream;
import java.util.Map;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.IXMLValidationService;

public class ValidationService implements IXMLValidationService {
    private PrintStream logger;

    public ValidationService(PrintStream logger) {
        this.logger = logger;
    }

    @Override
    public void validate(DOMDocument document, Map<String, Object> validationArgs) {
        logger.println("Validation");
    }
}
