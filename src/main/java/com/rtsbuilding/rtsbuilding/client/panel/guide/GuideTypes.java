package com.rtsbuilding.rtsbuilding.client.panel.guide;

public enum GuideTypes {

    CAMERA_MOVEMENT("Camera Movement", "Use WASD to pan, scroll to zoom, right-drag to rotate"),
    MODE_SWITCH("Mode Switch", "Use the top bar buttons to switch between Interact, Link, Rotate, and Funnel modes"),
    LINK_STORAGE("Link Storage", "Click a container while in Link mode to connect it to your RTS network"),
    MINING("Mining", "Click-and-hold left mouse to progressively mine blocks"),
    PLACEMENT("Placement", "Select a block from storage, right-click to place it"),
    QUICK_BUILD("Quick Build", "Use Quick Build to place shapes: lines, squares, walls, circles, and boxes"),
    ULTIMINE("Ultimine", "Enable Ultimine mode to mine multiple connected blocks at once");

    public final String title;
    public final String description;

    GuideTypes(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
