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
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 * The bugs of the first windows of an application run in a new process of their
 * own, as their reports do, without any other window: most do not show otherwise.
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
    private static final int PROCESS_TIMEOUT = 60;
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
        if (System.getProperty("tests.process") != null) {
            Application.launch(NewProcess.class, args);
        } else {
            launch(args);
        }
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
    private Stage window;
    private final List<Stage> stages = new ArrayList<>();
    private final List<Button> runButtons = new ArrayList<>();
    private final Label total = new Label("Not run");
    private final Button stop = new Button("Stop");
    private final VBox list = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(list);
    private volatile boolean running;
    private volatile boolean stopping;

    @Override
    public void start(Stage stage) {
        window = stage;
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
                .check("Restore maximized, on the second screen", "The Test of the report: a process maximizes a "
                        + "window on the second screen and keeps its position and size, and the next one sets them "
                        + "with setMaximized(true) before show. It stays within the screen. It needs a second monitor "
                        + "smaller than the first (--monitors=1920x1080,1280x720)", 1, this::restoreMaximized);

        new Bug("JDK-8366009", "Stage does not always enter full screen when shown with fullScreen set to true",
                "jfx25. Ubuntu 24.04, X.org and Wayland, intermittent. Works on Ubuntu 22.04")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .check("Shown in full screen", "HelloFullscreen, in a new process: setFullScreen(true), setScene, "
                        + "show", REPEAT, () -> inNewProcess("fullscreen"));

        new Bug("JDK-8367893", "StageFocusTest fails on some Linux systems",
                "jfx26. Ubuntu 24.04, Wayland (XWayland); also Ubuntu 22.04, X.org, 2 of 3 runs")
                .on(GNOME_WAYLAND, 0)
                .on(UBUNTU_22_XORG, 0)
                .check("Focus after show, primary stage", "StageFocusTest, in a new process: an undecorated stage is "
                        + "focused when shown, on top, and gets the keys of the Robot", REPEAT,
                        () -> robotInNewProcess("focus"))
                .check("Focus after show, new stage", "As above, with a new stage after the primary one was shown and "
                        + "hidden, the second run of the test in the report", REPEAT,
                        () -> robotInNewProcess("focus-new-stage"));

        new Bug("JDK-8321624", "DualWindowTest fails intermittently on Linux",
                "jfx22. Linux, in automated runs; reproduced on an Ubuntu 22.04 VM, once in 5 to 10 runs")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .on(UBUNTU_22_WAYLAND, 20)
                .on(UBUNTU_22_XORG, 20)
                .check("Two stages moved when shown", "DualWindowTest, in a new process: both stages get their "
                        + "position in a Platform.runLater from WINDOW_SHOWN", REPEAT, () -> inNewProcess("dual"));

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
                .check("Centered when shown", "PositionTest, in a new process: a 640 × 480 scene, with no position. It "
                        + "shows where Stage.centerOnScreen() puts it", REPEAT, () -> inNewProcess("position"));

        new Bug("JDK-8316891", "Intermittent test failure in IconifyTest.canIconifyDecoratedStage",
                "jfx21. Linux, in nightly test runs, no distribution given")
                .on(GNOME_WAYLAND, 20)
                .on(GNOME_XORG, 20)
                .check("Iconify a decorated stage", "IconifyTest.canIconifyDecoratedStage, in a new process: a "
                        + "decorated stage always on top of an undecorated one. Iconified, the one below shows on the "
                        + "screen, and deiconified it is above it again", REPEAT, () -> robotInNewProcess("iconify"));

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
        Path state = Files.createTempFile("jfx-tester-window", ".state");
        try {
            String property = "-Dtests.state=" + state;
            inNewProcess("maximize", property);
            return inNewProcess("restore", property);
        } finally {
            Files.deleteIfExists(state);
        }
    }

    private String robotInNewProcess(String name) throws Exception {
        requireRobot();
        return inNewProcess(name);
    }

    /** Runs a test in a new process, with no window of this one on the desktop, and gives its result. */
    private String inNewProcess(String name, String... properties) throws Exception {
        boolean hidden = fx(() -> {
            if (!window.isShowing()) {
                return false;
            }
            Platform.setImplicitExit(false);
            window.hide();
            return true;
        });
        if (hidden) {
            Thread.sleep(300);
        }
        ProcessHandle.Info self = ProcessHandle.current().info();
        List<String> command = new ArrayList<>();
        command.add(self.command().orElseThrow());
        command.add("-Dtests.process=" + name);
        command.addAll(List.of(properties));
        command.addAll(List.of(self.arguments().orElseThrow()));
        Path output = Files.createTempFile("jfx-tester-process", ".out");
        try {
            Process process = new ProcessBuilder(command).redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new Failure("The process did not end in " + PROCESS_TIMEOUT + " s");
            }
            for (String line : Files.readAllLines(output)) {
                if (line.startsWith(NewProcess.OK)) {
                    return line.substring(NewProcess.OK.length());
                }
                if (line.startsWith(NewProcess.FAIL)) {
                    throw new Failure(line.substring(NewProcess.FAIL.length()));
                }
            }
            throw new Failure("The process ended with " + process.exitValue() + ", without a result");
        } finally {
            Files.deleteIfExists(output);
        }
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
        // The problem, as the bug report gives it, before the runs of the check
        System.out.printf("%nTEST %s / %s%n  Problem: %s%n  Seen on: %s%n  Checks:  %s%n", c.bug.id, c.name,
                c.bug.title, c.bug.reported, c.steps);
        long start = System.nanoTime();
        int runs = 0, failures = 0;
        String firstFailure = null, result = null, skip = null;
        for (int i = 0; i < c.runs && !stopping && skip == null; i++) {
            runs++;
            System.out.printf("RUN  %s / %s: %d of %d%n", c.bug.id, c.name, runs, c.runs);
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
        try {
            fx(() -> {
                if (!window.isShowing()) {
                    window.show();
                    Platform.setImplicitExit(true);
                }
                return null;
            });
        } catch (Exception e) {
            failures++;
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

    /**
     * A test as a process of its own, whose first windows are the ones of the report. It prints its result, one line
     * that starts with OK or FAIL.
     */
    public static final class NewProcess extends Application {
        static final String OK = "OK: ";
        static final String FAIL = "FAIL: ";

        @Override
        public void start(Stage stage) throws Exception {
            later(PROCESS_TIMEOUT * 1000 - 10000, () -> fail("Timed out"));
            Platform.setImplicitExit(false);
            switch (System.getProperty("tests.process")) {
                case "maximize" -> maximize(stage);
                case "restore" -> restore(stage);
                case "fullscreen" -> fullscreen(stage);
                case "focus" -> focus(stage);
                case "focus-new-stage" -> focusNewStage(stage);
                case "dual" -> dual(stage);
                case "position" -> centered(stage);
                case "iconify" -> iconify();
                default -> fail("Unknown test");
            }
        }

        private static void ok(String result) {
            end(OK + result);
        }

        private static void fail(String result) {
            end(FAIL + result);
        }

        private static void end(String line) {
            System.out.println(line);
            System.out.flush();
            Runtime.getRuntime().halt(0);
        }

        private static void later(long millis, Runnable action) {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    return;
                }
                Platform.runLater(action);
            });
            thread.setDaemon(true);
            thread.start();
        }

        /** Ends the test when there is no problem for a second, or with the problem it has after some seconds. */
        private static void settle(Supplier<String> problem, Runnable then) {
            settle(problem, then, System.currentTimeMillis() + WAIT, 0);
        }

        private static void settle(Supplier<String> problem, Runnable then, long deadline, long since) {
            later(50, () -> {
                long now = System.currentTimeMillis();
                String wrong = problem.get();
                if (wrong != null) {
                    if (now > deadline) {
                        fail(wrong);
                    }
                    settle(problem, then, deadline, 0);
                } else if (since > 0 && now - since >= HOLD) {
                    then.run();
                } else {
                    settle(problem, then, deadline, since > 0 ? since : now);
                }
            });
        }

        /** The steps of the Test of JDK-8365201: the first run */
        private static void maximize(Stage stage) {
            Rectangle2D second = Screen.getScreens().get(1).getBounds();
            stage.setScene(new Scene(new Pane(), 400, 300));
            stage.setX(second.getMinX() + 60);
            stage.setY(second.getMinY() + 60);
            stage.show();
            settle(() -> stage.getX() >= second.getMinX() ? null : "it is at " + position(stage) + ", not on "
                    + bounds(second), () -> {
                stage.setMaximized(true);
                settle(() -> stage.isMaximized() ? null : "isMaximized is false", () -> {
                    try {
                        Files.writeString(Path.of(System.getProperty("tests.state")), String.format(Locale.ROOT,
                                "%f %f %f %f", stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()));
                    } catch (IOException e) {
                        fail(e.toString());
                    }
                    ok("maximized at " + position(stage));
                });
            });
        }

        /** The next run of the Test of JDK-8365201, which restores the state before setScene and show */
        private static void restore(Stage stage) throws IOException {
            Rectangle2D second = Screen.getScreens().get(1).getBounds();
            Scanner saved = new Scanner(Files.readString(Path.of(System.getProperty("tests.state")))).useLocale(Locale.ROOT);
            stage.setX(saved.nextDouble());
            stage.setY(saved.nextDouble());
            stage.setWidth(saved.nextDouble());
            stage.setHeight(saved.nextDouble());
            stage.setMaximized(true);
            stage.setScene(new Scene(new Pane()));
            stage.show();
            settle(() -> {
                if (!stage.isMaximized()) {
                    return "isMaximized is false";
                }
                boolean inside = stage.getX() >= second.getMinX() - 1 && stage.getY() >= second.getMinY() - 1
                        && stage.getX() + stage.getWidth() <= second.getMaxX() + 1
                        && stage.getY() + stage.getHeight() <= second.getMaxY() + 1;
                return inside ? null : "the window is " + size(stage) + " at " + position(stage) + ", past the screen "
                        + bounds(second);
            }, () -> ok(size(stage) + " at " + position(stage)));
        }

        /** HelloFullscreen, of JDK-8366009 */
        private static void fullscreen(Stage stage) {
            stage.setFullScreen(true);
            stage.setScene(new Scene(new Group(), 600, 450, Color.LIGHTGREEN));
            stage.show();
            settle(() -> notFullScreen(stage), () -> ok(sceneSize(stage)));
        }

        /** StageFocusTest of JDK-8367893, with the primary stage */
        private static void focus(Stage stage) {
            boolean[] pressed = {false};
            Scene scene = new Scene(new Group(), 200, 200, Color.LIGHTGREEN);
            scene.setOnKeyPressed(e -> pressed[0] |= e.getCode() == KeyCode.A);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setScene(scene);
            stage.setX(100);
            stage.setY(100);
            stage.show();
            Platform.runLater(() -> {
                if (!stage.isFocused()) {
                    fail("isFocused is false after show");
                }
                later(500, () -> {
                    Robot robot = new Robot();
                    Color c = robot.getPixelColor(200, 200);
                    if (!near(c.getRed(), Color.LIGHTGREEN.getRed(), 0.07)
                            || !near(c.getGreen(), Color.LIGHTGREEN.getGreen(), 0.07)) {
                        fail("Not on top: the pixel is " + c);
                    }
                    robot.keyPress(KeyCode.A);
                    robot.keyRelease(KeyCode.A);
                    later(2000, () -> {
                        if (pressed[0]) {
                            ok("focused, on top, key received");
                        }
                        fail("The stage did not receive the key of the Robot: focused " + stage.isFocused());
                    });
                });
            });
        }

        /** StageFocusTest of JDK-8367893, whose second run takes a new stage after the primary one was hidden */
        private static void focusNewStage(Stage primary) {
            primary.initStyle(StageStyle.UNDECORATED);
            primary.setScene(new Scene(new Group(), 200, 200, Color.LIGHTGREEN));
            primary.setX(100);
            primary.setY(100);
            primary.show();
            later(1000, () -> {
                primary.hide();
                focus(new Stage());
            });
        }

        /** DualWindowTest, of JDK-8321624 */
        private static void dual(Stage first) {
            Stage second = new Stage();
            double[][] at = {{100, 100}, {400, 100}};
            Stage[] two = {first, second};
            for (int i = 0; i < 2; i++) {
                Stage st = two[i];
                double x = at[i][0], y = at[i][1];
                st.setScene(new Scene(new Pane(), 200, 200));
                st.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> Platform.runLater(() -> {
                    st.setX(x);
                    st.setY(y);
                }));
                st.show();
            }
            settle(() -> {
                for (int i = 0; i < 2; i++) {
                    if (!near(two[i].getX(), at[i][0], 1) || !near(two[i].getY(), at[i][1], 1)) {
                        return "stage " + (i + 1) + " is at " + position(two[i]) + ", not at " + num(at[i][0]) + ", "
                                + num(at[i][1]);
                    }
                }
                return null;
            }, () -> ok("at " + position(first) + " and " + position(second)));
        }

        /** PositionTest, attached to JDK-8337400 */
        private static void centered(Stage stage) {
            stage.setScene(new Scene(new Pane(), 640, 480));
            stage.show();
            settle(() -> {
                Rectangle2D v = Screen.getPrimary().getVisualBounds();
                // What Window.centerOnScreen() does
                double x = v.getMinX() + (v.getWidth() - stage.getWidth()) / 2;
                double y = v.getMinY() + (v.getHeight() - stage.getHeight()) / 3;
                return near(stage.getX(), x, BOUNDS_DELTA) && near(stage.getY(), y, BOUNDS_DELTA) ? null
                        : "it is at " + position(stage) + ", centered is about " + num(x) + ", " + num(y);
            }, () -> ok("at " + position(stage)));
        }

        /** IconifyTest.canIconifyDecoratedStage, of JDK-8316891, where the primary stage does not show */
        private static void iconify() {
            Stage bottom = new Stage(StageStyle.UNDECORATED);
            bottom.setScene(new Scene(new Pane(), 300, 300, Color.LIME));
            bottom.setX(0);
            bottom.setY(0);
            bottom.show();
            Stage top = new Stage(StageStyle.DECORATED);
            top.setAlwaysOnTop(true);
            top.setScene(new Scene(new Pane(), 300, 300, Color.RED));
            top.setX(0);
            top.setY(0);
            top.show();
            later(1000, () -> {
                Robot robot = new Robot();
                expect(robot, Color.RED, "with the stage above shown");
                top.setIconified(true);
                later(1000, () -> {
                    if (!top.isIconified()) {
                        fail("isIconified is false");
                    }
                    expect(robot, Color.LIME, "with the stage above iconified");
                    top.setIconified(false);
                    later(1000, () -> {
                        if (top.isIconified()) {
                            fail("isIconified is true");
                        }
                        expect(robot, Color.RED, "with the stage above deiconified");
                        ok("iconified and deiconified");
                    });
                });
            });
        }

        private static void expect(Robot robot, Color expected, String when) {
            Color c = robot.getPixelColor(100, 100);
            if (!near(c.getRed(), expected.getRed(), 0.07) || !near(c.getGreen(), expected.getGreen(), 0.07)
                    || !near(c.getBlue(), expected.getBlue(), 0.07)) {
                fail("The pixel is " + c + " " + when + ", expected " + expected);
            }
        }
    }
}
