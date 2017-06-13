// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * TODO
 *
 * @author bratseth
 */
public class KeyboardChecker {

    private static boolean qPressed = false;

    private final Object lock = new Object();

    public KeyboardChecker() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent ke) {
                synchronized (lock) {
                    switch (ke.getID()) {
                        case KeyEvent.KEY_PRESSED:
                            if (ke.getKeyCode() == KeyEvent.VK_Q) {
                                qPressed = true;
                            }
                            break;

                        case KeyEvent.KEY_RELEASED:
                            if (ke.getKeyCode() == KeyEvent.VK_Q) {
                                qPressed = false;
                            }
                            break;
                    }
                    return false;
                }
            }
        });
    }

    public boolean isQPressed() {
        synchronized (lock) {
            return qPressed;
        }
    }

}
