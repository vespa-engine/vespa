package ai.vespa.hosted.cd;

/**
 * The Surefire configuration element &lt;excludedGroups&gt; requires a non-empty argument to reset another.
 * This class serves that purpose. Without it, no tests run in the various integration test profiles.
 *
 * @author jonmv
 */
public interface EmptyGroup { }
