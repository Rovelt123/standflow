package app.daos.generic;

import java.util.List;

public interface IDAO <T> {
    T create(T entity);
    T update(T entity);
    T getById(Object id);
    boolean existByColumn(Object o, String column);
    List<T> getAll();
    T delete(T entity);
    T deleteById(Object id);
    void deleteAll();
}