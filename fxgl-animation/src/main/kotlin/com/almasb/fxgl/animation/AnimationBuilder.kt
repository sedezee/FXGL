/*
 * FXGL - JavaFX Game Library. The MIT License (MIT).
 * Copyright (c) AlmasB (almaslvl@gmail.com).
 * See LICENSE for details.
 */

package com.almasb.fxgl.animation

import com.almasb.fxgl.core.util.EmptyRunnable
import com.almasb.fxgl.entity.Entity
import com.almasb.fxgl.logging.Logger
import com.almasb.fxgl.scene.Scene
import javafx.animation.Interpolator
import javafx.beans.property.DoubleProperty
import javafx.beans.value.WritableValue
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.shape.CubicCurve
import javafx.scene.shape.QuadCurve
import javafx.scene.shape.Shape
import javafx.scene.transform.Rotate
import javafx.scene.transform.Scale
import javafx.util.Duration
import java.lang.IllegalArgumentException
import java.util.function.Consumer

/**
 * Animation DSL that provides a fluent API for building and running animations.
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
open class AnimationBuilder
@JvmOverloads constructor(protected val scene: Scene? = null) {

    private var duration: Duration = Duration.seconds(1.0)
    private var delay: Duration = Duration.ZERO
    private var interpolator: Interpolator = Interpolator.LINEAR
    private var times: Int = 1
    private var onCycleFinished: Runnable = EmptyRunnable
    private var isAutoReverse: Boolean = false

    protected var onFinished: Runnable = EmptyRunnable

    constructor(copy: AnimationBuilder) : this(copy.scene) {
        duration = copy.duration
        delay = copy.delay
        interpolator = copy.interpolator
        times = copy.times
        onFinished = copy.onFinished
        onCycleFinished = copy.onCycleFinished
        isAutoReverse = copy.isAutoReverse
    }

    fun duration(duration: Duration): AnimationBuilder {
        this.duration = duration
        return this
    }

    fun delay(delay: Duration): AnimationBuilder {
        this.delay = delay
        return this
    }

    fun interpolator(interpolator: Interpolator): AnimationBuilder {
        this.interpolator = interpolator
        return this
    }

    fun repeat(times: Int): AnimationBuilder {
        this.times = times
        return this
    }

    fun repeatInfinitely(): AnimationBuilder {
        return repeat(Integer.MAX_VALUE)
    }

    fun onCycleFinished(onCycleFinished: Runnable): AnimationBuilder {
        this.onCycleFinished = onCycleFinished
        return this
    }

    fun autoReverse(autoReverse: Boolean): AnimationBuilder {
        this.isAutoReverse = autoReverse
        return this
    }

    fun onFinished(onFinished: Runnable): AnimationBuilder {
        this.onFinished = onFinished
        return this
    }

    protected fun makeConfig(): AnimationConfig {
        return AnimationConfig(duration, delay, interpolator, times, onFinished, onCycleFinished, isAutoReverse)
    }

    /* BEGIN BUILT-IN ANIMATIONS */

    fun <T> animate(value: AnimatedValue<T>) = GenericAnimationBuilder(this, value)

    fun <T> animate(property: WritableValue<T>) = PropertyAnimationBuilder(this, property)

    fun translate(vararg entities: Entity) = TranslationAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun translate(vararg entities: Node) = TranslationAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun translate(entities: Collection<Any>) = TranslationAnimationBuilder(this).apply {
        objects += entities.map { toAnimatable(it) }
    }

    fun fade(vararg entities: Entity) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun fade(vararg entities: Node) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun fade(entities: Collection<Any>) = FadeAnimationBuilder(this).apply {
        objects += entities.map { toAnimatable(it) }
    }

    fun scale(vararg entities: Entity) = ScaleAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun scale(vararg entities: Node) = ScaleAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun scale(entities: Collection<Any>) = ScaleAnimationBuilder(this).apply {
        objects += entities.map { toAnimatable(it) }
    }

    fun rotate(vararg entities: Entity) = RotationAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun rotate(vararg entities: Node) = RotationAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }

    fun rotate(entities: Collection<Any>) = RotationAnimationBuilder(this).apply {
        objects += entities.map { toAnimatable(it) }
    }

    private fun toAnimatable(obj: Any): Animatable = when (obj) {
        is Node -> obj.toAnimatable()
        is Entity -> obj.toAnimatable()
        else -> throw IllegalArgumentException("${obj.javaClass} must be Node or Entity")
    }

    fun fadeIn(vararg entities: Entity) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }.from(0.0).to(1.0)

    fun fadeIn(vararg entities: Node) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }.from(0.0).to(1.0)

    fun fadeOut(vararg entities: Entity) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }.from(1.0).to(0.0)

    fun fadeOut(vararg entities: Node) = FadeAnimationBuilder(this).apply {
        objects += entities.map { it.toAnimatable() }
    }.from(1.0).to(0.0)

    fun bobbleDown(node: Node) = duration(Duration.seconds(0.15))
            .autoReverse(true)
            .repeat(2)
            .translate(node)
            .from(Point2D(node.translateX, node.translateY))
            .to(Point2D(node.translateX, node.translateY + 5.0))

    /* END BUILT-IN ANIMATIONS */

    abstract class AM(private val animationBuilder: AnimationBuilder) : AnimationBuilder(animationBuilder) {
        val objects = arrayListOf<Animatable>()

        abstract fun build(): Animation<*>

        /**
         * Builds animation and plays in the game scene.
         */
        fun buildAndPlay() {
            if (animationBuilder.scene != null) {
                buildAndPlay(animationBuilder.scene)
            } else {
                Logger.get(javaClass).warning("No game scene was set to AnimationBuilder")
            }
        }

        fun buildAndPlay(scene: Scene) {
            build().also { animation ->

                animation.onFinished = Runnable {
                    scene.removeListener(animation)
                    onFinished.run()
                }

                animation.start()
                scene.addListener(animation)
            }
        }
    }

    class TranslationAnimationBuilder(animationBuilder: AnimationBuilder) : AM(animationBuilder) {

        private var path: Shape? = null
        private var fromPoint = Point2D.ZERO
        private var toPoint = Point2D.ZERO

        fun alongPath(path: Shape) = this.also {
            this.path = path
        }

        fun from(start: Point2D) = this.also {
            fromPoint = start
        }

        fun to(end: Point2D) = this.also {
            toPoint = end
        }

        override fun build(): Animation<*> {

            path?.let { curve ->
                return when (curve) {
                    is QuadCurve -> makeAnim(AnimatedQuadBezierPoint2D(curve))
                    is CubicCurve -> makeAnim(AnimatedCubicBezierPoint2D(curve))
                    else -> makeAnim(AnimatedPath(curve))
                }
            }

            return makeAnim(AnimatedPoint2D(fromPoint, toPoint))
        }

        private fun makeAnim(animValue: AnimatedValue<Point2D>): Animation<Point2D> {
            return makeConfig().build(
                    animValue,
                    Consumer { value ->
                        objects.forEach {
                            it.xProperty().value = value.x
                            it.yProperty().value = value.y
                        }
                    }
            )
        }
    }

    class FadeAnimationBuilder(animationBuilder: AnimationBuilder) : AM(animationBuilder) {

        private var from = 0.0
        private var to = 0.0

        fun from(start: Double) = this.also {
            from = start
        }

        fun to(end: Double) = this.also {
            to = end
        }

        override fun build(): Animation<*> {
            return makeConfig().build(AnimatedValue(from, to),
                    Consumer { value ->
                        objects.forEach {
                            it.opacityProperty().value = value
                        }
                    }
            )
        }
    }

    class ScaleAnimationBuilder(animationBuilder: AnimationBuilder) : AM(animationBuilder) {

        private var startScale = Point2D(1.0, 1.0)
        private var endScale = Point2D(1.0, 1.0)
        private var scaleOrigin: Point2D? = null

        fun from(start: Point2D): ScaleAnimationBuilder {
            startScale = start
            return this
        }

        fun to(end: Point2D): ScaleAnimationBuilder {
            endScale = end
            return this
        }

        fun origin(scaleOrigin: Point2D): ScaleAnimationBuilder {
            this.scaleOrigin = scaleOrigin
            return this
        }

        override fun build(): Animation<*> {
            scaleOrigin?.let { origin ->
                objects.forEach {
                    it.setScaleOrigin(origin)
                }
            }

            return makeConfig().build(
                    AnimatedPoint2D(startScale, endScale),
                    Consumer { value ->
                        objects.forEach {
                            it.scaleXProperty().value = value.x
                            it.scaleYProperty().value = value.y
                        }
                    }
            )
        }
    }

    class RotationAnimationBuilder(animationBuilder: AnimationBuilder) : AM(animationBuilder) {

        private var startAngle = 0.0
        private var endAngle = 0.0
        private var rotationOrigin: Point2D? = null

        fun from(startAngle: Double): RotationAnimationBuilder {
            this.startAngle = startAngle
            return this
        }

        fun to(endAngle: Double): RotationAnimationBuilder {
            this.endAngle = endAngle
            return this
        }

        fun origin(rotationOrigin: Point2D): RotationAnimationBuilder {
            this.rotationOrigin = rotationOrigin
            return this
        }

        override fun build(): Animation<*> {
            rotationOrigin?.let { origin ->
                objects.forEach {
                    it.setRotationOrigin(origin)
                }
            }

            return makeConfig().build(AnimatedValue(startAngle, endAngle),
                    Consumer { value ->
                        objects.forEach {
                            it.rotationProperty().value = value
                        }
                    }
            )
        }
    }

    class GenericAnimationBuilder<T>(animationBuilder: AnimationBuilder, val animValue: AnimatedValue<T>) : AM(animationBuilder) {

        private var progressConsumer: Consumer<T> = Consumer { }

        fun onProgress(progressConsumer: Consumer<T>): GenericAnimationBuilder<T> {
            this.progressConsumer = progressConsumer
            return this
        }

        override fun build(): Animation<T> {
            return makeConfig().build(animValue, progressConsumer)
        }
    }

    class PropertyAnimationBuilder<T>(animationBuilder: AnimationBuilder, private val property: WritableValue<T>) : AM(animationBuilder) {

        private var startValue: T = property.value
        private var endValue: T = property.value

        private var progressConsumer: Consumer<T> = Consumer {
            property.value = it
        }

        fun from(startValue: T): PropertyAnimationBuilder<T> {
            this.startValue = startValue
            return this
        }

        fun to(endValue: T): PropertyAnimationBuilder<T> {
            this.endValue = endValue
            return this
        }

        override fun build(): Animation<T> {
            return makeConfig().build(AnimatedValue(startValue, endValue), progressConsumer)
        }
    }
}

