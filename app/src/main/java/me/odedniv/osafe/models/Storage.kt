package me.odedniv.osafe.models

import android.content.Context
import android.util.Base64
import android.util.Base64DataException
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.runOnUiThread
import java.io.FileNotFoundException

class Storage(private val context: Context) {
    companion object {
        private const val FILENAME = "osafe.aes"
    }

    private val klaxon = Klaxon()
            .converter(object : Converter {
                override fun canConvert(cls: Class<*>)
                        = cls == ByteArray::class.java

                override fun toJson(value: Any)
                        = "\"${Base64.encodeToString(value as ByteArray, Base64.DEFAULT)}\""

                override fun fromJson(jv: JsonValue)
                        = Base64.decode(jv.string!!, Base64.DEFAULT)
            })

    private var _message: Encryption.Message? = null
    private var _messageRead = false

    val messageExists: Boolean
        get() = _message != null || fileExists() || driveExists()

    var message: Encryption.Message?
        get() = _message
        set(message) {
            _message = message
            writeToFile(message)
            writeToDrive(message)
        }

    fun getMessage(receiver: (message: Encryption.Message?) -> Unit) {
        doAsync {
            if (useBetterSource(readFromFile(), receiver)) {
                // updating drive
                writeToDrive(message)
            }
        }
        doAsync {
            if (useBetterSource(readFromDrive(), receiver)) {
                // updating file
                writeToFile(message)
            }
        }
    }

    @Synchronized
    private fun useBetterSource(message: Encryption.Message?, receiver: (message: Encryption.Message?) -> Unit): Boolean {
        var replaced = false
        // if no source read, or this message is better than the current better source
        if (!_messageRead
                || (message != null
                        && (_message == null || _message!!.version < message.version))) {
            replaced = _message != null
            // replace better source and activate receiver
            _messageRead = true
            _message = message
            context.runOnUiThread {
                receiver(message)
            }
        }
        return replaced
    }


    /*
    File storage
     */

    private var _fileRead = false
    private var _file: Encryption.Message? = null

    private fun fileExists(): Boolean {
        return if (_fileRead) {
            _file != null
        } else {
            val file = context.getFileStreamPath(FILENAME)
            file != null && file.exists()
        }
    }

    private fun readFromFile(): Encryption.Message? {
        if (_fileRead) return _file
        try {
            context.openFileInput(FILENAME).use {
                _file = klaxon.parse<Encryption.Message>(it)!!
            }
        }
        catch (e: FileNotFoundException) { }
        catch (e: KlaxonException) { }
        catch (e: Base64DataException) { }
        catch (e: NullPointerException) { }
        _fileRead = true
        return _file
    }

    private fun writeToFile(message: Encryption.Message?) {
        _file = message
        _fileRead = true
        if (message != null) {
            context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use {
                it.write(klaxon.toJsonString(message).toByteArray(Charsets.UTF_8))
            }
        } else {
            context.deleteFile(FILENAME)
        }
    }

    /*
    Drive storage
     */

    private var _driveRead = false
    private var _drive: Encryption.Message? = null

    private fun driveExists(): Boolean {
        return false
    }

    private fun readFromDrive(): Encryption.Message? {
        return _drive
    }

    private fun writeToDrive(message: Encryption.Message?) {
    }
}