package app.controllers.generic;

import io.javalin.http.Context;

public interface IController{

    void getAll(Context ctx);

    // ________________________________________________________

    void getByID(Context ctx);
}
