/*
 * Copyright (c) 2026, Thiago Sayão. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  The copyright holder designates
 * this particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;

import java.awt.image.BufferedImage;

/**
 * The robot of the labs: the Robot of JavaFX, or with -Dtests.input=libei
 * (jfx-tester --robot=libei) libei for the input and the Screenshot portal for
 * the screen: its PickColor for a pixel, clicked by libei, and screenshots for
 * the regions. Only the position of the pointer still comes from the Robot of
 * JavaFX, as neither of them tells it.
 */
final class TestRobot {

    private static final int BTN_LEFT = 0x110;
    private static final int BTN_RIGHT = 0x111;
    private static final int BTN_MIDDLE = 0x112;
    private static final int BTN_SIDE = 0x113;
    private static final int BTN_EXTRA = 0x114;

    private final Robot robot = new Robot();
    private final boolean useLibei = usesLibei();
    private EiInput ei;
    private String eiFailure;

    static boolean usesLibei() {
        return "libei".equals(System.getProperty("tests.input"));
    }

    // Connects at the first input, which may show the dialog of the portal
    private EiInput ei() {
        if (eiFailure != null) {
            throw new UnsupportedOperationException(eiFailure);
        }
        if (ei == null) {
            try {
                ei = new EiInput();
            } catch (UnsupportedOperationException e) {
                eiFailure = e.getMessage();
                throw e;
            }
        }
        return ei;
    }

    void mouseMove(double x, double y) {
        if (useLibei) {
            ei().moveTo(x, y);
        } else {
            robot.mouseMove(x, y);
        }
    }

    void mouseMove(Point2D p) {
        mouseMove(p.getX(), p.getY());
    }

    void mousePress(MouseButton... buttons) {
        if (useLibei) {
            for (MouseButton button : buttons) {
                ei().button(evdevButton(button), true);
            }
        } else {
            robot.mousePress(buttons);
        }
    }

    void mouseRelease(MouseButton... buttons) {
        if (useLibei) {
            for (MouseButton button : buttons) {
                ei().button(evdevButton(button), false);
            }
        } else {
            robot.mouseRelease(buttons);
        }
    }

    void mouseClick(MouseButton... buttons) {
        if (useLibei) {
            mousePress(buttons);
            mouseRelease(buttons);
        } else {
            robot.mouseClick(buttons);
        }
    }

    void mouseWheel(int amount) {
        if (useLibei) {
            ei().scroll(amount);
        } else {
            robot.mouseWheel(amount);
        }
    }

    void keyPress(KeyCode key) {
        if (useLibei) {
            ei().key(evdevKey(key), true);
        } else {
            robot.keyPress(key);
        }
    }

    void keyRelease(KeyCode key) {
        if (useLibei) {
            ei().key(evdevKey(key), false);
        } else {
            robot.keyRelease(key);
        }
    }

    void keyType(KeyCode key) {
        if (useLibei) {
            keyPress(key);
            keyRelease(key);
        } else {
            robot.keyType(key);
        }
    }

    Point2D getMousePosition() {
        return robot.getMousePosition();
    }

