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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Stop;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The tests of the robot, windows, screens, stage, dialogs, drag and drop,
 * clipboard, render, 3D, Swing, touch, input method and accessibility labs in
 * one window, a
 * tab per lab, with a button that runs them all, one lab after the other, and
 * an overview of their OK, FAIL and SKIP results. Each lab is an application
 * of its own as well, whose main method starts this one with that lab only
 * (java DragDropLab.java, or jfx-tester --tests=dnd,stage).
 * It runs with jfx-tester, on the glass of OpenJFX, in a nested desktop of each
 * distribution, whose portals the robot goes through (see lib/approve.py). The
 * other files of this directory are compiled with it by the source launcher, so
 * it needs a JDK of 22 or later and JavaFX 27 or later:
 * <pre>./jfx-tester --desktops=gnome --headless --manual=manual/testlab/TestLab.java --timeout=3600</pre>
 * With -Dtests.run=true the tests run at startup, and with -Dtests.exit=true
 * the application then exits, with status 1 when a test failed, which
 * jfx-tester reports as the outcome of the environment:
 * <pre>./jfx-tester --desktops=gnome --headless --manual=manual/testlab/TestLab.java --timeout=3600 --jvm-args="-Dtests.run=true -Dtests.exit=true"</pre>
 * Their results are in results/RUN/logs/ENV/manual.log. -Dtests.labs=NAME,...
 * keeps some labs, by the short names of LABS (dnd, a11y, ...) or their names
 * without spaces, and -Dtests.only=NAME,... some tests, by name without spaces.
 */
public class TestLab extends Application {

    public static void main(String[] args) {
        // The robot keeps its token in user.home: the one of the session of jfx-tester, as the system tests
        String home = System.getenv("JT_HOME");
        if (home != null) {
            System.setProperty("user.home", home);
        }
        launch(args);
    }

    /**
     * Starts the test lab with one lab only, for the main method of each lab,
     * so that each runs as an application of its own, unless -Dtests.labs
     * chooses others.
     */
    static void launchLab(String id, String[] args) {
        if (System.getProperty("tests.labs") == null) {
            System.setProperty("tests.labs", id);
        }
        main(args);
    }

    /** What a lab gives the test lab: its content, stylesheet and tests (none for a playground). */
    record Parts(Region content, String stylesheet, TestKit.Suite suite) {}

    /**
     * A lab: the short name that -Dtests.labs and jfx-tester --tests take, the
     * name of its tab, and how to build it.
     */
    record LabDef(String id, String name, java.util.function.Function<Stage, Parts> factory) {}

    /** The labs, in the order of their tabs and runs. */
    static final List<LabDef> LABS = List.of(
            new LabDef("robot", "Robot", s -> {
                RobotLab l = new RobotLab();
                return new Parts(l.content(s), RobotLab.STYLESHEET, l.suite);
            }),
            new LabDef("windows", "Windows", s -> {
                WindowsLab l = new WindowsLab();
                return new Parts(l.content(s), WindowsLab.STYLESHEET, l.suite);
            }),
            new LabDef("screens", "Screens", s -> {
                ScreensLab l = new ScreensLab();
                return new Parts(l.content(s), ScreensLab.STYLESHEET, l.suite);
            }),
            new LabDef("stage", "Stage", s -> new Parts(new StageLab().content(s), StageLab.STYLESHEET, null)),
            new LabDef("dialogs", "Dialogs", s -> {
                DialogsLab l = new DialogsLab();
                return new Parts(l.content(s), DialogsLab.STYLESHEET, l.suite);
            }),
            new LabDef("dnd", "Drag and drop", s -> {
                DragDropLab l = new DragDropLab();
                return new Parts(l.content(s), DragDropLab.STYLESHEET, l.suite);
            }),
            new LabDef("clipboard", "Clipboard", s -> {
                ClipboardLab l = new ClipboardLab();
                return new Parts(l.content(s), ClipboardLab.STYLESHEET, l.suite);
            }),
            new LabDef("render", "Render", s -> {
                RenderLab l = new RenderLab();
                return new Parts(l.content(s), l.stylesheet(), l.suite);
            }),
            new LabDef("3d", "3D", s -> {
                Scene3DLab l = new Scene3DLab();
                return new Parts(l.content(s), Scene3DLab.STYLESHEET, l.suite);
            }),
            new LabDef("swing", "Swing", s -> {
                SwingLab l = new SwingLab();
                return new Parts(l.content(s), SwingLab.STYLESHEET, l.suite);
            }),
            new LabDef("touch", "Touch", s -> {
                TouchLab l = new TouchLab();
                return new Parts(l.content(s), TouchLab.STYLESHEET, l.suite);
            }),
            new LabDef("ime", "Input methods", s -> {
                ImeLab l = new ImeLab();
                return new Parts(l.content(s), ImeLab.STYLESHEET, l.suite);
            }),
            new LabDef("scale", "Scale", s -> {
                ScaleLab l = new ScaleLab();
                return new Parts(l.content(s), ScaleLab.STYLESHEET, l.suite);
            }),
            new LabDef("crash", "Crashes", s -> {
                CrashLab l = new CrashLab();
                return new Parts(l.content(s), CrashLab.STYLESHEET, l.suite);
            }),
            new LabDef("a11y", "Accessibility", s -> {
                AccessibilityLab l = new AccessibilityLab();
                return new Parts(l.content(s), AccessibilityLab.STYLESHEET, l.suite);
            }));

