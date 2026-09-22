package app.services;

import app.enums.Notifications;
import app.exceptions.ApiException;
import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {

    public static boolean passwordEquals(String hash, String enteredHash) {
        return BCrypt.checkpw(hash, enteredHash);
    }

    // ________________________________________________________

    public static String hashHelper(String hash){
        return BCrypt.hashpw(hash, BCrypt.gensalt());
    }

    // ________________________________________________________

    public static void passwordValidation(String password) {

        if (password.length() < 8 || password.length() > 30) {
            throw new ApiException(
                    400,
                    Notifications.PASSWORD_LENGTH_INVALID.getDisplayName()
            );
        }

        if (!password.matches(".*[A-Z].*")){
            throw new ApiException(
                    400,
                    Notifications.PASSWORD_UPPERCASE_MISSING.getDisplayName()
            );
        }

        if (!password.matches(".*[a-z].*")) {
            throw new ApiException(
                    400,
                    Notifications.PASSWORD_LOWERCASE_MISSING.getDisplayName()
            );

        }

        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new ApiException(
                    400,
                    Notifications.PASSWORD_SPECIAL_CHAR_MISSING.getDisplayName()
            );
        }
    }
}




