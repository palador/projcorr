package de.fgoetze.projcorr

import javafx.application.Application
import javafx.application.Platform
import javafx.beans.binding.Binding
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import nu.pattern.OpenCV
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Callable

fun main(args: Array<String>) {
    OpenCV.loadShared()
    Application.launch(MainApp::class.java)
}

class MainApp : Application() {
    override fun start(stage: Stage?) {
        stage!!.apply {
            title = "Projection Correction"
            scene = buildMainScene()

            show()
        }
    }
}

fun buildMainScene(): Scene {
    // model
    val pointsProperty = SimpleObjectProperty<List<Point2D>>(emptyList())
    val pointsAvailableProperty = Bindings.createBooleanBinding(
            Callable { pointsProperty.value.size == 4 },
            pointsProperty)
    val pointsNotAvailableProperty = Bindings.not(pointsAvailableProperty)
    val selectedFileProperty = SimpleObjectProperty("")
    val isFileSelected = Bindings.createBooleanBinding(
            Callable { selectedFileProperty.value.isNotBlank() },
            selectedFileProperty)
    val isNoFileSelected = Bindings.not(isFileSelected)

    // active components
    val selFileTF = TextField().apply {
        prefWidth = 400.0
        textProperty().bindBidirectional(selectedFileProperty)
    }
    val selFileButton = Button("Select...").apply {
        setOnAction {
            FileChooser().showOpenDialog(scene.window)?.also {
                selectedFileProperty.value = it.toString()
            }
        }
    }
    val selDisplayCombo = ComboBox<Screen>(
            FXCollections.observableList(Screen.getScreens())
    ).apply {
        selectionModel.select(
                Screen.getScreens().firstOrNull { it != Screen.getPrimary() }
                        ?: Screen.getPrimary())
        onUpdateCell { item ->
            contentDisplay = ContentDisplay.TEXT_ONLY
            text = item.let { screen ->
                buildString {
                    append(Screen.getScreens().indexOf(screen) + 1).append(": ")
                    append(screen.bounds.width).append("x").append(screen.bounds.height)
                    if (screen == Screen.getPrimary())
                        append(" (primary)")
                }
            }
        }
    }
    val targetBoundsButton = Button("Define Boundingbox").apply {
        setOnAction { selDisplayCombo.value?.also { pointsProperty.value = defineBoundingBox(it) } }
    }
    val pointsLabel = Label().apply {
        textProperty().bind(Bindings.createObjectBinding(
                Callable { pointsProperty.value.map { "(${it.x}, ${it.y})" }.joinToString() },
                pointsProperty))
    }
    val createAndShowBtn = Button("Create and Show").apply {
        maxWidth = Double.MAX_VALUE
        isDefaultButton = true
        disableProperty().bind(Bindings.or(isNoFileSelected, pointsNotAvailableProperty))
        setOnAction {
            val out = File(selectedFileProperty.value).let {
                File(it.parent, it.nameWithoutExtension + ".projcorr.png")
            }
            createImage(
                    selDisplayCombo.value,
                    pointsProperty.value,
                    File(selectedFileProperty.value),
                    out)
            showImage(selDisplayCombo.value, out)
        }
    }


    // layout
    val root = VBox().apply {
        spacing = 10.0
        padding = Insets(10.0)
    }

    root.children += Label("Select Input image:")
    root.children += HBox(10.0, selFileTF, selFileButton)
    root.children += HBox(10.0,
            Label("Target Screen:"),
            selDisplayCombo,
            targetBoundsButton)
    root.children += pointsLabel
    root.children += createAndShowBtn

    return Scene(root)
}

