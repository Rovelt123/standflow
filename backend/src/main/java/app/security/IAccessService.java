package app.security;

import io.javalin.http.Context;

public interface IAccessService {
    void accessHandler(Context ctx);
}
