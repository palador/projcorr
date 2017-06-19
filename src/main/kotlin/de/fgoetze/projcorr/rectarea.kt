package de.fgoetze.projcorr

import javafx.animation.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.util.Duration

open class RectArea(
        val bg: Image?,
        val bgColor: Color,
        val w: Double,
        val h: Double,
        pointPos: List<Point2D>
) : Pane() {

    private val dragPnts = (0..3).map { DragablePoint() }
    private val wires = (0..3).map {
        Wire(
                dragPnts[it],
                dragPnts[(it + 1) % 4])
    }

    private val timeline = Timeline()
    private var __scene: Scene? = null

    private val keyHandler = EventHandler<KeyEvent> {
        onKey(it)
    }

    init {
        val (w, h) =
                if (bg == null) {
                    children += Group().also {
                        it.children += Rectangle(w, h).also {
                            it.fill = bgColor
                        }
                        StackPane.setAlignment(it, Pos.TOP_LEFT)
                    }
                    w to h
                } else {
                    children += ImageView(bg).also {
                        StackPane.setAlignment(it, Pos.TOP_LEFT)
                    }
                    bg.width to bg.height
                }

        // set default positions
        dragPnts.forEachIndexed { i, p ->
            p.pos = Point2D(
                    (if (i == 0 || i == 3) 0.25 else 0.75) * w,
                    (if (i == 0 || i == 1) 0.25 else 0.75) * h)
        }

        // overwrite with given points
        pointPos.take(4).forEachIndexed { i, p ->
            dragPnts[i].pos = p
        }

        wires.forEach {
            children += it.lines
        }
        children += dragPnts

        // animations
        val dashLen = 10.0
        wires.forEach {
            it.lines[0].stroke = Color.BLACK
            it.lines[0].strokeWidth = 1.0
            it.lines[1].stroke = Color.WHITE
            it.lines[1].strokeWidth = 2.0
            it.lines[1].strokeDashArray.addAll(dashLen, dashLen)
        }

        timeline.also { tl ->
            tl.keyFrames += KeyFrame(
                    Duration.ZERO,
                    *wires.map { KeyValue(it.lines[1].strokeDashOffsetProperty(), 0.0) }.toTypedArray())
            tl.keyFrames += KeyFrame(
                    Duration(1000.0),
                    * wires.map { KeyValue(it.lines[1].strokeDashOffsetProperty(), 2.0 * dashLen) }.toTypedArray())
            tl.cycleCount = Timeline.INDEFINITE
            tl.play()
        }

        // selections
        dragPnts.forEach { dp ->
            dp.isSuperSelected.addListener { _, _, newValue ->
                if (newValue) {
                    dragPnts.forEach {
                        if (it !== dp) it.isSuperSelected.value = false
                    }
                }
            }
        }

        // keys
        val sceneListener = object : ChangeListener<Scene> {
            override fun changed(p0: ObservableValue<out Scene>?,
                    oldValue: Scene?,
                    newValue: Scene?) {
                sceneProperty().removeListener(this)
                newValue!!.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler)
                __scene = newValue
            }
        }
        sceneProperty().addListener(sceneListener)
    }

    private fun onKey(e: KeyEvent) {
        println(e)
        val (x, y) = when (e.code) {
            KeyCode.LEFT -> -1 to 0
            KeyCode.UP -> 0 to -1
            KeyCode.RIGHT -> 1 to 0
            KeyCode.DOWN -> 0 to 1
            else -> 0 to 0
        }
        if (x != 0 || y != 0) {
            e.consume()
            dragPnts.firstOrNull { it.isSuperSelected.value }
                    ?.also {
                        it.pos = Point2D(it.pos.x + x.toDouble(), it.pos.y + y.toDouble())
                        val t1 = FillTransition(Duration.millis(500.0), it.midPointCircle, Color.PINK,
                                Color.PINK.invert()).also {
                            it.cycleCount = 10
                            it.isAutoReverse = true
                        }
                        val t2 = ScaleTransition(Duration.millis(100.0), it.midPointCircle).also {
                            it.byX = 4.0
                            it.byY = 4.0
                            it.isAutoReverse = true
                            it.cycleCount = 4
                        }

                        (it.properties["transistionsMy"] as? List<Transition>)?.forEach {
                            it.jumpTo(Duration.ZERO)
                            it.stop()
                        }
                        it.properties["transistionsMy"] = listOf(t1, t2).also {
                            it.forEach { it.play() }
                        }
                    }


        }

    }

    fun dispose(): List<Point2D> {
        __scene?.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler)
        timeline.stop()
        return dragPnts.map { it.pos }
    }
}

class Wire(
        val dp1: DragablePoint,
        val dp2: DragablePoint
) {
    val lines = listOf(
            Line(), Line())

    init {
        lines.forEach {
            it.apply {
                startXProperty().bind(dp1.translateXProperty())
                startYProperty().bind(dp1.translateYProperty())
                endXProperty().bind(dp2.translateXProperty())
                endYProperty().bind(dp2.translateYProperty())
            }
        }
    }
}

class DragablePoint : Group() {
    val selColor = Color.DARKBLUE
    val unselColor = Color.LIGHTBLUE
    val superSelColor = Color.RED
    val circle = Circle(0.0, 0.0, 20.0).also {
        it.stroke = unselColor
        it.strokeWidth = 4.0
        it.fill = Color.YELLOW.deriveColor(0.0, 1.0, 1.0, 0.1)
    }
    val midPointCircle = Circle(0.0, 0.0, 1.5).also {
        it.stroke = null
        it.fill = Color.PINK
    }

    val isSuperSelected = SimpleBooleanProperty(false).also {
        it.addListener { _, _, newValue ->
            if (newValue)
                circle.stroke = superSelColor
            else
                circle.stroke = unselColor
        }
    }

    var pos: Point2D
        get() = Point2D(translateX, translateY)
        set(value) {
            translateX = value.x; translateY = value.y
        }

    init {
        children += circle
        children += midPointCircle
        cursor = Cursor.MOVE

        var posDiff = Point2D(0.0, 0.0)
        setOnMouseEntered {
            if (circle.stroke != superSelColor)
                circle.stroke = selColor
        }
        setOnMouseExited {
            if (circle.stroke != superSelColor)
                circle.stroke = unselColor
        }
        setOnMousePressed {
            posDiff = Point2D(pos.x - it.sceneX, pos.y - it.sceneY)
        }
        setOnMouseDragged { e ->
            pos = Point2D(e.sceneX + posDiff.x, e.sceneY + posDiff.y)
        }
        setOnMouseClicked {
            isSuperSelected.value = true
        }
    }
}