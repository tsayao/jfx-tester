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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodHighlight;
import javafx.scene.input.InputMethodRequests;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A laboratory for input methods: text fields, a text area, a field in a
 * popup, an editable combo box and the cell editor of a table, which log the
 * input method events they get, their composed and committed text, and where
 * they place the window of candidates.
 * <p>
 * Its robot tests compose text through the input method of the platform,
 * whichever it is:
 * <ul>
 * <li>without an input method framework, the input method of GTK itself,
 *     whose Unicode entry (Ctrl+Shift+U, hexadecimal digits, Space) composes
 *     and commits é without a dictionary;</li>
 * <li>IBus (jfx-tester --ime=ibus, GTK_IM_MODULE=ibus), with the same Unicode
 *     entry in its engine of the US layout;</li>
 * <li>fcitx5 (jfx-tester --ime=fcitx5, GTK_IM_MODULE=fcitx), with Hangul, of
 *     fcitx5-hangul, which composes the same way every time.</li>
 * </ul>
 * With IBus and fcitx5, it also types Chinese with Pinyin (nihao: 你好, with
 * libpinyin or the Pinyin of fcitx5-chinese-addons), Japanese (nihongo, then
 * Space converts to 日本語, with Anthy or Mozc), Korean (gksrmf: 한글, with
 * Hangul) and Arabic (hgsghl: السلام, with the Arabic keyboard of m17n or of
 * XKB), checking the composition, the window of candidates and the result.
 * The lab switches the engine of IBus and fcitx5 itself, with ibus engine and
 * fcitx5-remote. Most tests stand for a bug of the JBS, whose number they
 * name. Its automatic tests send input method events to the controls
 * without the platform.
 */
public class ImeLab {

    /**
     * Runs this lab as an application of its own, with its tests:
     * <pre>java ImeLab.java</pre>
     * or {@code ./jfx-tester --tests=ime --ime=ibus,fcitx5}.
     */
    public static void main(String[] args) {
        TestLab.launchLab("ime", args);
    }

    private static final String CSS = TestKit.BASE_CSS + """
        .composing { -fx-font-family: "monospaced"; -fx-font-size: 12px; }
        """;

    static final String STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(CSS.getBytes(StandardCharsets.UTF_8));

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int LOG_SIZE = 300;

    final TestKit.Suite suite = new TestKit.Suite("Input methods");

