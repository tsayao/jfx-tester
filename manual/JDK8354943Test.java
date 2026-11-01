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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * The tests of JDK-8354943, on window sizing, positioning and state in the
 * glass of GTK, one group for each bug that it should fix, with the steps of
 * the bug or of the system test that shows it:
 * <ul>
 * <li>https://bugs.openjdk.org/browse/JDK-8354943 (the fix)</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8365201</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8366009</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8367893</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8321624</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8353556</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8355073</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8353612</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8337400</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8316891</li>
 * <li>https://bugs.openjdk.org/browse/JDK-8316425</li>
 * </ul>
 * It needs only this file. It shows where each bug was reported and where it runs.
 * A test passes when the window reaches the state
 * that it should and keeps it for a second, as some of these windows reach it
 * and then leave it. Some of these bugs are intermittent: those tests run
 * several times and report how many failed (-Dtests.repeat=N, 5 by default).
 * <pre>
 * ./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --manual=manual/JDK8354943Test.java
 * ./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --monitors=1920x1080,1280x720 --headless \
 *     --manual=manual/JDK8354943Test.java --timeout=900 --jvm-args="-Dtests.run=true -Dtests.exit=true"
 * </pre>
 * With -Dtests.run=true the tests run at startup, and with -Dtests.exit=true the
 * application then exits, with status 1 when a test failed.
 * -Dtests.bugs=JDK-8366009,JDK-8316425 keeps only those bugs, and
 * -Dtests.robot=false skips the tests that need the Robot, for the screen and
 * the keyboard.
 */
public class JDK8354943Test extends Application {

    private static final String JBS = "https://bugs.openjdk.org/browse/";
    private static final String FIX = "JDK-8354943";
    private static final int REPEAT = Math.max(1, Integer.getInteger("tests.repeat", 5));
    /** How long a window has to reach a state, and to keep it. */
    private static final long WAIT = 5000, HOLD = 1000;
    private static final long FX_SECONDS = 60;
    /** As in SizeToSceneTest: the differences that decorations and window managers make. */
    private static final double BOUNDS_DELTA = 50;
    private static final double ROOT_SIZE = 360;
    /**
     * The environments of jfx-tester of the reports of the bugs, or the nearest ones: a report without a kind of
     * session runs on both.
     */
    private static final String GNOME_WAYLAND = "--distros=ubuntu:24.04 --desktops=gnome --session=wayland";
    private static final String GNOME_XORG = "--distros=ubuntu:24.04 --desktops=gnome --session=xorg";
    private static final String CINNAMON_XORG = "--distros=mint:22.3 --desktops=cinnamon --session=xorg";
    private static final String UBUNTU_22_WAYLAND = "--distros=ubuntu:22.04 --desktops=gnome --session=wayland";
    private static final String UBUNTU_22_XORG = "--distros=ubuntu:22.04 --desktops=gnome --session=xorg";
    private static final String SMALLER_SECOND_MONITOR = "--monitors=1920x1080,1280x720";

    public static void main(String[] args) {
        // The robot keeps its token in user.home: the one of the session of jfx-tester, as the system tests
        String home = System.getenv("JT_HOME");
        if (home != null) {
            System.setProperty("user.home", home);
        }
        launch(args);
    }

    private enum Status {
        NOT_RUN("#888888"), RUNNING("#2a6fdb"), OK("#1a7f37"), FAIL("#c62828"), SKIP("#9a6700");

        final String color;

        Status(String color) {
            this.color = color;
        }
    }

    @FunctionalInterface
    private interface Body {
        String run() throws Exception;
    }

    private static final class Skip extends RuntimeException {
        Skip(String message) {
            super(message);
        }
    }

    private static final class Failure extends RuntimeException {
        Failure(String message) {
            super(message);
        }
    }

    private final class Bug {
        final String id;
        final String title;
        /** Where the bug was seen, from its report and comments in JBS. */
        final String reported;
        final List<String> commands = new ArrayList<>();
        final List<Check> checks = new ArrayList<>();
        final Label summary = new Label();

        Bug(String id, String title, String reported) {
            this.id = id;
            this.title = title;
            this.reported = reported;
            bugs.add(this);
        }

        /** The command that runs the tests of the bug in an environment, repeated for the intermittent ones. */
        Bug on(String environment, int repeat) {
            commands.add("./jfx-tester " + environment + " --manual=manual/JDK8354943Test.java --jvm-args=\""
                    + "-Dtests.run=true -Dtests.bugs=" + id + (repeat > 0 ? " -Dtests.repeat=" + repeat : "") + "\"");
            return this;
        }

        Bug check(String name, String steps, int runs, Body body) {
            checks.add(new Check(this, name, steps, runs, body));
            return this;
        }
    }

    private static final class Check {
        final Bug bug;
        final String name;
        final String steps;
        final int runs;
        final Body body;
        final Label status = new Label();
        final Label result = new Label();
        HBox row;
        Status outcome = Status.NOT_RUN;

