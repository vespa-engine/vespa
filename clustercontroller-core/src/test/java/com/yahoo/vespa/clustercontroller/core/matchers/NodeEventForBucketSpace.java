package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.core.NodeEvent;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.mockito.ArgumentMatcher;

import java.util.Optional;

public class NodeEventForBucketSpace extends ArgumentMatcher<NodeEvent> {
    private final Optional<String> bucketSpace;

    public NodeEventForBucketSpace(Optional<String> bucketSpace) {
        this.bucketSpace = bucketSpace;
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof NodeEvent)) {
            return false;
        }
        return bucketSpace.equals(((NodeEvent) o).getBucketSpace());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("NodeEvent for bucket space '%s'", bucketSpace.orElse("null")));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        NodeEvent other = (NodeEvent)item;
        description.appendText(String.format("got bucket space '%s'", other.getBucketSpace().orElse("null")));
    }

    @Factory
    public static NodeEventForBucketSpace nodeEventForBucketSpace(String bucketSpace) {
        return new NodeEventForBucketSpace(Optional.of(bucketSpace));
    }

    @Factory
    public static NodeEventForBucketSpace nodeEventForBaseline() {
        return new NodeEventForBucketSpace(Optional.empty());
    }
}
