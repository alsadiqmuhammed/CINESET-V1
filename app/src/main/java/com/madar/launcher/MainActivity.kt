package com.madar.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.madar.launcher.ui.LauncherController
import com.madar.launcher.ui.MadarRoot
import com.madar.launcher.ui.Overlay

class MainActivity : ComponentActivity() {

    private lateinit var controller: LauncherController

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionCallback?.invoke(it)
        permissionCallback = null
    }

    private var multiPermissionCallback: (() -> Unit)? = null
    private val multiPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        multiPermissionCallback?.invoke()
        multiPermissionCallback = null
    }

    private var documentCallback: ((Uri) -> Unit)? = null
    private val documentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            documentCallback?.invoke(uri)
        }
        documentCallback = null
    }

    private var speechCallback: ((String) -> Unit)? = null
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { speechCallback?.invoke(it) }
        speechCallback = null
    }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        controller = LauncherController(this, application as MadarApp)

        // A launcher never exits on Back; it only peels away the topmost overlay.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = controller.close()
        })

        setContent { MadarRoot(controller) }
    }

    override fun onResume() {
        super.onResume()
        val app = application as MadarApp
        app.stacks.archiveExpired()
        app.contacts.invalidate()
        app.shelf.purge()
    }

    /** Pressing Home while already home returns to the clean surface. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (controller.overlay !is Overlay.Settings || (application as MadarApp).settings.value.onboarded) controller.close()
    }

    fun requestPermission(permission: String, callback: (Boolean) -> Unit) {
        permissionCallback = callback
        permissionLauncher.launch(permission)
    }

    fun requestPermissions(permissions: Array<String>, callback: () -> Unit) {
        multiPermissionCallback = callback
        multiPermissionLauncher.launch(permissions)
    }

    fun pickDocument(callback: (Uri) -> Unit) {
        documentCallback = callback
        documentLauncher.launch(arrayOf("*/*"))
    }

    fun listen(callback: (String) -> Unit) {
        speechCallback = callback
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-IQ")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "گول الأمر")
        runCatching { speechLauncher.launch(intent) }.onFailure { controller.toast("التعرف على الصوت غير متوفر") }
    }

    val isDefaultHome: Boolean
        get() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    fun requestHomeRole() {
        roleLauncher.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
    }
}
