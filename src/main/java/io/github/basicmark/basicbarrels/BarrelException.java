package io.github.basicmark.basicbarrels;

public class BarrelException extends Exception {
    Reason reason;
    public BarrelException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {UNCLEAR_OPERATION, BARREL_PERMISSION, AIR_GAP, BLOCK_PERMISSION, SWITCH_SPACE, SWITCH_SUCCESS}
}