fun defineBoundingBox(screen: Screen): List<Point2D> {
    println(screen)
    val stage = Stage(StageStyle.UNDECORATED)
    stage.isFullScreen = true
    stage.x = screen.visualBounds.minX
    stage.y = screen.visualBounds.minY
    stage.width = screen.visualBounds.width
    stage.height = screen.visualBounds.height
    stage.fullScreenProperty().addListener { _, _, newValue ->
        if (newValue == false)
            stage.close()
    }

    val pane = Pane().also {
        it.prefWidth = Double.MAX_VALUE
        it.prefHeight = Double.MAX_VALUE
        it.isManaged = false
    }

    val points = Array<DraggableCorner>(4) {
        val dc = DraggableCorner()
        val x = if (it == 0 || it == 3) 0.2 else 0.8
        val y = if (it == 0 || it == 1) 0.2 else 0.8
        dc.pos = Point2D(screen.bounds.width * x, screen.bounds.height * y)
        pane.children += dc
        dc
    }.asList()

    // connect points
    for (i in 0..3) {
        val p1 = points[i]
        val p2 = points[(i + 1) % 4]
        val line = Line()
        line.startXProperty().bind(p1.translateXProperty())
        line.startYProperty().bind(p1.translateYProperty())
        line.endXProperty().bind(p2.translateXProperty())
        line.endYProperty().bind(p2.translateYProperty())

        pane.children += line
    }

    var okClicked = false

    // button in the middle
    Button("OK").also {
        it.prefWidth = 100.0
        it.prefHeight = 100.0
        it.translateX = .5 * (screen.bounds.width - it.prefWidth)
        it.translateY = .5 * (screen.bounds.height - it.prefHeight)
        pane.children += it
        okClicked = true
        it.setOnAction { stage.close() }
    }

    stage.scene = Scene(pane)
    stage.showAndWait()

    return if (okClicked)
        points.map { it.pos }
    else
        emptyList()
}

fun createImage(
        screen: Screen,
        points: List<Point2D>,
        inFile: File,
        outFile: File
) {
    val img = Imgcodecs.imread(inFile.absolutePath, Imgcodecs.CV_LOAD_IMAGE_COLOR)
    val imgWidth = img.size().width.toDouble()
    val imgHeight = img.size().height.toDouble()

    val scrToImgX = imgWidth / screen.bounds.width
    val scrToImgY = imgHeight / screen.bounds.height

    val imgPoints = points.map { Point2D(it.x * scrToImgX, it.y * scrToImgY) }

    val mat = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*(arrayOf(
                    Point(0.0, 0.0),
                    Point(imgWidth, 0.0),
                    Point(imgWidth, imgHeight),
                    Point(0.0, imgHeight)
            ))),
            MatOfPoint2f(*points.toCVPntArray())
    )
    val out = Mat(screen.bounds.height.toInt(), screen.bounds.width.toInt(), img.type(), Scalar(0.0, 0.0, 0.0))
    Imgproc.warpPerspective(img, out, mat, out.size(),
            Imgproc.INTER_LINEAR + Imgproc.CV_WARP_FILL_OUTLIERS,
            Core.BORDER_CONSTANT,
            Scalar(1.0, 0.5, 0.5))

    outFile.delete()

    Imgcodecs.imwrite(outFile.absolutePath, out)
}

fun showImage(screen: Screen,
        imageFile: File) {
    val stage = Stage(StageStyle.UNDECORATED)
    stage.isFullScreen = true
    stage.x = screen.visualBounds.minX
    stage.y = screen.visualBounds.minY
    stage.width = screen.visualBounds.width
    stage.height = screen.visualBounds.height
    stage.fullScreenProperty().addListener { _, _, newValue ->
        if (newValue == false)
            stage.close()
    }

    stage.scene = Scene(StackPane(ImageView(Image(imageFile.toURI().toString()))))

    stage.showAndWait()
}

fun Point2D.toCVPnt() = Point(x, y)
fun List<Point2D>.toCVPntArray() = map { it.toCVPnt() }.toTypedArray()

class DraggableCorner() : Group() {
    val selColor = Color.DARKBLUE
    val unselColor = Color.LIGHTBLUE
    val circle = Circle(0.0, 0.0, 20.0).also {
        it.stroke = unselColor
        it.strokeWidth = 4.0
        it.fill = Color.YELLOW.deriveColor(0.0, 1.0, 1.0, 0.1)
    }

    var pos: Point2D
        get() = Point2D(translateX, translateY)
        set(value) {
            translateX = value.x; translateY = value.y
        }

    init {
        children += circle
        cursor = Cursor.MOVE
        setOnMouseEntered {
            circle.stroke = selColor
        }
        setOnMouseExited {
            circle.stroke = unselColor
        }
        setOnMouseDragged { e ->
            pos = Point2D(e.sceneX, e.sceneY)
        }
    }
}