        Check(Bug bug, String name, String steps, int runs, Body body) {
            this.bug = bug;
            this.name = name;
            this.steps = steps;
            this.runs = runs;
            this.body = body;
        }
    }

    private final List<Bug> bugs = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();
    private final List<Button> runButtons = new ArrayList<>();
    private final Label total = new Label("Not run");
    private final Button stop = new Button("Stop");
    private final VBox list = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(list);
    private Robot robot;
    private volatile boolean running;
    private volatile boolean stopping;

    @Override
    public void start(Stage stage) {
        defineChecks();
        Hyperlink fix = link(FIX);
        Label title = new Label("Window sizing, positioning and state in glass-gtk");
        title.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button runAll = button("Run all", () -> run(allChecks()));
        stop.setDisable(true);
        stop.setOnAction(e -> stopping = true);
        HBox top = new HBox(10, title, fix, spacer, total, runAll, stop);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        list.setPadding(new Insets(10));
        for (Bug bug : bugs) {
            list.getChildren().add(view(bug));
        }
        scroll.setFitToWidth(true);

        String environment = environment();
        System.out.println(environment);
        Label info = new Label(environment);
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #666666;");
        info.setPadding(new Insets(0, 10, 8, 10));

        BorderPane body = new BorderPane(scroll);
        body.setTop(new VBox(top, info));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setScene(new Scene(body, Math.min(1000, screen.getWidth() * 0.9), Math.min(760, screen.getHeight() * 0.9)));
        stage.setTitle(FIX + " tests");
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();

        if (Boolean.getBoolean("tests.run")) {
            run(allChecks());
        }
    }

