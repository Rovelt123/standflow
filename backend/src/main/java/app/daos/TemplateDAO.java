package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Template;
import jakarta.persistence.EntityManager;

public class TemplateDAO extends EntityManagerDAO<Template> {

    public TemplateDAO(EntityManager em) {
        super(em, Template.class);
    }

}
