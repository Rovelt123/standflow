package app.utils;

import app.exceptions.ApiException;
import io.javalin.http.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ErrorHandler {

    public static int tryParseInt(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static Float tryParseFloat(String value, String message) {
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static UUID tryParseUUID(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static String tryString(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, message);
        }
        return value;
    }

    // ________________________________________________________

    public static <T> T tryEntity(T entity, String message) {
        if (entity == null) {
            throw new ApiException(404, message);
        }
        return entity;
    }

    // ________________________________________________________

    public static boolean tryParseBoolean(String value, String message) {
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static <T extends Enum<T>> T tryParseEnum(Class<T> enumClass, String value, String message) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static <T> List<T> tryList(List<T> list, String message) {
        if (list == null || list.isEmpty()) {
            throw new ApiException(400, message);
        }
        return list;
    }

    // ________________________________________________________

    public static double tryParseDouble(String value, String message) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static LocalDate tryParseLocalDate(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static LocalDateTime tryParseLocalDateTime(String value, String message) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static LocalTime tryParseLocalTime(String value, String message) {
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static <T> T tryBody(Context ctx, Class<T> clazz, String message) {
        try {
            return ctx.bodyAsClass(clazz);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }

    // ________________________________________________________

    public static Map<String, String> tryBodyMap(Context ctx, String message) {
        try {
            return ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            throw new ApiException(400, message);
        }
    }
}