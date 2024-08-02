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
import java.util.Objects
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

@Parcelize
data class Content(
  val cipherType: CipherType,
  val digestType: DigestType,
  val iv: ByteArray,
  val digest: ByteArray,
  val content: ByteArray,
) : Parcelable {
  override fun equals(other: Any?): Boolean =
    super.equals(other) ||
      (other is Content &&
        cipherType == other.cipherType &&
        digestType == other.digestType &&
        iv contentEquals other.iv &&
        digest contentEquals other.digest &&
        content contentEquals other.content)

  override fun hashCode(): Int =
    Objects.hash(
      cipherType,
      digestType,
      iv.contentHashCode(),
      digest.contentHashCode(),
      content.contentHashCode(),
    )

  enum class CipherType(val algorithm: String, val blockMode: String, val padding: String) {
    AES_128(
      algorithm = KeyProperties.KEY_ALGORITHM_AES,
      blockMode = KeyProperties.BLOCK_MODE_CBC,
      padding = KeyProperties.ENCRYPTION_PADDING_PKCS7,
    );

    val transformation by lazy { "$algorithm/$blockMode/$padding" }
  }

  enum class DigestType(val algorithm: String) {
    SHA_1(KeyProperties.DIGEST_SHA1)
  }

  companion object {
    private val DEFAULT_CIPHER_TYPE = CipherType.AES_128
    private val DEFAULT_DIGEST_TYPE = DigestType.SHA_1
    private val DISPATCHER = Dispatchers.Default

    private suspend fun Cipher.dispatchDoFinal(input: ByteArray): ByteArray =
      withContext(DISPATCHER) { doFinal(input) }

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

    suspend fun encrypt(cipher: Cipher, content: ByteArray): Content =
      Content(
        cipherType = DEFAULT_CIPHER_TYPE,
        digestType = DEFAULT_DIGEST_TYPE,
        iv = cipher.iv,
        digest = MessageDigest.getInstance(DEFAULT_DIGEST_TYPE.algorithm).digest(content),
        content = cipher.dispatchDoFinal(content),
      )

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

  suspend fun decrypt(cipher: Cipher): ByteArray? {
    val content =
      try {
        cipher.dispatchDoFinal(content)
      } catch (e: GeneralSecurityException) {
        return null
      }
    if (!digest.contentEquals(MessageDigest.getInstance(digestType.algorithm).digest(content))) {
      return null
    }
    return content
  }
}
