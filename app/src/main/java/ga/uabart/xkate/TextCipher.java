package ga.uabart.xkate;

import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

class TextCipher {
    private static int iterationCount = 1024;
    private static byte[] iv = "XKateCustomC0de1".getBytes();
    private static int keyStrength = 256;
    private static byte[] salt = "RandomSaltXKate".getBytes();
    private static final String UTF_LOCK = "\uD83D\uDD12\n";
    private static final String UTF_UNLOCK = "\uD83D\uDD13 ";

    private TextCipher() {
        throw new IllegalAccessError("Utility class");
    }

    static String decrypt(String data, String passPhrase) {
        try {
            if (data.startsWith(UTF_LOCK)) {
                String encrypted = data.substring(5);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount, keyStrength);
                SecretKey tmp = factory.generateSecret(spec);
                SecretKey key = new SecretKeySpec(tmp.getEncoded(), "AES");
                Cipher cipher = Cipher.getInstance("AES/CFB/ISO10126Padding");
                iv[7] = jBaseZ85.decode(data.substring(3, 5))[0];
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                byte[] decryptedData = jBaseZ85.decode(encrypted);
                byte[] utf8 = cipher.doFinal(decryptedData);
                return UTF_UNLOCK + new String(utf8, "UTF8");
            } else {
                return data;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    static String encrypt(String data, String passPhrase) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount, keyStrength);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey key = new SecretKeySpec(tmp.getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CFB/ISO10126Padding");
            byte[] code = new byte[1];
            code[0] = (byte) (Math.random() * 255);
            iv[7] = code[0];
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] utf8EncryptedData = cipher.doFinal(data.getBytes());
            return UTF_LOCK + jBaseZ85.encode(code) + jBaseZ85.encode(utf8EncryptedData);
        } catch (Exception ignored) {
            return null;
        }
    }
}
