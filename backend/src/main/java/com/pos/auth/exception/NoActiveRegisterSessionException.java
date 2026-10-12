package com.pos.auth.exception;

/**
 * Thrown when {@code switch-user} is called on a session that was not established via
 * PIN login (i.e. has no register attached), such as an email/password office session.
 */
public class NoActiveRegisterSessionException extends RuntimeException {

    public NoActiveRegisterSessionException(String message) {
        super(message);
    }
}
