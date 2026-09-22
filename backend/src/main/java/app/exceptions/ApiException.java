package app.exceptions;


import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private static final Logger logger = LoggerFactory.getLogger(ApiException.class);

    // ________________________________________________________

    public ApiException(int code, String msg){
        super(msg);
        this.status = code;
        logger.error("ApiException (code={}): {}", code, msg);
    }

    // ________________________________________________________

}