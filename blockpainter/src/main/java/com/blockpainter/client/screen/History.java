package com.blockpainter.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;

/** Undo/redo stack of int[] snapshots. */
public final class History {
    private static final int LIMIT = 64;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();

    public void push(int[] snapshot) {
        undo.push(snapshot.clone());
        if (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    public int[] undo(int[] current) {
        if (undo.isEmpty()) return null;
        redo.push(current.clone());
        return undo.pop();
    }

    public int[] redo(int[] current) {
        if (redo.isEmpty()) return null;
        undo.push(current.clone());
        return redo.pop();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
}
