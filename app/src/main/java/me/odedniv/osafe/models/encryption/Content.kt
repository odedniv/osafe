package me.odedniv.osafe.models.encryption

import android.os.Parcelable
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.parcelize.Parcelize

@Parcelize
data class Content(
  val cipherType: CipherType,
  val digestType: DigestType,
  val iv: ByteArray,
  val digest: ByteArray,
  val content: ByteArray,
) : Parcelable {

  enum class CipherType {
    AES_128;

    companion object {
      val ALGORITHMS = mapOf(AES_128 to KeyProperties.KEY_ALGORITHM_AES)
      val BLOCK_MODES = mapOf(AES_128 to KeyProperties.BLOCK_MODE_CBC)
      val PADDINGS = mapOf(AES_128 to KeyProperties.ENCRYPTION_PADDING_PKCS7)
    }

    val algorithm by lazy { ALGORITHMS[this]!! }
    val blockMode by lazy { BLOCK_MODES[this]!! }
    val padding by lazy { PADDINGS[this]!! }
    val transformation by lazy { "$algorithm/$blockMode/$padding" }
  }

  enum class DigestType {
    SHA_1;

    companion object {
      val ALGORITHMS = mapOf(SHA_1 to "SHA-1")
    }

    val algorithm by lazy { ALGORITHMS[this]!! }
  }

  companion object {
    val DEFAULT_CIPHER_TYPE = CipherType.AES_128
    val DEFAULT_DIGEST_TYPE = DigestType.SHA_1

    fun encryptCipher(key: ByteArray): Cipher {
      val cipher = Cipher.getInstance(DEFAULT_CIPHER_TYPE.transformation)
      cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key.copyOf(cipher.blockSize), DEFAULT_CIPHER_TYPE.algorithm),
      )
      return cipher
    }

    fun biometricEncryptCipher(): Cipher {
      val cipher = Cipher.getInstance(DEFAULT_CIPHER_TYPE.transformation)
      cipher.init(Cipher.ENCRYPT_MODE, getBiometricSecretKey() ?: generateBiometricSecretKey())
      return cipher
    }

    fun encrypt(cipher: Cipher, content: ByteArray): Content {
      return Content(
        cipherType = DEFAULT_CIPHER_TYPE,
        digestType = DEFAULT_DIGEST_TYPE,
        iv = cipher.iv,
        digest = MessageDigest.getInstance(DEFAULT_DIGEST_TYPE.algorithm).digest(content),
        content = cipher.doFinal(content),
      )
    }

    private fun generateBiometricSecretKey(): SecretKey {
      val keyGenerator = KeyGenerator.getInstance(DEFAULT_CIPHER_TYPE.algorithm, "AndroidKeyStore")
      keyGenerator.init(
        KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(DEFAULT_CIPHER_TYPE.blockMode)
          .setEncryptionPaddings(DEFAULT_CIPHER_TYPE.padding)
          .setUserAuthenticationRequired(true)
          // Invalidate the keys if the user has registered a new biometric credential, such as a
          // new fingerprint.
          .setInvalidatedByBiometricEnrollment(true)
          .build()
      )
      return keyGenerator.generateKey()
    }

    private fun getBiometricSecretKey(): SecretKey? {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null) // Before the keystore can be accessed, it must be loaded.
      return try {
        keyStore.getKey(BIOMETRIC_KEY_NAME, null) as SecretKey?
      } catch (e: Exception) {
        when (e) {
          is KeyStoreException,
          is NoSuchAlgorithmException,
          is UnrecoverableKeyException -> null
          else -> throw e
        }
      }
    }

    private const val BIOMETRIC_KEY_NAME = "biometric"
  }

  fun decryptCipher(key: ByteArray): Cipher {
    val cipher = Cipher.getInstance(cipherType.transformation)
    cipher.init(
      Cipher.DECRYPT_MODE,
      SecretKeySpec(key.copyOf(cipher.blockSize), cipherType.algorithm),
      IvParameterSpec(iv),
    )
    return cipher
  }

  fun biometricDecryptCipher(): Cipher? {
    val secretKey = getBiometricSecretKey() ?: return null
    val cipher = Cipher.getInstance(cipherType.transformation)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
    return cipher
  }

  fun decrypt(cipher: Cipher): ByteArray? {
    val content = try {
      cipher.doFinal(content)
    } catch (e: GeneralSecurityException) {
      return null
    }
    if (!digest.contentEquals(MessageDigest.getInstance(digestType.algorithm).digest(content))) {
      return null
    }
    return content
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Content

    if (cipherType != other.cipherType) return false
    if (digestType != other.digestType) return false
    if (!Arrays.equals(iv, other.iv)) return false
    if (!Arrays.equals(digest, other.digest)) return false
    if (!Arrays.equals(content, other.content)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = cipherType.hashCode()
    result = 31 * result + digestType.hashCode()
    result = 31 * result + Arrays.hashCode(iv)
    result = 31 * result + Arrays.hashCode(digest)
    result = 31 * result + Arrays.hashCode(content)
    return result
  }
}