    /**
     * Whether -Dtests.labs keeps a lab, by its short name or its name without
     * spaces, ignoring the case; all of them without it.
     */
    private static boolean selected(LabDef lab) {
        String only = System.getProperty("tests.labs");
        if (only == null || only.isBlank()) {
            return true;
        }
        return List.of(only.split(",")).stream().map(String::strip).anyMatch(n -> n.equalsIgnoreCase(lab.id())
                || n.equalsIgnoreCase(lab.name().replace(" ", "")));
    }

    private record Lab(String name, TestKit.Suite suite, Tab tab) {}

    private final List<Lab> labs = new ArrayList<>();
    private final TabPane tabs = new TabPane();
    private final Label total = new Label("Not run");
    private Stage stage;

    @Override
    public void start(Stage stage) {
        // The reader of the cross-process clipboard check starts this application again
        if (ClipboardLab.isReader(getParameters().getUnnamed())) {
            new ClipboardLab().startReader(stage);
            return;
        }
        // The process of a reproducer of the crash lab runs it instead
        if (CrashLab.runReproducer(getParameters(), stage)) {
            return;
        }
        this.stage = stage;

        // Only the labs asked for are built, so that each runs as an application of its own
        List<LabDef> chosen = LABS.stream().filter(TestLab::selected).toList();
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("No lab " + System.getProperty("tests.labs") + ": "
                    + LABS.stream().map(LabDef::id).collect(java.util.stream.Collectors.joining(", ")));
        }
        for (LabDef lab : chosen) {
            Parts parts = lab.factory().apply(stage);
            addLab(lab.name(), parts.content(), parts.stylesheet(), parts.suite());
        }
        String title = chosen.size() == 1 ? chosen.getFirst().name() + " lab" : "Test Lab";

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane body = new BorderPane();
        body.setTop(header());
        body.setCenter(tabs);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(body, Math.min(1440, screen.getWidth() * 0.95), Math.min(940, screen.getHeight() * 0.94));
        scene.getStylesheets().add(TestKit.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
        TestKit.idle("the test lab is open: run its tests");
        TestKit.autorun(() -> run(labs));
    }

    private void addLab(String name, Region content, String stylesheet, TestKit.Suite suite) {
        // The lab keeps its own rules, on the theme of JavaFX
        StackPane holder = new StackPane(content);
        holder.getStylesheets().add(stylesheet);
        Tab tab = new Tab(name, suite == null ? holder : besideTests(holder, suite));
        tabs.getTabs().add(tab);
        // A lab without tests is a playground, left out of the overview and the runs
        if (suite != null) {
            suite.setTab(tab);
            labs.add(new Lab(name, suite, tab));
        }
    }

    private Node header() {
        Label brand = new Label(labs.size() == 1 ? labs.getFirst().name() + " lab" : "Test Lab");
        brand.getStyleClass().add("brand");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button runAll = new Button("Run all labs");
        runAll.getStyleClass().add("test-run");
        runAll.disableProperty().bind(TestKit.busyProperty());
        runAll.setOnAction(e -> run(labs));
        Button stop = TestKit.stopButton(new Button("Stop"));
        total.getStyleClass().add("test-summary");
        return new ToolBar(brand, spacer, total, runAll, stop,
                pill(!TestKit.robotInput() ? "No robot input" : TestRobot.usesLibei() ? "libei input" : "Robot input"),
                pill(System.getProperty("glass.platform", "default") + " glass"));
    }

    private static HBox pill(String text) {
        HBox box = new HBox(new Label(text));
        box.getStyleClass().add("pill");
        return box;
    }

    /**
     * A lab beside its tests and results, which stay in view when a test
     * changes the sub-tab of the lab.
     */
    private static Node besideTests(Node lab, TestKit.Suite suite) {
        Label hint = new Label("Do not use the mouse or the keyboard while the robot tests run. Manual tests pass "
                + "when the lab sees their events, or with their buttons.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        VBox results = new VBox(10, suite.view(), hint);
        results.setPadding(new Insets(10));
        results.setMinWidth(380);
        SplitPane split = new SplitPane(lab, results);
        split.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(results, false);
        return split;
    }

    /**
     * Runs the suites of the labs one after the other, each in its tab.
     */
    private void run(List<Lab> selected) {
        if (!TestKit.tryLock()) {
            return;
        }
        total.setText("Running…");
        Thread.ofPlatform().daemon().name("tests").start(() -> {
            TestKit.Result result = TestKit.Result.NONE;
            long start = System.nanoTime();
            boolean stopped = false;
            TestKit.beginRun(selected.stream().mapToInt(l -> TestKit.only(l.suite().tests).size()).sum());
            try {
                for (Lab lab : selected) {
                    TestKit.fx(() -> tabs.getSelectionModel().select(lab.tab()));
                    TestKit.pause(800);
                    result = result.plus(lab.suite().run(lab.suite().tests));
                    if (TestKit.isStopping()) {
                        break;
                    }
                }
            } catch (TestKit.Stopped e) {
                // Stopped before a lab started
            } finally {
                TestKit.endRun();
                stopped = TestKit.isStopping();
                TestKit.unlock();
            }
            String text = (stopped ? "Stopped: " : "") + result + " in "
                    + (System.nanoTime() - start) / 1_000_000 / 1000 + " s";
            System.out.println("[All] " + text);
            boolean failed = result.fail() > 0;
            TestKit.fx(() -> {
                total.setText(text);
                total.getStyleClass().removeAll("ok", "fail");
                total.getStyleClass().add(failed ? "fail" : "ok");
                stage.toFront();
            });
            TestKit.exitIfAsked(result);
        });
    }
}
