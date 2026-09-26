import { createCipheriv, createDecipheriv, randomBytes, createHash } from 'node:crypto';

export interface EncryptedEnvelope {
  iv: string; // Base64
  tag: string; // Base64
  data: string; // Base64
}

export class AetherCrypto {
  /**
   * Generates a 32-byte SHA-256 hash for clipboard loop prevention or key derivation
   */
  public static sha256(content: string | Buffer): string {
    return createHash('sha256').update(content).digest('hex');
  }

  /**
   * Encrypts a plaintext string using AES-256-GCM
   * @param plaintext String to encrypt
   * @param key 32-byte Buffer or hex string
   */
  public static encrypt(plaintext: string, key: Buffer): EncryptedEnvelope {
    if (key.length !== 32) {
      throw new Error('Encryption key must be exactly 32 bytes (256 bits)');
    }
    const iv = randomBytes(12); // GCM recommended 96-bit IV
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    
    let encrypted = cipher.update(plaintext, 'utf8', 'base64');
    encrypted += cipher.final('base64');
    const tag = cipher.getAuthTag();

    return {
      iv: iv.toString('base64'),
      tag: tag.toString('base64'),
      data: encrypted
    };
  }

  /**
   * Decrypts an AES-256-GCM envelope
   */
  public static decrypt(envelope: EncryptedEnvelope, key: Buffer): string {
    if (key.length !== 32) {
      throw new Error('Encryption key must be exactly 32 bytes (256 bits)');
    }
    const iv = Buffer.from(envelope.iv, 'base64');
    const tag = Buffer.from(envelope.tag, 'base64');
    const decipher = createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);

    let decrypted = decipher.update(envelope.data, 'base64', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
  }
}
