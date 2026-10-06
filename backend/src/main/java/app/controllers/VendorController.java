package app.controllers;

import app.enums.Role;
import app.services.VendorService;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;

/** Public endpoint for the Stadeholdere page: lists accepted stand holders, 10 per page. */
public class VendorController {

    private final VendorService vendorService;

    //--------------------------------------------------------------

    public VendorController(VendorService vendorService) {
        this.vendorService = Objects.requireNonNull(vendorService);
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        return registerRoutes(new VendorService());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(VendorService vendorService) {
        VendorController controller = new VendorController(vendorService);
        return () -> get("/vendors", controller::getAccepted, Role.ANYONE);
    }

    //--------------------------------------------------------------

    public void getAccepted(Context ctx) {
        int page = parseIntOrDefault(ctx.queryParam("page"), 1);
        int pageSize = parseIntOrDefault(ctx.queryParam("pageSize"), 10);
        ctx.status(200).json(vendorService.getAcceptedVendors(page, pageSize));
    }

    //--------------------------------------------------------------

    private int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
