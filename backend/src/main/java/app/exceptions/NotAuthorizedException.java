package app.exceptions;

import lombok.Getter;

@Getter
public class NotAuthorizedException extends Exception {
    private final int statusCode;

    public NotAuthorizedException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

}
