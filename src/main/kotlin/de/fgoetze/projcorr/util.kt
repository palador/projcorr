package de.fgoetze.projcorr

import javafx.scene.control.*
import javafx.util.Callback

fun <T> ComboBox<T>.onUpdateCell(updater: ListCell<T>.(item: T) -> Unit) {
    cellFactory = Callback {
        CustomListCell(updater)
    }
    buttonCell = CustomListCell(updater)
}

fun <T> TreeView<T>.onUpdateCell(updater: TreeCell<T>.(item: T) -> Unit) {
    cellFactory = Callback {
        CustomTreeCell(updater)
    }
}

fun <T> ListView<T>.onUpdateCell(updater: ListCell<T>.(item: T) -> Unit) {
    cellFactory = Callback {
        CustomListCell(updater)
    }
}

private class CustomListCell<T>(private val updater: ListCell<T>.(item: T) -> Unit) : ListCell<T>() {
    override fun updateItem(item: T?,
            empty: Boolean) {
        super.updateItem(item, empty)
        if (item == null || empty) {
            text = null
            graphic = null
        } else
            updater(item)
    }
}

private class CustomTreeCell<T>(private val updater: TreeCell<T>.(item: T) -> Unit) : TreeCell<T>() {
    override fun updateItem(item: T?,
            empty: Boolean) {
        super.updateItem(item, empty)
        if (item == null || empty) {
            text = null
            graphic = null
        } else
            updater(item)
    }
}
