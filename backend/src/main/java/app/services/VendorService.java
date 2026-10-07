package app.services;

import app.daos.VendorDAO;
import app.dtos.VendorDTO;
import app.entities.Application;
import app.server.Setup;

import java.util.List;
import java.util.Objects;

/** Builds the paginated, public list of accepted stand holders. */
public class VendorService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 10;

    private final VendorDAO vendorDAO;

    //--------------------------------------------------------------

    public VendorService() {
        this(new VendorDAO(Setup.em));
    }

    //--------------------------------------------------------------

    public VendorService(VendorDAO vendorDAO) {
        this.vendorDAO = Objects.requireNonNull(vendorDAO);
    }

    //--------------------------------------------------------------

    public VendorPage getAcceptedVendors(int page, int pageSize) {
        int size = (pageSize < 1 || pageSize > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : pageSize;
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * size;

        long total = vendorDAO.countAccepted();
        List<VendorDTO> vendors = vendorDAO.findAccepted(offset, size).stream()
                .map(this::toDTO)
                .toList();

        return new VendorPage(safePage, size, total, vendors);
    }

    //--------------------------------------------------------------

    private VendorDTO toDTO(Application application) {
        return VendorDTO.builder()
                .company(application.getCompany())
                .description(application.getProducts())
                .website(application.getWebsite())
                .standType(application.getStandType() == null ? null : application.getStandType().name())
                .build();
    }

    //--------------------------------------------------------------

    /** One page of vendors: the current page, the page size, the total number of accepted vendors, and the rows. */
    public record VendorPage(int page, int pageSize, long total, List<VendorDTO> vendors) {
    }
}
