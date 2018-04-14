package me.odedniv.osafe.models

import android.content.Context
import android.util.Base64
import android.util.Base64DataException
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import java.io.FileNotFoundException

class Storage(private val context: Context) {
    private val klaxon = Klaxon()
            .converter(object : Converter {
                override fun canConvert(cls: Class<*>)
                        = cls == ByteArray::class.java

                override fun toJson(value: Any)
                        = "\"${Base64.encodeToString(value as ByteArray, Base64.DEFAULT)}\""

                override fun fromJson(jv: JsonValue)
                        = Base64.decode(jv.string!!, Base64.DEFAULT)
            })

    companion object {
        private const val FILENAME = "osafe.aes"
    }

    private var _read = false
    private var _message: Encryption.Message? = null
    var message: Encryption.Message?
        get() {
            if (_read) return _message
            _read = true
            try {
                context.openFileInput(FILENAME).use {
                    _message = klaxon.parse<Encryption.Message>(it)!!
                }
            }
            catch (e: FileNotFoundException) { }
            catch (e: KlaxonException) { }
            catch (e: Base64DataException) { }
            catch (e: NullPointerException) { }
            return _message
        }
        set(message) {
            _read = true
            _message = message
            if (message != null) {
                context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use {
                    it.write(klaxon.toJsonString(message).toByteArray(Charsets.UTF_8))
                }
            } else {
                context.deleteFile(FILENAME)
            }
        }
}