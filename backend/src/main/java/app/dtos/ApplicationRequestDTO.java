package app.dtos;

public record ApplicationRequestDTO(
        String company, String contact, String cvr, String email, String phone,
        String address, String city, String website, String products,
        Boolean previousExhibitor, String standType, Integer tables, Integer chairs) {
}
