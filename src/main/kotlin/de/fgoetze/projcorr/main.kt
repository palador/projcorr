package de.fgoetze.projcorr

import javafx.application.Application
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
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

    var lastFile = ""
    var lastCropPoints = emptyList<Point2D>()
    var lastBoudingBox = emptyList<Point2D>()

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
    val cropFileButton = Button("Crop").apply {
        setOnAction {
            lastCropPoints = CropTool(selFileTF.text, lastCropPoints).let {
                it.showTool()
                it.outImagePath?.also {
                    selFileTF.text = it
                }
                it.outPoints
            }
        }
    }
    val whiteBackgroundCheck = CheckBox("White background")
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
        setOnAction {
            selDisplayCombo.value?.also {
                pointsProperty.value = defineBoundingBox(it, pointsProperty.value, whiteBackgroundCheck.isSelected)
            }
        }
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
    root.children += HBox(10.0, selFileTF, selFileButton, cropFileButton)
    root.children += HBox(10.0,
            Label("Target Screen:"),
            selDisplayCombo,
            targetBoundsButton)
    root.children += whiteBackgroundCheck
    root.children += pointsLabel
    root.children += createAndShowBtn

    return Scene(root)
}

fun defineBoundingBox(screen: Screen,
        initPoints: List<Point2D>,
        whiteBG: Boolean
): List<Point2D> {
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

    val pane = RectArea(null, if (whiteBG) Color.WHITE else Color.BLACK, stage.width, stage.height, initPoints).also {
        it.prefWidth = Double.MAX_VALUE
        it.prefHeight = Double.MAX_VALUE
        it.isManaged = false
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
        it.textFill = if (whiteBG) Color.WHITE else Color.BLACK
        it.background = Background(
                BackgroundFill(if (!whiteBG) Color.WHITE else Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY))
    }

    stage.scene = Scene(pane)
    stage.showAndWait()

    return if (okClicked)
        pane.dispose()
    else {
        pane.dispose()
        emptyList()
    }
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