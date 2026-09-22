package app.daos.generic;

import app.daos.generic.IDAO;
import app.SetupTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public abstract class EntityManagerDAOTest<T> extends SetupTest {

    protected abstract IDAO<T> getDao();

    protected abstract T createEntity();

    protected abstract T createSecondEntity();

    protected abstract Object getEntityId(T entity);

    protected abstract Object getMissingEntityId();

    protected abstract EntityUpdate<T> getEntityUpdate();

    protected abstract String getExistingColumnName();

    protected abstract Object getExistingColumnValue(T entity);

    protected abstract Object getMissingColumnValue();

    // ________________________________________________________

    @Test
    void createShouldPersistEntity() {
        T created = getDao().create(createEntity());

        em.flush();
        em.clear();

        assertNotNull(created);
        assertNotNull(getEntityId(created));
        assertNotNull(getDao().getById(getEntityId(created)));
    }

    // ________________________________________________________

    @Test
    void getByIdShouldReturnEntityWhenIdExists() {
        T created = getDao().create(createEntity());

        em.flush();
        em.clear();

        T found = getDao().getById(getEntityId(created));

        assertNotNull(found);
        assertEquals(getEntityId(created), getEntityId(found));
    }

    // ________________________________________________________

    @Test
    void getByIdShouldReturnNullWhenIdDoesNotExist() {
        assertNull(getDao().getById(getMissingEntityId()));
    }

    // ________________________________________________________

    @Test
    void getAllShouldReturnAllPersistedEntities() {
        getDao().create(createEntity());
        getDao().create(createSecondEntity());

        em.flush();
        em.clear();

        List<T> entities = getDao().getAll();

        assertEquals(2, entities.size());
    }

    // ________________________________________________________

    @Test
    void updateShouldPersistChanges() {
        T created = getDao().create(createEntity());
        EntityUpdate<T> entityUpdate = getEntityUpdate();

        entityUpdate.change().accept(created);
        getDao().update(created);
        em.flush();
        em.clear();

        T updated = getDao().getById(getEntityId(created));

        entityUpdate.assertChanged().accept(updated);
    }

    // ________________________________________________________

    @Test
    void deleteShouldRemoveEntity() {
        T created = getDao().create(createEntity());
        Object id = getEntityId(created);

        T deleted = getDao().delete(created);
        em.flush();
        em.clear();

        assertNotNull(deleted);
        assertNull(getDao().getById(id));
    }

    // ________________________________________________________

    @Test
    void deleteByIdShouldRemoveEntity() {
        T created = getDao().create(createEntity());
        Object id = getEntityId(created);

        T deleted = getDao().deleteById(id);
        em.flush();
        em.clear();

        assertNotNull(deleted);
        assertNull(getDao().getById(id));
    }

    // ________________________________________________________

    @Test
    void existByColumnShouldReturnTrueWhenValueExists() {
        T created = getDao().create(createEntity());

        em.flush();
        em.clear();

        assertTrue(getDao().existByColumn(getExistingColumnValue(created), getExistingColumnName()));
    }

    // ________________________________________________________

    @Test
    void existByColumnShouldReturnFalseWhenValueDoesNotExist() {
        assertFalse(getDao().existByColumn(getMissingColumnValue(), getExistingColumnName()));
    }

    // ________________________________________________________

    @Test
    void deleteAllShouldRemoveAllEntities() {
        getDao().create(createEntity());
        getDao().create(createSecondEntity());

        getDao().deleteAll();
        em.flush();
        em.clear();

        assertTrue(getDao().getAll().isEmpty());
    }

    public record EntityUpdate<T>(
            Consumer<T> change,
            Consumer<T> assertChanged
    ) {}
}
