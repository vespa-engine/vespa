package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.Optional;


/**
 * An instantiation of a {@link GenericFunction} with
 * - A {@link FunctionSignature}
 * - An optional property (what comes after the '.' in features)
 *
 * Used for completion and hover information
 */
public class SpecificFunction {

    GenericFunction function;
    FunctionSignature signature;
    Optional<String> property;

    public SpecificFunction(GenericFunction function, FunctionSignature signature, Optional<String> property) {
        this.function = function;
        this.signature = signature;
        this.property = property;
    }

    public SpecificFunction(GenericFunction function, FunctionSignature signature) {
        this(function, signature, Optional.empty());
    }

    public String getSignatureString(boolean altSignature) {

        String signatureString = signature.toString();
        if (altSignature && property.isEmpty() && signatureString.equals("()")) {
            return function.getName();
        }

        return function.getName() + signatureString + (property.isPresent() ? "." + property.get() : "");
    }

    public String getSignatureString() {
        return getSignatureString(false);
    }

    public FunctionSignature getSignature() { return this.signature; }

    public void setProperty(String property) {
        this.property = Optional.of(property);
    }

    public void clearProperty() {
        this.property = Optional.empty();
    }

    public SpecificFunction clone() {
        return new SpecificFunction(this.function, this.signature, this.property);
    }
};
