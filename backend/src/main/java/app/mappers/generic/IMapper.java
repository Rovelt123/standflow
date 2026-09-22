package app.mappers.generic;

public interface IMapper<E, D> {
    E toEntity(D dto);
    D toDTO(E entity);
}
