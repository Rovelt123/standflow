package app.utils;

import app.exceptions.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Utils {

    public static String getPropertyValue(String propName, String resourceName) {
        String envValue = System.getenv(propName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        try (InputStream is = Utils.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new ApiException(500, "Resource not found: " + resourceName);
            }

            Properties prop = new Properties();
            prop.load(is);

            String value = prop.getProperty(propName);
            if (value != null) {
                return value.trim();
            } else {
                throw new ApiException(500, String.format("Property %s not found in %s", propName, resourceName));
            }
        } catch (IOException ex) {
            throw new ApiException(500, String.format("Could not read property %s.", propName));
        }
    }
}
