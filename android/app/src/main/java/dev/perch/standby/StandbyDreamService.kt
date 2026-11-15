package dev.perch.standby

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.perch.standby.ui.StandbyScreen

/**
 * The screensaver ("dream") the system shows while the phone is charging or docked.
 * A DreamService is not a LifecycleOwner, so it supplies the owners Compose needs itself.
 */
class StandbyDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true // swipes change pages instead of waking the phone
        isFullscreen = true
        isScreenBright = true
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@StandbyDreamService)
                setViewTreeSavedStateRegistryOwner(this@StandbyDreamService)
                setContent { StandbyScreen(graph) }
            }
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
