package tn.mnlr.vripper.exception;

public class VripperException extends Exception {
    public VripperException(String message) {
        super(message);
    }

    public VripperException(Exception e) {
        super(e);
    }
}