private fun Node.toAnimatable(): Animatable {
    val n = this
    return object : Animatable {
        private var scale: Scale? = null
        private var rotate: Rotate? = null

        override fun xProperty(): DoubleProperty {
            return n.translateXProperty()
        }

        override fun yProperty(): DoubleProperty {
            return n.translateYProperty()
        }

        override fun scaleXProperty(): DoubleProperty {
            return scale?.xProperty() ?: n.scaleXProperty()
        }

        override fun scaleYProperty(): DoubleProperty {
            return scale?.yProperty() ?: n.scaleYProperty()
        }

        override fun rotationProperty(): DoubleProperty {
            return rotate?.angleProperty() ?: n.rotateProperty()
        }

        override fun opacityProperty(): DoubleProperty {
            return n.opacityProperty()
        }

        override fun setScaleOrigin(pivotPoint: Point2D) {
            // if a node already has a previous transform, reuse it
            // this means the node was animated previously
            n.properties["anim_scale"]?.let { transform ->
                scale = transform as Scale
                scale!!.pivotX = pivotPoint.x
                scale!!.pivotY = pivotPoint.y
                return
            }

            scale = Scale(0.0, 0.0, pivotPoint.x, pivotPoint.y)
                    .also {
                        n.transforms.add(it)
                        n.properties["anim_scale"] = it
                    }
        }

        override fun setRotationOrigin(pivotPoint: Point2D) {
            // if a node already has a previous transform, reuse it
            // this means the node was animated previously
            n.properties["anim_rotate"]?.let { transform ->
                rotate = transform as Rotate
                rotate!!.pivotX = pivotPoint.x
                rotate!!.pivotY = pivotPoint.y
                return
            }

            rotate = Rotate(0.0, pivotPoint.x, pivotPoint.y)
                    .also {
                        it.axis = Rotate.Z_AXIS
                        n.transforms.add(it)
                        n.properties["anim_rotate"] = it
                    }
        }
    }
}

private fun Entity.toAnimatable(): Animatable {
    val e = this
    return object : Animatable {
        override fun xProperty(): DoubleProperty {
            return e.xProperty()
        }

        override fun yProperty(): DoubleProperty {
            return e.yProperty()
        }

        override fun scaleXProperty(): DoubleProperty {
            return e.transformComponent.scaleXProperty()
        }

        override fun scaleYProperty(): DoubleProperty {
            return e.transformComponent.scaleYProperty()
        }

        override fun rotationProperty(): DoubleProperty {
            return e.transformComponent.angleProperty()
        }

        override fun opacityProperty(): DoubleProperty {
            return e.viewComponent.opacityProperty
        }

        override fun setScaleOrigin(pivotPoint: Point2D) {
            e.transformComponent.scaleOrigin = pivotPoint
        }

        override fun setRotationOrigin(pivotPoint: Point2D) {
            e.transformComponent.rotationOrigin = pivotPoint
        }
    }
}