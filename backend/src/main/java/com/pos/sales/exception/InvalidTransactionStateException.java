package com.pos.sales.exception;

public class InvalidTransactionStateException extends RuntimeException {

    public InvalidTransactionStateException(String message) {
        super(message);
    }
}
