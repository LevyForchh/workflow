package com.squareup.workflow.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * A placeholder [View] that can replace itself with ones driven by workflow renderings,
 * similar to [android.view.ViewStub].
 *
 * ## Usage
 *
 * In the XML layout for your container view, place a [WorkflowViewStub] where
 * you want child renderings to be displayed. E.g.:
 *
 *    <LinearLayout…>
 *
 *        <com.squareup.workflow.ui.WorkflowViewStub
 *            android:id="@+id/child_stub"
 *            />
 *       …
 *
 * Then in your [LayoutRunner],
 *   - get the `ViewRegistry` in your constructor
 *   - pull the view out with `findViewById` like any other view
 *   - and update it in your `showRendering` method:
 *
 * ```
 *     class YourLayoutRunner(
 *       view: View, private
 *       val viewRegistry: ViewRegistry
 *     ) {
 *       private val child = view.findViewById<WorkflowViewStub>(R.id.child_stub)
 *
 *       override fun showRendering(rendering: YourRendering) {
 *         child.update(rendering.childRendering, viewRegistry)
 *       }
 *     }
 * ```
 */
class WorkflowViewStub(
  context: Context,
  attributeSet: AttributeSet? = null
) : View(context, attributeSet) {
  init {
    setWillNotDraw(true)
  }

  /**
   * By starting as our own delegate, we make the parent checking code in [update] simpler.
   */
  private var _actual: View = this

  /**
   * On-demand access to the delegate established by the last call to [update],
   * or null if none has been set yet.
   */
  val actual: View? get() = _actual.takeIf { it !is WorkflowViewStub }

  /**
   * Replaces this view with one that can display [rendering]. If the receiver
   * has already been replaced, updates the replacement if it [canShowRendering].
   * If the current replacement can't handle [rendering], a new view is put in place.
   *
   * @return the view that showed [rendering]
   * @throws RuntimeException if [rendering] cannot be displayed, typically indicating
   * a misconfigured [registry]
   */
  fun update(
    rendering: Any,
    registry: ViewRegistry
  ): View {
    _actual.takeIf { it.canShowRendering(rendering) }
        ?.let {
          it.showRendering(rendering)
          return it
        }

    return when (val parent = _actual.parent) {
      is ViewGroup -> registry.buildView(rendering, parent)
          .also { buildNewViewAndReplaceOldView(parent, it) }
      else -> registry.buildView(rendering, _actual.context)
    }.also { _actual = it }
  }

  private fun buildNewViewAndReplaceOldView(
    parent: ViewGroup,
    newView: View
  ) {
    val index = parent.indexOfChild(_actual)
    parent.removeView(_actual)

    _actual.layoutParams
        ?.let { parent.addView(newView, index, it) }
        ?: run { parent.addView(newView, index) }
  }
}