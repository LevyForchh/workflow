/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup, Boolean) -> BindingT

/**
 * A delegate that implements a [showRendering] method to be called when a workflow rendering
 * of type [RenderingT] is ready to be displayed in a view inflated from a layout resource
 * by a [ViewRegistry]. (Use [BuilderBinding] if you want to build views from code rather
 * than layouts.)
 *
 * Typical usage is to have a [LayoutRunner]'s `companion object` implement
 * [ViewBinding] by delegating to [LayoutRunner.bind], specifying the layout resource
 * it expects to drive.
 *
 *   class HelloLayoutRunner(view: View) : LayoutRunner<Rendering> {
 *     private val messageView: TextView = view.findViewById(R.id.hello_message)
 *
 *     override fun showRendering(rendering: Rendering) {
 *       messageView.text = rendering.message
 *       messageView.setOnClickListener { rendering.onClick(Unit) }
 *     }
 *
 *     companion object : ViewBinding<Rendering> by bind(
 *         R.layout.hello_goodbye_layout, ::HelloLayoutRunner
 *     )
 *   }
 *
 * This pattern allows us to assemble a [ViewRegistry] out of the [LayoutRunner] classes
 * themselves.
 *
 *    val TicTacToeViewBuilders = ViewRegistry(
 *        NewGameLayoutRunner, GamePlayLayoutRunner, GameOverLayoutRunner
 *    )
 */
interface LayoutRunner<RenderingT : Any> {
  fun showRendering(
    rendering: RenderingT,
    containerHints: ContainerHints
  )

  class Binding<RenderingT : Any>(
    override val type: KClass<RenderingT>,
    @LayoutRes private val layoutId: Int,
    private val runnerConstructor: (View) -> LayoutRunner<RenderingT>
  ) : ViewBinding<RenderingT> {
    override fun buildView(
      initialRendering: RenderingT,
      initialContainerHints: ContainerHints,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      return LayoutInflater.from(container?.context ?: contextForNewView)
          .cloneInContext(contextForNewView)
          .inflate(layoutId, container, false)
          .apply {
            bindShowRendering(
                initialRendering,
                initialContainerHints,
                runnerConstructor.invoke(this)::showRendering
            )
          }
    }
  }

  class ViewBindingBinding<BindingT : androidx.viewbinding.ViewBinding, RenderingT : Any>(
    override val type: KClass<RenderingT>,
    private val bindingInflater: ViewBindingInflater<BindingT>,
    private val runnerConstructor: (BindingT) -> LayoutRunner<RenderingT>
  ) : ViewBinding<RenderingT> {
    override fun buildView(
      initialRendering: RenderingT,
      initialContainerHints: ContainerHints,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      return LayoutInflater.from(container?.context ?: contextForNewView)
          .cloneInContext(contextForNewView)
          // This !! is problematic. Can we use the overload that only takes an inflater?
          .let { bindingInflater(it, container!!, false) }
          .also { binding ->
            binding.root.bindShowRendering(
                initialRendering,
                initialContainerHints,
                runnerConstructor.invoke(binding)::showRendering
            )
          }
          .root
    }
  }

  companion object {
    /**
     * Creates a [ViewBinding] that inflates [layoutId] to show renderings of type [RenderingT],
     * using a [LayoutRunner] created by [constructor].
     */
    inline fun <reified RenderingT : Any> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> LayoutRunner<RenderingT>
    ): ViewBinding<RenderingT> = Binding(RenderingT::class, layoutId, constructor)

    inline fun <BindingT : androidx.viewbinding.ViewBinding, reified RenderingT : Any> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> LayoutRunner<RenderingT>
    ): ViewBinding<RenderingT> = ViewBindingBinding(RenderingT::class, bindingInflater, constructor)

    inline fun <reified BindingT : androidx.viewbinding.ViewBinding, reified RenderingT : Any> bind(
      noinline constructor: (BindingT) -> LayoutRunner<RenderingT>
    ): ViewBinding<RenderingT> {
      val bindingInflater = getBindingInflaterForType(BindingT::class)
      return ViewBindingBinding(RenderingT::class, bindingInflater, constructor)
    }

    @PublishedApi
    internal fun <BindingT : androidx.viewbinding.ViewBinding> getBindingInflaterForType(
      type: KClass<BindingT>
    ): ViewBindingInflater<BindingT> {
      val bindingInflaterMethod = type.java.getMethod(
          "inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java
      )
      return { inflater, container, attach ->
        @Suppress("UNCHECKED_CAST")
        bindingInflaterMethod.invoke(null, inflater, container, attach) as BindingT
      }
    }

    /**
     * Creates a [ViewBinding] that inflates [layoutId] to "show" renderings of type [RenderingT],
     * with a no-op [LayoutRunner]. Handy for showing static views.
     */
    inline fun <reified RenderingT : Any> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ViewBinding<RenderingT> = bind(layoutId) {
      object : LayoutRunner<RenderingT> {
        override fun showRendering(
          rendering: RenderingT,
          containerHints: ContainerHints
        ) = Unit
      }
    }
  }
}
