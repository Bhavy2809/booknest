package com.booknest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Creates and checks login tokens.
 *
 * A token looks like   base64(userId:expiryTime) . base64(HMAC-SHA256 signature)
 * The server signs the first part with a secret key. If anybody edits the user id,
 * the signature no longer matches and the token is rejected. No session table is needed
 * (this is the same idea as a JWT, just smaller and easier to read).
 */
@Service
public class TokenService {

     private final byte[] secret;
     private final long ttlMillis;

     public TokenService(@Value("${app.token-secret}") String secret,
                         @Value("${app.token-ttl-hours}") long ttlHours) {
         this.secret = secret.getBytes(StandardCharsets.UTF_8);
         this.ttlMillis = ttlHours * 60 * 60 * 1000;
     }

    public String create(Long userId) {
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        String payload = b64((userId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
        return payload + "." + b64(sign(payload));
    }

     /** Returns the user id inside a valid, unexpired token, otherwise null. */
    public Long verify(String token) {
        if (token == null) return null;
        int dot = token.indexOf('.');
        if (dot < 1) return null;
        String payload = token.substring(0, dot);
        byte[] given = decode(token.substring(dot + 1));
        if (given == null || !MessageDigest.isEqual(given, sign(payload))) return null;
        byte[] raw = decode(payload);
        if (raw == null) return null;
        String[] parts = new String(raw, StandardCharsets.UTF_8).split(":");
        if (parts.length != 2) return null;
        try {
             if (Long.parseLong(parts[1]) < System.currentTimeMillis()) return null;
             return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
             return null;
        }
    }

     private byte[] sign(String data) {
         try {
             Mac mac = Mac.getInstance("HmacSHA256");
             mac.init(new SecretKeySpec(secret, "HmacSHA256"));
             return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
         } catch (Exception e) {
             throw new IllegalStateException(e);
         }
     }
     private static String b64(byte[] bytes) {
         return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
     }

     private static byte[] decode(String s) {
         try {
             return Base64.getUrlDecoder().decode(s);
         } catch (IllegalArgumentException e) {
             return null;
         }
     }
}
