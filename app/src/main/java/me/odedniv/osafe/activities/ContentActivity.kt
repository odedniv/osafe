package me.odedniv.osafe.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.FloatingActionButton
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.BackgroundColorSpan
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.DriveScopes
import kotlinx.android.synthetic.main.activity_content.*
import me.odedniv.osafe.R
import me.odedniv.osafe.dialogs.GeneratePassphraseDialog
import me.odedniv.osafe.extensions.logFailure
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.Message
import me.odedniv.osafe.models.storage.StorageFormat
import me.odedniv.osafe.services.EncryptionStorageService
import java.util.*
import java.util.regex.Pattern

class ContentActivity : BaseActivity(), GeneratePassphraseDialog.Listener {
    companion object {
        private const val REQUEST_GOOGLE_SIGN_IN = 1
        private const val REQUEST_ENCRYPTION = 2

        private val WORD_SEPARATOR_PATTERN = Pattern.compile("\\W")
    }

    private val storage = Storage(this)
    private var started = false
    private var resumed = false
    private var googleSignInReceived = false
    private var encryption: Encryption? = null
    private var encryptionStorage : EncryptionStorageService.EncryptionStorageBinder? = null
    private var imm: InputMethodManager? = null

    private var wordsUpdated = true
    private var searchHasFocus = false
    private var contentEditable = true
    private var originalMessage: Message? = null
    private var lastStored: String? = null
    private var pauseScrollPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_content)
        setSupportActionBar(toolbar_content)

        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        startService(encryptionStorageIntent)
        bindService(
                encryptionStorageIntent,
                encryptionStorageConnection,
                Context.BIND_AUTO_CREATE
        )

        edit_content.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!resumed) return
                dumpLater()
                wordsUpdated = true
                setSearchAutoCompleteAdapter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })
        edit_content.setOnFocusChangeListener { _, hasFocus ->
            // hiding keyboard if it's shown
            if (hasFocus && !contentEditable) imm!!.hideSoftInputFromWindow(edit_content.windowToken, 0)
        }
        autocomplete_search.setOnFocusChangeListener { _, hasFocus ->
            searchHasFocus = hasFocus
            setSearchAutoCompleteAdapter()
        }
        autocomplete_search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                findNext()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })
        autocomplete_search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                autocomplete_search.dismissDropDown()
                imm!!.hideSoftInputFromWindow(autocomplete_search.windowToken, 0)
                findNext()
                true
            } else {
                false
            }
        }
        autocomplete_search.setOnItemClickListener { _, _, _, _ ->
            imm!!.hideSoftInputFromWindow(autocomplete_search.windowToken, 0)
        }
        (button_insert_passphrase as FloatingActionButton).setOnClickListener {
            GeneratePassphraseDialog().show(supportFragmentManager, "GeneratePassphraseDialog")
        }
        (button_toggle_input as FloatingActionButton).setOnClickListener {
            setContentEditable(!contentEditable)
        }
        setContentEditable(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_content, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?) = when(item!!.itemId) {
        R.id.action_change_passphrase -> {
            startActivityForResult(
                    Intent(this, NewPassphraseActivity::class.java)
                            .putExtra(EXTRA_ENCRYPTION, encryption)
                            .putExtra(EXTRA_STORAGE, storage.state),
                    REQUEST_ENCRYPTION
            )
            true
        } else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        unbindService(encryptionStorageConnection)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        started = true
        getGoogleSignInAccount()
        getEncryptionAndLoad()
    }

    override fun onStop() {
        started = false
        encryption = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        dump()
        // reset instance state
        pauseScrollPosition = scroll_content.scrollY
        edit_content.text.clear()
        autocomplete_search.text.clear()
        setContentEditable(false)
        currentFocus?.clearFocus()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_GOOGLE_SIGN_IN -> {
                GoogleSignIn.getSignedInAccountFromIntent(data)
                        .addOnSuccessListener {
                            storage.setGoogleSignInAccount(it)
                            googleSignInReceived = true
                            getEncryptionAndLoad()
                        }
                        .logFailure(this, "GoogleSignIn", "Failed getting Google account")
                        .addOnFailureListener {
                            finish()
                        }
            }
            REQUEST_ENCRYPTION -> {
                if (resultCode != Activity.RESULT_OK) {
                    finish()
                    return
                }
                data!!
                encryption = data.getParcelableExtra(EXTRA_ENCRYPTION)
                encryptionStorage?.set(
                        encryption = encryption!!,
                        timeout = data.getLongExtra(EXTRA_ENCRYPTION_TIMEOUT, 0)
                )
                getEncryptionAndLoad()
            }
        }
    }

    private fun setSearchAutoCompleteAdapter() {
        if (!searchHasFocus || !wordsUpdated) return
        autocomplete_search.setAdapter(ArrayAdapter<String>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                edit_content.text.split(WORD_SEPARATOR_PATTERN)
        ))
        wordsUpdated = false
    }

    private fun findNext() {
        edit_content.text.getSpans(0, edit_content.text.length, BackgroundColorSpan::class.java).forEach {
            edit_content.text.removeSpan(it)
        }

        var first = true
        if (!autocomplete_search.text.isBlank()) {
            val start = edit_content.selectionEnd
            var next = start
            var looped = false
            while (!looped || next < start) {
                val index = edit_content.text.indexOf(autocomplete_search.text.toString(), next, true)
                if (index != -1) {
                    if (first) {
                        edit_content.setSelection(index + autocomplete_search.text.length)
                        val layout = edit_content.layout
                        val line = layout.getLineForOffset(index)
                        scroll_content.smoothScrollTo(0, layout.getLineBaseline(line) + (layout.getLineAscent(line) / 2) - (scroll_content.height / 2))
                        first = false
                    }
                    edit_content.text.setSpan(BackgroundColorSpan(Color.YELLOW), index, index + autocomplete_search.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    next = index + autocomplete_search.text.length
                } else if (!looped) {
                    looped = true
                    next = 0
                } else {
                    break
                }
            }
        }
    }

    private fun setContentEditable(value: Boolean) {
        contentEditable = value
        // enabling/disabling input on touch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // API 21
            edit_content.showSoftInputOnFocus = value
        } else { // API 11-20
            if (!value) {
                edit_content.setTextIsSelectable(true)
            } else {
                val selectionStart = edit_content.selectionStart
                val selectionEnd = edit_content.selectionEnd
                edit_content.setTextIsSelectable(false)
                edit_content.isFocusable = true
                edit_content.isFocusableInTouchMode = true
                edit_content.isClickable = true
                edit_content.isLongClickable = true
                edit_content.movementMethod = ArrowKeyMovementMethod.getInstance()
                edit_content.setText(edit_content.text, TextView.BufferType.SPANNABLE)
                edit_content.setSelection(selectionStart, selectionEnd)
            }
        }
        // showing/hiding input now
        if (value) {
            if (edit_content.requestFocus()) {
                imm!!.showSoftInput(edit_content, 0)
            }
        } else {
            imm!!.hideSoftInputFromWindow(edit_content.windowToken, 0)
        }
        // setting button icon
        button_toggle_input.setImageResource(
                if (!value)
                    R.drawable.ic_keyboard_show
                else
                    R.drawable.ic_keyboard_hide
        )
    }

    override fun onInsertPassphrase(value: String) {
        edit_content.text.replace(edit_content.selectionStart, edit_content.selectionEnd, value)
    }

    private fun getGoogleSignInAccount() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            googleSignInReceived = true
            return
        }
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (googleSignInAccount != null
                && googleSignInAccount.grantedScopes.contains(Scope(Scopes.EMAIL))
                && googleSignInAccount.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))) {
            storage.setGoogleSignInAccount(googleSignInAccount)
            googleSignInReceived = true
            return
        }
        startActivityForResult(
                GoogleSignIn.getClient(
                        this,
                        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                                .build()
                ).signInIntent,
                REQUEST_GOOGLE_SIGN_IN
        )
    }

    private fun getEncryptionAndLoad() {
        if (!started || !googleSignInReceived || encryptionStorage == null) return
        if (encryption == null) encryption = encryptionStorage?.encryption
        resolveConflicts().addOnSuccessListener {
            if (encryption != null) {
                // from EncryptionStorageService
                load()
            } else {
                // either timed out, never set
                storage.exists.addOnSuccessListener { exists ->
                    val activity =
                            if (exists)
                                ExistingPassphraseActivity::class.java
                            else
                                NewPassphraseActivity::class.java
                    startActivityForResult(
                            Intent(this@ContentActivity, activity),
                            REQUEST_ENCRYPTION
                    )
                }.logFailure(this, "Load", "Failed checking existing")
            }
        }.logFailure(this, "Load", "Failed resolving conflicts")
    }

    private var dumpLaterTimer: Timer? = null

    private fun dumpLater() {
        encryption ?: return

        if (dumpLaterTimer != null) {
            dumpLaterTimer?.cancel()
            dumpLaterTimer = null
        }

        if (lastStored == edit_content.text.toString()) return

        dumpLaterTimer = Timer()
        dumpLaterTimer!!.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    progress_spinner.visibility = View.VISIBLE
                    dump().addOnSuccessListener {
                        progress_spinner.visibility = View.GONE
                    }
                }
            }
        }, 5000)
    }

    private fun dump(): Task<Unit> {
        encryption ?: return Tasks.forResult(Unit)

        if (dumpLaterTimer != null) {
            dumpLaterTimer?.cancel()
            dumpLaterTimer = null
        }

        val content = edit_content.text.toString()
        if (lastStored == content) return Tasks.forResult(Unit)
        lastStored = content

        return encryption!!.encrypt(content)
                .onSuccessTask { message ->
                    storage.set(message!!)
                }.logFailure(this, "Dump", "Failed encrypting")
    }

    private fun load() {
        storage.get { message ->
            if (message == null) return@get
            if (encryption == null) return@get

            encryption!!.decrypt(message)
                    .addOnSuccessListener { content ->
                        originalMessage = message
                        lastStored = content
                        edit_content.setText(content)
                        edit_content.isEnabled = true
                        scroll_content.scrollY = pauseScrollPosition
                    }
                    .addOnFailureListener {
                        encryption = null
                        encryptionStorage?.clear()
                        Toast.makeText(this, R.string.wrong_passphrase, Toast.LENGTH_SHORT).show()
                        getEncryptionAndLoad()
                    }
        }.addOnSuccessListener {
            progress_spinner.visibility = View.GONE
        }.logFailure(this, "Load", "Failed loading")
    }

    private fun resolveConflicts(): Task<Unit> {
        return storage.conflicts.onSuccessTask { conflictedStorageFormats ->
            resolveNextConflict(conflictedStorageFormats!!)
        }
    }

    private fun resolveNextConflict(conflictedStorageFormats: List<StorageFormat>, index: Int = conflictedStorageFormats.size - 1): Task<Unit> {
        if (index == -1) return Tasks.forResult(Unit)
        val currentStorageFormat = conflictedStorageFormats[index]
        val task = TaskCompletionSource<Unit>()

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.resolve_conflict_title, getString(currentStorageFormat.stringId)))
                .setNegativeButton(R.string.resolve_conflict_overwrite) { _, _ ->
                    currentStorageFormat.clear()
                    resolveNextConflict(conflictedStorageFormats, index - 1)
                            .addOnSuccessListener { task.setResult(Unit) }
                }
                .setPositiveButton(R.string.resolve_conflict_use) { _, _ ->
                    storage.resolveConflictWith(currentStorageFormat)
                    task.setResult(Unit)
                }
                .show()

        return task.task
    }

    private val encryptionStorageConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            encryptionStorage = service as EncryptionStorageService.EncryptionStorageBinder
            getEncryptionAndLoad()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            encryptionStorage = null
        }
    }

    private val encryptionStorageIntent: Intent
        get() = Intent(this, EncryptionStorageService::class.java)
}
