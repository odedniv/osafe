package me.odedniv.osafe.models.encryption

import android.os.Parcel
import android.os.Parcelable
import android.security.keystore.KeyProperties
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

@Parcelize
data class Key(val label: @WriteWith<LabelParceler>() Label, val content: Content) : Parcelable {
  sealed interface Label {
    data class Passphrase(val digestType: DigestType = DEFAULT_DIGEST_TYPE) : Label {
      enum class DigestType(val algorithm: String) {
        SHA_512(KeyProperties.DIGEST_SHA512)
      }

      fun digest(passphrase: String): ByteArray =
        MessageDigest.getInstance(digestType.algorithm)
          .digest(passphrase.toByteArray(Charsets.UTF_8))

      override fun toString() = "PASSPHRASE/$digestType"

      companion object {
        val DEFAULT_DIGEST_TYPE = DigestType.SHA_512
      }
    }

    data class Biometric(val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)) :
      Label {
      override fun toString() = "BIOMETRIC/$createdAt"
    }

    companion object {
      fun fromString(value: String): Label {
        val split = value.split('/', limit = 2)
        return when (split[0]) {
          "PASSPHRASE" ->
            Passphrase(
              digestType =
                split.getOrNull(1)?.let { Passphrase.DigestType.valueOf(it) }
                  ?: Passphrase.DEFAULT_DIGEST_TYPE
            )
          "BIOMETRIC" -> Biometric(createdAt = Instant.parse(split[1]))
          // Unknown types will try to decrypt as passphrase, and will fail.
          else -> Passphrase()
        }
      }
    }
  }

  object LabelParceler : Parceler<Label> {
    override fun create(parcel: Parcel) = Label.fromString(parcel.readString()!!)

    override fun Label.write(parcel: Parcel, flags: Int) {
      parcel.writeString(toString())
    }
  }
}
