package app.controllers;

import app.dtos.UserDTO;
import app.exceptions.ApiException;
import app.services.FileService;
import app.services.FileValidator;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.javalin.apibuilder.ApiBuilder.post;

public class FileController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileController.class);
    public static final long MAX_REQUEST_SIZE = 4L * 1_048_576;
    private final FileService service;

    //--------------------------------------------------------------

    public FileController(FileService service) {
        this.service = service;
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(FileService service) {
        FileController controller = new FileController(service);
        return () -> post("/files", controller::upload, app.enums.Role.USER, app.enums.Role.ADMIN);
    }

    //--------------------------------------------------------------

    public static void configureMultipart(JavalinConfig config) {
        config.jetty.multipartConfig.maxFileSize(FileValidator.MAX_SIZE, SizeUnit.BYTES);
        config.jetty.multipartConfig.maxTotalRequestSize(MAX_REQUEST_SIZE, SizeUnit.BYTES);
        config.jetty.multipartConfig.maxInMemoryFileSize(64 * 1024, SizeUnit.BYTES);
    }

    //--------------------------------------------------------------

    private void upload(Context ctx) {
        UserDTO user = ctx.attribute("user");
        if (user == null || user.getId() == null) throw new ApiException(401, "Login er påkrævet.");
        if (!ctx.isMultipartFormData()) throw new ApiException(400, "Upload skal være multipart/form-data.");
        Collection<Part> parts = List.of();
        List<FileValidator.Upload> uploads = new ArrayList<>();
        try {
            parts = multipartParts(ctx);
            if (parts.isEmpty() || parts.size() > FileValidator.MAX_FILES) {
                throw new ApiException(400, "Vælg mellem 1 og 3 filer.");
            }
            for (Part part : parts) {
                if (!part.getName().equals("files") || part.getSubmittedFileName() == null) {
                    throw new ApiException(400, "Upload accepterer kun filer i feltet files.");
                }
                uploads.add(new FileValidator.Upload(part.getSubmittedFileName(), part.getInputStream(), part.getSize()));
            }
            ctx.status(201).json(Map.of("files", service.upload(user.getId(), uploads)));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            LOGGER.error("Could not read multipart upload data.", exception);
            throw new ApiException(500, "Filerne kunne ikke læses. Prøv igen senere.");
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected file upload failure.", exception);
            throw new ApiException(500, "Filerne kunne ikke gemmes. Prøv igen senere.");
        } finally {
            for (var upload : uploads) {
                try {
                    upload.content().close();
                } catch (IOException exception) {
                    LOGGER.warn("Could not close a multipart file stream.", exception);
                }
            }
            for (Part part : parts) {
                try {
                    part.delete();
                } catch (IOException exception) {
                    LOGGER.warn("Could not remove multipart temporary data.", exception);
                }
            }
        }
    }

    //--------------------------------------------------------------

    private Collection<Part> multipartParts(Context ctx) {
        try {
            // Javalin installs the configured Jetty multipart limits before parsing.
            ctx.uploadedFileMap();
            return ctx.req().getParts();
        } catch (IllegalStateException exception) {
            throw new ApiException(413, "Upload er for stor. Hver fil må højst være 1 MiB.");
        } catch (ServletException | IOException exception) {
            if (exception.getCause() instanceof IllegalStateException) {
                throw new ApiException(413, "Upload er for stor. Hver fil må højst være 1 MiB.");
            }
            throw new ApiException(400, "Upload indeholder ugyldige multipart-data.");
        }
    }
}
