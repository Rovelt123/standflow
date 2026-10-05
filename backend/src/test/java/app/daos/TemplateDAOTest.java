package app.daos;

import app.dtos.TemplateDTO;
import app.entities.Template;
import app.exceptions.ApiException;
import app.services.TemplateService;
import app.support.TemplateTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateDAOTest extends TemplateTestSupport {

    @Test
    void persistsEveryFieldAndReloadsInNewPersistenceContext() throws Exception {
        TemplateDTO created = service.create(request(validBody()));
        Template saved = reload(created.getId());
        assertAll(
                () -> assertEquals(created.getId(), saved.getId()),
                () -> assertEquals("Kvittering", saved.getName()),
                () -> assertEquals("Tak for din ansøgning", saved.getSubject()),
                () -> assertEquals("Kære <navn>,\n\nTak for din ansøgning.", saved.getBody()));
        assertNotNull(dao.getById(created.getId()));
        assertEquals(1, dao.getAll().size());
    }

    //--------------------------------------------------------------

    @Test
    void rollsBackFailedInsertAndCanSaveNextTemplate() throws Exception {
        Template invalid = Template.builder().name(null).subject("Emne").body("Tekst").build();
        assertThrows(RuntimeException.class, () -> dao.create(invalid));
        assertFalse(em.getTransaction().isActive());
        assertEquals(0, countTemplates());
        em.clear();
        service.create(request(validBody()));
        assertEquals(1, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void serviceMapsDatabaseFailureToGeneric500() throws Exception {
        var failing = new TemplateService(new TemplateDAO(em) {
            @Override
            public Template create(Template template) {
                throw new IllegalStateException("private database details");
            }
        });
        var request = request(validBody());
        ApiException error = assertThrows(ApiException.class, () -> failing.create(request));
        assertEquals(500, error.getStatus());
        assertFalse(error.getMessage().contains("private"));
        assertEquals(0, countTemplates());
    }
}
