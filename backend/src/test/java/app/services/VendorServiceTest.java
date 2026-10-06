package app.services;

import app.dtos.VendorDTO;
import app.enums.ApplicationStatus;
import app.support.VendorTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VendorServiceTest extends VendorTestSupport {

    @Test
    void onlyAcceptedVendorsAreReturned() {
        persistApplication("Accepteret A", ApplicationStatus.ACCEPTED);
        persistApplication("Accepteret B", ApplicationStatus.ACCEPTED);
        persistApplication("Afventer", ApplicationStatus.PENDING);
        persistApplication("Afvist", ApplicationStatus.REJECTED);
        persistApplication("Info", ApplicationStatus.INFO_REQUESTED);

        VendorService.VendorPage page = service.getAcceptedVendors(1, 10);

        assertEquals(2, page.total());
        assertEquals(2, page.vendors().size());
        assertTrue(page.vendors().stream().allMatch(v -> v.getCompany().startsWith("Accepteret")));
    }

    //--------------------------------------------------------------

    @Test
    void paginationReturnsAtMostPageSizePerPage() {
        for (int i = 1; i <= 12; i++) {
            persistApplication(String.format("Firma %02d", i), ApplicationStatus.ACCEPTED);
        }

        VendorService.VendorPage first = service.getAcceptedVendors(1, 10);
        assertEquals(12, first.total());
        assertEquals(10, first.vendors().size());

        VendorService.VendorPage second = service.getAcceptedVendors(2, 10);
        assertEquals(12, second.total());
        assertEquals(2, second.vendors().size());
    }

    //--------------------------------------------------------------

    @Test
    void pageSizeIsCappedAtTenAndDefaultsWhenInvalid() {
        for (int i = 1; i <= 11; i++) {
            persistApplication("Firma " + i, ApplicationStatus.ACCEPTED);
        }

        assertEquals(10, service.getAcceptedVendors(1, 50).pageSize());
        assertEquals(10, service.getAcceptedVendors(1, 50).vendors().size());
        assertEquals(10, service.getAcceptedVendors(1, 0).pageSize());
        assertEquals(5, service.getAcceptedVendors(1, 5).pageSize());
    }

    //--------------------------------------------------------------

    @Test
    void invalidPageNumberBecomesFirstPage() {
        persistApplication("Firma", ApplicationStatus.ACCEPTED);

        assertEquals(1, service.getAcceptedVendors(0, 10).page());
        assertEquals(1, service.getAcceptedVendors(-3, 10).page());
    }

    //--------------------------------------------------------------

    @Test
    void emptyWhenNoAcceptedVendors() {
        persistApplication("Afventer", ApplicationStatus.PENDING);

        VendorService.VendorPage page = service.getAcceptedVendors(1, 10);

        assertEquals(0, page.total());
        assertTrue(page.vendors().isEmpty());
    }

    //--------------------------------------------------------------

    @Test
    void dtoExposesPublicFieldsAndMapsProductsToDescription() {
        persistApplication("Nordic Craft", ApplicationStatus.ACCEPTED);

        VendorDTO vendor = service.getAcceptedVendors(1, 10).vendors().get(0);

        assertEquals("Nordic Craft", vendor.getCompany());
        assertEquals("Varer fra Nordic Craft", vendor.getDescription());
        assertEquals("A", vendor.getStandType());
    }
}
