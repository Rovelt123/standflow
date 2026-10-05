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


    // REGISTER
    EMAIL_EXISTS("Email: %s already exists! Choose another email"),
    REGISTER_SUCCESS("Welcome to Wedding planner %s! We hope you will enjoy the site"),
    REGISTER_NO_EMAIL("You must enter a valid email"),
    REGISTER_NO_PASSWORD("You must enter a valid password"),
    REGISTER_NO_PASSWORD_REPEAT("You must verify your password"),
    REGISTER_PASSWORD_MISMATCH("Password confirmation does not match"),
    REGISTER_NO_FIRSTNAME("You must enter your first name"),
    REGISTER_NO_LASTNAME("You must enter your last name"),
    REGISTER_NO_COMPANY("You must enter a company name"),
    REGISTER_NO_CVR("You must enter your CVR number"),
    REGISTER_NO_PHONE("You must enter your phone number"),
    REGISTER_NO_ADRESS("You must enter your adress"),



    // LOGIN
    LOGGED_IN("Welcome back %s!"),
    WRONG_CREDENTIALS("You entered the wrong credentials!"),


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
