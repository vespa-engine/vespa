package ai.vespa.hosted.cd;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;

/**
 * @author jonmv
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledInRegionsCondition.class)
public @interface EnabledInRegions {

    /** One or more regions that this should be enabled in. */
    String[] value();

}

class EnabledInRegionsCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<EnabledInRegions> annotation = AnnotationUtils.findAnnotation(context.getElement(), EnabledInRegions.class);
        if (annotation.isEmpty())
            return ConditionEvaluationResult.enabled(EnabledInRegions.class.getSimpleName() + " is not present");

        List<String> enablingRegions = List.of(annotation.get().value());
        String thisRegion = TestRuntime.get().application().instance();
        String reason = "Enabled in: %s. Current region: %s.".formatted(enablingRegions.isEmpty() ? "no regions" : "regions " + String.join(", ", enablingRegions), thisRegion);
        return enablingRegions.contains(thisRegion) ? ConditionEvaluationResult.enabled(reason) : ConditionEvaluationResult.disabled(reason);
    }

}