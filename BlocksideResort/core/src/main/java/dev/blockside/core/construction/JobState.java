package dev.blockside.core.construction;

public enum JobState {
    /** Zone placed, waiting for a crew. */
    PLANNED,
    /** Crew on site and building. */
    ACTIVE,
    /** Crew can't continue (reason in job.blockedReason); resumes automatically when cleared. */
    BLOCKED,
    COMPLETE
}
