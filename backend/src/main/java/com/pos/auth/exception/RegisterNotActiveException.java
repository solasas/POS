package com.pos.auth.exception;

public class RegisterNotActiveException extends RuntimeException {

    public RegisterNotActiveException(String message) {
        super(message);
    }
}
