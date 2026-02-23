package io.loom.core.exception;

public class GuardRejectedException extends LoomException {
    private final String guardName;

    public GuardRejectedException(String guardName) {
        super("Guard '" + guardName + "' rejected the request");
        this.guardName = guardName;
    }

    public GuardRejectedException(String guardName, String reason) {
        super("Guard '" + guardName + "' rejected the request: " + reason);
        this.guardName = guardName;
    }

    public String getGuardName() {
        return guardName;
    }
}
