package app.dtos;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record AdminApplicationDTO(@JsonUnwrapped ApplicationDTO application, String comment,
                                  Boolean attended, String standLocation, java.util.List<Participation> history) {
    public record Participation(java.util.UUID id, Integer marketYear, String company,
                                String standType, String standLocation) { }
}
