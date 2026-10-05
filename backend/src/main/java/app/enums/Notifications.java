package app.enums;


import lombok.Getter;

@Getter
public enum Notifications {

    // TEMPLATE
    Notify("This is a message with args: %s!"),
    Notify2("This is a hardcoded message"),

    // PASSWORD
    PASSWORD_LENGTH_INVALID("Password must be between 8 and 30 characters long."),
    PASSWORD_UPPERCASE_MISSING("Password must contain at least one uppercase letter."),
    PASSWORD_LOWERCASE_MISSING("Password must contain at least one lowercase letter."),
    PASSWORD_SPECIAL_CHAR_MISSING("Password must contain at least one special character."),


    // GENERICS
    GET_ALL_EMPTY("No data was fetched because %s was empty!"),
    GET_BY_ID("You fetched %s with ID: %s"),
    GET_ALL("You fetched %s %ss"),
    BODY_EMPTY("Body is empty or invalid!"),
    MUST_BE_FLOAT("The entered must be a decimal number!")

    ;

    // ________________________________________________________

    private final String displayName;

    // ________________________________________________________

    Notifications(String displayName) {
        this.displayName = displayName;
    }
}
