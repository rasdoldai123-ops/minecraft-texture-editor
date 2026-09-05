package com.blockpainter.client.screen;

public enum PaintTool {
    PENCIL("pencil"),
    ERASER("eraser"),
    FILL("fill"),
    PICKER("picker"),
    BUILD("build");

    public final String key;

    PaintTool(String key) {
        this.key = key;
    }
}
