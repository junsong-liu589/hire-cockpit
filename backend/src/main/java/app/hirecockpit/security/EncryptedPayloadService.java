package app.hirecockpit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EncryptedPayloadService {
    private final String encodedKey;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;
    public EncryptedPayloadService(@Value("${app.encryption-key:}") String encodedKey,ObjectMapper mapper){this.encodedKey=encodedKey;this.mapper=mapper;}
    @PostConstruct void load(){if(encodedKey==null||encodedKey.isBlank())return;try{byte[] bytes=Base64.getDecoder().decode(encodedKey);if(bytes.length!=32)throw new IllegalArgumentException("APP_ENCRYPTION_KEY must decode to 32 bytes");key=new SecretKeySpec(bytes,"AES");Arrays.fill(bytes,(byte)0);}catch(Exception e){throw new IllegalStateException("Invalid APP_ENCRYPTION_KEY",e);}}
    public byte[] encrypt(String workspaceId,Object value){requireKey();try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(workspaceId.getBytes(java.nio.charset.StandardCharsets.US_ASCII));byte[] encrypted=c.doFinal(mapper.writeValueAsBytes(value));byte[] result=Arrays.copyOf(nonce,nonce.length+encrypted.length);System.arraycopy(encrypted,0,result,nonce.length,encrypted.length);return result;}catch(Exception e){throw new IllegalStateException("Unable to encrypt profile data",e);}}
    public <T>T decrypt(byte[] payload,Class<T> type,String workspaceId){requireKey();try{if(payload==null||payload.length<29)throw new IllegalArgumentException("Invalid encrypted profile payload");byte[] nonce=Arrays.copyOfRange(payload,0,12),ciphertext=Arrays.copyOfRange(payload,12,payload.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(workspaceId.getBytes(java.nio.charset.StandardCharsets.US_ASCII));return mapper.readValue(c.doFinal(ciphertext),type);}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Profile data could not be decrypted; verify the configured encryption key",e);}}
    private void requireKey(){if(key==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Set APP_ENCRYPTION_KEY to enable encrypted personal profile storage");}
}
