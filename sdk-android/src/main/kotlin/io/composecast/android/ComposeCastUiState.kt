package io.composecast.android

/** What a [ComposeCastView] shows (T-059); passed to its `onStateChanged`. */
public sealed interface ComposeCastUiState {
    /** The screen is being fetched. */
    public data object Loading : ComposeCastUiState

    /** [screen] is shown; its [ScreenDocument.title] is the screen's title (e.g. for an app bar). */
    public data class Ready(val screen: ScreenDocument) : ComposeCastUiState

    /** Fetching failed; the error slot is shown with a retry. */
    public data class Error(val error: ComposeCastError) : ComposeCastUiState
}
