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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.InputMethodRequests;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The bugs of the JBS that crashed JavaFX on Linux, each run again as it was
 * reported, so that they do not come back. A crash ends the JVM, so each test
 * runs its reproducer in a process of its own, this application started again
 * with --crash=NAME: the test passes when the process ends as it should, and
 * fails when it reports a fatal error (SIGSEGV, SIGABRT), is killed by a
 * signal or hangs. The reproducers that need input, such as a key that
 * closes a window, get it from the robot of this process, at the points the
 * reproducer prints.
 * <p>
 * The process prints, for this lab:
 * <ul>
 * <li>{@code CRASHLAB POINT name x y}, a point on the screen to click;</li>
 * <li>{@code CRASHLAB READY}, when it waits for the input of the test;</li>
 * <li>{@code CRASHLAB DONE}, when the reproducer ended as it should.</li>
 * </ul>
 */
public class CrashLab {

    /**
     * Runs this lab as an application of its own, with its tests:
     * <pre>java CrashLab.java</pre>
     * or {@code ./jfx-tester --tests=crash}.
     */
    public static void main(String[] args) {
        TestLab.launchLab("crash", args);
    }

    private static final String CSS = TestKit.BASE_CSS + """
        .crash-log { -fx-font-family: "monospaced"; -fx-font-size: 11px; }
        """;

    static final String STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(CSS.getBytes(StandardCharsets.UTF_8));

    private static final String PREFIX = "CRASHLAB ";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int LOG_SIZE = 500;

    final TestKit.Suite suite = new TestKit.Suite("Crashes");
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final ListView<String> logView = new ListView<>(log);

    // ---------------------------------------------------------------------
    // The lab, in the process of the tests

