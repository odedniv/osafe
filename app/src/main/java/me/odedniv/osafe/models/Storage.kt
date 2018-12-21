package me.odedniv.osafe.models

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.extensions.toResult
import me.odedniv.osafe.models.encryption.Message
import me.odedniv.osafe.models.storage.DriveStorageFormat
import me.odedniv.osafe.models.storage.FileStorageFormat
import me.odedniv.osafe.models.storage.StorageFormat

class Storage(private val context: Context) {

    class State constructor() : Parcelable {
        var googleSignInAccount: GoogleSignInAccount? = null

        private constructor(parcel: Parcel) : this() {
            googleSignInAccount = parcel.readParcelable(GoogleSignInAccount::class.java.classLoader)
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(googleSignInAccount, 0)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<State> {
            override fun createFromParcel(parcel: Parcel): State {
                return State(parcel)
            }

            override fun newArray(size: Int): Array<State?> {
                return arrayOfNulls(size)
            }
        }
    }

    private var _state: State = State()
    var state: State
        get() = _state
        set(value) {
            _state = value
            setGoogleSignInAccount(_state.googleSignInAccount)
        }

    private val storageFormats = ArrayList<StorageFormat>()

    init {
        storageFormats.add(FileStorageFormat(context))
    }

    fun setGoogleSignInAccount(googleSignInAccount: GoogleSignInAccount?) {
        state.googleSignInAccount = googleSignInAccount
        storageFormats.removeAll { it is DriveStorageFormat }
        if (googleSignInAccount != null) {
            storageFormats.add(DriveStorageFormat(context, googleSignInAccount))
        }
    }

    val exists: Task<Boolean>
        get() {
            val taskCompletionSource = TaskCompletionSource<Boolean>()

            Tasks.whenAll(storageFormats.map { storageFormat ->
                storageFormat.exists().addOnSuccessListener { exists ->
                    // sets first true
                    if (exists) taskCompletionSource.trySetResult(true)
                }
            }).addOnSuccessListener {
                // sets false if finished and no true
                taskCompletionSource.trySetResult(false)
            }.addOnFailureListener {
                taskCompletionSource.trySetException(it)
            }

            return taskCompletionSource.task
        }

    val conflicts: Task<List<StorageFormat>>
        get() {
            val tasks = storageFormats.associateBy({ it }, { it.conflicts() })
            return Tasks.whenAll(tasks.values)
                    .onSuccessTask {
                        Tasks.forResult(
                                storageFormats.filter { tasks[it]!!.result!! }
                        )
                    }
        }

    fun resolveConflictWith(storageFormat: StorageFormat): Task<Unit> {
        return storageFormat.read()
                .onSuccessTask { content ->
                    Tasks.whenAll(
                            storageFormats
                                    .filter { it !== storageFormat }
                                    .map { it.write(content!!) }
                    )
                }.toResult(Unit)
    }

    fun get() = get { }

    fun get(receiver: (message: Message?) -> Unit): Task<Message?> {
        // prefer the last storage format (in the list of storage formats)
        var lastStorageFormatIndex: Int = -1
        var lastContent: ByteArray? = null
        var lastMessage: Message? = null
        val allContents = HashMap<StorageFormat, ByteArray>()
        return Tasks.whenAll(
                storageFormats.mapIndexed { index, storageFormat ->
                    storageFormat.read()
                            .addOnSuccessListener { content ->
                                if (content == null) return@addOnSuccessListener
                                allContents[storageFormat] = content
                                if (lastStorageFormatIndex < index) {
                                    lastStorageFormatIndex = index
                                    lastContent = content
                                }
                                if (lastStorageFormatIndex == index) {
                                    lastMessage = Message.decode(content)
                                    receiver(lastMessage)
                                }
                            }
                }
        ).onSuccessTask {
            if (lastContent == null) return@onSuccessTask Tasks.forResult(lastMessage)
            Tasks.whenAll(
                    storageFormats
                            .filter { !allContents.containsKey(it) || !lastContent!!.contentEquals(allContents[it]!!) }
                            .map { storageFormat -> storageFormat.write(lastContent!!) }
            ).toResult(lastMessage)
        }
    }

    fun set(message: Message): Task<Unit> {
        val content = message.encode()
        return Tasks.whenAll(storageFormats.map { it.write(content) }).toResult(Unit)
    }

    fun reset(): Task<Unit> {
        return Tasks.whenAll(
                storageFormats.map { it.clear() }
        ).toResult(Unit)
    }
}