    /** The input method framework of the session: none (GTK itself), ibus or fcitx5. */
    private final String framework = framework();
    private Stage stage;
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final ListView<String> logView = new ListView<>(log);
    private final Label status = new Label();
    private final TextField field = new TextField();
    private final TextField field2 = new TextField();
    private final TextArea area = new TextArea();
    private final TextField popupField = new TextField();
    private final Popup popup = new Popup();
    private final Button popupButton = new Button("Open the popup");
    private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("One", "Two", "Three"));
    private final TableView<Row> table = new TableView<>();
    private final ContextMenu menu = new ContextMenu(new MenuItem("Copy"), new MenuItem("Paste"));

    /** What each control saw: its input method events and its KEY_TYPED. */
    private final Map<Node, Seen> seen = new HashMap<>();

    /** The screen points that the platform got for the window of candidates, per control. */
    private final Map<Node, List<Point2D>> locations = new HashMap<>();

    public static final class Row {
        final SimpleStringProperty name = new SimpleStringProperty();

        Row(String name) {
            this.name.set(name);
        }
    }

    private static final class Seen {
        final List<String> composed = new ArrayList<>();
        final List<String> committed = new ArrayList<>();
        final StringBuilder typed = new StringBuilder();

        void clear() {
            composed.clear();
            committed.clear();
            typed.setLength(0);
        }

        @Override
        public String toString() {
            return "composed " + composed + ", committed " + committed + ", typed \"" + typed + "\"";
        }
    }

    private static String framework() {
        String ime = System.getProperty("tests.ime");
        if (ime == null || ime.isBlank()) {
            String module = System.getenv("GTK_IM_MODULE");
            ime = module == null ? "none" : module.startsWith("fcitx") ? "fcitx5" : module;
        }
        return switch (ime) {
            case "ibus", "fcitx5" -> ime;
            default -> "none";
        };
    }

    /**
     * The lab without its header bar, which the test lab shows in a tab.
     */
    Region content(Stage stage) {
        this.stage = stage;
        field.setPromptText("Compose here");
        field2.setPromptText("Then here");
        area.setPrefRowCount(3);
        area.setPromptText("A text area");
        popupField.setPromptText("A field in a popup");
        popupField.setPrefWidth(260);
        VBox popupBox = new VBox(popupField);
        popupBox.setPadding(new Insets(10));
        popupBox.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #475569;");
        popup.getContent().add(popupBox);
        popup.setAutoHide(true);
        popupButton.setOnAction(e -> showPopup());
        combo.setEditable(true);
        combo.setPromptText("An editable combo box");
        field.setContextMenu(menu);
        table.setEditable(true);
        TableColumn<Row, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(r -> r.getValue().name);
        name.setCellFactory(TextFieldTableCell.forTableColumn());
        name.setPrefWidth(240);
        table.getColumns().add(name);
        table.getItems().setAll(new Row("First"), new Row("Second"));
        table.setPrefHeight(110);
        for (TextInputControl c : List.of(field, field2, area, popupField, combo.getEditor())) {
            watch(c, describe(c));
        }
        watchCombo();
        status.setText("Input method framework: " + switch (framework) {
            case "ibus" -> "IBus";
            case "fcitx5" -> "fcitx5";
            default -> "none (the input method of GTK)";
        } + ", GTK_IM_MODULE=" + System.getenv("GTK_IM_MODULE"));
        status.getStyleClass().add("section");

        GridPane controls = new GridPane(10, 8);
        controls.addRow(0, new Label("Text field"), field);
        controls.addRow(1, new Label("Second field"), field2);
        controls.addRow(2, new Label("Text area"), area);
        controls.addRow(3, new Label("Popup"), popupButton);
        controls.addRow(4, new Label("Combo box"), combo);
        controls.addRow(5, new Label("Table"), table);
        GridPane.setHgrow(field, Priority.ALWAYS);
        Label hint = muted("Type with an input method: without one, Ctrl+Shift+U, e, 9 and Space compose é with the "
                + "input method of GTK, and IBus does the same; Hangul composes 한글 from g k s r m f. The log shows "
                + "the composed and committed text of each control, and where it asked for the window of "
                + "candidates.");
        VBox left = new VBox(14, card("Input methods", status, controls, hint));
        logView.setPrefHeight(360);
        logView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                setWrapText(true);
                setPrefWidth(0);
                getStyleClass().add("composing");
            }
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> log.clear());
        VBox right = new VBox(14, card("Events", logView, clear));
        VBox.setVgrow(logView, Priority.ALWAYS);
        HBox columns = new HBox(14, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(1);
        right.setPrefWidth(1);
        columns.setPadding(new Insets(4, 18, 18, 18));
        defineTests();
        suite.setBeforeRun(() -> {
            stage.toFront();
            stage.requestFocus();
        });
        suite.setAfterRobotFailure(this::reset);
        return columns;
    }

    private static String describe(TextInputControl c) {
        return c.getPromptText() != null ? c.getPromptText() : c.getClass().getSimpleName();
    }

    /**
     * Logs the input method events and KEY_TYPED of a control, and wraps its
     * input method requests, once its skin sets them, to see where the
     * platform places the window of candidates.
     */
    private void watch(TextInputControl c, String name) {
        Seen s = seen.computeIfAbsent(c, k -> new Seen());
        c.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, e -> {
            StringBuilder composed = new StringBuilder();
            for (InputMethodTextRun run : e.getComposed()) {
                composed.append(run.getText());
            }
            if (!composed.isEmpty()) {
                s.composed.add(composed.toString());
            }
            if (!e.getCommitted().isEmpty()) {
                s.committed.add(e.getCommitted());
            }
            log(name + ": composed \"" + composed + "\", committed \"" + e.getCommitted() + "\", caret "
                    + e.getCaretPosition());
        });
        c.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().isEmpty() && e.getCharacter().charAt(0) >= ' ') {
                s.typed.append(e.getCharacter());
            }
        });
        wrapRequests(c, name);
        c.inputMethodRequestsProperty().addListener((obs, o, n) -> {
            if (n != null && !(n instanceof Recording)) {
                wrapRequests(c, name);
            }
        });
    }

    /**
     * The editable combo box has the focus, and hands the input method events
     * to the handler of its editor, and the requests of its editor to the
     * platform: they are watched on the combo box, for its editor.
     */
    private void watchCombo() {
        TextField editor = combo.getEditor();
        Seen s = seen.get(editor);
        combo.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, e -> {
            StringBuilder composed = new StringBuilder();
            for (InputMethodTextRun run : e.getComposed()) {
                composed.append(run.getText());
            }
            if (!composed.isEmpty()) {
                s.composed.add(composed.toString());
            }
            if (!e.getCommitted().isEmpty()) {
                s.committed.add(e.getCommitted());
            }
            log("combo box: composed \"" + composed + "\", committed \"" + e.getCommitted() + "\"");
        });
        combo.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().isEmpty() && e.getCharacter().charAt(0) >= ' ') {
                s.typed.append(e.getCharacter());
            }
        });
        Runnable wrap = () -> {
            InputMethodRequests r = combo.getInputMethodRequests();
            if (r != null && !(r instanceof Recording)) {
                combo.setInputMethodRequests(new Recording(r, editor, "combo box"));
            }
        };
        wrap.run();
        combo.inputMethodRequestsProperty().addListener((obs, o, n) -> {
            if (n != null && !(n instanceof Recording)) {
                wrap.run();
            }
        });
    }

    /** Input method requests that record the location they give the platform. */
    private final class Recording implements InputMethodRequests {
        private final InputMethodRequests delegate;
        private final Node node;
        private final String name;

        Recording(InputMethodRequests delegate, Node node, String name) {
            this.delegate = delegate;
            this.node = node;
            this.name = name;
        }

        @Override
        public Point2D getTextLocation(int offset) {
            Point2D p = delegate.getTextLocation(offset);
            if (p != null) {
                locations.computeIfAbsent(node, k -> new ArrayList<>()).add(p);
                log(String.format(Locale.ROOT, "%s: candidates at %.1f, %.1f", name, p.getX(), p.getY()));
            }
            return p;
        }

        @Override
        public int getLocationOffset(int x, int y) {
            return delegate.getLocationOffset(x, y);
        }

        @Override
        public void cancelLatestCommittedText() {
            delegate.cancelLatestCommittedText();
        }

        @Override
        public String getSelectedText() {
            return delegate.getSelectedText();
        }
    }

    private void wrapRequests(TextInputControl c, String name) {
        InputMethodRequests r = c.getInputMethodRequests();
        if (r != null && !(r instanceof Recording)) {
            c.setInputMethodRequests(new Recording(r, c, name));
        }
    }

    private void showPopup() {
        Bounds b = popupButton.localToScreen(popupButton.getLayoutBounds());
        popup.show(popupButton, b.getMinX(), b.getMaxY() + 6);
    }

    private void reset() {
        popup.hide();
        menu.hide();
        combo.hide();
        table.edit(-1, null);
        selectEngine(primaryEngine());
    }

    // ---------------------------------------------------------------------
    // Composers: the keys that compose and commit with the input method

    /**
     * A way to compose with the input method of the session: the keys that
     * open a composition, what it shows, and the keys that commit it.
     */
    private interface Composer {
        String name();

        /** Opens a composition and types into it. */
        void compose();

        /** Text that the composed text must have, once compose() typed. */
        String composing();

        /** The keys whose KEY_TYPED must not reach the control while composing. */
        String filtered();

        /** Commits the composition. */
        void commit();

        /** The text of the control after the commit. */
        String committed();

        /** Deletes the last character of the composition, and what then commits. */
        String backspaceCommitted();

        /** Whether Escape cancels the composition. */
        boolean escapeCancels();

        /** Whether Enter commits the composition and nothing else. */
        boolean enterCommits();
    }

    /** Ctrl+Shift+U, e, 9: é, of the input method of GTK and of the engines of IBus for a layout. */
    private static final class UnicodeComposer implements Composer {
        @Override
        public String name() {
            return "Unicode entry";
        }

        @Override
        public void compose() {
            TestKit.shortcut(KeyCode.U, KeyCode.CONTROL, KeyCode.SHIFT);
            TestKit.pause(150);
            TestKit.type("e9");
            TestKit.pause(250);
        }

        @Override
        public String composing() {
            return "e9";
        }

        @Override
        public String filtered() {
            return "e9";
        }

        @Override
        public void commit() {
            TestKit.shortcut(KeyCode.SPACE);
            TestKit.pause(250);
        }

        @Override
        public String committed() {
            return "é";
        }

        @Override
        public String backspaceCommitted() {
            // u e, then a: U+00EA
            TestKit.shortcut(KeyCode.BACK_SPACE);
            TestKit.pause(150);
            TestKit.type("a");
            TestKit.pause(200);
            commit();
            return "ê";
        }

        @Override
        public boolean escapeCancels() {
            return true;
        }

        @Override
        public boolean enterCommits() {
            return true;
        }
    }

    /** g k s on the two-set layout: 한, of ibus-hangul and fcitx5-hangul. */
    private static final class HangulComposer implements Composer {
        @Override
        public String name() {
            return "Hangul";
        }

        @Override
        public void compose() {
            TestKit.type("gks");
            TestKit.pause(250);
        }

        @Override
        public String composing() {
            return "한";
        }

        @Override
        public String filtered() {
            return "gks";
        }

        @Override
        public void commit() {
            TestKit.shortcut(KeyCode.SPACE);
            TestKit.pause(250);
        }

        @Override
        public String committed() {
            // Space commits the syllable and is typed after it
            return "한 ";
        }

        @Override
        public String backspaceCommitted() {
            // 한 less its final consonant: 하
            TestKit.shortcut(KeyCode.BACK_SPACE);
            TestKit.pause(200);
            commit();
            return "하 ";
        }

        @Override
        public boolean escapeCancels() {
            return false;
        }

        @Override
        public boolean enterCommits() {
            return false;
        }
    }

    /** The engine that the composition tests use: Unicode entry, or Hangul for fcitx5. */
    private String primaryEngine() {
        return switch (framework) {
            case "ibus" -> "xkb:us::eng";
            case "fcitx5" -> "hangul";
            default -> null;
        };
    }

    private Composer composer;

    /**
     * The composer of the session, after selecting its engine, or skips when
     * the framework has no engine that composes the same way every time.
     */
    private Composer composer() {
        return composer(field);
    }

    /**
     * The composer, with its engine selected for a control that has the
     * focus: fcitx5 switches the engine of the focused input context only.
     */
    private Composer composer(TextInputControl target) {
        start(target);
        if (composer == null) {
            composer = framework.equals("fcitx5") ? new HangulComposer() : new UnicodeComposer();
        }
        selectEngine(primaryEngine());
        if (primaryEngine() != null) {
            TestKit.pause(300);
            if (!primaryEngine().equals(currentEngine())) {
                TestKit.skip(framework + " did not activate " + primaryEngine() + " (now " + currentEngine() + ")");
            }
        }
        return composer;
    }

    /** Selects an engine of IBus or fcitx5, for the focused window. */
    private void selectEngine(String engine) {
        if (engine == null) {
            return;
        }
        switch (framework) {
            case "ibus" -> run("ibus", "engine", engine);
            case "fcitx5" -> run("fcitx5-remote", "-s", engine);
            default -> { }
        }
    }

    private static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Tests

    private void defineTests() {
        suite.auto("Input method requests", "The text controls, the editor of an editable combo box and of a table "
                + "cell, which the Scene enables the input method for, have InputMethodRequests and a handler", () -> {
            List<String> missing = new ArrayList<>();
            fx(() -> this.showPopup());
            TestKit.await("the popup", () -> popup.isShowing() && popupField.getSkin() != null);
            try {
                for (Node c : List.of(field, field2, area, popupField, combo)) {
                    if (fx(() -> c.getInputMethodRequests() == null || c.getOnInputMethodTextChanged() == null)) {
                        missing.add(c instanceof TextInputControl t ? describe(t) : "editable combo box");
                    }
                }
            } finally {
                fx(() -> popup.hide());
            }
            TestKit.check(missing.isEmpty(), "no input method requests or handler: " + missing);
            return "5 controls";
        });
        suite.auto("Composed, then committed", "Sends an INPUT_METHOD_TEXT_CHANGED with composed text and then one "
                + "with the committed text, without the platform", () -> fx(() -> {
            TextField f = new TextField("ab");
            new Stage().setScene(new javafx.scene.Scene(new VBox(f)));
            f.applyCss();
            f.positionCaret(1);
            Event.fireEvent(f, composedEvent("xyz", 1));
            TestKit.check(f.getText().equals("axyzb"), "the composed text shows as \"" + f.getText() + "\"");
            TestKit.check(f.getCaretPosition() == 2, "the caret is at " + f.getCaretPosition()
                    + " in the composed text, expected 2");
            Event.fireEvent(f, committedEvent("é"));
            TestKit.check(f.getText().equals("aéb"), "after the commit the text is \"" + f.getText() + "\"");
            return "\"axyzb\", then \"aéb\"";
        }));
        suite.auto("Skin replaced", "JDK-8268877: a commit after the skin of the text field changed is inserted once",
                () -> fx(() -> {
            TextField f = new TextField("text");
            new Stage().setScene(new javafx.scene.Scene(new VBox(f)));
            f.applyCss();
            Event.fireEvent(f, committedEvent("1"));
            f.setSkin(new TextFieldSkin(f));
            Event.fireEvent(f, committedEvent("2"));
            TestKit.check(f.getText().equals("12text"), "the text is \"" + f.getText() + "\", expected \"12text\"");
            return "\"12text\"";
        }));
        suite.robot("Composition and commit", "Composes in the text field, which shows the composed text, and "
                + "commits it: the keys of the composition must not arrive as KEY_TYPED", () -> {
            Composer c = composer();
            Seen s = start(field);
            c.compose();
            awaitComposing(field, c);
            c.commit();
            awaitText(field, c.committed());
            checkFiltered(field, c);
            return c.name() + ": " + fx(() -> s.toString());
        });
        suite.robot("Escape cancels the composition", "Composes, presses Escape: nothing is committed", () -> {
            Composer c = composer();
            if (!c.escapeCancels()) {
                TestKit.skip(c.name() + " does not cancel with Escape");
            }
            start(field);
            c.compose();
            awaitComposing(field, c);
            TestKit.shortcut(KeyCode.ESCAPE);
            TestKit.pause(300);
            awaitText(field, "");
            TestKit.check(fx(() -> seen.get(field).committed.isEmpty()), "committed after Escape: "
                    + fx(() -> seen.get(field).toString()));
            return "cancelled";
        });
        suite.robot("Backspace in the composition", "JDK-8120039, JDK-8117022: one Backspace deletes the last "
                + "character of the composition, which then commits", () -> {
            Composer c = composer();
            start(field);
            c.compose();
            awaitComposing(field, c);
            String want = c.backspaceCommitted();
            awaitText(field, want);
            return "\"" + want + "\"";
        });
        suite.robot("Enter commits, without a new line", "JDK-8301900: Enter commits the composition in the text "
                + "area, and adds no new line", () -> {
            Composer c = composer(area);
            if (!c.enterCommits()) {
                TestKit.skip(c.name() + " commits with Enter and then types it");
            }
            start(area);
            c.compose();
            awaitComposing(area, c);
            TestKit.shortcut(KeyCode.ENTER);
            TestKit.pause(300);
            awaitText(area, c.committed());
            return "\"" + c.committed() + "\" and no new line";
        });
        suite.robot("Focus change ends the composition", "JDK-8320912, JDK-8122169: composes in the first field and "
                + "clicks the second, which takes the next composition, and the first keeps no composed text",
                () -> {
            Composer c = composer();
            start(field);
            c.compose();
            awaitComposing(field, c);
            fx(() -> {
                field2.clear();
                seen.get(field2).clear();
            });
            TestKit.click(field2);
            TestKit.pause(400);
            String first = fx(() -> field.getText());
            TestKit.check(first.isEmpty() || first.equals(c.committed().strip()), "JDK-8320912: the first field "
                    + "keeps \"" + first + "\" after the focus left it");
            c.compose();
            awaitComposing(field2, c);
            c.commit();
            awaitText(field2, c.committed());
            TestKit.check(fx(() -> field.getText()).equals(first), "the first field changed to \"" + fx(() -> field.getText())
                    + "\" while composing in the second");
            return "the first field holds \"" + first + "\", the second \"" + c.committed() + "\"";
        });
        suite.robot("Text field in a popup", "JDK-8288893, JDK-8336423: composes in a text field of a popup", () -> {
            try {
                fx(() -> this.showPopup());
                TestKit.await("the popup", () -> popup.isShowing() && popupField.localToScreen(0, 0) != null);
                TestKit.pause(300);
                Composer c = composer(popupField);
                c.compose();
                awaitComposing(popupField, c);
                checkLocation(popupField);
                c.commit();
                awaitText(popupField, c.committed());
                return "\"" + c.committed() + "\" in the popup";
            } finally {
                fx(() -> popup.hide());
            }
        });
        suite.robot("Editable combo box", "JDK-8331319: opens and closes the list of an editable combo box, then "
                + "composes in its editor, with the window of candidates at its caret", () -> {
            TextField editor = combo.getEditor();
            fx(() -> combo.show());
            TestKit.pause(400);
            fx(() -> combo.hide());
            TestKit.pause(300);
            Composer c = composer(editor);
            c.compose();
            awaitComposing(editor, c);
            checkLocation(editor);
            c.commit();
            awaitText(editor, c.committed());
            return "\"" + c.committed() + "\" in the editor";
        });
        suite.robot("Table cell editor", "JDK-8334586: starts editing a cell of a table, whose text field the Scene "
                + "must enable the input method for, and composes in it", () -> {
            Composer c = composer();
            try {
                TestKit.reveal(table);
                fx(() -> {
                    table.requestFocus();
                    table.edit(0, table.getColumns().getFirst());
                });
                TestKit.await("the editor of the cell", () -> table.lookup(".text-field") instanceof TextField t
                        && t.isFocused());
                TextField editor = fx(() -> (TextField) table.lookup(".text-field"));
                fx(() -> {
                    editor.clear();
                    watch(editor, "cell editor");
                    seen.get(editor).clear();
                });
                c.compose();
                try {
                    awaitComposing(editor, c);
                } catch (AssertionError e) {
                    throw new AssertionError("JDK-8334586: no composition in the editor of the cell: "
                            + fx(() -> seen.get(editor).toString()) + ", text \"" + fx(() -> editor.getText()) + "\"");
                }
                c.commit();
                awaitText(editor, c.committed());
            } finally {
                fx(() -> table.edit(-1, null));
            }
            return "\"" + c.committed() + "\" in the cell";
        });
        suite.robot("Window of candidates", "JDK-8117310, JDK-8331319, JDK-8301893: with the text scrolled, and "
                + "after a context menu, the platform gets a location for the window of candidates at the caret",
                () -> {
            Composer c = composer();
            fx(() -> {
                field.setText("A long text that scrolls the field to its end ".repeat(4));
                field.end();
            });
            start(field, false);
            c.compose();
            awaitComposing(field, c);
            checkLocation(field);
            TestKit.shortcut(KeyCode.ESCAPE);
            fx(() -> field.clear());
            TestKit.pause(200);
            Bounds b = fx(() -> TestKit.screenBounds(field));
            fx(() -> menu.show(field, b.getCenterX(), b.getMaxY()));
            TestKit.pause(300);
            fx(() -> menu.hide());
            TestKit.pause(200);
            start(field);
            c.compose();
            awaitComposing(field, c);
            checkLocation(field);
            c.commit();
            awaitText(field, c.committed());
            return "at the caret, " + fx(() -> locations.get(field).size()) + " locations asked";
        });
        language(new Language("Chinese (Pinyin)", "nihao", List.of("ibus:libpinyin", "ibus:pinyin", "fcitx5:pinyin"),
                "a", t -> t.contains("a") || han(t), t -> t.contains("ni") || t.contains("你"), "你好", false,
                KeyCode.SPACE));
        language(new Language("Japanese", "nihongo", List.of("ibus:anthy", "ibus:mozc-jp", "fcitx5:anthy",
                "fcitx5:mozc"), "a", t -> t.contains("あ"), t -> t.contains("にほんご"), "日本語", true, KeyCode.SPACE,
                KeyCode.ENTER));
        language(new Language("Korean (Hangul)", "gksrmf", List.of("ibus:hangul", "fcitx5:hangul"),
                "r", t -> t.contains("ㄱ"), t -> t.contains("한") || t.contains("글"), "한글 ", false, KeyCode.SPACE));
        language(new Language("Arabic", "hgsghl", List.of("ibus:m17n:ar:kbd", "ibus:xkb:ara::ara",
                "fcitx5:m17n_ar_kbd", "fcitx5:keyboard-ara"), "h", t -> t.contains("ا"), null, "السلام", false));
        suite.robot("Dead keys", "JDK-8367045, JDK-8376268, JDK-8308644: dead acute and e type é, where the "
                + "keyboard layout has a dead acute", () -> {
            Seen s = start(field);
            // An engine of a layout, which composes dead keys as the input method of GTK does
            selectEngine(switch (framework) {
                case "ibus" -> "xkb:us::eng";
                case "fcitx5" -> "keyboard-us";
                default -> null;
            });
            TestKit.pause(300);
            fx(() -> {
                field.clear();
                s.clear();
            });
            try {
                fx(() -> {
                    TestKit.robot().keyPress(KeyCode.DEAD_ACUTE);
                    TestKit.robot().keyRelease(KeyCode.DEAD_ACUTE);
                });
            } catch (RuntimeException e) {
                TestKit.skip("the robot cannot type a dead acute: " + e.getMessage());
            }
            TestKit.pause(200);
            TestKit.type("e");
            TestKit.pause(400);
            String text = fx(() -> field.getText());
            if (text.equals("e")) {
                TestKit.skip("the keyboard layout has no dead acute");
            }
            try {
                TestKit.check(text.equals("é"), "JDK-8367045: dead acute and e typed \"" + text + "\": "
                        + fx(() -> s.toString()));
            } finally {
                selectEngine(primaryEngine());
            }
            return "é";
        });
    }

    /**
     * A language of an engine of IBus or fcitx5: the keys typed, a key and the
     * text it must give, to tell that the engine is in the mode of the
     * language, what the composition shows (null for a layout, which commits
     * each key), the keys that convert and commit, and the text that results.
     */
    private record Language(String name, String keys, List<String> engines, String probe,
                            java.util.function.Predicate<String> probed,
                            java.util.function.Predicate<String> composing, String result, boolean converts,
                            KeyCode... commitKeys) {}

    /**
     * Selects the first engine of the language that the framework of the
     * session activates, or skips.
     */
    private String selectLanguage(Language l) {
        if (framework.equals("none")) {
            TestKit.skip("no input method framework (jfx-tester --ime=ibus or --ime=fcitx5)");
        }
        List<String> tried = new ArrayList<>();
        for (String e : l.engines()) {
            if (!e.startsWith(framework + ":")) {
                continue;
            }
            String engine = e.substring(framework.length() + 1);
            selectEngine(engine);
            TestKit.pause(300);
            if (!engine.equals(currentEngine())) {
                tried.add(engine + " (not activated)");
                continue;
            }
            if (probeMode(l)) {
                return engine;
            }
            tried.add(engine + " (\"" + l.probe() + "\" gave nothing of " + l.name() + ")");
        }
        TestKit.skip(framework + " has no working engine of " + l.name() + ": " + tried);
        return null;
    }

    private static boolean han(String t) {
        return t.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /** The engine of IBus or fcitx5 now, or null. */
    private String currentEngine() {
        String out = switch (framework) {
            case "ibus" -> run("ibus", "engine");
            case "fcitx5" -> run("fcitx5-remote", "-n");
            default -> null;
        };
        return out == null ? null : out.strip();
    }

    /**
     * The test of a language: types its keys in the text field with its
     * engine, checks the composition and, for Chinese and Japanese, that the
     * window of candidates goes to the caret, converts, commits, and checks
     * the text.
     */
    private void language(Language l) {
        suite.robot(l.name(), "Types \"" + l.keys() + "\" with an engine of " + l.name() + " ("
                + String.join(", ", l.engines()) + "), which must give \"" + l.result().strip() + "\"", () -> {
            Seen s = start(field);
            String engine = null;
            try {
                engine = selectLanguage(l);
                fx(() -> {
                    field.clear();
                    s.clear();
                    locations.remove(field);
                });
                TestKit.type(l.keys());
                TestKit.pause(500);
                if (l.composing() != null) {
                    try {
                        TestKit.await("the composition", 3000, () -> s.composed.stream().anyMatch(l.composing()));
                    } catch (AssertionError e) {
                        throw new AssertionError("no composition of \"" + l.keys() + "\" with " + engine + ": "
                                + fx(() -> s.toString()) + ", text \"" + fx(() -> field.getText()) + "\"");
                    }
                    checkLocation(field);
                    checkFiltered(field, l.keys());
                }
                for (KeyCode k : l.commitKeys()) {
                    TestKit.shortcut(k);
                    TestKit.pause(500);
                }
                if (l.converts()) {
                    TestKit.check(fx(() -> s.composed.stream().anyMatch(t -> t.codePoints().anyMatch(
                            cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN))),
                            "the conversion composed no kanji: " + fx(() -> s.toString()));
                }
                awaitText(field, l.result());
                return engine + ": " + fx(() -> s.toString());
            } finally {
                selectEngine(primaryEngine());
            }
        });
    }

    /**
     * Types the probe key of a language, which must give its text: an engine
     * in another mode, as ibus-hangul starts in, is toggled with Shift+Space.
     *
     * @return whether the engine types the language
     */
    private boolean probeMode(Language l) {
        Seen s = seen.get(field);
        // An engine loads its dictionaries when first selected, which takes seconds; one in another mode, as
        // ibus-hangul starts in, toggles with Shift+Space. Backspace and not Escape undoes the probe, as Escape
        // leaves the mode of some engines
        for (int attempt = 0; attempt < 6; attempt++) {
            fx(() -> {
                field.clear();
                s.clear();
            });
            TestKit.type(l.probe());
            TestKit.pause(500);
            boolean ok = fx(() -> l.probed().test(field.getText() + String.join("", s.composed)));
            for (int i = 0; i < 3; i++) {
                TestKit.shortcut(KeyCode.BACK_SPACE);
                TestKit.pause(80);
            }
            TestKit.pause(200);
            fx(() -> field.clear());
            if (ok) {
                return true;
            }
            if (attempt == 3) {
                TestKit.shortcut(KeyCode.SPACE, KeyCode.SHIFT);
                TestKit.pause(400);
            } else {
                TestKit.pause(700);
            }
        }
        return false;
    }

    private void checkFiltered(TextInputControl c, String keys) {
        String typed = fx(() -> seen.get(c).typed.toString());
        for (char ch : keys.toCharArray()) {
            TestKit.check(typed.indexOf(ch) < 0, "'" + ch + "' of the composition arrived as KEY_TYPED: \"" + typed
                    + "\"");
        }
    }

    private static InputMethodEvent composedEvent(String composed, int caret) {
        return new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                List.of(new InputMethodTextRun(composed, InputMethodHighlight.UNSELECTED_RAW)), "", caret);
    }

    private static InputMethodEvent committedEvent(String committed) {
        return new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, List.of(), committed, 0);
    }

    private Seen start(TextInputControl c) {
        return start(c, true);
    }

    /**
     * Focuses a control with a click, clearing it first unless asked not to,
     * and forgets what it saw.
     */
    private Seen start(TextInputControl c, boolean clear) {
        Seen s = seen.get(c);
        fx(() -> {
            if (clear) {
                c.clear();
            }
            s.clear();
            locations.remove(c);
        });
        TestKit.click(c);
        TestKit.await("the focus in " + describe(c), c::isFocused);
        if (!clear) {
            fx(() -> c.end());
        }
        TestKit.pause(200);
        return s;
    }

    private void awaitComposing(TextInputControl c, Composer composer) {
        Seen s = seen.get(c);
        try {
            TestKit.await("the composition", 3000, () -> s.composed.stream().anyMatch(t -> t.contains(composer.composing())));
        } catch (AssertionError e) {
            throw new AssertionError("no composed text with \"" + composer.composing() + "\" (" + composer.name()
                    + ", " + framework + "): " + fx(() -> s.toString()) + ", text \"" + fx(() -> c.getText()) + "\"");
        }
        TestKit.check(fx(() -> c.getText()).contains(composer.composing()), "the control does not show the composed text: \""
                + fx(() -> c.getText()) + "\"");
    }

    private void awaitText(TextInputControl c, String want) {
        try {
            TestKit.await("the text \"" + want + "\"", 3000, () -> c.getText().equals(want));
        } catch (AssertionError e) {
            throw new AssertionError("the text is \"" + fx(() -> c.getText()) + "\", expected \"" + want + "\": "
                    + fx(() -> seen.get(c).toString()));
        }
    }

    private void checkFiltered(TextInputControl c, Composer composer) {
        checkFiltered(c, composer.filtered());
    }

    /**
     * The last location the platform asked for, during the composition, must
     * be at the caret: within the control across, and from its top to a line
     * below it.
     */
    private void checkLocation(TextInputControl c) {
        TestKit.await("a location for the window of candidates", 2000,
                () -> locations.containsKey(c) && !locations.get(c).isEmpty());
        Point2D p = fx(() -> locations.get(c).getLast());
        Bounds b = fx(() -> TestKit.screenBounds(c));
        TestKit.check(p.getX() >= b.getMinX() - 2 && p.getX() <= b.getMaxX() + 2
                && p.getY() >= b.getMinY() - 2 && p.getY() <= b.getMaxY() + b.getHeight(),
                "the window of candidates goes to " + TestKit.fmt(p) + ", away from the control at "
                + String.format(Locale.ROOT, "%.0f, %.0f, %.0f × %.0f", b.getMinX(), b.getMinY(), b.getWidth(),
                b.getHeight()));
    }

    // ---------------------------------------------------------------------
    // Helpers

    private static VBox card(String title, Node... content) {
        Label label = new Label(title.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("section");
        VBox box = new VBox(10);
        box.getChildren().add(label);
        box.getChildren().addAll(content);
        box.getStyleClass().add("glass");
        return box;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    private void log(String text) {
        log.add(LocalTime.now().format(CLOCK) + "  " + text);
        if (log.size() > LOG_SIZE) {
            log.remove(0, log.size() - LOG_SIZE);
        }
        logView.scrollTo(log.size() - 1);
        System.out.println(text);
    }

    private static void fx(Runnable r) {
        TestKit.fx(r);
    }

    private static <T> T fx(java.util.function.Supplier<T> s) {
        return TestKit.fx(s);
    }
}
