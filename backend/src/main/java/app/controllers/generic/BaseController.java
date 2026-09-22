package app.controllers.generic;

import app.enums.Notifications;
import io.javalin.http.Context;
import app.mappers.generic.IMapper;
import app.services.MessageService;

import java.util.*;


public abstract class BaseController<E, D> implements IController {

    protected Class<E> entityClass;
    protected IMapper<E, D> mapper;
    protected abstract List<E> getAllEntities();
    protected abstract E getEntityById(UUID id);
    // ________________________________________________________

    protected final MessageService messageService = new MessageService();

    // ________________________________________________________

    protected BaseController(Class<E> entityClass, IMapper<E, D> mapper) {
        this.entityClass = entityClass;
        this.mapper = mapper;
    }

    // ________________________________________________________

    @Override
    public void getAll(Context ctx) {

        List<D> list = new ArrayList<>();

        if (getAllEntities().isEmpty()) {
            String message = messageService.buildMessage(Notifications.GET_ALL_EMPTY, entityClass.getSimpleName().toLowerCase(Locale.ROOT));
            respond(ctx, 204, message, null);
            return;
        }

        getAllEntities().forEach(entity -> list.add(mapper.toDTO(entity)));


        String message = messageService.buildMessage(
                Notifications.GET_ALL,
                String.valueOf(list.size()),
                entityClass.getSimpleName().toLowerCase(Locale.ROOT)
        );

        respond(ctx, 200, message, Map.of("data", list));
    }

    // ________________________________________________________

    @Override
    public void getByID(Context ctx) {
        UUID id = UUID.fromString(ctx.pathParam("id"));

        E entity = getEntityById(id);

        D dto = mapper.toDTO(entity);

        String message = messageService.buildMessage(
            Notifications.GET_BY_ID,
            entityClass.getSimpleName().toLowerCase(Locale.ROOT),
            String.valueOf(id)
        );

        respond(ctx, 200, message, Map.of("data", dto));
    }


    // ________________________________________________________

    protected void respond(Context ctx, int status, String message, Object data) {
        if (data != null) {
            ctx.status(status).json(Map.of(
                    "message", message,
                    "data", data
            ));
        } else {
            ctx.status(status).json(Map.of(
                    "message", message
            ));
        }
    }

}