    /** Where the tests run, for the window and the log. */
    private static String environment() {
        String wayland = System.getenv("WAYLAND_DISPLAY");
        String x11 = System.getenv("DISPLAY");
        String session = wayland != null && !wayland.isEmpty()
                ? "Wayland" + (x11 != null && !x11.isEmpty() ? ", Xwayland " + x11 : "")
                : "X11" + (x11 != null ? " " + x11 : "");
        StringBuilder screens = new StringBuilder();
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D b = screen.getBounds();
            screens.append(screens.isEmpty() ? "" : ", ").append(num(b.getWidth())).append(" × ")
                    .append(num(b.getHeight())).append(" @").append(num(screen.getOutputScaleX()));
        }
        String gtk = command("pkg-config", "--modversion", "gtk+-3.0");
        return "This run: " + distribution() + " · Kernel: " + System.getProperty("os.version")
                + " · Desktop: " + desktop() + " · Session: " + session
                + (gtk.isEmpty() ? "" : " · GTK: " + gtk)
                + " · JavaFX: " + System.getProperty("javafx.runtime.version", "unknown")
                + " · Java: " + System.getProperty("java.version")
                + " · Screens: " + screens;
    }

    private static String distribution() {
        try {
            for (String line : Files.readAllLines(Path.of("/etc/os-release"))) {
                if (line.startsWith("PRETTY_NAME=")) {
                    return line.substring(line.indexOf('=') + 1).replace("\"", "");
                }
            }
        } catch (IOException e) {
            // Below
        }
        return "unknown";
    }

    private static String desktop() {
        String name = System.getenv("XDG_CURRENT_DESKTOP");
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String version = lower.contains("gnome") ? command("gnome-shell", "--version")
                : lower.contains("kde") ? command("plasmashell", "--version")
                : lower.contains("cinnamon") ? command("cinnamon", "--version") : "";
        return version.isEmpty() ? name : version;
    }

    /** The first line that a command prints, or nothing. */
    private static String command(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = new String(process.getInputStream().readAllBytes()).strip();
            return process.waitFor() == 0 ? out.lines().findFirst().orElse("") : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private List<Check> allChecks() {
        return bugs.stream().flatMap(b -> b.checks.stream()).toList();
    }

    private Hyperlink link(String id) {
        String url = JBS + id;
        Hyperlink link = new Hyperlink(id);
        link.setTooltip(new Tooltip(url));
        link.setOnAction(e -> {
            System.out.println(url);
            getHostServices().showDocument(url);
        });
        return link;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        runButtons.add(b);
        return b;
    }

    private VBox view(Bug bug) {
        Label title = new Label(bug.title);
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold;");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox header = new HBox(8, link(bug.id), title, bug.summary, button("Run", () -> run(bug.checks)));
        header.setAlignment(Pos.CENTER_LEFT);
        Label reported = new Label("Reported on: " + bug.reported);
        reported.setWrapText(true);
        reported.setStyle("-fx-text-fill: #666666; -fx-font-style: italic;");
        VBox box = new VBox(4, header, reported);
        for (String command : bug.commands) {
            box.getChildren().add(commandRow(command));
        }
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8;");
        for (Check c : bug.checks) {
            show(c, Status.NOT_RUN, "");
            c.status.setMinWidth(70);
            Label name = new Label(c.name);
            name.setTooltip(new Tooltip(c.steps));
            Label steps = new Label(c.steps);
            steps.setWrapText(true);
            steps.setStyle("-fx-text-fill: #666666;");
            c.result.setWrapText(true);
            VBox text = new VBox(1, name, steps, c.result);
            HBox.setHgrow(text, Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            c.row = new HBox(8, c.status, text, button("Run", () -> run(List.of(c))));
            c.row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(c.row);
        }
        return box;
    }

    private static HBox commandRow(String command) {
        TextField text = new TextField(command);
        text.setEditable(false);
        text.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        HBox.setHgrow(text, Priority.ALWAYS);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(command);
            Clipboard.getSystemClipboard().setContent(content);
        });
        HBox row = new HBox(6, text, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Scrolls the list to the middle of the row of a test. */
    private void reveal(Check c) {
        scroll.layout();
        Bounds row = list.sceneToLocal(c.row.localToScene(c.row.getBoundsInLocal()));
        double viewport = scroll.getViewportBounds().getHeight();
        double extra = list.getHeight() - viewport;
        if (extra > 0) {
            double y = row.getMinY() - (viewport - row.getHeight()) / 2;
            scroll.setVvalue(Math.clamp(y / extra, 0, 1));
        }
    }

    private static void show(Check c, Status status, String text) {
        c.status.setText(status == Status.NOT_RUN ? "—" : status.name());
        c.status.setStyle("-fx-font-weight: bold; -fx-text-fill: " + status.color + ";");
        c.result.setText(text);
    }

    // ---------------------------------------------------------------------
    // The tests

    private void defineChecks() {
        new Bug("JDK-8365201", "Restoring the maximized state of a window makes it exceed the screen's bounds",
                "jfx21, jfx25. Linux Mint 22.1 (base Ubuntu 24.04), Cinnamon 6.4.8, Muffin 6.4.1, GTK "
                + "3.24.41, kernel 6.8, two 1920 × 1080 monitors. Not seen on Ubuntu 24.04, Xorg or Wayland")
                .on(CINNAMON_XORG + " " + SMALLER_SECOND_MONITOR, 0)
                .check("Restore maximized, on each screen", "Maximizes a window on a screen and keeps its position and "
                        + "size, then shows a new window with them and setMaximized(true). Its title bar stays on the "
                        + "screen. It needs a second monitor smaller than the first (--monitors=1920x1080,1280x720)", 1,
                        this::restoreMaximized);

        new Bug("JDK-8366009", "Stage does not always enter full screen when shown with fullScreen set to true",
                "jfx25. Ubuntu 24.04, X.org and Wayland, intermittent. Works on Ubuntu 22.04")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .check("Shown in full screen", "HelloFullscreen: setFullScreen(true), setScene, show", REPEAT, () -> {
                    Stage s = open(600, 450, st -> {
                        st.setFullScreen(true);
                        st.show();
                    });
                    settle("full screen", () -> notFullScreen(s));
                    return size(s);
                });

        new Bug("JDK-8367893", "StageFocusTest fails on some Linux systems",
                "jfx26. Ubuntu 24.04, Wayland (XWayland); also Ubuntu 22.04, X.org, 2 of 3 runs")
                .on(GNOME_WAYLAND, 0)
                .on(UBUNTU_22_XORG, 0)
                .check("Focus after show", "An undecorated stage is focused when shown, on top, and gets the keys of "
                        + "the Robot", REPEAT, this::focusAfterShow);

        new Bug("JDK-8321624", "DualWindowTest fails intermittently on Linux",
                "jfx22. Linux, in automated runs; reproduced on an Ubuntu 22.04 VM, once in 5 to 10 runs")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .on(UBUNTU_22_WAYLAND, 20)
                .on(UBUNTU_22_XORG, 20)
                .check("Two stages moved when shown", "DualWindowTest: both stages get their position in a "
                        + "Platform.runLater from WINDOW_SHOWN", REPEAT, () -> dualWindows(true))
                .check("Two stages moved before show", "As above, with the position set before show", REPEAT,
                        () -> dualWindows(false));

        new Bug("JDK-8353556", "RestoreSceneSizeTest fails in Ubuntu 24.04",
                "jfx25. Ubuntu 24.04, Wayland and Xorg. Not on Ubuntu 22.04")
                .on(GNOME_WAYLAND, 0)
                .on(GNOME_XORG, 0)
                .check("Scene size, full screen before show", "RestoreSceneSizeTest: setFullScreen(true), show, then "
                        + "setFullScreen(false). The scene is 234 × 255 again", 1, () -> restoreSceneSize(true))
                .check("Scene size, full screen after show", "As above, with setFullScreen(true) after show", 1,
                        () -> restoreSceneSize(false));

        new Bug("JDK-8355073", "View size glitch when resizing past max window size",
                "jfx24. Linux, no distribution given")
                .on(GNOME_WAYLAND, 0)
                .on(GNOME_XORG, 0)
                .check("setSize past the max size", "ResizeWindowApp: a 300 × 300 scene with a max size of 300 × 300 "
                        + "asks for 400 × 400. The view fits the window, with its content centered", 1,
                        this::resizePastMax);

        Bug sizeToScene = new Bug("JDK-8353612", "Some of the SizeToSceneTest fail in Ubuntu 24.04",
                "jfx25. Ubuntu 24.04, Wayland and Xorg, VM and hardware. Not on Ubuntu 22.04")
                .on(GNOME_WAYLAND, 0)
                .on(GNOME_XORG, 0);
        screenSized(sizeToScene, "Maximized, sizeToScene, show", st -> {
            st.setMaximized(true);
            st.sizeToScene();
            st.show();
        });
        screenSized(sizeToScene, "Full screen, sizeToScene, show", st -> {
            st.setFullScreen(true);
            st.sizeToScene();
            st.show();
        });
        screenSized(sizeToScene, "sizeToScene, maximized, show", st -> {
            st.sizeToScene();
            st.setMaximized(true);
            st.show();
        });
        screenSized(sizeToScene, "sizeToScene, full screen, show", st -> {
            st.sizeToScene();
            st.setFullScreen(true);
            st.show();
        });
        screenSized(sizeToScene, "Show, sizeToScene, full screen", st -> {
            st.show();
            st.sizeToScene();
            st.setFullScreen(true);
        });
        screenSized(sizeToScene, "Show, sizeToScene, maximized", st -> {
            st.show();
            st.sizeToScene();
            st.setMaximized(true);
        });
        screenSized(sizeToScene, "Show, full screen, sizeToScene", st -> {
            st.show();
            st.setFullScreen(true);
            st.sizeToScene();
        });
        screenSized(sizeToScene, "Show, maximized, sizeToScene", st -> {
            st.show();
            st.setMaximized(true);
            st.sizeToScene();
        });
        sceneSized(sizeToScene, "sizeToScene, show", st -> {
            st.sizeToScene();
            st.show();
        });
        sceneSized(sizeToScene, "Full screen on, sizeToScene, off, show", st -> {
            st.setWidth(100);
            st.setHeight(100);
            st.setFullScreen(true);
            st.sizeToScene();
            st.setFullScreen(false);
            st.show();
        });
        sceneSized(sizeToScene, "sizeToScene, full screen on, off, show", st -> {
            st.setWidth(100);
            st.setHeight(100);
            st.sizeToScene();
            st.setFullScreen(true);
            st.setFullScreen(false);
            st.show();
        });
        sceneSized(sizeToScene, "Maximized on, sizeToScene, off, show", st -> {
            st.setWidth(100);
            st.setHeight(100);
            st.setMaximized(true);
            st.sizeToScene();
            st.setMaximized(false);
            st.show();
        });
        sceneSized(sizeToScene, "sizeToScene, maximized on, off, show", st -> {
            st.setWidth(100);
            st.setHeight(100);
            st.sizeToScene();
            st.setMaximized(true);
            st.setMaximized(false);
            st.show();
        });

        new Bug("JDK-8337400", "Initial window position is not centered on Ubuntu 24.04 / Xorg",
                "jfx23. Ubuntu 24.04, GNOME, only on Xorg")
                .on(GNOME_XORG, 0)
                .check("Centered when shown", "PositionTest: a 640 × 480 scene, with no position. It shows where "
                        + "Stage.centerOnScreen() puts it", REPEAT, this::centeredWhenShown);

        new Bug("JDK-8316891", "Intermittent test failure in IconifyTest.canIconifyDecoratedStage",
                "jfx21. Linux, in nightly test runs, no distribution given")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .check("Iconify a decorated stage", "IconifyTest: a stage above another. Iconified, the one below "
                        + "shows on the screen, and deiconified it is above it again", REPEAT, this::iconify);

        new Bug("JDK-8316425", "Stage.setMaximized() before show() does not persist",
                "jfx21. Fedora 38 (Linux 6.4) VM and Ubuntu 23.04, X11, not on Wayland. Fine on Ubuntu "
                + "22.04")
                .on(GNOME_XORG, 0)
                .check("Maximized before show", "MaximizeNotPersisting: setMaximized(true), show. It stays maximized",
                        REPEAT, () -> {
                            Stage s = open(400, 400, st -> {
                                st.setMaximized(true);
                                st.show();
                            });
                            settle("maximized", () -> notMaximized(s));
                            return size(s);
                        })
                .check("Full screen before show", "MaximizeNotPersisting: setFullScreen(true), show. It stays in full "
                        + "screen", REPEAT, () -> {
                            Stage s = open(400, 400, st -> {
                                st.setFullScreen(true);
                                st.show();
                            });
                            settle("full screen", () -> notFullScreen(s));
                            return size(s);
                        })
                .check("Dialog maximized before show", "DialogMaximizeFS: a resizable Dialog, maximized before it "
                        + "shows. It stays maximized", REPEAT, () -> {
                            Stage s = fx(() -> {
                                Dialog<ButtonType> dialog = new Dialog<>();
                                dialog.setTitle("Maximized dialog");
                                dialog.setResizable(true);
                                dialog.setContentText("This dialog should stay maximized");
                                dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                                Stage window = (Stage) dialog.getDialogPane().getScene().getWindow();
                                window.setMaximized(true);
                                dialog.show();
                                stages.add(window);
                                return window;
                            });
                            settle("maximized", () -> notMaximized(s));
                            return size(s);
                        });

        String only = System.getProperty("tests.bugs");
        if (only != null) {
            List<String> ids = List.of(only.split(","));
            bugs.removeIf(b -> !ids.contains(b.id));
        }
    }

    private void screenSized(Bug bug, String name, Consumer<Stage> steps) {
        bug.check(name, "SizeToSceneTest: a window the size of the screen, not of its scene", 1, () -> {
            Stage s = openRoot(steps);
            settle("the size of the screen", () -> notScreenSized(s));
            return size(s);
        });
    }

    private void sceneSized(Bug bug, String name, Consumer<Stage> steps) {
        bug.check(name, "SizeToSceneTest: a window the size of its scene, of 360 × 360", 1, () -> {
            Stage s = openRoot(steps);
            settle("the size of the scene", () -> notSceneSized(s));
            return size(s);
        });
    }

    private String restoreMaximized() throws Exception {
        List<Screen> screens = fx(() -> List.copyOf(Screen.getScreens()));
        if (screens.size() < 2) {
            throw new Skip("It needs a second monitor smaller than the first (--monitors=1920x1080,1280x720), "
                    + "there is one");
        }
        Rectangle2D firstScreen = screens.get(0).getBounds(), secondScreen = screens.get(1).getBounds();
        if (secondScreen.getWidth() >= firstScreen.getWidth() || secondScreen.getHeight() >= firstScreen.getHeight()) {
            throw new Skip("It needs a second monitor smaller than the first (--monitors=1920x1080,1280x720), "
                    + "they are " + bounds(firstScreen) + " and " + bounds(secondScreen));
        }
        StringBuilder sb = new StringBuilder();
        for (Screen screen : screens) {
            Rectangle2D v = screen.getVisualBounds();
            Stage first = open(400, 300, st -> {
                st.setX(v.getMinX() + 60);
                st.setY(v.getMinY() + 60);
                st.show();
            });
            settle("shown on " + bounds(v), () -> near(first.getX(), v.getMinX() + 60, 60)
                    ? null : "it is at " + position(first));
            fx(() -> {
                first.setMaximized(true);
                return null;
            });
            settle("maximized on " + bounds(v), () -> notMaximized(first));
            double[] saved = fx(() -> new double[] {first.getX(), first.getY(), first.getWidth(), first.getHeight()});
            closeStages();

            Stage restored = open(400, 300, st -> {
                st.setX(saved[0]);
                st.setY(saved[1]);
                st.setWidth(saved[2]);
                st.setHeight(saved[3]);
                st.setMaximized(true);
                st.show();
            });
            settle("restored maximized on " + bounds(v), () -> {
                Rectangle2D b = screen.getBounds();
                if (!restored.isMaximized()) {
                    return "isMaximized is false";
                }
                boolean inside = restored.getX() >= b.getMinX() - 1 && restored.getY() >= b.getMinY() - 1
                        && restored.getX() + restored.getWidth() <= b.getMaxX() + 1
                        && restored.getY() + restored.getHeight() <= b.getMaxY() + 1;
                return inside ? null : "the window is " + size(restored) + " at " + position(restored)
                        + ", past the screen " + bounds(b);
            });
            sb.append(sb.isEmpty() ? "" : ", ").append(fx(() -> size(restored)));
            closeStages();
        }
        return sb.toString();
    }

    private String focusAfterShow() throws Exception {
        requireRobot();
        boolean[] pressed = {false};
        Color fill = Color.rgb(0, 200, 0);
        Stage s = fx(() -> {
            Stage st = new Stage(StageStyle.UNDECORATED);
            // A Group: the root style of a Pane would paint over the fill
            Scene scene = new Scene(new Group(), 200, 200, fill);
            scene.setOnKeyPressed(e -> pressed[0] |= e.getCode() == KeyCode.A);
            st.setScene(scene);
            st.setX(100);
            st.setY(100);
            stages.add(st);
            st.show();
            return st;
        });
        settle("focused", () -> s.isFocused() ? null : "isFocused is false");
        awaitColor("on top", s, fill);
        fx(() -> {
            robot().keyPress(KeyCode.A);
            robot().keyRelease(KeyCode.A);
            return null;
        });
        await("the key of the Robot", () -> pressed[0] ? null : "no key event: focused " + s.isFocused());
        return "focused, on top, key received";
    }

    private String dualWindows(boolean whenShown) throws Exception {
        double[][] at = {{100, 100}, {400, 120}};
        Stage[] two = new Stage[2];
        for (int i = 0; i < 2; i++) {
            double x = at[i][0], y = at[i][1];
            two[i] = open(200, 200, st -> {
                if (whenShown) {
                    st.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> Platform.runLater(() -> {
                        st.setX(x);
                        st.setY(y);
                    }));
                } else {
                    st.setX(x);
                    st.setY(y);
                }
                st.show();
            });
        }
        for (int i = 0; i < 2; i++) {
            Stage st = two[i];
            double x = at[i][0], y = at[i][1];
            settle("stage " + (i + 1) + " at " + num(x) + ", " + num(y), () -> near(st.getX(), x, 1)
                    && near(st.getY(), y, 1) ? null : "it is at " + position(st));
        }
        return "at " + fx(() -> position(two[0])) + " and " + fx(() -> position(two[1]));
    }

    private String restoreSceneSize(boolean before) throws Exception {
        double width = 234, height = 255;
        Stage s = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new VBox(), width, height));
            stages.add(st);
            if (before) {
                st.setFullScreen(true);
            }
            st.show();
            if (!before) {
                st.setFullScreen(true);
            }
            return st;
        });
        settle("full screen", () -> notFullScreen(s));
        fx(() -> {
            s.setFullScreen(false);
            return null;
        });
        double w = fx(() -> Math.ceil(width * s.getOutputScaleX()) / s.getOutputScaleX());
        double h = fx(() -> Math.ceil(height * s.getOutputScaleY()) / s.getOutputScaleY());
        settle("the scene at " + num(w) + " × " + num(h), () -> {
            if (s.isFullScreen()) {
                return "isFullScreen is true";
            }
            return near(s.getScene().getWidth(), w, 0.1) && near(s.getScene().getHeight(), h, 0.1)
                    ? null : "the scene is " + sceneSize(s);
        });
        return sceneSize(s);
    }

    private String resizePastMax() throws Exception {
        double max = 300;
        Button content = new Button("Should stay centered");
        Stage s = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new StackPane(content), max, max));
            st.setMinWidth(100);
            st.setMinHeight(100);
            st.setMaxWidth(max);
            st.setMaxHeight(max);
            stages.add(st);
            st.show();
            return st;
        });
        settle("within its max size", () -> notWithin(s, max));
        double frameW = fx(() -> s.getWidth() - s.getScene().getWidth());
        double frameH = fx(() -> s.getHeight() - s.getScene().getHeight());
        fx(() -> {
            s.setWidth(400);
            s.setHeight(400);
            return null;
        });
        Thread.sleep(300);
        settle("the view within the window", () -> {
            String within = notWithin(s, max);
            if (within != null) {
                return within;
            }
            double w = s.getWidth() - frameW, h = s.getHeight() - frameH;
            if (!near(s.getScene().getWidth(), w, 1) || !near(s.getScene().getHeight(), h, 1)) {
                return "the scene is " + sceneSize(s) + " in a window of " + size(s) + ", expected " + num(w)
                        + " × " + num(h);
            }
            // As in the screenshot of the bug, laid out for the size that was asked
            Point2D center = content.localToScene(content.getWidth() / 2, content.getHeight() / 2);
            if (!near(center.getX(), w / 2, 2) || !near(center.getY(), h / 2, 2)) {
                return "the center of the content is at " + num(center.getX()) + ", " + num(center.getY())
                        + ", not of a view of " + num(w) + " × " + num(h);
            }
            return null;
        });
        return size(s) + ", scene " + sceneSize(s);
    }

    private String centeredWhenShown() throws Exception {
        Stage s = open(640, 480, Stage::show);
        settle("centered", () -> {
            Rectangle2D v = Screen.getPrimary().getVisualBounds();
            // What Window.centerOnScreen() does
            double x = v.getMinX() + (v.getWidth() - s.getWidth()) / 2;
            double y = v.getMinY() + (v.getHeight() - s.getHeight()) / 3;
            return near(s.getX(), x, BOUNDS_DELTA) && near(s.getY(), y, BOUNDS_DELTA) ? null
                    : "it is at " + position(s) + ", centered is about " + num(x) + ", " + num(y);
        });
        return "at " + fx(() -> position(s));
    }

    private String iconify() throws Exception {
        requireRobot();
        Color bottomColor = Color.LIME, topColor = Color.RED;
        Stage bottom = filled(StageStyle.DECORATED, bottomColor);
        settle("the stage below shown", () -> bottom.isShowing() ? null : "not showing");
        awaitColor("the stage below", bottom, bottomColor);
        Stage top = filled(StageStyle.DECORATED, topColor);
        awaitColor("the stage above", top, topColor);
        fx(() -> {
            top.setIconified(true);
            return null;
        });
        settle("iconified", () -> top.isIconified() ? null : "isIconified is false");
        awaitColor("the stage below, with the other iconified", bottom, bottomColor);
        fx(() -> {
            top.setIconified(false);
            return null;
        });
        settle("deiconified", () -> top.isIconified() ? "isIconified is true" : null);
        awaitColor("the stage above again", top, topColor);
        return "iconified and deiconified";
    }

    private Stage filled(StageStyle style, Color fill) throws Exception {
        return fx(() -> {
            Stage st = new Stage(style);
            st.setScene(new Scene(new Group(), 300, 300, fill));
            st.setX(100);
            st.setY(100);
            stages.add(st);
            st.show();
            return st;
        });
    }

    // ---------------------------------------------------------------------
    // What is wrong with a window, or null

    private static String notFullScreen(Stage s) {
        if (!s.isFullScreen()) {
            return "isFullScreen is false, the window is " + size(s) + " at " + position(s);
        }
        Rectangle2D b = screenOf(s).getBounds();
        Scene scene = s.getScene();
        return near(scene.getWidth(), b.getWidth(), 1) && near(scene.getHeight(), b.getHeight(), 1)
                ? null : "the scene is " + sceneSize(s) + " on a screen of " + bounds(b);
    }

    private static String notMaximized(Stage s) {
        if (!s.isMaximized()) {
            return "isMaximized is false, the window is " + size(s) + " at " + position(s);
        }
        return notScreenSized(s);
    }

    /** As SizeToSceneTest.assertStageScreenBounds. */
    private static String notScreenSized(Stage s) {
        Screen screen = screenOf(s);
        Rectangle2D v = screen.getVisualBounds(), b = screen.getBounds();
        boolean fits = s.getWidth() >= v.getWidth() - BOUNDS_DELTA && s.getHeight() >= v.getHeight() - BOUNDS_DELTA
                && s.getWidth() <= b.getWidth() + BOUNDS_DELTA && s.getHeight() <= b.getHeight() + BOUNDS_DELTA;
        return fits ? null : "the window is " + size(s) + " on a screen of " + bounds(b);
    }

    /** As SizeToSceneTest.assertStageSceneBounds. */
    private static String notSceneSized(Stage s) {
        boolean fits = s.getWidth() >= ROOT_SIZE && s.getHeight() >= ROOT_SIZE
                && s.getWidth() <= ROOT_SIZE + BOUNDS_DELTA && s.getHeight() <= ROOT_SIZE + BOUNDS_DELTA;
        return fits ? null : "the window is " + size(s) + ", its scene is " + num(ROOT_SIZE) + " × " + num(ROOT_SIZE);
    }

    private static String notWithin(Stage s, double max) {
        return s.getWidth() <= max + 1 && s.getHeight() <= max + 1 ? null
                : "the window is " + size(s) + ", past its max size of " + num(max) + " × " + num(max);
    }

    private static Screen screenOf(Stage s) {
        List<Screen> screens = Screen.getScreensForRectangle(s.getX(), s.getY(), Math.max(1, s.getWidth()),
                Math.max(1, s.getHeight()));
        return screens.isEmpty() ? Screen.getPrimary() : screens.getFirst();
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static String size(Stage s) {
        return num(s.getWidth()) + " × " + num(s.getHeight());
    }

    private static String sceneSize(Stage s) {
        return num(s.getScene().getWidth()) + " × " + num(s.getScene().getHeight());
    }

    private static String position(Stage s) {
        return num(s.getX()) + ", " + num(s.getY());
    }

    private static String bounds(Rectangle2D b) {
        return num(b.getWidth()) + " × " + num(b.getHeight()) + " at " + num(b.getMinX()) + ", " + num(b.getMinY());
    }

    private static String num(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    // ---------------------------------------------------------------------
    // Helpers of the tests

    /** A new window with a scene of that size, on the FX thread. */
    private Stage open(double width, double height, Consumer<Stage> steps) throws Exception {
        return fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new Pane(), width, height));
            stages.add(st);
            steps.accept(st);
            return st;
        });
    }

    /** The window of SizeToSceneTest. */
    private Stage openRoot(Consumer<Stage> steps) throws Exception {
        return fx(() -> {
            Stage st = new Stage();
            Button root = new Button();
            root.setMinSize(ROOT_SIZE, ROOT_SIZE);
            st.setScene(new Scene(root));
            stages.add(st);
            steps.accept(st);
            return st;
        });
    }

    private void closeStages() throws Exception {
        fx(() -> {
            stages.forEach(Stage::hide);
            stages.clear();
            return null;
        });
        Thread.sleep(250);
    }

    /** Runs on the FX thread. It ends the application if that thread is stuck, as nothing else would run. */
    private static <T> T fx(Callable<T> body) throws Exception {
        FutureTask<T> task = new FutureTask<>(body);
        Platform.runLater(task);
        try {
            return task.get(FX_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        } catch (TimeoutException e) {
            System.out.println("FAIL the FX thread did not answer for " + FX_SECONDS + " s: ending");
            System.out.flush();
            Runtime.getRuntime().halt(1);
            throw e;
        }
    }

    private static void await(String what, Callable<String> problem) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT;
        String last = fx(problem);
        while (last != null) {
            if (System.currentTimeMillis() > deadline) {
                throw new Failure("Timed out waiting for " + what + ": " + last);
            }
            Thread.sleep(50);
            last = fx(problem);
        }
    }

    /** That the window reaches the state and keeps it. */
    private static void settle(String what, Callable<String> problem) throws Exception {
        await(what, problem);
        long deadline = System.currentTimeMillis() + HOLD;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            String now = fx(problem);
            if (now != null) {
                throw new Failure("It was " + what + ", but then not: " + now);
            }
        }
    }

    private void awaitColor(String what, Stage s, Color expected) throws Exception {
        await(what + " on the screen", () -> {
            Scene scene = s.getScene();
            Point2D center = scene.getRoot().localToScreen(scene.getWidth() / 2, scene.getHeight() / 2);
            if (center == null) {
                return "not on a screen";
            }
            Color c = robot().getPixelColor(center.getX(), center.getY());
            boolean same = near(c.getRed(), expected.getRed(), 0.07) && near(c.getGreen(), expected.getGreen(), 0.07)
                    && near(c.getBlue(), expected.getBlue(), 0.07);
            return same ? null : "the pixel is " + c + ", expected " + expected;
        });
    }

    private Robot robot() {
        if (robot == null) {
            robot = new Robot();
        }
        return robot;
    }

    private static void requireRobot() {
        if (!Boolean.parseBoolean(System.getProperty("tests.robot", "true"))) {
            throw new Skip("The Robot is turned off with -Dtests.robot=false");
        }
    }

    // ---------------------------------------------------------------------
    // Running them

    private void run(List<Check> checks) {
        if (running) {
            return;
        }
        running = true;
        stopping = false;
        runButtons.forEach(b -> b.setDisable(true));
        stop.setDisable(false);
        total.setText("Running…");
        for (Check c : checks) {
            show(c, Status.NOT_RUN, "");
            c.outcome = Status.NOT_RUN;
        }
        Thread thread = new Thread(() -> runChecks(checks), "tests");
        thread.setDaemon(true);
        thread.start();
    }

    private void runChecks(List<Check> checks) {
        long start = System.nanoTime();
        for (Check c : checks) {
            if (stopping) {
                break;
            }
            runCheck(c);
        }
        long ok = count(checks, Status.OK), fail = count(checks, Status.FAIL), skip = count(checks, Status.SKIP);
        String text = String.format(Locale.ROOT, "%d OK · %d FAIL · %d SKIP in %d s%s", ok, fail, skip,
                (System.nanoTime() - start) / 1_000_000_000L, stopping ? " (stopped)" : "");
        System.out.println();
        for (Bug bug : bugs) {
            List<Check> ran = bug.checks.stream().filter(c -> c.outcome != Status.NOT_RUN).toList();
            if (!ran.isEmpty()) {
                System.out.printf("%s %d OK · %d FAIL · %d SKIP  %s%n    Reported on: %s%n", bug.id,
                        count(ran, Status.OK), count(ran, Status.FAIL), count(ran, Status.SKIP), JBS + bug.id,
                        bug.reported);
                bug.commands.forEach(command -> System.out.println("    " + command));
            }
        }
        System.out.println("TESTS " + text);
        Platform.runLater(() -> {
            total.setText(text);
            total.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (fail > 0 ? Status.FAIL : Status.OK).color + ";");
            for (Bug bug : bugs) {
                long failed = count(bug.checks, Status.FAIL);
                bug.summary.setText(failed > 0 ? failed + " failed" : "");
                bug.summary.setStyle("-fx-text-fill: " + Status.FAIL.color + ";");
            }
            runButtons.forEach(b -> b.setDisable(false));
            stop.setDisable(true);
            running = false;
        });
        if (Boolean.getBoolean("tests.exit")) {
            System.out.flush();
            System.exit(fail > 0 ? 1 : 0);
        }
    }

    private static long count(List<Check> checks, Status status) {
        return checks.stream().filter(c -> c.outcome == status).count();
    }

    private void runCheck(Check c) {
        Platform.runLater(() -> {
            show(c, Status.RUNNING, "");
            reveal(c);
        });
        long start = System.nanoTime();
        int runs = 0, failures = 0;
        String firstFailure = null, result = null, skip = null;
        for (int i = 0; i < c.runs && !stopping && skip == null; i++) {
            runs++;
            try {
                result = c.body.run();
            } catch (Skip e) {
                skip = e.getMessage();
            } catch (Throwable e) {
                failures++;
                if (firstFailure == null) {
                    firstFailure = e instanceof Failure ? e.getMessage() : e.toString();
                }
            }
            try {
                closeStages();
            } catch (Exception e) {
                failures++;
            }
        }
        Status status = skip != null ? Status.SKIP : failures > 0 ? Status.FAIL : Status.OK;
        String text;
        if (status == Status.SKIP) {
            text = skip;
        } else if (status == Status.FAIL) {
            text = (runs > 1 ? failures + " of " + runs + " runs failed: " : "") + firstFailure;
        } else {
            text = (runs > 1 ? runs + " runs: " : "") + result;
        }
        c.outcome = status;
        System.out.printf("%-4s %s / %s: %s (%d ms)%n", status, c.bug.id, c.name, text,
                (System.nanoTime() - start) / 1_000_000);
        Platform.runLater(() -> show(c, status, text));
    }
}
