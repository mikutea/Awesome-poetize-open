package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES加密工具类
 * 用于API密钥等敏感信息的加密存储
 * 与Python端Fernet加密保持兼容
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Slf4j
@Component
public class AESCryptoUtil {

    /**
     * AES加密密钥（从环境变量获取，默认值与Python端保持一致）
     */
    @Value("${poetize.aes.key:sarasarasarasara}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 加密数据
     *
     * @param data 原始数据
     * @return Base64编码的加密数据，加密失败返回null
     */
    public String encrypt(String data) {
        if (!StringUtils.hasText(data)) {
            return data;
        }

        try {
            // 确保密钥长度为16字节（128位）
            byte[] keyBytes = get16ByteKey(secretKey);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            log.error("数据加密失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解密数据
     *
     * @param encryptedData Base64编码的加密数据
     * @return 解密后的原始数据，解密失败返回null
     */
    public String decrypt(String encryptedData) {
        if (!StringUtils.hasText(encryptedData)) {
            return encryptedData;
        }

        try {
            // 确保密钥长度为16字节（128位）
            byte[] keyBytes = get16ByteKey(secretKey);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("数据解密失败: {}", e.getMessage(), e);
            // 解密失败时返回原文本（可能是未加密的旧数据）
            return encryptedData;
        }
    }

    /**
     * 数据脱敏显示（仅显示前4位和后4位）
     *
     * @param sensitiveData 敏感数据
     * @return 脱敏后的数据
     */
    public String mask(String sensitiveData) {
        if (!StringUtils.hasText(sensitiveData)) {
            return "";
        }

        int length = sensitiveData.length();
        if (length <= 8) {
            // 长度不足8位，全部脱敏
            return "*".repeat(length);
        }

        String prefix = sensitiveData.substring(0, 4);
        String suffix = sensitiveData.substring(length - 4);
        int maskedLength = length - 8;

        return prefix + "*".repeat(maskedLength) + suffix;
    }

    /**
     * 将密钥转换为16字节（128位）
     * 如果密钥不足16位，则补齐；超过16位则截取
     *
     * @param key 原始密钥
     * @return 16字节密钥
     */
    private byte[] get16ByteKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[16];

        if (keyBytes.length >= 16) {
            // 密钥长度大于等于16，截取前16位
            System.arraycopy(keyBytes, 0, result, 0, 16);
        } else {
            // 密钥长度小于16，补齐到16位
            System.arraycopy(keyBytes, 0, result, 0, keyBytes.length);
            // 剩余部分用0填充
            for (int i = keyBytes.length; i < 16; i++) {
                result[i] = 0;
            }
        }

        return result;
    }

    /**
     * 验证加密解密功能
     *
     * @return 测试是否成功
     */
    public boolean testEncryption() {
        try {
            String testData = "test_api_key_12345";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            boolean success = testData.equals(decrypted);
            if (success) {
                log.info("加密解密测试成功");
            } else {
                log.error("加密解密测试失败: 原始数据与解密数据不匹配");
            }

            return success;

        } catch (Exception e) {
            log.error("加密解密测试失败: {}", e.getMessage(), e);
            return false;
        }
    }
}

