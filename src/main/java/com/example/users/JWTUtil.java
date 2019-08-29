package com.example.users;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Date;

import io.vertx.core.json.Json;

public class JWTUtil {

  private static Algorithm algorithm = Algorithm.HMAC256("superSecret");
  private static String issuer = "Super Issue Application";

  public static String sign(Object subject, Integer expireTimeInMinutes) {
    String subjectValue = Json.encode(subject);
    JWTCreator.Builder builder = JWT.create().withIssuer(issuer).withSubject(subjectValue);
    if (expireTimeInMinutes != null) {
      builder.withExpiresAt(plusMinutes(expireTimeInMinutes));
    }
    return builder.sign(algorithm);
  }

  public static DecodedJWT verify(String jwt) {
    JWTVerifier verifier = JWT.require(algorithm)
      .withIssuer(issuer)
      .build();
    return verifier.verify(jwt);
  }

  private static Date plusMinutes(int minutes) {
    int oneMinuteInMillis = 60000;
    Calendar calendar = Calendar.getInstance();
    return new Date(calendar.getTimeInMillis() + (minutes * oneMinuteInMillis));
  }

}
