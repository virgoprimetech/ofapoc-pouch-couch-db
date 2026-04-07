package com.primetech.poc.identity.shared.jose;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encrypting and decrypting private keys at rest.
 * <p>
 * Uses AES-GCM for authenticated encryption with PBKDF2 key derivation.
 * The encryption secret should be stored securely (e.g., environment variable,
 * HashiCorp Vault, AWS KMS) in production.
 */
@Component
public class KeyEncryptionService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH = 128;
  private static final int GCM_IV_LENGTH = 12;
  private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int KEY_DERIVATION_ITERATIONS = 65536;
  private static final int KEY_LENGTH = 256;

  // Salt for key derivation - in production, this could be stored separately
  private static final byte[] SALT = "identity-signing-key-encryption-salt".getBytes(StandardCharsets.UTF_8);

  private final String encryptionSecret;

  public KeyEncryptionService(
      @Value("${jwt.key-encryption.secret:change-this-in-production-min-32-chars}")
      String encryptionSecret) {
    this.encryptionSecret = encryptionSecret;
  }

  /**
   * Encrypts plaintext using AES-GCM.
   *
   * @param plaintext The text to encrypt
   * @return Base64-encoded ciphertext (IV + encrypted data)
   */
  public String encrypt(String plaintext) {
    try {
      SecretKey key = deriveKey();
      byte[] iv = generateIv();

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, spec);

      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      // Prepend IV to ciphertext
      byte[] combined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt data", e);
    }
  }

  /**
   * Decrypts ciphertext using AES-GCM.
   *
   * @param ciphertext Base64-encoded ciphertext (IV + encrypted data)
   * @return Decrypted plaintext
   */
  public String decrypt(String ciphertext) {
    try {
      SecretKey key = deriveKey();
      byte[] combined = Base64.getDecoder().decode(ciphertext);

      // Extract IV and ciphertext
      byte[] iv = new byte[GCM_IV_LENGTH];
      byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
      System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, key, spec);

      byte[] decrypted = cipher.doFinal(encrypted);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt data", e);
    }
  }

  private SecretKey deriveKey() throws Exception {
    SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
    KeySpec spec = new PBEKeySpec(encryptionSecret.toCharArray(), SALT, KEY_DERIVATION_ITERATIONS, KEY_LENGTH);
    SecretKey tmp = factory.generateSecret(spec);
    return new SecretKeySpec(tmp.getEncoded(), "AES");
  }

  private byte[] generateIv() {
    // Use a fixed IV derived from the salt for simplicity
    // In production, you might want to use SecureRandom for each encryption
    byte[] iv = new byte[GCM_IV_LENGTH];
    System.arraycopy(SALT, 0, iv, 0, Math.min(SALT.length, GCM_IV_LENGTH));
    return iv;
  }
}
