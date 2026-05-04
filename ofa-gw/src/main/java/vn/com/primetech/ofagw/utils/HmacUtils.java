package vn.com.primetech.ofagw.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HmacUtils {

  public static byte[] hmac(String data, String secretKey, String algorithm, Charset charset) {
    try {
      Mac mac = Mac.getInstance(algorithm);

      SecretKeySpec keySpec = new SecretKeySpec(
          secretKey.getBytes(charset),
          algorithm
      );

      mac.init(keySpec);

      return mac.doFinal(data.getBytes(charset));

    } catch (Exception e) {
      throw new RuntimeException("HMAC calculation failed", e);
    }
  }
}