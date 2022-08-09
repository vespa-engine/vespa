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
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@ExtendWith(EnabledInInstancesCondition.class)
public @interface EnabledInInstances {

    /** One or more instances that this should be enabled in. */
    String[] value();

}

class EnabledInInstancesCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<EnabledInInstances> annotation = AnnotationUtils.findAnnotation(context.getElement(), EnabledInInstances.class);
        if (annotation.isEmpty())
            return ConditionEvaluationResult.enabled(EnabledInInstances.class.getSimpleName() + " is not present");

        List<String> enablingInstances = List.of(annotation.get().value());
        String thisInstance = TestRuntime.get().application().instance();
        String reason = "Enabled in: %s. Current instance: %s.".formatted(enablingInstances.isEmpty() ? "no instances" : "instances " + String.join(", ", enablingInstances), thisInstance);
        return enablingInstances.contains(thisInstance) ? ConditionEvaluationResult.enabled(reason) : ConditionEvaluationResult.disabled(reason);
    }

}