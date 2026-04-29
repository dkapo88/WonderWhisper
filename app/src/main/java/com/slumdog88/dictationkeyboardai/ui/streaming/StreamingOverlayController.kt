package com.slumdog88.dictationkeyboardai.ui.streaming

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.slumdog88.dictationkeyboardai.transcription.streaming.StreamingUiState
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StreamingOverlayController(
    private val context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onStopRequested()
        fun onCancelRequested()
        fun onCopyRequested(text: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val stateFlow: MutableStateFlow<StreamingUiState> =
        MutableStateFlow(StreamingUiState.idle())

    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached: Boolean = false
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun updateState(state: StreamingUiState) {
        if (!state.isActive) {
            hide()
            return
        }
        stateFlow.value = state
        showIfNeeded()
    }

    fun hide() {
        mainHandler.post {
            lifecycleOwner?.handleDetach()
            lifecycleOwner = null
            if (isAttached) {
                try {
                    composeView?.let { windowManager.removeView(it) }
                } catch (_: Exception) {
                }
                isAttached = false
            }
            composeView?.disposeComposition()
            composeView = null
            layoutParams = null
        }
    }

    fun release() {
        hide()
    }

    private fun showIfNeeded() {
        mainHandler.post {
            if (!isAttached) {
                val owner = OverlayLifecycleOwner()
                val view = ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        val uiState by stateFlow.collectAsState()
                        AppTheme {
                            StreamingOverlayContent(
                                state = uiState,
                                actions = StreamingUiActions(
                                    onStop = { callbacks.onStopRequested() },
                                    onCancel = { callbacks.onCancelRequested() },
                                    onCopy = {
                                        val copySource = uiState.formattedTranscript.ifBlank {
                                            uiState.livePreview
                                        }
                                        if (copySource.isNotBlank()) {
                                            callbacks.onCopyRequested(copySource)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                composeView = view
                lifecycleOwner = owner
                view.setTag(androidx.lifecycle.runtime.R.id.view_tree_lifecycle_owner, owner)
                view.setTag(androidx.savedstate.R.id.view_tree_saved_state_registry_owner, owner)
                layoutParams = createLayoutParams()
                tryAddView(view)
                owner.handleAttach()
            }
        }
    }

    private fun tryAddView(view: View) {
        val params = layoutParams ?: createLayoutParams()
        layoutParams = params
        try {
            windowManager.addView(view, params)
            isAttached = true
        } catch (_: Exception) {
            isAttached = false
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val width = (context.resources.displayMetrics.widthPixels * 0.9f).toInt()
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (density * 48).toInt()
        }
    }

    val uiState: StateFlow<StreamingUiState> = stateFlow

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun handleAttach() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun handleDetach() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
    }
}
