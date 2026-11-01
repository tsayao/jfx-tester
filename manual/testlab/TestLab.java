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
 * clipboard, render, 3D and Swing labs in one window, a
 * tab per lab, with a button that runs them all, one lab after the other, and
 * an overview of their OK, FAIL and SKIP results.
 * It runs with jfx-tester, on the glass of OpenJFX, in a nested desktop of each
 * distribution, whose portals the robot goes through (see lib/approve.py). The
 * other files of this directory are compiled with it by the source launcher, so
 * it needs a JDK of 22 or later and JavaFX 27 or later:
 * <pre>./jfx-tester --desktops=gnome --headless --manual=manual/testlab/TestLab.java --timeout=3600</pre>
 * With -Dtests.run=true the tests run at startup, and with -Dtests.exit=true
 * the application then exits, with status 1 when a test failed, which
 * jfx-tester reports as the outcome of the environment:
 * <pre>./jfx-tester --desktops=gnome --headless --manual=manual/testlab/TestLab.java --timeout=3600 --jvm-args="-Dtests.run=true -Dtests.exit=true"</pre>
 * Their results are in results/RUN/logs/ENV/manual.log.
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
        this.stage = stage;

        RobotLab robot = new RobotLab();
        addLab("Robot", robot.content(stage), RobotLab.STYLESHEET, robot.suite);
        WindowsLab windows = new WindowsLab();
        addLab("Windows", windows.content(stage), WindowsLab.STYLESHEET, windows.suite);
        ScreensLab screens = new ScreensLab();
        addLab("Screens", screens.content(stage), ScreensLab.STYLESHEET, screens.suite);
        StageLab stageLab = new StageLab();
        addLab("Stage", stageLab.content(stage), StageLab.STYLESHEET, null);
        DialogsLab dialogs = new DialogsLab();
        addLab("Dialogs", dialogs.content(stage), DialogsLab.STYLESHEET, dialogs.suite);
        DragDropLab dragDrop = new DragDropLab();
        addLab("Drag and drop", dragDrop.content(stage), DragDropLab.STYLESHEET, dragDrop.suite);
        ClipboardLab clipboard = new ClipboardLab();
        addLab("Clipboard", clipboard.content(stage), ClipboardLab.STYLESHEET, clipboard.suite);
        Scene3DLab scene3d = new Scene3DLab();
        RenderLab render = new RenderLab();
        addLab("Render", render.content(stage), render.stylesheet(), render.suite);
        addLab("3D", scene3d.content(stage), Scene3DLab.STYLESHEET, scene3d.suite);
        SwingLab swing = new SwingLab();
        addLab("Swing", swing.content(stage), SwingLab.STYLESHEET, swing.suite);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane body = new BorderPane();
        body.setTop(header());
        body.setCenter(tabs);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(body, Math.min(1440, screen.getWidth() * 0.95), Math.min(940, screen.getHeight() * 0.94));
        scene.getStylesheets().add(TestKit.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle("Test Lab");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
        TestKit.autorun(() -> run(labs));
    }

    private void addLab(String name, Region content, String stylesheet, TestKit.Suite suite) {
        // -Dtests.labs=clipboard,dragAndDrop keeps only those labs, by name without spaces
        String only = System.getProperty("tests.labs");
        if (only != null && List.of(only.split(",")).stream()
                .noneMatch(n -> n.equalsIgnoreCase(name.replace(" ", "")))) {
            return;
        }
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
        Label brand = new Label("Test Lab");
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
