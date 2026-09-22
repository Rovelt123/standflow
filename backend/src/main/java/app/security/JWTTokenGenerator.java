package app.security;

import app.Main;
import app.daos.UserDAO;
import app.dtos.UserDTO;
import app.mappers.UserMapper;
import app.server.Setup;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.Date;

public class JWTTokenGenerator {

    private final static UserDAO userDAO = new UserDAO(Setup.em);
    private final static UserMapper mapper = new UserMapper();

    // ________________________________________________________

    public static String createToken(UserDTO user, String issuer, String expireMillis, String secretKey) throws JOSEException {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(user.getEmail())
            .issuer(issuer)
            .claim("email", user.getEmail())
            .expirationTime(new Date((new Date()).getTime() + (long)Integer.parseInt(expireMillis)))
            .build();

        Payload payload = new Payload(claims.toJSONObject());
        JWSSigner signer = new MACSigner(secretKey);
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWSObject jwsObject = new JWSObject(header, payload);
        jwsObject.sign(signer);

        return jwsObject.serialize();
    }

    // ________________________________________________________

    public static boolean tokenIsValid(String token, String secretKey) throws JOSEException, ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        return signedJWT.verify(new MACVerifier(secretKey));
    }

    // ________________________________________________________

    public static boolean tokenNotExpired(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
        return expiration != null && expiration.after(new Date());
    }

    // ________________________________________________________

    public static UserDTO getUserFromToken(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
        return mapper.toDTO(userDAO.getByEmail(email));
    }
}
