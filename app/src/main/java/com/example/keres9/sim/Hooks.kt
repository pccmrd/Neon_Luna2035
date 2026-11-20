package com.example.keres9.sim

import com.example.keres9.core.Body
import com.example.keres9.core.Contact
import com.example.keres9.core.Ship

object Hooks {
    var toast: (String, String?) -> Unit = { _, _ -> }
    var banner: (String, String?) -> Unit = { _, _ -> }
    var closeStation: () -> Unit = {}
    var openStation: (Body, String?) -> Unit = { _, _ -> }
    var onDock: (Body, String?) -> Unit = { _, _ -> }
    var onTouch: (Ship) -> Unit = {}
    var onCatalog: (Body, String) -> Unit = { _, _ -> }
    var delivered: (Body, String, Int) -> Unit = { _, _, _ -> }
    var resolved: (Contact) -> Unit = {}
    var shiftDone: (Int, Int) -> Unit = { _, _ -> }
    var bought: (Ship) -> Unit = {}
    var liftArrive: (Boolean) -> Unit = {}
    var boardCrew: (Ship) -> Unit = {}
    var shipLost: (Ship) -> Unit = {}
    var suitOut: () -> Unit = {}
    var storyStep: (Int) -> Unit = {}
    var win: () -> Unit = {}
    var armor: () -> Double = { 1.0 }
    var onSave: () -> Unit = {}
}