    Region content(Stage stage) {
        logView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                setWrapText(true);
                setPrefWidth(0);
                getStyleClass().add("crash-log");
            }
        });
        VBox.setVgrow(logView, Priority.ALWAYS);
        Label hint = new Label("Each test runs a reproducer of a bug of the JBS that crashed JavaFX on Linux, in a "
                + "process of its own, and fails when that process crashes, is killed by a signal or hangs. The "
                + "log shows the output of each process.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        Button clear = new Button("Clear");
        clear.setOnAction(e -> log.clear());
        VBox box = new VBox(10, hint, logView, clear);
        box.getStyleClass().add("glass");
        VBox.setVgrow(box, Priority.ALWAYS);
        VBox root = new VBox(box);
        root.setPadding(new Insets(4, 18, 18, 18));
        defineTests();
        suite.setBeforeRun(() -> {
            stage.toFront();
            stage.requestFocus();
        });
        return root;
    }

    private void defineTests() {
        crash("IME control focused before shown", "JDK-8344102", "A control with input method requests takes the "
                + "focus in its skin, before the window shows: SIGSEGV in gdk_x11_window_get_xid", null);
        crash("Escape closes the stage", "JDK-8301219", "A key handler closes the stage and keeps working, on "
                + "Escape and then on Enter", p -> {
            p.click("field");
            TestKit.shortcut(KeyCode.ESCAPE);
        });
        crash("Key closes a dialog of start", "JDK-8272617", "A TextInputDialog shown with showAndWait in start, "
                + "closed with Enter, then another with Escape", p -> {
            p.click("dialog");
            TestKit.shortcut(KeyCode.ENTER);
            p.await("dialog2");
            p.click("dialog2");
            TestKit.shortcut(KeyCode.ESCAPE);
        });
        crash("Default button hides its stage", "JDK-8124741", "Enter fires the default button, whose action hides "
                + "its stage, before the key is released", p -> {
            p.click("label");
            TestKit.shortcut(KeyCode.ENTER);
        });
        crash("Owner and modal child closed together", "JDK-8118208", "An action closes a stage and then its "
                + "window-modal child, and the other way round", null);
        crash("Exit with dialogs shown", "JDK-8236680", "Platform.exit while an undecorated stage, its dialog and "
                + "another dialog show", null);
        crash("Stage hidden with its popup", "JDK-8118804", "Hides a stage while its popup shows, and shows it again",
                null);
        crash("Unicode text", "JDK-8246348, JDK-8248922, JDK-8252338", "A Text of the first 10000 code points, "
                + "control characters and NUL included: pango_itemize asserted", null);
        crash("Emoji with joiners", "JDK-8249964, JDK-8234473", "Thirty Text nodes of emoji joined with ZWJ and "
                + "skin tones, and a text field with them: junk, then SIGSEGV in libpango", null);
        crash("Bad clipboard data", "JDK-8269630", "A List as PLAIN_TEXT in the clipboard: NegativeArraySizeException "
                + "and a fatal error in the main loop", null);
        crash("Custom clipboard format", "JDK-8127504", "A string of a custom DataFormat put in the clipboard and "
                + "read back", null);
        crash("Scene changed again and again", "JDK-8114905", "Changes the scene of a stage 60 times, between "
                + "scenes of many nodes", null);
        crash("Pannable scroll pane", "JDK-8094007", "Pans a scroll pane in circles: glDeleteTextures", p -> {
            Point2D c = p.point("pane");
            TestKit.dragPress(c, c.add(40, 0));
            for (int i = 0; i <= 36; i++) {
                double a = Math.toRadians(i * 20);
                TestKit.move(c.add(60 * Math.cos(a), 60 * Math.sin(a)));
                TestKit.pause(25);
            }
            TestKit.dragRelease(c);
        });
        crash("Choice box selection", "JDK-8119216", "Picks an item of a choice box with the mouse", p -> {
            p.click("choice");
            TestKit.pause(500);
            TestKit.shortcut(KeyCode.DOWN);
            TestKit.shortcut(KeyCode.ENTER);
        });
        crash("Tooltips shown and hidden", "JDK-8119176", "Shows and hides a tooltip 40 times: GLXBadDrawable on the "
                + "es2 pipeline", null);
        crash("Window states", "JDK-8094223, JDK-8123684", "Iconifies, maximizes, puts in full screen and back, "
                + "fast: process_net_wm_property", null);
        crash("Resized while animating", "JDK-8339523, JDK-8382883", "Resizes an animated window 150 times: "
                + "swapBuffers and makeCurrent", null);
        crash("Stages closed at exit", "JDK-8116818, JDK-8125397", "Platform.exit with five stages showing, one "
                + "of them owned: getNativeWindowImpl and XQueryExtension at exit", null);
        crash("Localized extension filter", "JDK-8095277", "A file chooser with a filter named in Cyrillic, "
                + "closed with Escape", p -> {
            TestKit.pause(1500);
            for (int i = 0; i < 3 && p.alive(); i++) {
                TestKit.shortcut(KeyCode.ESCAPE);
                TestKit.pause(1000);
            }
            if (p.alive()) {
                p.kill();
                TestKit.skip("the file chooser did not close with Escape");
            }
        });
        crash("Start without a display", "JDK-8116996", "Starts with no DISPLAY nor WAYLAND_DISPLAY: JavaFX must "
                + "fail to start, without a fatal error", null);
    }

    /**
     * A test that runs a reproducer in a process of its own: an automatic one,
     * or a robot one with input.
     */
    private void crash(String name, String bugs, String description, Consumer<Child> input) {
        TestKit.Step body = () -> {
            Child p = new Child(id(name), name.equals("Start without a display"));
            try {
                if (input != null) {
                    p.await("READY");
                    input.accept(p);
                }
                return p.verdict();
            } finally {
                p.kill();
            }
        };
        String text = bugs + ": " + description;
        if (input == null) {
            suite.auto(name, text, body);
        } else {
            suite.robot(name, text, body);
        }
    }

    private static String id(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    /**
     * The process of a reproducer: this application again, with --crash, and
     * what it prints.
     */
    private final class Child {
        private final String id;
        private final boolean noDisplay;
        private final Process process;
        private final List<String> output = new ArrayList<>();
        private final Map<String, Point2D> points = new HashMap<>();
        private final Path dir;

        Child(String id, boolean noDisplay) throws IOException {
            this.id = id;
            this.noDisplay = noDisplay;
            dir = Files.createTempDirectory("jfx-crashlab");
            List<String> command = command(id, dir);
            if (command == null) {
                TestKit.skip("cannot tell the command line of this process");
            }
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.directory(Path.of(System.getProperty("user.dir")).toFile());
            if (noDisplay) {
                builder.environment().remove("DISPLAY");
                builder.environment().remove("WAYLAND_DISPLAY");
            }
            log("== " + id);
            process = builder.start();
            Thread reader = new Thread(this::read, "crashlab-" + id);
            reader.setDaemon(true);
            reader.start();
        }

        private void read() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(),
                    StandardCharsets.UTF_8))) {
                for (String line; (line = in.readLine()) != null; ) {
                    synchronized (output) {
                        output.add(line);
                        if (line.startsWith(PREFIX + "POINT ")) {
                            String[] f = line.split(" ");
                            points.put(f[2], new Point2D(Double.parseDouble(f[3]), Double.parseDouble(f[4])));
                        }
                        output.notifyAll();
                    }
                    String shown = line;
                    Platform.runLater(() -> log(shown));
                }
            } catch (IOException e) {
                // The process ended
            }
        }

        /** Waits for a line READY, or for a point, by its name. */
        void await(String what) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            synchronized (output) {
                while (!(what.equals("READY") ? output.contains(PREFIX + "READY") : points.containsKey(what))) {
                    long left = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());
                    if (left <= 0 || !process.isAlive() && !what.equals("READY")) {
                        break;
                    }
                    try {
                        output.wait(Math.min(left, 200));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!process.isAlive() && what.equals("READY") && !output.contains(PREFIX + "READY")) {
                        break;
                    }
                }
            }
            TestKit.pause(300);
        }

        Point2D point(String name) {
            await(name);
            synchronized (output) {
                Point2D p = points.get(name);
                if (p == null) {
                    throw new AssertionError("the reproducer did not show " + name + verdictOfEnd());
                }
                return p;
            }
        }

        void click(String name) {
            TestKit.click(point(name));
            TestKit.pause(200);
        }

        boolean alive() {
            return process.isAlive();
        }

        void kill() {
            process.destroyForcibly();
        }

        /**
         * How the process ended: a fatal error, a signal, a hang, or as the
         * reproducer expects.
         */
        String verdict() throws InterruptedException {
            if (!process.waitFor(40, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("hung: the reproducer did not end in 40 s" + lastLines());
            }
            process.waitFor(2, TimeUnit.SECONDS);
            TestKit.pause(200);
            int status = process.exitValue();
            String fatal = fatal();
            if (fatal != null) {
                throw new AssertionError("crashed: " + fatal);
            }
            if (status > 128) {
                throw new AssertionError("killed by signal " + (status - 128) + lastLines());
            }
            boolean done;
            synchronized (output) {
                done = output.contains(PREFIX + "DONE");
            }
            if (noDisplay) {
                // JavaFX cannot start: it must say so, with an exception and not a crash
                check(status != 0 || done, "started without a display?");
                return "failed to start, without a fatal error (status " + status + ")";
            }
            check(done && status == 0, "the reproducer ended with status " + status
                    + (done ? "" : ", before its end") + lastLines());
            return "no crash";
        }

        private String verdictOfEnd() {
            if (process.isAlive()) {
                return lastLines();
            }
            String fatal = fatal();
            return fatal != null ? ": it crashed: " + fatal : ": it ended with status " + process.exitValue()
                    + lastLines();
        }

        /** The problematic frame of a fatal error of the JVM, or of a GLib abort, or null. */
        private String fatal() {
            synchronized (output) {
                int i = -1;
                for (int k = 0; k < output.size(); k++) {
                    String l = output.get(k);
                    if (l.contains("A fatal error has been detected") || l.contains("** ERROR **")
                            || l.contains("Bail out!")) {
                        i = k;
                        break;
                    }
                }
                if (i < 0) {
                    return null;
                }
                String signal = "", frame = "";
                for (int k = i; k < Math.min(output.size(), i + 30); k++) {
                    String l = output.get(k).replace("#", "").strip();
                    if (l.startsWith("SIG") && signal.isEmpty()) {
                        signal = l;
                    }
                    if (output.get(k).contains("Problematic frame")) {
                        frame = k + 1 < output.size() ? output.get(k + 1).replace("#", "").strip() : "";
                    }
                    if (l.contains("** ERROR **") || l.contains("Bail out!")) {
                        signal = l;
                    }
                }
                return (signal + (frame.isEmpty() ? "" : ", in " + frame)).strip();
            }
        }

        private String lastLines() {
            synchronized (output) {
                List<String> tail = output.stream().filter(l -> !l.startsWith(PREFIX)).toList();
                tail = tail.subList(Math.max(0, tail.size() - 4), tail.size());
                return tail.isEmpty() ? "" : ": " + String.join(" | ", tail);
            }
        }
    }

    /**
     * The command line of this process, with the reproducer as a named
     * parameter, its error file in a directory of its own and no core dump.
     */
    private static List<String> command(String id, Path dir) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (info.command().isEmpty() || info.arguments().isEmpty()) {
            return null;
        }
        List<String> command = new ArrayList<>();
        command.add(info.command().get());
        command.add("-XX:ErrorFile=" + dir.resolve("hs_err_pid%p.log"));
        command.add("-XX:-CreateCoredumpOnCrash");
        // Not the options that run the tests
        Arrays.stream(info.arguments().get()).filter(a -> !a.startsWith("-Dtests.")).forEach(command::add);
        command.add("--crash=" + id);
        return command;
    }

    private void log(String text) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> log(text));
            return;
        }
        log.add(LocalTime.now().format(CLOCK) + "  " + text);
        if (log.size() > LOG_SIZE) {
            log.remove(0, log.size() - LOG_SIZE);
        }
        logView.scrollTo(log.size() - 1);
        System.out.println("[crash] " + text);
    }

    private static void check(boolean condition, String message) {
        TestKit.check(condition, message);
    }

    // ---------------------------------------------------------------------
    // The reproducers, in their own process

    /**
     * Runs the reproducer that --crash names, instead of the test lab.
     *
     * @return whether there was one
     */
    static boolean runReproducer(Application.Parameters parameters, Stage stage) {
        String id = parameters.getNamed().get("crash");
        if (id == null) {
            return false;
        }
        // A reproducer that hangs is ended, which the lab reports
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(35_000);
            } catch (InterruptedException e) {
                return;
            }
            System.out.println("The reproducer hung");
            System.exit(3);
        }, "crashlab-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        Reproducers r = new Reproducers(stage);
        switch (id) {
            case "ime-control-focused-before-shown" -> r.imeFocusBeforeShown();
            case "escape-closes-the-stage" -> r.escapeCloses();
            case "key-closes-a-dialog-of-start" -> r.dialogOfStart();
            case "default-button-hides-its-stage" -> r.defaultButtonHides();
            case "owner-and-modal-child-closed-together" -> r.ownerAndChildClosed();
            case "exit-with-dialogs-shown" -> r.exitWithDialogs();
            case "stage-hidden-with-its-popup" -> r.stageHiddenWithPopup();
            case "unicode-text" -> r.unicodeText();
            case "emoji-with-joiners" -> r.emojiWithJoiners();
            case "bad-clipboard-data" -> r.badClipboardData();
            case "custom-clipboard-format" -> r.customClipboardFormat();
            case "scene-changed-again-and-again" -> r.sceneChanges();
            case "pannable-scroll-pane" -> r.pannableScrollPane();
            case "choice-box-selection" -> r.choiceBox();
            case "tooltips-shown-and-hidden" -> r.tooltips();
            case "window-states" -> r.windowStates();
            case "resized-while-animating" -> r.resizeWhileAnimating();
            case "stages-closed-at-exit" -> r.stagesAtExit();
            case "localized-extension-filter" -> r.localizedFilter();
            // Starting is the test: it does not get here
            case "start-without-a-display" -> r.done();
            default -> {
                System.out.println("No reproducer " + id);
                System.exit(2);
            }
        }
        return true;
    }

    /** The reproducers, each after the code of its report. */
    private static final class Reproducers {
        private final Stage stage;

        Reproducers(Stage stage) {
            this.stage = stage;
            Platform.setImplicitExit(false);
        }

        private static void print(String text) {
            System.out.println(PREFIX + text);
            System.out.flush();
        }

        private static void point(String name, Node node) {
            Bounds b = node.localToScreen(node.getLayoutBounds());
            print(String.format(Locale.ROOT, "POINT %s %.1f %.1f", name, b.getCenterX(), b.getCenterY()));
        }

        private static void ready() {
            print("READY");
        }

        /** The end, as the reproducer expects: DONE, and the JVM exits. */
        void done() {
            print("DONE");
            Platform.exit();
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                System.out.println("The JavaFX toolkit did not exit");
                System.exit(4);
            });
            t.setDaemon(true);
            t.start();
        }

        /** Runs an action after some pulses, so that the scene is drawn first. */
        private static void later(double ms, Runnable action) {
            Timeline t = new Timeline(new KeyFrame(Duration.millis(ms), e -> action.run()));
            t.play();
        }

        private void show(Region root, double w, double h) {
            stage.setScene(new Scene(root, w, h));
            stage.setTitle("Crash reproducer");
            stage.show();
            stage.toFront();
            stage.requestFocus();
        }

        // JDK-8344102
        void imeFocusBeforeShown() {
            Control control = new Control() {
                @Override
                protected Skin<?> createDefaultSkin() {
                    return new SkinBase<>(this) {
                        {
                            Label label = new Label("Composed text: ");
                            getSkinnable().setInputMethodRequests(new InputMethodRequests() {
                                @Override
                                public Point2D getTextLocation(int offset) {
                                    return null;
                                }

                                @Override
                                public int getLocationOffset(int x, int y) {
                                    return 0;
                                }

                                @Override
                                public void cancelLatestCommittedText() {
                                }

                                @Override
                                public String getSelectedText() {
                                    return null;
                                }
                            });
                            getSkinnable().setOnInputMethodTextChanged(e -> label.setText(label.getText()
                                    + e.getCommitted()));
                            getChildren().add(label);
                            // The focus before the window shows
                            getSkinnable().requestFocus();
                        }
                    };
                }
            };
            show(new StackPane(control), 400, 300);
            later(1000, this::done);
        }

        // JDK-8301219
        void escapeCloses() {
            TextField field = new TextField();
            BorderPane root = new BorderPane(field);
            root.setOnKeyPressed(e -> {
                stage.close();
                try {
                    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                } catch (javax.xml.parsers.ParserConfigurationException ex) {
                    ex.printStackTrace();
                }
                later(800, this::done);
            });
            show(root, 300, 120);
            later(500, () -> {
                point("field", field);
                ready();
            });
        }

        // JDK-8272617
        void dialogOfStart() {
            // Many nodes first, which made it crash more often
            FlowPane many = new FlowPane();
            for (int i = 0; i < 400; i++) {
                many.getChildren().add(new Button("Button " + i));
            }
            show(new ScrollPane(many), 500, 400);
            for (String name : List.of("dialog", "dialog2")) {
                TextInputDialog dialog = new TextInputDialog("text");
                dialog.initOwner(stage);
                dialog.setOnShown(e -> later(400, () -> {
                    point(name, dialog.getDialogPane().getContent() != null
                            ? dialog.getDialogPane().getContent() : dialog.getDialogPane());
                    if (name.equals("dialog")) {
                        ready();
                    }
                }));
                // showAndWait in start, as the report
                dialog.showAndWait();
            }
            later(500, this::done);
        }

        // JDK-8124741
        void defaultButtonHides() {
            Button button = new Button("Default");
            button.setDefaultButton(true);
            Label label = new Label("Press Enter");
            button.setOnAction(e -> {
                stage.hide();
                later(800, this::done);
            });
            show(new VBox(10, label, button), 300, 120);
            later(500, () -> {
                point("label", label);
                ready();
            });
        }

        // JDK-8118208
        void ownerAndChildClosed() {
            show(new StackPane(new Label("Owner")), 300, 200);
            Runnable[] round = new Runnable[1];
            int[] n = {0};
            round[0] = () -> {
                Stage owner = n[0] == 0 ? stage : new Stage();
                if (owner != stage) {
                    owner.setScene(new Scene(new StackPane(new Label("Owner " + n[0])), 300, 200));
                    owner.show();
                }
                Stage child = new Stage();
                child.initModality(Modality.WINDOW_MODAL);
                child.initOwner(owner);
                child.setScene(new Scene(new StackPane(new Button("Hide")), 200, 100));
                child.show();
                boolean ownerFirst = n[0] % 2 == 0;
                later(400, () -> {
                    if (ownerFirst) {
                        owner.close();
                        child.close();
                    } else {
                        child.close();
                        owner.close();
                    }
                    if (++n[0] < 6) {
                        later(300, round[0]);
                    } else {
                        later(500, this::done);
                    }
                });
            };
            later(500, round[0]);
        }

        // JDK-8236680
        void exitWithDialogs() {
            stage.initStyle(StageStyle.UNDECORATED);
            show(new StackPane(new Button("Open Dialogs")), 200, 200);
            List<Dialog<ButtonType>> dialogs = new ArrayList<>();
            for (boolean withOwner : new boolean[] {true, false}) {
                Dialog<ButtonType> d = new Dialog<>();
                d.setTitle("Multi-line Dialog");
                d.setContentText("This\nis\na\ntest\nmulti\nline\nblah");
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                if (withOwner) {
                    d.initOwner(stage);
                }
                dialogs.add(d);
            }
            later(500, () -> {
                dialogs.forEach(Dialog::show);
                later(500, () -> {
                    stage.hide();
                    print("DONE");
                    // The exit is the test: the JVM must end, with status 0
                    Platform.exit();
                });
            });
        }

        // JDK-8118804
        void stageHiddenWithPopup() {
            Popup popup = new Popup();
            popup.setAutoFix(true);
            popup.setAutoHide(false);
            popup.getContent().add(new StackPane(new Circle(50, Color.AQUAMARINE), new Text("Popup!!")));
            show(new VBox(new Label("Parent")), 300, 300);
            later(400, () -> {
                popup.show(stage, stage.getX() + 100, stage.getY() + 100);
                later(400, () -> {
                    stage.hide();
                    later(1500, () -> {
                        stage.show();
                        later(500, () -> {
                            popup.hide();
                            later(300, this::done);
                        });
                    });
                });
            });
        }

        // JDK-8246348, JDK-8248922, JDK-8252338
        void unicodeText() {
            StringBuilder b = new StringBuilder();
            for (int c = 0; c < 10000; c++) {
                b.appendCodePoint(c);
            }
            Text text = new Text(b.toString());
            text.setWrappingWidth(780);
            show(new ScrollPane(new VBox(10, text, new TextField(b.substring(0, 2000)))), 800, 600);
            later(1500, this::done);
        }

        // JDK-8249964, JDK-8234473
        void emojiWithJoiners() {
            String line = new StringBuilder("(").appendCodePoint(0x1F469).appendCodePoint(0x1F3FD).append('‍')
                    .appendCodePoint(0x1F91D).append('‍').appendCodePoint(0x1F468).appendCodePoint(0x1F3FB)
                    .append(")\n").toString();
            TextFlow flow = new TextFlow();
            for (int i = 0; i < 30; i++) {
                flow.getChildren().add(new Text(line));
            }
            TextField field = new TextField(line.strip() + " " + new StringBuilder().appendCodePoint(0x1F4A5));
            show(new BorderPane(new ScrollPane(flow), field, null, null, null), 400, 500);
            later(500, () -> {
                field.requestFocus();
                field.end();
                field.appendText(line.strip());
            });
            later(1500, this::done);
        }

        // JDK-8269630
        void badClipboardData() {
            show(new StackPane(new Label("Clipboard")), 300, 100);
            later(300, () -> {
                try {
                    ClipboardContent content = new ClipboardContent();
                    content.put(DataFormat.PLAIN_TEXT, List.of("A", "B"));
                    Clipboard.getSystemClipboard().setContent(content);
                    System.out.println("Clipboard: " + Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT));
                } catch (RuntimeException e) {
                    // Refusing the data is fine: crashing is not
                    System.out.println("Refused: " + e);
                }
                later(800, this::done);
            });
        }

        // JDK-8127504
        void customClipboardFormat() {
            show(new StackPane(new Label("Clipboard")), 300, 100);
            later(300, () -> {
                DataFormat custom = DataFormat.lookupMimeType("dndwithcontrols.custom.string") != null
                        ? DataFormat.lookupMimeType("dndwithcontrols.custom.string")
                        : new DataFormat("dndwithcontrols.custom.string");
                ClipboardContent content = new ClipboardContent();
                content.put(custom, "Hello Custom String!");
                Clipboard.getSystemClipboard().setContent(content);
                Object back = Clipboard.getSystemClipboard().getContent(custom);
                System.out.println("Read back: " + back);
                later(500, this::done);
            });
        }

        // JDK-8114905
        void sceneChanges() {
            show(new StackPane(new Label("Scenes")), 500, 400);
            int[] n = {0};
            Timeline t = new Timeline(new KeyFrame(Duration.millis(40), e -> {
                FlowPane pane = new FlowPane();
                for (int i = 0; i < 300; i++) {
                    pane.getChildren().add(i % 2 == 0 ? new Button("B" + i) : new Circle(6, Color.hsb(i, 0.7, 0.9)));
                }
                stage.setScene(new Scene(new ScrollPane(pane), 500, 400));
                if (++n[0] == 60) {
                    later(500, this::done);
                }
            }));
            t.setCycleCount(60);
            later(300, t::play);
        }

        // JDK-8094007
        void pannableScrollPane() {
            Group content = new Group();
            for (int i = 0; i < 400; i++) {
                content.getChildren().add(new Circle(20 + (i % 20) * 60, 20 + (i / 20) * 60, 25,
                        Color.hsb(i * 7, 0.6, 0.9)));
            }
            ScrollPane pane = new ScrollPane(content);
            pane.setPannable(true);
            show(new StackPane(pane), 400, 300);
            pane.setOnMouseReleased(e -> later(800, this::done));
            later(500, () -> {
                point("pane", pane);
                ready();
            });
        }

        // JDK-8119216
        void choiceBox() {
            ChoiceBox<String> choice = new ChoiceBox<>(FXCollections.observableArrayList("One", "Two", "Three"));
            choice.setValue("One");
            choice.valueProperty().addListener((obs, o, n) -> later(800, this::done));
            show(new VBox(20, new Label("Choose"), choice), 300, 200);
            later(500, () -> {
                point("choice", choice);
                ready();
            });
        }

        // JDK-8119176
        void tooltips() {
            Label label = new Label("Hover");
            show(new StackPane(label), 300, 200);
            Tooltip tip = new Tooltip("A tooltip");
            int[] n = {0};
            Timeline t = new Timeline(new KeyFrame(Duration.millis(60), e -> {
                if (tip.isShowing()) {
                    tip.hide();
                } else {
                    Bounds b = label.localToScreen(label.getLayoutBounds());
                    tip.show(label, b.getMaxX(), b.getMaxY());
                }
                if (++n[0] == 80) {
                    tip.hide();
                    later(500, this::done);
                }
            }));
            t.setCycleCount(80);
            later(400, t::play);
        }

        // JDK-8094223, JDK-8123684
        void windowStates() {
            show(new StackPane(new Label("States")), 400, 300);
            List<Runnable> steps = List.of(
                    () -> stage.setIconified(true), () -> stage.setIconified(false),
                    () -> stage.setMaximized(true), () -> stage.setMaximized(false),
                    () -> stage.setFullScreen(true), () -> stage.setFullScreen(false),
                    () -> stage.setMaximized(true), () -> stage.setFullScreen(true),
                    () -> stage.setFullScreen(false), () -> stage.setMaximized(false),
                    () -> stage.setIconified(true), () -> stage.setMaximized(true),
                    () -> stage.setIconified(false), () -> stage.setMaximized(false));
            int[] n = {0};
            Timeline t = new Timeline(new KeyFrame(Duration.millis(150), e -> {
                steps.get(n[0] % steps.size()).run();
                if (++n[0] == steps.size() * 2) {
                    later(800, this::done);
                }
            }));
            t.setCycleCount(steps.size() * 2);
            later(400, t::play);
        }

        // JDK-8339523, JDK-8382883
        void resizeWhileAnimating() {
            Circle circle = new Circle(30, Color.CORNFLOWERBLUE);
            StackPane root = new StackPane(circle);
            show(root, 300, 300);
            Timeline spin = new Timeline(new KeyFrame(Duration.millis(16),
                    e -> circle.setTranslateX(80 * Math.sin(System.nanoTime() / 2e8))));
            spin.setCycleCount(Animation.INDEFINITE);
            spin.play();
            int[] n = {0};
            Timeline resize = new Timeline(new KeyFrame(Duration.millis(20), e -> {
                stage.setWidth(300 + (n[0] % 30) * 15);
                stage.setHeight(300 + (n[0] % 17) * 20);
                if (++n[0] == 150) {
                    spin.stop();
                    later(500, this::done);
                }
            }));
            resize.setCycleCount(150);
            later(300, resize::play);
        }

        // JDK-8116818, JDK-8125397
        void stagesAtExit() {
            show(new StackPane(new Label("Main")), 300, 200);
            for (int i = 0; i < 4; i++) {
                Stage s = new Stage();
                if (i == 0) {
                    s.initOwner(stage);
                }
                s.setScene(new Scene(new StackPane(new Label("Stage " + i)), 200, 120));
                s.setX(stage.getX() + 40 * (i + 1));
                s.setY(stage.getY() + 40 * (i + 1));
                s.show();
            }
            later(800, () -> {
                print("DONE");
                // The exit is the test: the JVM must end, with status 0
                Platform.exit();
            });
        }

        // JDK-8095277
        void localizedFilter() {
            show(new StackPane(new Label("Chooser")), 300, 100);
            later(300, () -> {
                FileChooser chooser = new FileChooser();
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Файл XML", "*.xml"),
                        new FileChooser.ExtensionFilter("ファイル テキスト", "*.txt"));
                ready();
                chooser.showOpenDialog(stage);
                later(500, this::done);
            });
        }
    }
}
