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

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * A laboratory for the scale of the screens: what JavaFX knows of the scale
 * of the desktop, and the bugs of the JBS that come with scales other than 1,
 * fractional ones above all. Run it at such a scale:
 * <pre>./jfx-tester --tests=scale --scale=1.25 --headless</pre>
 * <pre>./jfx-tester --tests=scale --monitors=1080p@2,1080p@1.5 --headless</pre>
 */
public class ScaleLab {

    /**
     * Runs this lab as an application of its own, with its tests:
     * <pre>java ScaleLab.java</pre>
     * or {@code ./jfx-tester --tests=scale}.
     */
    public static void main(String[] args) {
        TestLab.launchLab("scale", args);
    }

    private static final String CSS = TestKit.BASE_CSS;

    static final String STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(CSS.getBytes(StandardCharsets.UTF_8));

    final TestKit.Suite suite = new TestKit.Suite("Scale");
    private final GridPane info = new GridPane(16, 6);
    private final List<Stage> stages = new ArrayList<>();

    Region content(Stage stage) {
        Label title = new Label("SCALES");
        title.getStyleClass().add("section");
        refresh();
        Button again = new Button("Refresh");
        again.setOnAction(e -> refresh());
        Label hint = new Label("The scale of each screen and of this window, the settings it comes from, and the "
                + "tests of the bugs of the JBS that show at scales other than 1. Run them at 1.25, 1.5 or 2, "
                + "such as with jfx-tester --scale=1.25.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        VBox box = new VBox(10, title, info, again, hint);
        box.getStyleClass().add("glass");
        VBox root = new VBox(box);
        root.setPadding(new Insets(4, 18, 18, 18));
        stage.renderScaleXProperty().addListener((obs, o, n) -> refresh());
        defineTests();
        suite.setAfterEach(this::closeStages);
        return root;
    }

    private void refresh() {
        info.getChildren().clear();
        int row = 0;
        for (Screen s : Screen.getScreens()) {
            info.addRow(row++, new Label("Screen " + row), new Label(String.format(Locale.ROOT,
                    "%.0f × %.0f, output scale %.2f × %.2f%s", s.getBounds().getWidth(), s.getBounds().getHeight(),
                    s.getOutputScaleX(), s.getOutputScaleY(), s.equals(Screen.getPrimary()) ? ", primary" : "")));
        }
        for (String key : List.of("glass.gtk.uiScale", "glass.platform", "prism.order")) {
            info.addRow(row++, new Label(key), new Label(String.valueOf(System.getProperty(key))));
        }
        for (String key : List.of("GDK_SCALE", "GDK_DPI_SCALE", "JT_MONITORS", "JT_DESKTOP", "JT_SESSION")) {
            info.addRow(row++, new Label(key), new Label(String.valueOf(System.getenv(key))));
        }
    }

    private void closeStages() {
        stages.forEach(Stage::hide);
        stages.clear();
    }

    /** A stage of the tests, shown, and its size awaited. */
    private Stage show(Parent root, double w, double h) {
        Stage s = TestKit.fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(root, w, h));
            stage.setTitle("Scale test");
            stages.add(stage);
            stage.show();
            return stage;
        });
        TestKit.await("the stage shown", 5000, () -> s.isShowing() && s.getScene().getWidth() > 0);
        TestKit.pause(400);
        return s;
    }

    private static double scale() {
        return TestKit.fx(() -> Screen.getPrimary().getOutputScaleX());
    }

    private static boolean whole(double v) {
        return Math.abs(v - Math.rint(v)) < 0.02;
    }

    private void defineTests() {
        suite.auto("Output scale of the desktop", "JDK-8262861, JDK-8248126, JDK-8326428, JDK-8137050, "
                + "JDK-8160194: the output scale of the screen is the one of the desktop", this::outputScaleTest);
        suite.auto("Render scale of a window", "JDK-8299758: the render scale of a window is the output scale of "
                + "its screen", () -> {
            Stage s = show(new StackPane(new Label("Render scale")), 300, 200);
            double[] v = TestKit.fx(() -> new double[] {s.getRenderScaleX(), s.getRenderScaleY(),
                    s.getOutputScaleX(), Screen.getScreensForRectangle(s.getX(), s.getY(), 1, 1).stream()
                    .findFirst().orElse(Screen.getPrimary()).getOutputScaleX()});
            TestKit.check(Math.abs(v[0] - v[3]) < 0.01 && Math.abs(v[1] - v[3]) < 0.01, String.format(Locale.ROOT,
                    "JDK-8299758: the render scale is %.2f × %.2f on a screen of %.2f", v[0], v[1], v[3]));
            return String.format(Locale.ROOT, "%.2f", v[0]);
        });
        suite.auto("Second setScene keeps the size", "JDK-8322252, JDK-8299968: scenes set again and again on a "
                + "showing stage keep their size, at the scale", () -> {
            Stage s = show(new StackPane(new Label("First")), 320, 220);
            double w = TestKit.fx(() -> s.getWidth()), h = TestKit.fx(() -> s.getHeight());
            for (int i = 2; i <= 4; i++) {
                int n = i;
                TestKit.fx(() -> s.setScene(new Scene(new StackPane(new Label("Scene " + n)), 320, 220)));
                TestKit.pause(400);
                double sw = TestKit.fx(() -> s.getScene().getWidth()), sh = TestKit.fx(() -> s.getScene().getHeight());
                double ww = TestKit.fx(() -> s.getWidth()), wh = TestKit.fx(() -> s.getHeight());
                TestKit.check(Math.abs(sw - 320) < 1 && Math.abs(sh - 220) < 1, String.format(Locale.ROOT,
                        "JDK-8322252: scene %d is %.1f × %.1f, not 320 × 220", i, sw, sh));
                TestKit.check(Math.abs(ww - w) < 2 && Math.abs(wh - h) < 2, String.format(Locale.ROOT,
                        "JDK-8299968: the stage went from %.0f × %.0f to %.0f × %.0f with scene %d", w, h, ww, wh, i));
            }
            return String.format(Locale.ROOT, "320 × 220 four times, at × %.2f", scale());
        });
        suite.auto("Min size at the scale", "JDK-8146136: a stage keeps its min size at the scale", () -> {
            Stage s = TestKit.fx(() -> {
                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(new Label("Min size")), 450, 350));
                stage.setMinWidth(420);
                stage.setMinHeight(320);
                stages.add(stage);
                stage.show();
                return stage;
            });
            TestKit.await("the stage shown", 5000, () -> s.isShowing());
            TestKit.pause(300);
            TestKit.fx(() -> {
                s.setWidth(200);
                s.setHeight(150);
            });
            TestKit.pause(600);
            double w = TestKit.fx(() -> s.getWidth()), h = TestKit.fx(() -> s.getHeight());
            TestKit.check(w >= 419 && h >= 319, String.format(Locale.ROOT, "JDK-8146136: the stage is %.0f × %.0f, "
                    + "below its min of 420 × 320", w, h));
            return String.format(Locale.ROOT, "%.0f × %.0f, at × %.2f", w, h, scale());
        });
        suite.auto("Tool bar without overflow", "JDK-8364487, JDK-8365554: a tool bar as wide as its buttons shows "
                + "no overflow menu, at fractional scales too", () -> {
            ToolBar bar = TestKit.fx(() -> new ToolBar(new Button("New"), new Button("Open"), new Button("Save"),
                    new Button("Print"), new Button("Close")));
            VBox root = TestKit.fx(() -> new VBox(bar));
            Stage s = show(root, 100, 80);
            TestKit.fx(() -> {
                s.sizeToScene();
                root.applyCss();
                root.layout();
                double w = bar.prefWidth(-1);
                s.setScene(new Scene(new VBox(bar), Math.ceil(w), 80));
            });
            TestKit.pause(500);
            boolean overflow = TestKit.fx(() -> {
                Node button = bar.lookup(".tool-bar-overflow-button");
                return button != null && button.isVisible();
            });
            TestKit.check(!overflow, String.format(Locale.ROOT, "JDK-8364487: the tool bar shows its overflow menu at "
                    + "× %.2f", scale()));
            return String.format(Locale.ROOT, "no overflow at × %.2f", scale());
        });
        suite.auto("Labels of a dialog not truncated", "JDK-8262856, JDK-8257096, JDK-8258664: the labels and the "
                + "buttons of an owned dialog show their whole text, at the scale", () -> {
            Stage owner = show(new StackPane(new Label("Owner")), 300, 200);
            Alert alert = TestKit.fx(() -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Do you want to save the changes to the document?",
                        ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                a.setHeaderText("Unsaved changes in the document");
                a.initOwner(owner);
                a.initModality(Modality.NONE);
                a.show();
                return a;
            });
            try {
                TestKit.await("the dialog", 5000, () -> alert.isShowing() && alert.getDialogPane().getWidth() > 0);
                TestKit.pause(500);
                List<String> cut = TestKit.fx(() -> {
                    List<String> found = new ArrayList<>();
                    for (Node n : alert.getDialogPane().lookupAll(".labeled")) {
                        if (n instanceof Labeled l && l.getText() != null && !l.getText().isEmpty()) {
                            for (Node t : l.lookupAll(".text")) {
                                if (t instanceof Text text && !text.getText().equals(l.getText())
                                        && text.getText().endsWith("…") && l.isWrapText() == false) {
                                    found.add("\"" + l.getText() + "\" shows \"" + text.getText() + "\"");
                                }
                            }
                        }
                    }
                    return found;
                });
                TestKit.check(cut.isEmpty(), "JDK-8258664: truncated at × " + scale() + ": " + cut);
                return String.format(Locale.ROOT, "whole texts at × %.2f", scale());
            } finally {
                TestKit.fx(alert::close);
            }
        });
        suite.auto("Layout on whole pixels", "JDK-8306708: the children of a box with fractional spacing and padding "
                + "land on whole device pixels", () -> {
            VBox box = TestKit.fx(() -> {
                VBox b = new VBox(3.3);
                b.setPadding(new Insets(1.7, 2.3, 1.1, 2.9));
                for (int i = 0; i < 8; i++) {
                    Label l = new Label("Label " + i);
                    l.setPadding(new Insets(0.6));
                    b.getChildren().add(l);
                }
                return b;
            });
            Stage s = show(new StackPane(box), 300, 300);
            double sc = TestKit.fx(() -> s.getRenderScaleY());
            List<String> off = TestKit.fx(() -> {
                List<String> found = new ArrayList<>();
                for (Node n : box.getChildren()) {
                    Point2D p = n.localToScene(0, 0);
                    if (!whole(p.getX() * sc) || !whole(p.getY() * sc)) {
                        found.add(String.format(Locale.ROOT, "%.3f, %.3f", p.getX() * sc, p.getY() * sc));
                    }
                }
                return found;
            });
            TestKit.check(off.isEmpty(), String.format(Locale.ROOT, "JDK-8306708: children between device pixels at "
                    + "× %.2f: %s", sc, off));
            return String.format(Locale.ROOT, "8 children on whole pixels at × %.2f", sc);
        });
        suite.auto("Scroll pane content on whole pixels", "JDK-8266840, JDK-8211294: the content of a scroll pane "
                + "scrolled anywhere lands on whole device pixels, so that it is not blurry", () -> {
            VBox content = TestKit.fx(() -> {
                VBox v = new VBox(4);
                for (int i = 0; i < 60; i++) {
                    v.getChildren().add(new Label("Line " + i + " of the content"));
                }
                return v;
            });
            ScrollPane pane = TestKit.fx(() -> new ScrollPane(content));
            Stage s = show(new StackPane(pane), 300, 240);
            double sc = TestKit.fx(() -> s.getRenderScaleY());
            List<String> off = new ArrayList<>();
            for (double v : new double[] {0.13, 0.37, 0.5, 0.71, 0.93}) {
                TestKit.fx(() -> pane.setVvalue(v));
                TestKit.pause(150);
                Point2D p = TestKit.fx(() -> content.localToScene(0, 0));
                if (!whole(p.getX() * sc) || !whole(p.getY() * sc)) {
                    off.add(String.format(Locale.ROOT, "at %.2f: %.3f, %.3f", v, p.getX() * sc, p.getY() * sc));
                }
            }
            TestKit.check(off.isEmpty(), String.format(Locale.ROOT, "JDK-8266840: the content is between device "
                    + "pixels at × %.2f: %s", sc, off));
            return String.format(Locale.ROOT, "5 positions on whole pixels at × %.2f", sc);
        });
        suite.auto("Image with a @2x variant", "JDK-8181976, JDK-8342530: an image with a @2x file keeps its size "
                + "in logical pixels, and the size asked for", () -> {
            Path dir = Files.createTempDirectory("jfx-scale");
            Path base = dir.resolve("icon.png");
            Path twice = dir.resolve("icon@2x.png");
            Files.write(base, png(20, Color.web("#2563eb")));
            Files.write(twice, png(40, Color.web("#dc2626")));
            String url = base.toUri().toString();
            double[] sizes = TestKit.fx(() -> {
                Image plain = new Image(url);
                Image sized = new Image(url, 50, 50, false, false);
                return new double[] {plain.getWidth(), plain.getHeight(), sized.getWidth(), sized.getHeight()};
            });
            TestKit.check(sizes[0] == 20 && sizes[1] == 20, "the image is " + sizes[0] + " × " + sizes[1]
                    + " logical pixels, not 20 × 20");
            TestKit.check(sizes[2] == 50 && sizes[3] == 50, "JDK-8181976: the image asked at 50 × 50 is " + sizes[2]
                    + " × " + sizes[3]);
            return String.format(Locale.ROOT, "20 × 20, and 50 × 50 when asked, at × %.2f", scale());
        });
    }

    /** A PNG of one color, through a snapshot-free path: the image writer of javafx.swing. */
    private static byte[] png(int size, Color color) throws java.io.IOException {
        WritableImage image = new WritableImage(size, size);
        PixelWriter w = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                w.setColor(x, y, color);
            }
        }
        java.awt.image.BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(bi, "png", out);
            return out.toByteArray();
        }
    }

    /**
     * The output scale that JavaFX must see, from how jfx-tester set up the
     * desktop, or a skip where it has no clear answer.
     */
    private String outputScaleTest() {
        String monitors = System.getenv("JT_MONITORS");
        if (monitors == null || monitors.isBlank()) {
            TestKit.skip("not in a session of jfx-tester (JT_MONITORS)");
        }
        double[] scales = Arrays.stream(monitors.split(","))
                .mapToDouble(m -> m.contains("@") ? Double.parseDouble(m.substring(m.indexOf('@') + 1)) : 1).toArray();
        double max = Arrays.stream(scales).max().orElse(1);
        String desktop = String.valueOf(System.getenv("JT_DESKTOP"));
        String session = String.valueOf(System.getenv("JT_SESSION"));
        double expected;
        String why;
        if ("Gtk4".equalsIgnoreCase(System.getProperty("glass.platform"))) {
            expected = scales[0];
            why = "the scale of the first monitor, on Wayland";
        } else if (session.equals("wayland") && desktop.equals("kde")) {
            boolean system = "system".equals(System.getenv("JT_XWAYLAND_SCALE"));
            expected = system ? 1 : max;
            why = system ? "1, as KWin scales the X11 windows" : "the largest scale, Xft.dpi of KWin";
        } else if (session.equals("x11")) {
            expected = scales[0];
            why = "the scale of the first monitor, GDK_SCALE or glass.gtk.uiScale on X11";
        } else {
            TestKit.skip("no expected scale for X11 applications on " + desktop + " (" + session + ")");
            return null;
        }
        double actual = scale();
        TestKit.check(Math.abs(actual - expected) < 0.01, String.format(Locale.ROOT, "the output scale is %.2f, "
                + "expected %.2f: %s", actual, expected, why));
        return String.format(Locale.ROOT, "%.2f, %s", actual, why);
    }
}