    Color getPixelColor(double x, double y) {
        if (!useLibei) {
            return robot.getPixelColor(x, y);
        }
        double[] rgb = ei().pickColor(x, y);
        if (rgb != null) {
            return Color.color(clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]));
        }
        return capture(null, x, y, 1, 1, true).getPixelReader().getColor(0, 0);
    }

    private static double clamp(double v) {
        return Math.clamp(v, 0, 1);
    }

    Color getPixelColor(Point2D p) {
        return getPixelColor(p.getX(), p.getY());
    }

    WritableImage getScreenCapture(WritableImage image, Rectangle2D bounds) {
        return getScreenCapture(image, bounds, true);
    }

    WritableImage getScreenCapture(WritableImage image, double x, double y, double width, double height) {
        return getScreenCapture(image, x, y, width, height, true);
    }

    WritableImage getScreenCapture(WritableImage image, Rectangle2D bounds, boolean scaleToFit) {
        return getScreenCapture(image, bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight(),
                scaleToFit);
    }

    WritableImage getScreenCapture(WritableImage image, double x, double y, double width, double height,
                                   boolean scaleToFit) {
        return useLibei ? capture(image, x, y, width, height, scaleToFit)
                : robot.getScreenCapture(image, x, y, width, height, scaleToFit);
    }

    /**
     * Crops a screenshot of all the screens, whose origin is that of their
     * union, in the pixels of the compositor.
     */
    private static WritableImage capture(WritableImage target, double x, double y, double width, double height,
                                         boolean scaleToFit) {
        BufferedImage shot = EiInput.screenshot();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D b = screen.getBounds();
            minX = Math.min(minX, b.getMinX());
            minY = Math.min(minY, b.getMinY());
            maxX = Math.max(maxX, b.getMaxX());
        }
        double scale = shot.getWidth() / (maxX - minX);
        int w = (int) Math.max(1, Math.round(scaleToFit ? width : width * scale));
        int h = (int) Math.max(1, Math.round(scaleToFit ? height : height * scale));
        WritableImage image = target != null && target.getWidth() == w && target.getHeight() == h
                ? target : new WritableImage(w, h);
        PixelWriter writer = image.getPixelWriter();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int px = (int) Math.floor((x - minX + (i + 0.5) * width / w) * scale);
                int py = (int) Math.floor((y - minY + (j + 0.5) * height / h) * scale);
                boolean inside = px >= 0 && py >= 0 && px < shot.getWidth() && py < shot.getHeight();
                writer.setArgb(i, j, inside ? shot.getRGB(px, py) | 0xff000000 : 0xff000000);
            }
        }
        return image;
    }

    private static int evdevButton(MouseButton button) {
        return switch (button) {
            case PRIMARY -> BTN_LEFT;
            case SECONDARY -> BTN_RIGHT;
            case MIDDLE -> BTN_MIDDLE;
            case BACK -> BTN_SIDE;
            case FORWARD -> BTN_EXTRA;
            default -> throw new IllegalArgumentException("libei cannot press " + button);
        };
    }

    // The keys of a US layout, which the input of the labs assumes
    private static int evdevKey(KeyCode key) {
        return switch (key) {
            case ESCAPE -> 1;
            case DIGIT1 -> 2;
            case DIGIT2 -> 3;
            case DIGIT3 -> 4;
            case DIGIT4 -> 5;
            case DIGIT5 -> 6;
            case DIGIT6 -> 7;
            case DIGIT7 -> 8;
            case DIGIT8 -> 9;
            case DIGIT9 -> 10;
            case DIGIT0 -> 11;
            case MINUS -> 12;
            case EQUALS -> 13;
            case BACK_SPACE -> 14;
            case TAB -> 15;
            case Q -> 16;
            case W -> 17;
            case E -> 18;
            case R -> 19;
            case T -> 20;
            case Y -> 21;
            case U -> 22;
            case I -> 23;
            case O -> 24;
            case P -> 25;
            case OPEN_BRACKET -> 26;
            case CLOSE_BRACKET -> 27;
            case ENTER -> 28;
            case CONTROL -> 29;
            case A -> 30;
            case S -> 31;
            case D -> 32;
            case F -> 33;
            case G -> 34;
            case H -> 35;
            case J -> 36;
            case K -> 37;
            case L -> 38;
            case SEMICOLON -> 39;
            case QUOTE -> 40;
            case BACK_QUOTE -> 41;
            case SHIFT -> 42;
            case BACK_SLASH -> 43;
            case Z -> 44;
            case X -> 45;
            case C -> 46;
            case V -> 47;
            case B -> 48;
            case N -> 49;
            case M -> 50;
            case COMMA -> 51;
            case PERIOD -> 52;
            case SLASH -> 53;
            case ALT -> 56;
            case SPACE -> 57;
            case F1 -> 59;
            case F2 -> 60;
            case F3 -> 61;
            case F4 -> 62;
            case F5 -> 63;
            case F6 -> 64;
            case F7 -> 65;
            case F8 -> 66;
            case F9 -> 67;
            case F10 -> 68;
            case F11 -> 87;
            case F12 -> 88;
            case HOME -> 102;
            case UP -> 103;
            case PAGE_UP -> 104;
            case LEFT -> 105;
            case RIGHT -> 106;
            case END -> 107;
            case DOWN -> 108;
            case PAGE_DOWN -> 109;
            case INSERT -> 110;
            case DELETE -> 111;
            case META, WINDOWS -> 125;
            default -> throw new IllegalArgumentException("libei cannot type " + key);
        };
    }
}
