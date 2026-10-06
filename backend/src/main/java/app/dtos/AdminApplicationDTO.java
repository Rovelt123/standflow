package app.dtos;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record AdminApplicationDTO(@JsonUnwrapped ApplicationDTO application, String comment) {
}
