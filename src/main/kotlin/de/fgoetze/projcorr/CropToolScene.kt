package de.fgoetze.projcorr

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle
import nu.pattern.OpenCV
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

class CropTool(
        val imagePath: String,
        var pointPos: List<Point2D>
) : Stage() {

    init {
        initStyle(StageStyle.DECORATED)
        scene = CropToolScene(imagePath, pointPos)
    }

    var outPoints = pointPos
    var outImagePath: String? = null

    fun showTool() {
        showAndWait()
        (scene as CropToolScene).also {
            if (it.success) {
                outPoints = it.outPoints
                outImagePath = it.outImagePath
            }
        }

    }
}

class CropToolScene(
        val imagePath: String,
        val pointPos: List<Point2D>
) : Scene(BorderPane()) {

    val borderPane = root as BorderPane
    val scrollPane = ScrollPane()
    val editArea = RectArea(Image(File(imagePath).toURI().toString()), Color.GRAY, 0.0, 0.0, pointPos)

    var outPoints = pointPos
    var outImagePath: String? = null
    var success = false

    private var scrollLvl = 0.0
    private val maxScrollLvl = 12.0
    private val scrollScaleStep = 1.1

    init {
        borderPane.center = scrollPane

        scrollPane.isFitToHeight = true
        scrollPane.isFitToWidth = true
        scrollPane.content = StackPane(Group(editArea))

        borderPane.bottom = HBox().also { hbox ->
            hbox.spacing = 5.0
            hbox.padding = Insets(5.0)
            hbox.children += Button("OK").also { b ->
                b.setOnAction {
                    dispose()
                    doTheJob()
                    success = true
                    b.scene.window.hide()
                }
            }
            hbox.children += Button("Cancel").also { b ->
                b.setOnAction {
                    dispose()
                    b.scene.window.hide()
                }
            }
        }

        scrollPane.addEventFilter(ScrollEvent.SCROLL) {
            val scrollLvlDelta = when {
                it.deltaY > 0.0 -> 1
                it.deltaY < 0.0 -> -1
                else -> 0
            }
            scrollLvl += scrollLvlDelta
            scrollLvl = Math.max(-maxScrollLvl, Math.min(maxScrollLvl, scrollLvl))
            val scale = Math.pow(scrollScaleStep, Math.abs(scrollLvl).toDouble()).let {
                if (scrollLvl < 0) 1.0 / it
                else it
            }
            editArea.scaleX = scale
            editArea.scaleY = scale

            it.consume()
        }

        scrollPane.addEventFilter(KeyEvent.ANY) {
            if (it.code == KeyCode.SPACE) {
                when (it.eventType) {
                    KeyEvent.KEY_PRESSED -> scrollPane.isPannable = true
                    else -> scrollPane.isPannable = false
                }
                it.consume()
            }
        }
    }

    fun dispose(): List<Point2D> {
        editArea.scaleX = 1.0
        editArea.scaleY = 1.0
        outPoints = editArea.dispose()
        return outPoints
    }

    private fun doTheJob() {
        val inFile = File(imagePath)
        val outFile = File(inFile.parentFile, inFile.nameWithoutExtension + ".cropped.png")
        outImagePath = outFile.absolutePath

        val img = Imgcodecs.imread(inFile.absolutePath, Imgcodecs.CV_LOAD_IMAGE_COLOR)

        val outWidth = outPoints.maxBy { it.x }!!.x - outPoints.minBy { it.x }!!.x
        val outHeight = outPoints.maxBy { it.y }!!.y - outPoints.minBy { it.y }!!.y

        val mat = Imgproc.getPerspectiveTransform(
                MatOfPoint2f(*outPoints.toCVPntArray()),
                MatOfPoint2f(
                        Point(0.0, 0.0),
                        Point(outWidth, 0.0),
                        Point(outWidth, outHeight),
                        Point(0.0, outHeight))
        )
        val out = Mat(outHeight.toInt(), outWidth.toInt(), img.type(), Scalar(0.0, 0.0, 0.0))
        Imgproc.warpPerspective(img, out, mat, out.size(),
                Imgproc.INTER_LINEAR + Imgproc.CV_WARP_FILL_OUTLIERS,
                Core.BORDER_CONSTANT,
                Scalar(1.0, 0.5, 0.5))

        outFile.delete()

        Imgcodecs.imwrite(outFile.absolutePath, out)
    }

}

class TestApp() : Application() {
    override fun start(s: Stage) {
        CropTool(
                "/home/palador/Documents/Quake2box.jpg",
                emptyList()).show()
    }
}

fun main(args: Array<String>) {
    OpenCV.loadShared()
    Application.launch(TestApp::class.java)
}
