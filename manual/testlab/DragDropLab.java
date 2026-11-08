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

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A laboratory for drag and drop, after the DnD manual tests of OpenJFX:
 * <ul>
 * <li>a matrix of sources and destinations with each set of transfer modes,
 *     with the modifiers that choose the mode (DndBasic);</li>
 * <li>sources of text, HTML, images, URLs, a custom format and two files whose
 *     contents the destination verifies (DndTestMultiple), dragged to this or
 *     another window or to other applications;</li>
 * <li>drag images from a snapshot or an opaque RGB image
 *     (DndTestDragViewRawImage), with ordinary and extreme offsets (the DnD
 *     page of Monkey Tester);</li>
 * <li>a full press-drag-release gesture, and lists that reorder their items
 *     and move them between each other;</li>
 * <li>a context menu shown when a drag ends, whose items must take clicks
 *     once the drag has given back the pointer;</li>
 * <li>colors dragged as a serialized double[] between swatches and circles
 *     that are sources and destinations at once, a move leaving the source
 *     gray (DragDropColor of the DragDrop toy), and RTF and custom bytes
 *     (DragDropWithControls of the same toy).</li>
 * <li>the pointer grab of a drag: cancelled with Escape, released outside the
 *     window, taken out of the window and back, started in a popup that holds
 *     a grab of its own, and the pointer after each of them;</li>
 * <li>drags that end without completing the drop: on a destination that
 *     refuses it in DRAG_DROPPED, on one that ignores DRAG_DROPPED, and on a
 *     node that accepts nothing;</li>
 * <li>every pair of source and destination modes, and modifiers pressed or
 *     released during the drag;</li>
 * <li>Unicode, 4 MiB of text, a large image, file names that a URI escapes,
 *     and every format at once;</li>
 * <li>the order, targets and coordinates of the events, nested and rotated
 *     destinations, acceptance that changes while hovering, data read during
 *     DRAG_OVER, a mouse-transparent overlay, a source that is its own
 *     destination and one that the drop removes from the scene;</li>
 * <li>gestures that are not drags: an empty dragboard, a DRAG_DETECTED that
 *     starts nothing, simple press-drag-releases with each button, a pressed
 *     child that removes itself, a click on a node that asks for drag
 *     detection at the press;</li>
 * <li>the colors of the drag image, which must vanish after Escape, many
 *     drags in a row, and the pressed state of the source;</li>
 * <li>drags between two windows, from an inactive window and in an extended
 *     stage, and the collection of a window that was dragged in.</li>
 * </ul>
 * Most of them stand for a bug of the JBS on Linux, whose number the test
 * names, so that it does not come back. Every test fails as well when an
 * event handler throws or JavaFX prints that its drag and drop broke.
 * Its tests (see TestKit) drag with the robot between the tiles, from the
 * format sources to the drop target, between the lists and between the
 * colors.
 */
public class DragDropLab {

    /**
     * Runs this lab as an application of its own, with its tests:
     * <pre>java DragDropLab.java</pre>
     * or {@code ./jfx-tester --tests=dnd}.
     */
    public static void main(String[] args) {
        TestLab.launchLab("dnd", args);
    }

    private static final String CSS = TestKit.BASE_CSS + """
        .tile { -fx-border-width: 1; -fx-padding: 7 10 7 10; -fx-spacing: 2; }
        .tile .title { -fx-font-weight: bold; -fx-font-size: 12px; }
        .tile .line { -fx-font-size: 11px; }
        .tile.source { -fx-background-color: #dcfce7; -fx-border-color: #16a34a; -fx-cursor: open-hand; }
        .tile.target { -fx-background-color: #e0f2fe; -fx-border-color: #0284c7; }
        .tile.target.over { -fx-background-color: #bae6fd; }
        .tile.target.dropped { -fx-border-color: #d97706; -fx-border-width: 2; }
        .chip { -fx-background-color: #fef3c7; -fx-border-color: #d97706; -fx-padding: 8 12 8 12; -fx-cursor: open-hand; }
        .chip .title { -fx-font-weight: bold; }
        .chip .detail { -fx-font-size: 11px; }
        .drop-zone { -fx-border-color: #888; -fx-border-style: segments(8, 6); -fx-padding: 10; }
        .drop-zone.accepting { -fx-background-color: #dcfce7; -fx-border-color: #16a34a; }
        .drop-zone.rejecting { -fx-background-color: #ffe4e6; -fx-border-color: #e11d48; }
        .verdict { -fx-font-weight: bold; -fx-padding: 3 10 3 10; }
        .verdict.pass { -fx-background-color: #bbf7d0; -fx-text-fill: #14532d; }
        .verdict.fail { -fx-background-color: #fecdd3; -fx-text-fill: #881337; }
        .full-target { -fx-border-color: #888; -fx-padding: 10; }
        .full-target.over { -fx-background-color: #f5d0fe; -fx-border-color: #a21caf; }
        .list-view .list-cell.drop-before { -fx-border-color: #d97706 transparent transparent transparent; -fx-border-width: 2 0 0 0; }
        """;

    static final String STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(CSS.getBytes(StandardCharsets.UTF_8));

    private static final DataFormat CUSTOM = new DataFormat("application/x-jfx-dragdrop-demo");
    private static final DataFormat LIST_ITEM = new DataFormat("application/x-jfx-dragdrop-demo-item");
    private static final DataFormat BYTES = new DataFormat("application/x-jfx-dragdrop-demo-bytes");
    // Color is not Serializable, so the colors travel as red, green, blue and opacity
    // Only this lab takes it, so that the desktop rejects a drop outside the window
    private static final DataFormat GRAB = new DataFormat("application/x-jfx-dragdrop-demo-grab");
    private static final DataFormat COLOR = new DataFormat("application/x-jfx-dragdrop-demo-color");
    private static final String URL = "https://openjdk.org/projects/openjfx/";
    private static final String HTML = "<b>Bold</b> and <i>italic</i> from <a href=\"https://openjfx.io\">JavaFX</a>";
    private static final String UNICODE = "Olá, ação, Ünïcödé — 日本語のテキスト — עברית — Ελληνικά — "
            + "😀 🇧🇷 👩‍💻 — e\u0301 — \u200b — tab\tnew line\nend";
    private static final String LARGE_TEXT = largeText(4 * 1024 * 1024);
    private static final List<String> ODD_NAMES = List.of("with spaces.txt", "percent%20sign #1.txt",
            "plus+and&ampersand.txt", "ação ü 日本 😀.txt");
    private static final String RTF = "{\\rtf1\\ansi\\uc1{\\colortbl;\\red255\\green0\\blue0;}"
            + "\\uc1\\b\\i FRED\\par rtf\\par text}";
    private static final Color EMPTY = Color.web("#475569");
    private static final int LOG_SIZE = 300;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final TransferMode[] COPY = {TransferMode.COPY};
    private static final TransferMode[] MOVE = {TransferMode.MOVE};
    private static final TransferMode[] LINK = {TransferMode.LINK};
    private static final TransferMode[][] MODE_SETS = {
        TransferMode.ANY, TransferMode.COPY_OR_MOVE, COPY, MOVE, LINK
    };

    /** The contents of the files this application dragged, shared by its windows to verify drops. */
    private static final Map<Path, String> draggedFiles = new HashMap<>();
    private static int windows;

    private enum Accept {
        ANY("Any mode", TransferMode.ANY),
        COPY_OR_MOVE("Copy or move", TransferMode.COPY_OR_MOVE),
        COPY("Copy only", DragDropLab.COPY),
        MOVE("Move only", DragDropLab.MOVE),
        LINK("Link only", DragDropLab.LINK),
        NONE("Reject everything", new TransferMode[0]);

        final String label;
        final TransferMode[] modes;

        Accept(String label, TransferMode[] modes) {
            this.label = label;
            this.modes = modes;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum DragView {
        SNAPSHOT("Snapshot of the source"),
        STRIPES("Opaque RGB stripes"),
        NONE("None (platform default)");

        final String label;

        DragView(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Map<String, Double> OFFSETS = new java.util.LinkedHashMap<>();
    static {
        OFFSETS.put("At the pointer", null);
        OFFSETS.put("0", 0.0);
        OFFSETS.put("10", 10.0);
        OFFSETS.put("33.3", 33.3);
        OFFSETS.put("100", 100.0);
        OFFSETS.put("NaN", Double.NaN);
        OFFSETS.put("Double.MAX_VALUE", Double.MAX_VALUE);
        OFFSETS.put("Double.MIN_VALUE", Double.MIN_VALUE);
        OFFSETS.put("Infinity", Double.POSITIVE_INFINITY);
    }

    private boolean backdropSupported;
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final ListView<String> logView = new ListView<>(log);
    private final CheckBox logOver = new CheckBox("DRAG_OVER");
    private final CheckBox logMouse = new CheckBox("Mouse events of the sources");
    {
        // -Ddragdrop.logMouse=true logs them from the start, to debug the tests
        logMouse.setSelected(Boolean.getBoolean("dragdrop.logMouse"));
    }
    private final ComboBox<DragView> dragViewBox = new ComboBox<>(FXCollections.observableArrayList(DragView.values()));
    private final ComboBox<String> offsetBox = new ComboBox<>(FXCollections.observableArrayList(OFFSETS.keySet()));
    private final ComboBox<Accept> acceptBox = new ComboBox<>(FXCollections.observableArrayList(Accept.values()));
    private final Label zoneTitle = new Label("Drop here");
    private final Label zoneDetail = new Label();
    private final VBox received = new VBox(8);
    private final List<Label> sourceResults = new ArrayList<>();
    private final List<Label> targetResults = new ArrayList<>();
    private Node lastDropTarget;
    private TransferMode lastOverMode;
    private int counter;
    // The formats that the chip "Image and more" adds to its image
    private List<String> imageWith = List.of();

    // What the tests check, written by the event handlers
    final TestKit.Suite suite = new TestKit.Suite("Drag and drop");
    private final List<Node> sourceTiles = new ArrayList<>();
    private final List<Node> targetTiles = new ArrayList<>();
    private final Map<String, Node> chips = new HashMap<>();
    private final Map<String, String> chipDone = new HashMap<>();
    private final List<Label> fullTargets = new ArrayList<>();
    private Label fullSource;
    private String fullDragResult;
    private Node menuSource;
    private Node menuTarget;
    private ContextMenu dragEndMenu;
    private String menuPicked;
    // The mouse events that reached the menu shown after a drag
    private int menuMouseEvents;
    private ListView<String> todoList;
    private ListView<String> doneList;
    private boolean reorderedInList;
    private int drops;
    private String droppedText;
    private Image droppedImage;
    private boolean droppedHasImage;
    private String droppedFiles;
    private List<File> droppedFileList;
    private String droppedHtml;
    private String droppedUrl;
    private Set<DataFormat> droppedTypes;
    private Object droppedCustom;
    private String droppedRtf;
    private byte[] droppedBytes;
    private final List<Shape> swatches = new ArrayList<>();
    private final List<Circle> colorCircles = new ArrayList<>();
    private Label colorText;
    private TransferMode colorDone;
    private static final double STAGE_DRAG_OFFSET = 20;
    private Label grabSource;
    private Label grabTarget;
    private Label grabRefuser;
    private Label grabIgnorer;
    private Clicker grabClicker;
    private Label grabStatus;
    private Button grabPopupButton;
    private Popup grabPopup;
    private Label grabPopupSource;
    // "accepted MODE" or "not accepted" at the DRAG_DONE of the last drag of the card
    private String grabDone;
    // The drops that each destination of the card received, completed or not
    private final Map<Label, Integer> grabDropped = new HashMap<>();
    private int grabEntered;
    private Stage stage;
    private ContextMenu dropMenu;
    private Label dropMenuSource;
    private Label dropMenuTarget;
    private String dropMenuPicked;
    private Label dialogSource;
    private Label dialogTarget;
    private Alert dialogAfterDrag;
    private ButtonType dialogResult;
    private Label stageDragSource;
    private Label stageDragTarget;
    private Stage draggedStage;
    private int stageDragEvents;
    private boolean stageDragReleased;
    private Point2D stageDragEnd;
    private int stageDragClicks;

    // Exceptions on the FX thread, and warnings that the DnD code of JavaFX prints, during any test of the lab
    private static final List<String> fxErrors = new CopyOnWriteArrayList<>();
    private static final List<String> dndWarnings = new CopyOnWriteArrayList<>();
    private static boolean watching;

    // Events: a source, nested destinations that record their events, and destinations of special cases
    private Label seqSource;
    private String seqPayload;
    private boolean seqDetected;
    private VBox seqOuter;
    private Label seqOuterTitle;
    private Label seqInner;
    private final List<String> seqEvents = new ArrayList<>();
    private String seqDropTarget;
    private Point2D seqDropScreen;
    private Point2D seqDropLocal;
    private Point2D seqDropExpected;
    private Object seqDropAccepting;
    private Object seqDropSource;
    private String seqDone;
    private String seqDoneString;
    private Object seqDoneTarget;
    private Label halfTarget;
    private int halfDrops;
    private int halfEntered;
    private Label readTarget;
    private int readOvers;
    private String readProblem;
    private Label underTarget;
    private int underDrops;
    private int overlayHits;
    private Label selfTile;
    private String selfDone;
    private Object selfDoneTarget;
    private HBox removableBox;
    private Label removable;
    private Label removerTarget;
    private String removableDone;
    private boolean removableDoneDetached;
    private Clicker eventsClicker;

    // Situations: gestures that are not plain drags
    private final Counts emptyCounts = new Counts();
    private final Counts noDndCounts = new Counts();
    private final Counts pdrCounts = new Counts();
    private final Counts childCounts = new Counts();
    private Label emptySource;
    private Label noDndSource;
    private Label pdrNode;
    private Label pdrOther;
    private int pdrStray;
    private Pane childBox;
    private Rectangle childRect;
    private Button pdButton;
    private int pdDetected;
    private int pdDone;
    private Label imgSource;
    private String imgDone;
    private Label sitTarget;
    private int sitDrops;
    private Clicker sitClicker;
    private int fullDone;

    // Windows: a second window, an extended stage
    private Label winMainSource;
    private Label winMainTarget;
    private String winMainDone;
    private Object winMainDropSource;
    private int winMainDrops;
    private Stage secondStage;
    private Label winSource;
    private Label winTarget;
    private String winSourceDone;
    private Object winDropSource;
    private int winDrops;
    private Stage extendedStage;
    private Label extSource;
    private Label extTarget;
    private String extDone;
    private Clicker extClicker;

    /** A tile that accepts nothing, to hover and click after a drag. */
    private static final class Clicker {
        final Label node = new Label("Hover and click me");
        int moves;
        int clicks;

        Clicker() {
            node.getStyleClass().add("tile");
            node.setOnMouseMoved(e -> moves++);
            node.setOnMouseClicked(e -> {
                clicks++;
                node.setText("Clicked " + clicks + (clicks == 1 ? " time" : " times"));
            });
        }
    }

    /** What a node saw of a gesture that is not a plain drag and drop. */
    private static final class Counts {
        int detected;
        int dragged;
        int middle;
        int secondary;
        int released;
        int done;

        void reset() {
            detected = dragged = middle = secondary = released = done = 0;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d DRAG_DETECTED, %d MOUSE_DRAGGED, %d MOUSE_RELEASED, %d DRAG_DONE",
                    detected, dragged, released, done);
        }
    }

    /**
     * The lab without its header bar, which the test lab shows in a tab.
     */
    Region content(Stage stage) {
        this.stage = stage;
        backdropSupported = Boolean.getBoolean("glass.gtk4.backdrop.active");
        Tab modes = TestKit.subTab("Transfer modes", column(modesCard()));
        Tab formats = TestKit.subTab("Formats", column(formatsCard()), column(dropCard()));
        Tab colors = TestKit.subTab("Colors", column(colorsCard()));
        Tab gestures = TestKit.subTab("Gestures", column(fullDragCard(), stageDragCard()));
        Tab after = TestKit.subTab("After a drag", column(dragEndMenuCard(), dropMenuCard()),
                column(dialogAfterDragCard()));
        Tab reorder = TestKit.subTab("Reorder", column(reorderCard()));
        Tab grab = TestKit.subTab("Grab", column(grabCard()));
        Tab events = TestKit.subTab("Events", column(eventsCard()), column(specialTargetsCard()));
        Tab situations = TestKit.subTab("Situations", column(gesturesCard()), column(dragImageCard()));
        Tab windowsTab = TestKit.subTab("Windows", column(windowsCard()));
        Tab log = TestKit.subTab("Log", column(logCard()));
        watchErrors();
        defineTests();
        suite.setBeforeRun(() -> {
            stage.toFront();
            stage.requestFocus();
        });
        // The keys that reach the scene, during a drag too, with the mouse events of -Ddragdrop.logMouse
        TestKit.whenInScene(grabSource, scene -> scene.addEventFilter(javafx.scene.input.KeyEvent.ANY, e -> {
            if (logMouse.isSelected()) {
                log(e.getEventType() + " " + e.getCode() + " in the scene" + (drag() ? ", during a drag" : ""));
            }
        }));
        suite.setAfterRobotFailure(this::resetOptions);
        suite.onTab(modes, "Copy", "Move", "Link", "Incompatible modes", "Ctrl chooses COPY", "Shift chooses MOVE",
                "Ctrl+Shift chooses LINK", "Shift pressed during the drag", "Ctrl released during the drag",
                "Every pair of modes");
        suite.onTab(formats, "Text", "Image", "Files", "Custom format", "RTF", "Custom bytes",
                "Rejected by the target", "Extreme image offsets", "HTML", "URL", "Unicode text", "Large text",
                "Large image", "Odd file names", "Every format at once", "Image with each other format");
        suite.onTab(colors, "Color copy", "Color move", "Color as text");
        suite.onTab(gestures, "Full drag", "Full drag released outside the window", "New stage during a drag");
        suite.onTab(after, "Menu after a drag", "Menu after a drop", "Dialog after a drag");
        suite.onTab(reorder, "Move between lists");
        suite.onTab(grab, "Pointer after a drop", "Drop not completed", "Drop not handled",
                "Release over no destination", "Escape cancels a drag", "Drag after a cancelled drag",
                "Release outside the window",
                "Out of the window and back", "Drag from a popup");
        suite.onTab(reorder, "Reorder within a list");
        suite.onTab(events, "Event order", "Nested destinations", "Gesture target at DRAG_DONE",
                "Acceptance changes while hovering",
                "Data read during DRAG_OVER", "Through a mouse-transparent node", "Drop on its own source",
                "Source removed by the drop", "Another button during a drag", "Drag with the secondary button");
        suite.onTab(situations, "Empty dragboard", "Drag detected, no drag started", "Simple press-drag-release",
                "Middle and secondary button drags", "Child removed on press", "Click with setDragDetect(true)",
                "Pressed state after a drag", "Drag image colors", "Twenty drags in a row");
        suite.onTab(windowsTab, "To another window", "From another window", "Drag from an inactive window",
                "Extended stage", "No leak after a drag");
        return TestKit.subTabs(modes, formats, colors, gestures, after, reorder, grab, events, situations,
                windowsTab, log);
    }

    private static VBox column(Node... cards) {
        VBox box = new VBox(14, cards);
        box.setFillWidth(true);
        box.setMinWidth(0);
        return box;
    }

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

    private static Label styled(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    // ---------------------------------------------------------------------
    // Transfer modes: every source against every destination

    private Node modesCard() {
        GridPane grid = new GridPane(8, 8);
        grid.add(styled("SOURCES", "section"), 0, 0);
        grid.add(styled("DESTINATIONS", "section"), 1, 0);
        for (int i = 0; i < MODE_SETS.length; i++) {
            Node source = modeSource(MODE_SETS[i]);
            Node target = modeTarget(MODE_SETS[i]);
            GridPane.setHgrow(source, Priority.ALWAYS);
            GridPane.setHgrow(target, Priority.ALWAYS);
            grid.add(source, 0, i + 1);
            grid.add(target, 1, i + 1);
        }
        Label hint = muted("""
                Drag from a green source to a blue destination; an action is possible when both list it. \
                Drag first without modifiers, then holding Ctrl (COPY), Shift (MOVE) and Ctrl+Shift (LINK) \
                until the drop. A drop must show "Done, accepted MODE" in the source and "Dropped here, \
                proposed MODE" in the destination, with a MODE of both; other combinations must be rejected. \
                Other applications can be the source or the destination as well.""");
        return card("Transfer modes", hint, grid);
    }

    private Node modeSource(TransferMode[] modes) {
        Label result = styled("", "line");
        sourceResults.add(result);
        VBox tile = new VBox(styled(modesText(modes), "title"), result);
        tile.getStyleClass().addAll("tile", "source");
        sourceTiles.add(tile);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setOnDragDetected(e -> {
            Dragboard db = tile.startDragAndDrop(modes);
            ClipboardContent content = new ClipboardContent();
            content.putString(UUID.randomUUID().toString());
            db.setContent(content);
            sourceResults.forEach(l -> l.setText(""));
            targetResults.forEach(l -> l.setText(""));
            result.setText("Dragging");
            log("DRAG_DETECTED source " + modesText(modes));
            e.consume();
        });
        tile.setOnDragDone(e -> {
            String text = "Done, " + (e.isAccepted() ? "accepted" : "rejected")
                    + (e.getTransferMode() != null ? " " + e.getTransferMode() : "");
            result.setText(text);
            log("DRAG_DONE source " + modesText(modes) + ": " + text);
            e.consume();
        });
        logMouseEvents(tile, "source " + modesText(modes));
        return tile;
    }

    private Node modeTarget(TransferMode[] modes) {
        Label available = styled("", "line");
        Label result = styled("", "line");
        targetResults.add(result);
        VBox tile = new VBox(styled(modesText(modes), "title"), available, result);
        tile.getStyleClass().addAll("tile", "target");
        targetTiles.add(tile);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setOnDragEntered(e -> {
            tile.getStyleClass().add("over");
            e.consume();
        });
        tile.setOnDragOver(e -> {
            e.acceptTransferModes(modes);
            targetResults.forEach(l -> l.setText(""));
            if (e.getGestureSource() == null) {
                sourceResults.forEach(l -> l.setText(""));
            }
            result.setText(e.getTransferMode() != null ? "Proposed " + e.getTransferMode() : "");
            // The modes of the source, which the modifiers may filter
            available.setText("Source offers " + modesText(e.getDragboard().getTransferModes()));
            e.consume();
        });
        tile.setOnDragDropped(e -> {
            lastDropTarget = tile;
            String text = "Dropped here" + (e.getTransferMode() != null ? ", proposed " + e.getTransferMode() : "");
            result.setText(text);
            log("DRAG_DROPPED destination " + modesText(modes) + ": " + text + " from " + source(e));
            e.setDropCompleted(true);
            e.consume();
        });
        tile.setOnDragExited(e -> {
            tile.getStyleClass().remove("over");
            available.setText("");
            if (tile != lastDropTarget) {
                result.setText("");
            }
            e.consume();
        });
        return tile;
    }

    private static String modesText(TransferMode[] modes) {
        return modesText(Set.of(modes));
    }

    private static String modesText(Set<TransferMode> modes) {
        String text = Arrays.stream(TransferMode.values())
                .filter(modes::contains)
                .map(Enum::name)
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "nothing" : text;
    }

    // ---------------------------------------------------------------------
    // Data formats and drag images

    private Node formatsCard() {
        dragViewBox.setValue(DragView.valueOf(System.getProperty("dragdrop.dragView", "SNAPSHOT")));
        offsetBox.setValue("At the pointer");
        GridPane options = new GridPane(10, 6);
        options.addRow(0, new Label("Drag image"), dragViewBox);
        options.addRow(1, new Label("Image offset"), offsetBox);

        FlowPane chips = new FlowPane(8, 8,
                chip("Text", "text/plain", c -> c.putString("Hello from JavaFX at " + now())),
                chip("HTML", "text/html + text/plain", c -> {
                    c.putHtml(HTML);
                    c.putString("Bold and italic from JavaFX");
                }),
                chip("Image", "image/png", sampleImage, c -> c.putImage(sampleImage)),
                chip("RGB stripes", "image/png, the drag image too", stripesImage,
                        c -> c.putImage(stripesImage)),
                chip("Two files", "text/uri-list, verified on drop", c -> c.putFiles(sampleFiles())),
                chip("URL", "text/uri-list + text/plain", c -> {
                    c.putUrl(URL);
                    c.putString(URL);
                }),
                chip("Custom", CUSTOM.getIdentifiers().iterator().next(),
                        c -> c.put(CUSTOM, "Custom payload #" + (++counter))),
                chip("RTF", "text/rtf + text/plain", c -> {
                    c.putRtf(RTF);
                    c.putString("FRED rtf text");
                }),
                chip("Bytes", "four bytes, serialized", c -> c.put(BYTES, new byte[] {1, 2, 3, 4})),
                chip("Unicode", "accents, CJK, RTL, emoji", c -> c.putString(UNICODE)),
                chip("Large text", "4 MiB, in chunks over X11", c -> c.putString(LARGE_TEXT)),
                chip("Large image", "1600 × 1200 gradient", c -> c.putImage(largeImage)),
                chip("Odd file names", "spaces, %, #, +, &, accents, emoji", c -> c.putFiles(oddFiles())),
                chip("Image and more", "the image and the formats of a test", sampleImage, c -> {
                    c.putImage(sampleImage);
                    for (String f : imageWith) {
                        switch (f) {
                            case "text" -> c.putString("Text with the image");
                            case "HTML" -> c.putHtml("<p>HTML with the image</p>");
                            case "files" -> c.putFiles(sampleFiles());
                            case "URL" -> c.putUrl(URL);
                            case "custom" -> c.put(CUSTOM, "Custom with the image");
                            default -> throw new IllegalArgumentException(f);
                        }
                    }
                }),
                chip("Everything", "all of the above", c -> {
                    c.putString("Everything from JavaFX");
                    c.putHtml("<p>Everything from <b>JavaFX</b></p>");
                    c.putImage(sampleImage);
                    c.putFiles(sampleFiles());
                    c.put(CUSTOM, "Custom payload #" + (++counter));
                }));

        Label hint = muted("These sources offer every mode. Drop them on the drop target, on another window, "
                + "on a file manager (Image, Two files) or on a text editor (Text, HTML, URL). The colors of "
                + "the RGB stripes drag image must match the source, red, green and blue, even if its size "
                + "differs. Extreme offsets must not break the drag.");
        return card("Data formats", options, chips, hint);
    }

    private Node chip(String title, String detail, Consumer<ClipboardContent> filler) {
        return chip(title, detail, null, filler);
    }

    // A chip with an image shows it, so the dropped image can be compared with the source
    private Node chip(String title, String detail, Image preview, Consumer<ClipboardContent> filler) {
        VBox chip = new VBox(2, styled(title, "title"), styled(detail, "detail"));
        if (preview != null) {
            ImageView thumbnail = new ImageView(preview);
            thumbnail.setPreserveRatio(true);
            thumbnail.setFitHeight(64);
            VBox.setMargin(thumbnail, new Insets(4, 0, 0, 0));
            chip.getChildren().add(thumbnail);
        }
        chip.getStyleClass().add("chip");
        chips.put(title, chip);
        chip.setOnDragDetected(e -> {
            Dragboard db = chip.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            filler.accept(content);
            db.setContent(content);
            setDragView(db, chip, e, title.equals("RGB stripes"));
            log("DRAG_DETECTED " + title + ": formats " + formats(content.keySet()) + ", drag image "
                    + dragViewBox.getValue() + ", offset " + offsetBox.getValue());
            e.consume();
        });
        chip.setOnDragDone(e -> {
            chipDone.put(title, e.getTransferMode() == null ? "not accepted" : e.getTransferMode().name());
            log("DRAG_DONE " + title + ": " + (e.getTransferMode() == null ? "not accepted" : e.getTransferMode()));
            e.consume();
        });
        logMouseEvents(chip, "chip " + title);
        return chip;
    }

    private void setDragView(Dragboard db, Node source, MouseEvent e, boolean stripes) {
        DragView view = stripes ? DragView.STRIPES : dragViewBox.getValue();
        Image image = switch (view) {
            case SNAPSHOT -> {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                yield source.snapshot(params, null);
            }
            case STRIPES -> stripesImage;
            case NONE -> null;
        };
        if (image == null) {
            return;
        }
        Double offset = OFFSETS.get(offsetBox.getValue());
        if (offset == null) {
            boolean snapshot = view == DragView.SNAPSHOT;
            db.setDragView(image, snapshot ? e.getX() : image.getWidth() / 2, snapshot ? e.getY() : image.getHeight() / 2);
        } else {
            db.setDragView(image);
            db.setDragViewOffsetX(offset);
            db.setDragViewOffsetY(offset);
        }
    }

    private final Image sampleImage = sampleImage();
    private final Image stripesImage = stripes(240, 240);
    private final Image largeImage = gradient(1600, 1200);

    /**
     * An opaque gradient whose every pixel tells its position.
     */
    private static Image gradient(int w, int h) {
        WritableImage image = new WritableImage(w, h);
        PixelWriter pw = image.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setArgb(x, y, gradientArgb(x, y, w, h));
            }
        }
        return image;
    }

    private static int gradientArgb(int x, int y, int w, int h) {
        return 0xff000000 | (x * 255 / (w - 1)) << 16 | (y * 255 / (h - 1)) << 8 | ((x + y) & 0xff);
    }

    /**
     * Lines of text that number themselves, so that a lost or repeated chunk
     * shows.
     */
    private static String largeText(int size) {
        StringBuilder sb = new StringBuilder(size + 64);
        for (int line = 0; sb.length() < size; line++) {
            sb.append(String.format(Locale.ROOT, "%08d The quick brown fox jumps over the lazy dog.\n", line));
        }
        sb.setLength(size);
        return sb.toString();
    }

    /**
     * A sunset over mountains and a lake, with a translucent corner so the
     * alpha channel is transferred as well.
     */
    private static Image sampleImage() {
        int w = 240, h = 160;
        Color skyTop = Color.web("#312e81"), skyBottom = Color.web("#f97316");
        Color sun = Color.web("#fde68a"), far = Color.web("#7c3aed"), near = Color.web("#1e1b4b");
        Color water = Color.web("#0f172a");
        WritableImage image = new WritableImage(w, h);
        PixelWriter pw = image.getPixelWriter();
        double horizon = h * 0.68;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Below the horizon the scene is mirrored and darkened
                boolean below = y > horizon;
                double sy = below ? 2 * horizon - y : y;
                double dx = x - w * 0.6, dy = sy - h * 0.42;
                double farRidge = h * 0.5 + 10 * Math.sin(x * 0.045) + 6 * Math.sin(x * 0.11 + 1);
                double nearRidge = h * 0.6 + 8 * Math.sin(x * 0.07 + 2) + 4 * Math.sin(x * 0.19);
                Color c;
                if (sy > nearRidge) {
                    c = near;
                } else if (sy > farRidge) {
                    c = far;
                } else if (dx * dx + dy * dy < 22 * 22) {
                    c = sun;
                } else {
                    double glow = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy) / 90);
                    c = skyTop.interpolate(skyBottom, sy / horizon).interpolate(sun, glow * 0.5);
                }
                if (below) {
                    double ripple = 0.5 + 0.1 * Math.sin(y * 1.3 + x * 0.05);
                    c = c.interpolate(water, ripple);
                }
                double corner = Math.hypot(x, y);
                double alpha = corner < 40 ? 0.35 + 0.65 * corner / 40 : 1;
                pw.setColor(x, y, c.deriveColor(0, 1, 1, alpha));
            }
        }
        return image;
    }

    /**
     * Opaque vertical stripes of pure red, green and blue.
     */
    private static Image stripes(int w, int h) {
        WritableImage image = new WritableImage(w, h);
        PixelWriter pw = image.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setArgb(x, y, x < w * 0.33 ? 0xffff0000 : x < w * 0.66 ? 0xff00ff00 : 0xff0000ff);
            }
        }
        return image;
    }

    private static List<File> sampleFiles() {
        return files(List.of("dnd-test-file-1.txt", "dnd-test-file-2.txt"));
    }

    // JDK-8131481, JDK-8124934, JDK-8231642: names that a URI escapes
    private static List<File> oddFiles() {
        return files(ODD_NAMES);
    }

    private static List<File> files(List<String> names) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "jfx-dragdrop-demo");
            Files.createDirectories(dir);
            List<File> files = new ArrayList<>();
            for (int i = 1; i <= names.size(); i++) {
                Path file = dir.resolve(names.get(i - 1));
                String text = "test_string_" + i + " " + UUID.randomUUID();
                Files.writeString(file, text);
                synchronized (draggedFiles) {
                    draggedFiles.put(file.toAbsolutePath(), text);
                }
                files.add(file.toFile());
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------------
    // Drop target

    private Node dropCard() {
        acceptBox.setValue(Accept.ANY);
        HBox options = new HBox(10, new Label("Accepts"), acceptBox);
        options.setAlignment(Pos.CENTER_LEFT);

        zoneTitle.getStyleClass().add("big");
        zoneDetail.getStyleClass().add("muted");
        zoneDetail.setWrapText(true);
        received.getChildren().add(muted("Nothing dropped yet. Drops from other applications (files from a "
                + "file manager, text or images from a browser) land here as well."));
        VBox zone = new VBox(8, zoneTitle, zoneDetail, received);
        zone.getStyleClass().add("drop-zone");
        zone.setMinHeight(230);
        VBox.setVgrow(zone, Priority.ALWAYS);

        zone.setOnDragEntered(e -> {
            Dragboard db = e.getDragboard();
            boolean accepts = acceptBox.getValue() != Accept.NONE;
            zone.getStyleClass().add(accepts ? "accepting" : "rejecting");
            zoneTitle.setText(accepts ? "Release to drop" : "This target rejects drops");
            zoneDetail.setText("From " + source(e) + ", offers " + modesText(db.getTransferModes()) + "\n"
                    + formats(db.getContentTypes()));
            log("DRAG_ENTERED from " + source(e) + ", offers " + modesText(db.getTransferModes())
                    + ", formats " + formats(db.getContentTypes()));
            lastOverMode = null;
            e.consume();
        });
        zone.setOnDragOver(e -> {
            if (!e.getDragboard().getContentTypes().isEmpty()) {
                e.acceptTransferModes(acceptBox.getValue().modes);
            }
            TransferMode mode = e.getTransferMode();
            if (logOver.isSelected()) {
                log(String.format(Locale.ROOT, "DRAG_OVER at %.1f, %.1f (screen %.1f, %.1f): %s",
                        e.getX(), e.getY(), e.getScreenX(), e.getScreenY(), mode));
            } else if (mode != lastOverMode) {
                log("DRAG_OVER: proposed " + mode);
            }
            lastOverMode = mode;
            e.consume();
        });
        zone.setOnDragExited(e -> {
            zone.getStyleClass().removeAll("accepting", "rejecting");
            zoneTitle.setText("Drop here");
            log("DRAG_EXITED");
            e.consume();
        });
        zone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            log("DRAG_DROPPED as " + e.getTransferMode() + " from " + source(e));
            boolean ok = showReceived(db, e.getTransferMode(), source(e));
            e.setDropCompleted(ok);
            e.consume();
        });

        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            received.getChildren().setAll(muted("Cleared"));
            zoneDetail.setText("");
        });
        VBox box = card("Drop target", options, zone, clear);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /**
     * Shows the dropped content, and verifies the files this application
     * dragged.
     *
     * @return false when a dragged file does not hold what was written to it
     */
    private boolean showReceived(Dragboard db, TransferMode mode, String source) {
        drops++;
        droppedText = db.hasString() ? db.getString() : null;
        // getImage apart from hasImage, which answers false with HTML before the image (see "Image with each
        // other format")
        droppedHasImage = db.hasImage();
        droppedImage = db.getContentTypes().contains(DataFormat.IMAGE) ? db.getImage() : null;
        droppedFiles = db.hasFiles() ? verifyFiles(db.getFiles()) : null;
        droppedFileList = db.hasFiles() ? List.copyOf(db.getFiles()) : null;
        droppedHtml = db.hasHtml() ? db.getHtml() : null;
        droppedUrl = db.hasUrl() ? db.getUrl() : null;
        droppedTypes = Set.copyOf(db.getContentTypes());
        droppedCustom = db.hasContent(CUSTOM) ? db.getContent(CUSTOM) : null;
        droppedRtf = db.hasRtf() ? db.getRtf() : null;
        droppedBytes = deserialize(db.getContent(BYTES), byte[].class);
        received.getChildren().clear();
        Label head = new Label("Received " + mode + " from " + source + " at " + now());
        head.setStyle("-fx-font-weight: bold;");
        received.getChildren().add(head);
        boolean ok = true;
        if (db.hasString()) {
            received.getChildren().add(entry("Text", db.getString()));
        }
        if (db.hasHtml()) {
            received.getChildren().add(entry("HTML", db.getHtml()));
        }
        if (db.hasUrl()) {
            received.getChildren().add(entry("URL", db.getUrl()));
        }
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            received.getChildren().add(entry("Files", files.stream()
                    .map(f -> f.getAbsolutePath() + (f.exists() ? " (" + f.length() + " bytes)" : " (missing)"))
                    .collect(Collectors.joining("\n"))));
            String failure = verifyFiles(files);
            if (failure != null) {
                ok = !failure.startsWith("FAILED");
                Label verdict = new Label(failure);
                verdict.getStyleClass().addAll("verdict", ok ? "pass" : "fail");
                received.getChildren().add(verdict);
                log("Files: " + failure);
            }
        }
        if (db.hasContent(CUSTOM)) {
            received.getChildren().add(entry("Custom", String.valueOf(db.getContent(CUSTOM))));
        }
        if (db.hasRtf()) {
            received.getChildren().add(entry("RTF", db.getRtf()));
        }
        if (droppedBytes != null) {
            received.getChildren().add(entry("Bytes", Arrays.toString(droppedBytes)));
        }
        if (db.hasImage()) {
            Image image = db.getImage();
            if (image != null) {
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setFitWidth(Math.min(220, image.getWidth()));
                received.getChildren().addAll(entry("Image", String.format(Locale.ROOT, "%.0f × %.0f",
                        image.getWidth(), image.getHeight())), view);
            } else {
                received.getChildren().add(entry("Image", "advertised, but could not be read"));
            }
        }
        if (received.getChildren().size() == 1) {
            received.getChildren().add(entry("Formats", formats(db.getContentTypes())));
        }
        return ok;
    }

    /**
     * Checks the contents of the dropped files that this application wrote.
     *
     * @return the verdict, or null when none of the files was dragged from here
     */
    private static String verifyFiles(List<File> files) {
        int checked = 0;
        for (File f : files) {
            String expected;
            synchronized (draggedFiles) {
                expected = draggedFiles.get(f.toPath().toAbsolutePath());
            }
            if (expected == null) {
                continue;
            }
            checked++;
            try {
                String actual = Files.readString(f.toPath());
                if (!actual.equals(expected)) {
                    return "FAILED: the contents of " + f.getName() + " differ";
                }
            } catch (IOException e) {
                return "FAILED: " + f.getName() + ": " + e.getMessage();
            }
        }
        return checked == 0 ? null : "SUCCESS: " + checked + " of " + files.size() + " files verified";
    }

    /**
     * Dropped content of a serializable type. JavaFX deserializes only strings,
     * unless -Djavafx.dnd.deserializeObjectGraph=true, and otherwise hands over
     * the serialized bytes, which are read here allowing just that type.
     */
    private static <T> T deserialize(Object content, Class<T> type) {
        if (type.isInstance(content)) {
            return type.cast(content);
        }
        if (!(content instanceof ByteBuffer buffer)) {
            return null;
        }
        ByteBuffer copy = buffer.duplicate().rewind();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(info -> info.serialClass() == null ? ObjectInputFilter.Status.UNDECIDED
                    : info.serialClass() == type ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED);
            Object value = in.readObject();
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    private static Node entry(String key, String value) {
        Label k = styled(key, "section");
        k.setMinWidth(54);
        String shown = value == null ? "null" : value.length() > 400 ? value.substring(0, 400) + "…" : value;
        Label v = styled(shown, "mono");
        v.setWrapText(true);
        HBox box = new HBox(10, k, v);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private static String source(DragEvent e) {
        Object source = e.getGestureSource();
        if (source == null) {
            return "another application";
        }
        if (source instanceof Node n && n.getScene() != null && e.getSource() instanceof Node t
                && t.getScene() != null && n.getScene().getWindow() != t.getScene().getWindow()) {
            return "another JavaFX window";
        }
        return "this window";
    }

    private static String formats(Set<DataFormat> formats) {
        return formats.stream()
                .map(f -> f.getIdentifiers().iterator().next())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    // ---------------------------------------------------------------------
    // Full press-drag-release

    private Node fullDragCard() {
        Label source = new Label("Press here and drag");
        source.getStyleClass().addAll("tile", "source");
        fullSource = source;
        source.setOnDragDetected(e -> {
            source.startFullDrag();
            log("startFullDrag()");
            e.consume();
        });
        // JDK-8365635: the end of the gesture, wherever the button goes up
        source.addEventHandler(MouseDragEvent.MOUSE_DRAG_DONE, e -> {
            fullDone++;
            log("MOUSE_DRAG_DONE on the full drag source");
        });
        FlowPane targets = new FlowPane(8, 8);
        for (String name : List.of("One", "Two", "Three")) {
            Label target = new Label(name);
            target.getStyleClass().add("full-target");
            target.setOnMouseDragEntered(e -> target.getStyleClass().add("over"));
            target.setOnMouseDragExited(e -> target.getStyleClass().remove("over"));
            fullTargets.add(target);
            target.setOnMouseDragReleased(e -> {
                target.getStyleClass().remove("over");
                fullDragResult = name + (e.getGestureSource() == source ? " from the source" : "");
                log(String.format(Locale.ROOT, "MOUSE_DRAG_RELEASED on %s at %.1f, %.1f from %s", name,
                        e.getX(), e.getY(), e.getGestureSource() == source ? "the source" : e.getGestureSource()));
            });
            target.addEventHandler(MouseDragEvent.MOUSE_DRAG_ENTERED, e -> log("MOUSE_DRAG_ENTERED " + name));
            targets.getChildren().add(target);
        }
        HBox row = new HBox(12, source, targets);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("A full press-drag-release gesture stays inside JavaFX: the targets get "
                + "MOUSE_DRAG events and no platform drag starts.");
        return card("Full drag", row, hint);
    }

    // ---------------------------------------------------------------------
    // Colors, after DragDropColor of the DragDrop toy

    private Node colorsCard() {
        HBox sources = new HBox(8);
        for (Color c : List.of(Color.web("#ef4444"), Color.web("#22c55e"), Color.web("#3b82f6"))) {
            Rectangle swatch = new Rectangle(34, 34, c);
            swatch.setArcWidth(10);
            swatch.setArcHeight(10);
            colorSource(swatch, TransferMode.COPY);
            swatches.add(swatch);
            sources.getChildren().add(swatch);
        }
        HBox circles = new HBox(12);
        for (int i = 0; i < 2; i++) {
            Circle circle = new Circle(30, EMPTY);
            circle.setStroke(Color.rgb(255, 255, 255, 0.3));
            colorSource(circle, TransferMode.COPY_OR_MOVE);
            colorTarget(circle);
            colorCircles.add(circle);
            circles.getChildren().add(circle);
        }
        colorText = new Label("Text drop target");
        colorText.getStyleClass().add("full-target");
        colorText.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        colorText.setOnDragDropped(e -> {
            colorText.setText(e.getDragboard().getString());
            e.setDropCompleted(e.getDragboard().hasString());
            e.consume();
        });
        HBox row = new HBox(16, sources, circles, colorText);
        row.setAlignment(Pos.CENTER_LEFT);
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            colorCircles.forEach(c -> c.setFill(EMPTY));
            colorText.setText("Text drop target");
        });
        Label hint = muted("The swatches copy their color; the circles copy or move it (Shift), and a moved "
                + "color leaves its circle gray. The color travels as a serialized double[], and as text "
                + "for the text target and other applications.");
        return card("Colors", row, hint, clear);
    }

    private void colorSource(Shape shape, TransferMode... modes) {
        shape.setOnDragDetected(e -> {
            if (!(shape.getFill() instanceof Color c) || c.equals(EMPTY)) {
                return;
            }
            Dragboard db = shape.startDragAndDrop(modes);
            ClipboardContent content = new ClipboardContent();
            content.putString(c.toString());
            content.put(COLOR, new double[] {c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()});
            db.setContent(content);
            log("DRAG_DETECTED color " + c);
            e.consume();
        });
        shape.setOnDragDone(e -> {
            colorDone = e.getTransferMode();
            if (e.getTransferMode() == TransferMode.MOVE) {
                shape.setFill(EMPTY);
            }
            log("DRAG_DONE color: " + (e.getTransferMode() == null ? "not accepted" : e.getTransferMode()));
            e.consume();
        });
        logMouseEvents(shape, "color");
    }

    private void colorTarget(Circle circle) {
        circle.setOnDragOver(e -> {
            if (e.getGestureSource() != circle && e.getDragboard().hasContent(COLOR)) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        circle.setOnDragDropped(e -> {
            boolean ok = false;
            double[] a = deserialize(e.getDragboard().getContent(COLOR), double[].class);
            if (a != null && a.length == 4) {
                circle.setFill(new Color(a[0], a[1], a[2], a[3]));
                ok = true;
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    // ---------------------------------------------------------------------
    // A context menu when a drag ends

    private Node dragEndMenuCard() {
        Label result = styled("", "line");
        dragEndMenu = new ContextMenu();
        for (String name : List.of("Copy here", "Move here", "Cancel")) {
            MenuItem item = new MenuItem(name);
            item.setId(name.replace(" ", "-").toLowerCase(Locale.ROOT));
            item.setOnAction(e -> {
                menuPicked = name;
                result.setText("Picked \"" + name + "\"");
                log("Menu item \"" + name + "\" after the drag");
            });
            dragEndMenu.getItems().add(item);
        }
        // Where the clicks of the menu go, with -Ddragdrop.logMouse
        dragEndMenu.setOnShown(e -> dragEndMenu.getScene().addEventFilter(MouseEvent.ANY, m -> {
            menuMouseEvents++;
            if (logMouse.isSelected() && m.getEventType() != MouseEvent.MOUSE_MOVED) {
                log(m.getEventType() + " in the menu shown after the drag, on " + m.getTarget());
            }
        }));
        VBox source = new VBox(styled("Drag me", "title"), result);
        source.getStyleClass().addAll("tile", "source");
        menuSource = source;
        Label target = new Label("Drop here");
        target.getStyleClass().addAll("tile", "target");
        menuTarget = target;
        source.setOnDragDetected(e -> {
            Dragboard db = source.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("A menu opens when this drag ends");
            db.setContent(content);
            result.setText("Dragging");
            e.consume();
        });
        target.setOnDragOver(e -> {
            if (e.getGestureSource() == source) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        target.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
        });
        // Shown from DRAG_DONE, while the platform may still be ending the drag
        source.setOnDragDone(e -> {
            result.setText("Done, " + (e.isAccepted() ? "accepted" : "rejected") + ": pick an item");
            log("DRAG_DONE menu source: showing the context menu");
            dragEndMenu.show(target, Side.BOTTOM, 0, 4);
            e.consume();
        });
        HBox row = new HBox(12, source, target);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("When the drag ends, the source shows a context menu under the destination. Its "
                + "items must highlight under the pointer and take clicks, as the drag no longer holds the "
                + "pointer.");
        return card("Menu after a drag", row, hint);
    }

    // ---------------------------------------------------------------------
    // Reorderable lists

    /**
     * JDK-8395594: a context menu shown at the end of a drop, while the FX
     * thread is busy, must take the mouse.
     */
    private Node dropMenuCard() {
        dropMenu = new ContextMenu();
        for (String name : List.of("Alpha", "Beta", "Gamma")) {
            MenuItem item = new MenuItem(name);
            item.setId("drop-" + name.toLowerCase(Locale.ROOT));
            item.setOnAction(e -> {
                dropMenuPicked = name;
                log("Menu item \"" + name + "\" after the drop");
            });
            dropMenu.getItems().add(item);
        }
        dropMenuSource = new Label("Drag me");
        dropMenuSource.getStyleClass().addAll("tile", "source");
        dropMenuTarget = new Label("Drop here");
        dropMenuTarget.getStyleClass().addAll("tile", "target");
        dropMenuSource.setOnDragDetected(e -> {
            Dragboard db = dropMenuSource.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString("A menu opens at the drop");
            db.setContent(content);
            e.consume();
        });
        dropMenuTarget.setOnDragOver(e -> {
            e.acceptTransferModes(TransferMode.ANY);
            e.consume();
        });
        dropMenuTarget.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
            double x = e.getScreenX(), y = e.getScreenY();
            Platform.runLater(() -> dropMenu.show(dropMenuTarget.getScene().getWindow(), x, y));
        });
        HBox row = new HBox(12, dropMenuSource, dropMenuTarget);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("JDK-8395594: the menu opens at the drop. Its items must highlight under the pointer "
                + "and take clicks, which must not reach the window below.");
        return card("Menu after a drop", row, hint);
    }

    /**
     * JDK-8375965: the buttons of a dialog shown after a drag must take clicks.
     */
    private Node dialogAfterDragCard() {
        dialogSource = new Label("Drag me");
        dialogSource.getStyleClass().addAll("tile", "source");
        dialogTarget = new Label("Drop here");
        dialogTarget.getStyleClass().addAll("tile", "target");
        dialogSource.setOnDragDetected(e -> {
            Dragboard db = dialogSource.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString("text");
            db.setContent(content);
            e.consume();
        });
        dialogTarget.setOnDragOver(e -> {
            if (e.getGestureSource() == dialogSource) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        dialogTarget.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
        });
        Button open = new Button("Open the dialog");
        open.setOnAction(e -> showDialogAfterDrag());
        HBox row = new HBox(12, dialogSource, dialogTarget, open);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("JDK-8375965: drag to the destination, then open the dialog: its OK and Cancel "
                + "buttons must take clicks.");
        return card("Dialog after a drag", row, hint);
    }

    private void showDialogAfterDrag() {
        dialogAfterDrag = new Alert(Alert.AlertType.CONFIRMATION, "Quit?");
        dialogAfterDrag.initOwner(stage);
        dialogResult = null;
        dialogAfterDrag.showAndWait().ifPresent(b -> dialogResult = b);
        log("The dialog after the drag answered " + dialogResult);
    }

    /**
     * JDK-8292922: a stage created in a drag handler must not take the drag
     * away, as docking frameworks drag such a stage. It shows under the
     * pointer, the harder case.
     */
    private Node stageDragCard() {
        stageDragSource = new Label("Drag me");
        stageDragSource.getStyleClass().addAll("tile", "source");
        stageDragTarget = new Label("Then click here");
        stageDragTarget.getStyleClass().addAll("tile", "target");
        stageDragTarget.setOnMouseClicked(e -> stageDragClicks++);
        stageDragSource.setOnMouseDragged(e -> {
            if (draggedStage == null) {
                Pane content = new Pane();
                content.setPrefSize(120, 60);
                content.setStyle("-fx-background-color: rgba(59, 130, 246, 0.5);");
                Scene scene = new Scene(content);
                scene.setFill(null);
                draggedStage = new Stage(StageStyle.TRANSPARENT);
                draggedStage.initOwner(stage);
                draggedStage.setScene(scene);
                draggedStage.setX(e.getScreenX() - STAGE_DRAG_OFFSET);
                draggedStage.setY(e.getScreenY() - STAGE_DRAG_OFFSET);
                draggedStage.show();
                log("A new stage at the first MOUSE_DRAGGED");
            } else {
                stageDragEvents++;
                draggedStage.setX(e.getScreenX() - STAGE_DRAG_OFFSET);
                draggedStage.setY(e.getScreenY() - STAGE_DRAG_OFFSET);
            }
        });
        stageDragSource.setOnMouseReleased(e -> {
            stageDragReleased = true;
            if (draggedStage != null) {
                stageDragEnd = new Point2D(draggedStage.getX() + STAGE_DRAG_OFFSET,
                        draggedStage.getY() + STAGE_DRAG_OFFSET);
                log(String.format(Locale.ROOT, "MOUSE_RELEASED after %d MOUSE_DRAGGED on the new stage",
                        stageDragEvents));
            }
            closeDraggedStage();
        });
        HBox row = new HBox(12, stageDragSource, stageDragTarget);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("JDK-8292922: dragging the source creates an undecorated stage, under the pointer, "
                + "that must follow it until the release. The mouse must work afterwards.");
        return card("New stage during a drag", row, hint);
    }

    private void closeDraggedStage() {
        if (draggedStage != null) {
            draggedStage.close();
            draggedStage = null;
        }
    }

    // ---------------------------------------------------------------------
    // The pointer grab of a drag, and drags that do not complete the drop

    /**
     * While a drag lasts, the platform holds the pointer and the keyboard; when
     * it ends, completed or not, it must give them back, and JavaFX must not
     * think that the button is still down. The source offers only a format of
     * this lab, so that nothing outside the window takes it.
     */
    private Node grabCard() {
        grabSource = new Label("Drag me");
        grabSource.getStyleClass().addAll("tile", "source");
        grabTarget = grabTarget("Drop here", true, true);
        grabRefuser = grabTarget("Accepts, then refuses the drop", true, false);
        grabIgnorer = grabTarget("Accepts, ignores the drop", false, false);
        grabClicker = new Clicker();
        grabStatus = styled("", "line");
        grabSource(grabSource);

        grabPopupSource = new Label("Drag me out of the popup");
        grabPopupSource.setStyle("-fx-background-color: #1e3a8a; -fx-text-fill: white; -fx-padding: 18 22 18 22; "
                + "-fx-background-radius: 8; -fx-border-color: #60a5fa; -fx-border-radius: 8;");
        grabSource(grabPopupSource);
        grabPopup = new Popup();
        grabPopup.setAutoHide(true);
        grabPopup.getContent().add(grabPopupSource);
        grabPopupButton = new Button("Open the popup");
        grabPopupButton.setOnAction(e -> showGrabPopup());

        HBox row = new HBox(12, grabSource, grabTarget, grabClicker.node);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox refusers = new HBox(12, grabRefuser, grabIgnorer);
        refusers.setAlignment(Pos.CENTER_LEFT);
        HBox popupRow = new HBox(12, grabPopupButton, grabStatus);
        popupRow.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("Drag the source and press Escape, release it outside the window or on the tile "
                + "that accepts nothing, or take it out and back to the destination. The two lower destinations "
                + "accept the drag, and then one calls setDropCompleted(false) and the other does not handle "
                + "DRAG_DROPPED: the drag must end not accepted. The popup holds a grab of its own, as an "
                + "auto-hiding popup does. After each drag, the tile must highlight under the pointer and take "
                + "clicks, with no MOUSE_DRAGGED while no button is down.");
        return card("Pointer grab and incomplete drops", row, refusers, popupRow, hint);
    }

    /**
     * A destination that accepts the drag in DRAG_OVER and, at the drop,
     * completes it, refuses it or, unhandled, leaves it.
     */
    private Label grabTarget(String text, boolean handleDrop, boolean complete) {
        Label target = new Label(text);
        target.getStyleClass().addAll("tile", "target");
        target.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(GRAB)) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (target == grabTarget) {
                grabEntered++;
            }
            e.consume();
        });
        // Counted by a filter, so that the ignoring destination leaves DRAG_DROPPED unhandled
        target.addEventFilter(DragEvent.DRAG_DROPPED, e -> {
            grabDropped.merge(target, 1, Integer::sum);
            log("Grab DRAG_DROPPED on \"" + text + "\" from " + e.getDragboard().getContent(GRAB));
        });
        if (handleDrop) {
            target.setOnDragDropped(e -> {
                e.setDropCompleted(complete);
                e.consume();
            });
        }
        return target;
    }

    private int grabDrops(Label target) {
        return grabDropped.getOrDefault(target, 0);
    }

    private void grabSource(Label source) {
        String name = source == grabSource ? "the source" : "the popup";
        source.setOnDragDetected(e -> {
            Dragboard db = source.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.put(GRAB, name);
            db.setContent(content);
            grabDone = null;
            grabStatus.setText("Dragging from " + name);
            e.consume();
        });
        source.setOnDragDone(e -> {
            grabDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            grabStatus.setText("Done from " + name + ", " + grabDone);
            log("Grab DRAG_DONE from " + name + ", " + grabDone);
            e.consume();
        });
        logMouseEvents(source, name);
    }

    private void showGrabPopup() {
        javafx.geometry.Bounds b = grabPopupButton.localToScreen(grabPopupButton.getLayoutBounds());
        grabPopup.show(grabPopupButton, b.getMinX(), b.getMaxY() + 6);
    }

    // ---------------------------------------------------------------------
    // Events: their order, nested destinations, and destinations of special cases

    /**
     * A source and two nested destinations that record the events they get,
     * so that the tests can check their order, their targets and their
     * coordinates. The inner destination is rotated.
     */
    private Node eventsCard() {
        seqSource = new Label("Drag me");
        seqSource.getStyleClass().addAll("tile", "source");
        seqSource.setOnDragDetected(e -> {
            seqDetected = true;
            seqPayload = "Sequence " + (++counter) + " ação 😀";
            Dragboard db = seqSource.startDragAndDrop(TransferMode.COPY_OR_MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(seqPayload);
            content.put(CUSTOM, seqPayload);
            db.setContent(content);
            log("DRAG_DETECTED events source, " + e.getButton());
            e.consume();
        });
        seqSource.setOnDragDone(e -> {
            // JDK-8124226: the source can still read the data at DRAG_DONE
            seqDoneString = e.getDragboard().getString();
            seqDoneTarget = e.getGestureTarget();
            seqDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            seqEvents.add("source DONE");
            log("DRAG_DONE events source, " + seqDone);
            e.consume();
        });
        logMouseEvents(seqSource, "events source");

        seqOuterTitle = styled("Outer destination", "title");
        seqInner = new Label("Inner destination");
        seqInner.getStyleClass().addAll("tile", "target");
        seqInner.setRotate(8);
        seqOuter = new VBox(14, seqOuterTitle, seqInner);
        seqOuter.getStyleClass().addAll("tile", "target");
        seqOuter.setPadding(new Insets(10, 30, 18, 14));
        recordingTarget(seqOuter, "outer");
        recordingTarget(seqInner, "inner");
        // The events that bubble from the inner destination
        seqOuter.addEventHandler(DragEvent.DRAG_ENTERED_TARGET, e -> {
            if (e.getTarget() == seqInner) {
                seqEvents.add("outer ENTERED_TARGET");
            }
        });
        eventsClicker = new Clicker();
        HBox row = new HBox(16, seqSource, seqOuter, eventsClicker.node);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("Each destination records DRAG_ENTERED, DRAG_OVER, DRAG_DROPPED and DRAG_EXITED, and the "
                + "source DRAG_DONE, in the log. A drop must come after DRAG_ENTERED and before DRAG_EXITED, and "
                + "DRAG_DONE last; entering the inner destination must not exit the outer one, and the drop "
                + "coordinates must be where the pointer is, in the rotated inner destination too. The source "
                + "must still read the data at DRAG_DONE (JDK-8124226).");
        return card("Event order", row, hint);
    }

    private void recordingTarget(Node node, String name) {
        node.addEventHandler(DragEvent.DRAG_ENTERED, e -> {
            seqEvents.add(name + " ENTERED");
            log("DRAG_ENTERED " + name);
        });
        node.addEventHandler(DragEvent.DRAG_EXITED, e -> {
            seqEvents.add(name + " EXITED");
            log("DRAG_EXITED " + name);
        });
        node.setOnDragOver(e -> {
            if (seqEvents.isEmpty() || !seqEvents.getLast().equals(name + " OVER")) {
                seqEvents.add(name + " OVER");
            }
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        node.setOnDragDropped(e -> {
            seqEvents.add(name + " DROPPED");
            seqDropTarget = name;
            seqDropScreen = new Point2D(e.getScreenX(), e.getScreenY());
            seqDropLocal = new Point2D(e.getX(), e.getY());
            seqDropExpected = node.screenToLocal(seqDropScreen);
            // The node that accepted the last DRAG_OVER
            seqDropAccepting = e.getGestureTarget();
            seqDropSource = e.getGestureSource();
            log(String.format(Locale.ROOT, "DRAG_DROPPED %s at %.1f, %.1f (screen %.1f, %.1f)", name, e.getX(),
                    e.getY(), e.getScreenX(), e.getScreenY()));
            e.setDropCompleted(true);
            e.consume();
        });
    }

    /**
     * Destinations of special cases: one whose acceptance depends on the
     * position, one that reads the data while hovering, one under a
     * mouse-transparent node, a source that is its own destination, and a
     * source that the drop removes from the scene.
     */
    private Node specialTargetsCard() {
        halfTarget = new Label("Rejects here  |  Accepts here");
        halfTarget.getStyleClass().addAll("tile", "target");
        halfTarget.setMinWidth(300);
        halfTarget.setOnDragEntered(e -> {
            halfEntered++;
            e.consume();
        });
        halfTarget.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && e.getX() > halfTarget.getWidth() / 2) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        halfTarget.setOnDragDropped(e -> {
            halfDrops++;
            e.setDropCompleted(true);
            e.consume();
        });

        // JDK-8131121, JDK-8131193: the data, and the formats it lacks, read before the drop
        readTarget = new Label("Reads the data while hovering");
        readTarget.getStyleClass().addAll("tile", "target");
        readTarget.setOnDragEntered(e -> {
            readData(e);
            e.consume();
        });
        readTarget.setOnDragOver(e -> {
            readOvers++;
            if (readData(e)) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        readTarget.setOnDragDropped(e -> {
            e.setDropCompleted(readData(e));
            e.consume();
        });

        underTarget = new Label("Under a mouse-transparent node");
        underTarget.getStyleClass().addAll("tile", "target");
        underTarget.setMaxWidth(Double.MAX_VALUE);
        underTarget.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        underTarget.setOnDragDropped(e -> {
            underDrops++;
            e.setDropCompleted(true);
            e.consume();
        });
        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: rgba(168, 85, 247, 0.25);");
        overlay.setMouseTransparent(true);
        overlay.setOnDragOver(e -> {
            overlayHits++;
            e.acceptTransferModes(TransferMode.ANY);
            e.consume();
        });
        StackPane overlayBox = new StackPane(underTarget, overlay);
        overlayBox.setMaxWidth(300);

        selfTile = new Label("Source and destination");
        selfTile.getStyleClass().addAll("tile", "source");
        selfTile.setOnDragDetected(e -> {
            Dragboard db = selfTile.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("self");
            db.setContent(content);
            selfDone = null;
            e.consume();
        });
        selfTile.setOnDragOver(e -> {
            if (e.getGestureSource() == selfTile) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        selfTile.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
        });
        selfTile.setOnDragDone(e -> {
            selfDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            selfDoneTarget = e.getGestureTarget();
            log("DRAG_DONE on the source that is its own destination, " + selfDone);
            e.consume();
        });

        // JDK-8205521: DRAG_DONE reaches a source that the drop took out of the scene
        removable = new Label("Removed by the drop");
        removable.getStyleClass().addAll("tile", "source");
        removable.setOnDragDetected(e -> {
            Dragboard db = removable.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("removable");
            db.setContent(content);
            removableDone = null;
            e.consume();
        });
        removable.setOnDragDone(e -> {
            removableDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            removableDoneDetached = removable.getScene() == null;
            log("DRAG_DONE on the removed source, " + removableDone);
            e.consume();
        });
        removerTarget = new Label("Removes the source");
        removerTarget.getStyleClass().addAll("tile", "target");
        removerTarget.setOnDragOver(e -> {
            if (e.getGestureSource() == removable) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        removerTarget.setOnDragDropped(e -> {
            removableBox.getChildren().remove(removable);
            e.setDropCompleted(true);
            e.consume();
        });
        Button restore = new Button("Restore");
        restore.setOnAction(e -> restoreRemovable());
        removableBox = new HBox(12, removable, removerTarget, restore);
        removableBox.setAlignment(Pos.CENTER_LEFT);

        Label hint = muted("Drag the source of the event card here. The first destination accepts only on its right "
                + "half; the second reads the text, the custom format and the absent formats in DRAG_ENTERED and "
                + "DRAG_OVER (JDK-8131121, JDK-8131193); the third lies under a mouse-transparent overlay, which "
                + "must not take the drag. The fourth tile takes its own drag, and the last source is removed from "
                + "the scene by the drop, and must still get DRAG_DONE (JDK-8205521).");
        return card("Special destinations", halfTarget, readTarget, overlayBox, selfTile, removableBox, hint);
    }

    /**
     * Reads the data of the events source during the drag.
     *
     * @return whether it was all as dragged
     */
    private boolean readData(DragEvent e) {
        Dragboard db = e.getDragboard();
        String where = e.getEventType().getName();
        String text = db.getString();
        Object custom = db.getContent(CUSTOM);
        String problem = null;
        if (seqPayload == null || !seqPayload.equals(text)) {
            problem = "getString() in " + where + " returned " + text + ", expected " + seqPayload;
        } else if (!seqPayload.equals(custom)) {
            problem = "the custom format in " + where + " is " + custom;
        } else if (db.getHtml() != null || db.getUrl() != null || db.getRtf() != null || db.getImage() != null
                || (db.getFiles() != null && !db.getFiles().isEmpty()) || db.hasImage() || db.hasFiles()) {
            problem = "formats that were not dragged are not empty in " + where;
        }
        if (problem != null && readProblem == null) {
            readProblem = problem;
            log(problem);
        }
        return problem == null;
    }

    private void restoreRemovable() {
        if (!removableBox.getChildren().contains(removable)) {
            removableBox.getChildren().addFirst(removable);
        }
    }

    // ---------------------------------------------------------------------
    // Situations: gestures that are not plain drags, and the drag image

    private Node gesturesCard() {
        // A drag started without content does not start: the press-drag-release goes on
        emptySource = new Label("startDragAndDrop, no content");
        emptySource.getStyleClass().addAll("tile", "source");
        emptySource.setOnDragDetected(e -> {
            emptyCounts.detected++;
            emptySource.startDragAndDrop(TransferMode.COPY);
            e.consume();
        });
        emptySource.setOnDragDone(e -> emptyCounts.done++);
        countGesture(emptySource, emptyCounts);

        noDndSource = new Label("DRAG_DETECTED, no drag");
        noDndSource.getStyleClass().addAll("tile", "source");
        noDndSource.setOnDragDetected(e -> {
            noDndCounts.detected++;
            e.consume();
        });
        countGesture(noDndSource, noDndCounts);

        // JDK-8350551: a simple press-drag-release stays on the pressed node
        pdrNode = new Label("Press here, drag over the others");
        pdrNode.getStyleClass().addAll("tile", "source");
        countGesture(pdrNode, pdrCounts);
        pdrOther = new Label("Another node");
        pdrOther.getStyleClass().add("tile");
        HBox pdrBox = new HBox(12, pdrNode, pdrOther);
        pdrBox.setAlignment(Pos.CENTER_LEFT);
        pdrBox.addEventHandler(MouseEvent.ANY, e -> {
            if ((e.getEventType() == MouseEvent.MOUSE_DRAGGED || e.getEventType() == MouseEvent.MOUSE_RELEASED)
                    && !isInside(e.getTarget(), pdrNode) && pdrCounts.dragged > 0) {
                pdrStray++;
                log(e.getEventType() + " on " + e.getTarget() + " instead of the pressed node");
            }
        });

        // JDK-8087684: removing the pressed child must not stop the drag of its parent
        childBox = new Pane();
        childBox.setPrefSize(220, 60);
        childBox.setMinSize(220, 60);
        childBox.setStyle("-fx-background-color: rgba(14, 165, 233, 0.2); -fx-border-color: #0284c7;");
        childRect = new Rectangle(70, 15, 80, 30);
        childRect.setFill(Color.web("#0284c7"));
        childRect.setOnMousePressed(e -> childBox.getChildren().remove(childRect));
        childBox.getChildren().add(childRect);
        childBox.setOnDragDetected(e -> childCounts.detected++);
        childBox.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> childCounts.dragged++);
        childBox.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            childCounts.released++;
            restoreChild();
        });
        logMouseEvents(childBox, "parent of the removed child");
        logMouseEvents(childRect, "removed child");

        // JDK-8285918: a click on a node that asks for drag detection at the press
        pdButton = new Button("Click me: setDragDetect(true) at the press");
        pdButton.setOnMousePressed(e -> e.setDragDetect(true));
        pdButton.setOnDragDetected(e -> {
            pdDetected++;
            Dragboard db = pdButton.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("button");
            db.setContent(content);
            log("DRAG_DETECTED on the button that asks for it at the press");
            e.consume();
        });
        pdButton.setOnDragDone(e -> {
            pdDone++;
            e.consume();
        });

        Label hint = muted("A drag that sets no content must not start, and one that DRAG_DETECTED does not start "
                + "is a simple press-drag-release, whose events all go to the pressed node (JDK-8350551), also "
                + "with the middle (JDK-8131110) and secondary buttons. The blue rectangle removes itself at the "
                + "press, and its parent must still detect the drag (JDK-8087684). A click on the button, which "
                + "asks for drag detection at the press, must not leave a drag behind (JDK-8285918).");
        return card("Gestures that are not drags", new HBox(12, emptySource, noDndSource), pdrBox, childBox,
                pdButton, hint);
    }

    private void restoreChild() {
        if (!childBox.getChildren().contains(childRect)) {
            childBox.getChildren().add(childRect);
        }
    }

    private void countGesture(Node node, Counts counts) {
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            counts.dragged++;
            if (e.isMiddleButtonDown()) {
                counts.middle++;
            }
            if (e.isSecondaryButtonDown()) {
                counts.secondary++;
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> counts.released++);
        logMouseEvents(node, "gesture source");
    }

    private Node dragImageCard() {
        imgSource = new Label("Drag me: RGB stripes drag image");
        imgSource.getStyleClass().addAll("tile", "source");
        imgSource.setOnDragDetected(e -> {
            Dragboard db = imgSource.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("stripes");
            db.setContent(content);
            db.setDragView(stripesImage, stripesImage.getWidth() / 2, stripesImage.getHeight() / 2);
            imgDone = null;
            e.consume();
        });
        imgSource.setOnDragDone(e -> {
            imgDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            e.consume();
        });
        logMouseEvents(imgSource, "drag image source");
        sitTarget = new Label("Drop here");
        sitTarget.getStyleClass().addAll("tile", "target");
        sitTarget.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        sitTarget.setOnDragDropped(e -> {
            sitDrops++;
            e.setDropCompleted(true);
            e.consume();
        });
        sitClicker = new Clicker();
        HBox row = new HBox(12, imgSource, sitTarget, sitClicker.node);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = muted("The drag image is red, green and blue from left to right, centered on the pointer: red "
                + "and blue must not swap (JDK-8271398, JDK-8327469), and it must vanish when Escape cancels the "
                + "drag (JDK-8143599). Many drags with a drag image in a row must all complete (JDK-8280383, "
                + "JDK-8227004), and the source must not stay pressed (JDK-8307098).");
        return card("Drag image", row, hint);
    }

    // ---------------------------------------------------------------------
    // Windows: a second window, an inactive window, an extended stage

    private Node windowsCard() {
        winMainSource = new Label("Drag me to the second window");
        winMainSource.getStyleClass().addAll("tile", "source");
        winMainSource.setOnDragDetected(e -> {
            Dragboard db = winMainSource.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("from the main window");
            db.setContent(content);
            winMainDone = null;
            e.consume();
        });
        winMainSource.setOnDragDone(e -> {
            winMainDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            e.consume();
        });
        winMainTarget = new Label("Drop here from the second window");
        winMainTarget.getStyleClass().addAll("tile", "target");
        winMainTarget.setOnDragEntered(e -> log("DRAG_ENTERED the main window target from " + source(e)));
        winMainTarget.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        winMainTarget.setOnDragDropped(e -> {
            winMainDrops++;
            winMainDropSource = e.getGestureSource();
            log("DRAG_DROPPED in the main window from " + source(e));
            e.setDropCompleted(true);
            e.consume();
        });
        Button second = new Button("Open the second window");
        second.setOnAction(e -> openSecondWindow());
        Button extended = new Button("Open an extended stage");
        extended.setOnAction(e -> openExtendedStage());
        VBox tiles = new VBox(10, winMainSource, winMainTarget, new HBox(10, second, extended));
        Label hint = muted("Drags between two windows of the application (JDK-8126843), from a window that is not "
                + "active (JDK-8126395), and inside an extended stage, which must take the mouse right after the "
                + "drop (JDK-8367557). A window that was dragged in must be collected once closed (JDK-8279214).");
        return card("Windows", tiles, hint);
    }

    private void openSecondWindow() {
        if (secondStage != null) {
            secondStage.toFront();
            return;
        }
        winSource = new Label("Drag me to the main window");
        winSource.getStyleClass().addAll("tile", "source");
        winSource.setOnDragDetected(e -> {
            Dragboard db = winSource.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("from the second window");
            db.setContent(content);
            winSourceDone = null;
            log("DRAG_DETECTED in the second window");
            e.consume();
        });
        winSource.setOnDragDone(e -> {
            winSourceDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            log("DRAG_DONE in the second window, " + winSourceDone);
            e.consume();
        });
        logMouseEvents(winSource, "second window source");
        winTarget = new Label("Drop here from the main window");
        winTarget.getStyleClass().addAll("tile", "target");
        winTarget.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        winTarget.setOnDragDropped(e -> {
            winDrops++;
            winDropSource = e.getGestureSource();
            log("DRAG_DROPPED in the second window from " + source(e));
            e.setDropCompleted(true);
            e.consume();
        });
        VBox root = new VBox(12, winSource, winTarget);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #f8fafc;");
        Scene scene = new Scene(root, 320, 150);
        scene.getStylesheets().add(STYLESHEET);
        secondStage = new Stage();
        secondStage.setTitle("Drag and drop: second window");
        secondStage.initOwner(stage);
        secondStage.setScene(scene);
        // Over the right of the main window, away from its tiles at the left
        secondStage.setX(stage.getX() + Math.max(420, stage.getWidth() - 380));
        secondStage.setY(stage.getY() + 120);
        secondStage.setOnHidden(e -> secondStage = null);
        secondStage.show();
    }

    private void closeSecondWindow() {
        if (secondStage != null) {
            secondStage.close();
            secondStage = null;
        }
    }

    private void openExtendedStage() {
        if (extendedStage != null) {
            extendedStage.toFront();
            return;
        }
        extSource = new Label("Drag me");
        extSource.getStyleClass().addAll("tile", "source");
        extSource.setOnDragDetected(e -> {
            Dragboard db = extSource.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("extended");
            db.setContent(content);
            extDone = null;
            e.consume();
        });
        extSource.setOnDragDone(e -> {
            extDone = e.getTransferMode() == null ? "not accepted" : "accepted " + e.getTransferMode();
            e.consume();
        });
        extTarget = new Label("Drop here");
        extTarget.getStyleClass().addAll("tile", "target");
        extTarget.setOnDragOver(e -> {
            if (e.getGestureSource() == extSource) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        extTarget.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
        });
        extClicker = new Clicker();
        HBox tiles = new HBox(14, extSource, extTarget, extClicker.node);
        tiles.setAlignment(Pos.CENTER_LEFT);
        tiles.setPadding(new Insets(20));
        HeaderBar header = new HeaderBar();
        header.setCenter(new Label("Extended stage"));
        BorderPane root = new BorderPane(tiles);
        root.setTop(header);
        root.setStyle("-fx-background-color: #f8fafc;");
        Scene scene = new Scene(root, 520, 200);
        scene.getStylesheets().add(STYLESHEET);
        extendedStage = new Stage(StageStyle.EXTENDED);
        extendedStage.setTitle("Drag and drop: extended stage");
        extendedStage.initOwner(stage);
        extendedStage.setScene(scene);
        extendedStage.setX(stage.getX() + 60);
        extendedStage.setY(stage.getY() + 160);
        extendedStage.setOnHidden(e -> extendedStage = null);
        extendedStage.show();
    }

    private void closeExtendedStage() {
        if (extendedStage != null) {
            extendedStage.close();
            extendedStage = null;
        }
    }

    // ---------------------------------------------------------------------
    // Exceptions and warnings during the tests

    /**
     * Records the exceptions of the event handlers, which JavaFX hands to the
     * handler of the FX thread, and the messages that its DnD code prints on
     * an unexpected state, such as "GOT A dragOver when dndGesture is null!"
     * (JDK-8380944, JDK-8275033, JDK-8165749).
     */
    private static void watchErrors() {
        if (watching) {
            return;
        }
        watching = true;
        Thread fxThread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = fxThread.getUncaughtExceptionHandler();
        fxThread.setUncaughtExceptionHandler((t, e) -> {
            fxErrors.add(e.toString());
            previous.uncaughtException(t, e);
        });
        System.setOut(new PrintStream(new WatchStream(System.out), true, System.out.charset()));
        System.setErr(new PrintStream(new WatchStream(System.err), true, System.err.charset()));
    }

    /** Passes the bytes on, and records the lines of the DnD code of JavaFX about a broken state. */
    private static final class WatchStream extends OutputStream {
        private static final List<String> PATTERNS = List.of("dndGesture", "DnDGesture", "UNEXPECTED",
                "Flush is forbidden", "dragboard is null");
        private final PrintStream out;
        private final java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();

        WatchStream(PrintStream out) {
            this.out = out;
        }

        @Override
        public synchronized void write(int b) {
            out.write(b);
            if (b == '\n') {
                String text = line.toString(out.charset());
                line.reset();
                if (PATTERNS.stream().anyMatch(text::contains)) {
                    dndWarnings.add(text.strip());
                }
            } else if (line.size() < 4096) {
                line.write(b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        @Override
        public void flush() {
            out.flush();
        }
    }

    /**
     * After a failed test, ends a drag that the platform may still hold, so
     * that it does not fail the next tests: Escape, and a click in a corner
     * of the window that is no destination, with the buttons released first.
     */
    /** Whether a drag of the grab card has started and not ended. */
    private boolean drag() {
        return grabStatus != null && grabStatus.getText().startsWith("Dragging");
    }

    private void recoverDrag() {
        try {
            fx(() -> TestKit.robot().mouseRelease(javafx.scene.input.MouseButton.PRIMARY));
            TestKit.shortcut(KeyCode.ESCAPE);
            TestKit.pause(300);
            Point2D corner = fx(() -> new Point2D(stage.getX() + 12, stage.getY() + stage.getHeight() - 12));
            TestKit.click(corner);
            TestKit.pause(800);
            log("Recovered from a failed test: Escape and a click at " + TestKit.fmt(corner));
        } catch (RuntimeException e) {
            log("Could not recover from a failed test: " + e);
        }
    }

    /**
     * A robot test that fails as well when an event handler threw, or when
     * JavaFX printed that its drag and drop was in a broken state.
     */
    private void robot(String name, String description, TestKit.Step body) {
        suite.robot(name, description, () -> {
            int errors = fxErrors.size();
            int warnings = dndWarnings.size();
            String result;
            try {
                result = body.run();
            } catch (AssertionError e) {
                recoverDrag();
                throw e;
            }
            TestKit.pause(100);
            check(fxErrors.size() == errors, "an exception on the FX thread: "
                    + String.join("; ", fxErrors.subList(errors, fxErrors.size())));
            check(dndWarnings.size() == warnings, "JavaFX printed: "
                    + String.join("; ", dndWarnings.subList(warnings, dndWarnings.size())));
            return result;
        });
    }

    private Node reorderCard() {
        ListView<String> todo = reorderList(FXCollections.observableArrayList(
                "Wayland surfaces", "Vulkan swap chain", "Input methods", "Drag and drop", "Touch gestures"));
        ListView<String> done = reorderList(FXCollections.observableArrayList(
                "Clipboard", "File dialogs", "Backdrop blur"));
        todoList = todo;
        doneList = done;
        VBox a = new VBox(6, styled("To do", "title"), todo);
        VBox b = new VBox(6, styled("Done", "title"), done);
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);
        HBox lists = new HBox(10, a, b);
        Label hint = muted("Drag items to reorder them, to move them to the other list or to another "
                + "window, or drop them on the drop target as text.");
        return card("Reorder", lists, hint);
    }

    private ListView<String> reorderList(ObservableList<String> items) {
        ListView<String> list = new ListView<>(items);
        list.setPrefHeight(170);
        list.setMinHeight(60);
        list.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setOnDragDetected(e -> {
                if (cell.isEmpty()) {
                    return;
                }
                Dragboard db = cell.startDragAndDrop(TransferMode.COPY_OR_MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(LIST_ITEM, cell.getItem());
                content.putString(cell.getItem());
                db.setContent(content);
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                db.setDragView(cell.snapshot(params, null), e.getX(), e.getY());
                e.consume();
            });
            cell.setOnDragOver(e -> {
                if (e.getDragboard().hasContent(LIST_ITEM)) {
                    e.acceptTransferModes(TransferMode.MOVE);
                    if (!cell.getStyleClass().contains("drop-before")) {
                        cell.getStyleClass().add("drop-before");
                    }
                }
                e.consume();
            });
            cell.setOnDragExited(e -> cell.getStyleClass().remove("drop-before"));
            cell.setOnDragDropped(e -> {
                cell.getStyleClass().remove("drop-before");
                int index = cell.isEmpty() ? items.size() : cell.getIndex();
                insert(e, list, items, (String) e.getDragboard().getContent(LIST_ITEM), index);
            });
            cell.addEventHandler(MouseEvent.DRAG_DETECTED, e -> reorderedInList = false);
            cell.setOnDragDone(e -> {
                // Not by the gesture target, which DRAG_DONE does not give (see "Gesture target at DRAG_DONE")
                if (e.getTransferMode() == TransferMode.MOVE && !reorderedInList) {
                    items.remove(e.getDragboard().getString());
                    log("Moved \"" + e.getDragboard().getString() + "\" out of the list");
                }
                e.consume();
            });
            return cell;
        });
        // Drops below the last cell
        list.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(LIST_ITEM)) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        list.setOnDragDropped(e -> insert(e, list, items, (String) e.getDragboard().getContent(LIST_ITEM),
                items.size()));
        return list;
    }

    private void insert(DragEvent e, ListView<String> list, ObservableList<String> items, String item, int index) {
        if (item == null) {
            e.setDropCompleted(false);
            e.consume();
            return;
        }
        int old = items.indexOf(item);
        boolean local = old >= 0;
        reorderedInList = local;
        if (local) {
            items.remove(old);
            if (old < index) {
                index--;
            }
        }
        index = Math.max(0, Math.min(index, items.size()));
        items.add(index, item);
        list.getSelectionModel().select(index);
        log((local ? "Reordered \"" : "Received \"") + item + "\" at position " + (index + 1));
        e.setDropCompleted(true);
        e.consume();
    }

    private static boolean isInside(Object target, Node container) {
        for (Node n = target instanceof Node t ? t : null; n != null; n = n.getParent()) {
            if (n == container) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Tests

    private void resetOptions() {
        closeSecondWindow();
        closeExtendedStage();
        restoreRemovable();
        restoreChild();
        acceptBox.setValue(Accept.ANY);
        dragViewBox.setValue(DragView.SNAPSHOT);
        offsetBox.setValue("At the pointer");
    }

    private void defineTests() {
        robot("Copy", "Drags the COPY source to the COPY destination", () -> modes(2, 2, "COPY"));
        robot("Move", "Drags the MOVE source to the MOVE destination", () -> modes(3, 3, "MOVE"));
        robot("Link", "Drags the LINK source to the LINK destination", () -> {
            if ("Gtk4".equalsIgnoreCase(System.getProperty("glass.platform"))) {
                // Wayland drags copy, move or ask; GTK offers LINK as COPY
                TestKit.skip("Wayland has no link action for drag and drop");
            }
            return modes(4, 4, "LINK");
        });
        robot("Incompatible modes", "Drags the COPY source to the MOVE destination, which must reject it",
                () -> modes(2, 3, null));
        robot("Ctrl chooses COPY", "Drags ANY to ANY holding Ctrl", () -> modes(0, 0, "COPY", KeyCode.CONTROL));
        robot("Shift chooses MOVE", "Drags ANY to ANY holding Shift", () -> modes(0, 0, "MOVE", KeyCode.SHIFT));
        robot("Text", "Drops the Text source on the drop target", () -> {
            dropChip("Text");
            String text = fx(() -> droppedText);
            check(text != null && text.startsWith("Hello from JavaFX"), "the dropped text is " + text);
            return "\"" + text + "\"";
        });
        robot("Image", "Drops the Image source and compares its pixels", () -> {
            dropChip("Image");
            Image image = fx(() -> droppedImage);
            check(image != null, "no image was dropped");
            check(image.getWidth() == sampleImage.getWidth() && image.getHeight() == sampleImage.getHeight(),
                    "the image is " + image.getWidth() + " × " + image.getHeight());
            for (int[] p : new int[][] {{120, 40}, {150, 70}, {60, 140}, {5, 5}}) {
                int want = sampleImage.getPixelReader().getArgb(p[0], p[1]);
                int got = image.getPixelReader().getArgb(p[0], p[1]);
                check(closeArgb(want, got), String.format(Locale.ROOT, "pixel %d, %d is %08x, expected %08x",
                        p[0], p[1], got, want));
            }
            return String.format(Locale.ROOT, "%.0f × %.0f, pixels and alpha match", image.getWidth(),
                    image.getHeight());
        });
        robot("Files", "Drops the Two files source and verifies the contents of the files", () -> {
            dropChip("Two files");
            String verdict = fx(() -> droppedFiles);
            check(verdict != null && verdict.startsWith("SUCCESS"), "the files verdict is " + verdict);
            return verdict;
        });
        robot("Custom format", "Drops the Custom source, a DataFormat of the application", () -> {
            dropChip("Custom");
            Object value = fx(() -> droppedCustom);
            String want = "Custom payload #" + fx(() -> counter);
            check(want.equals(value), "the custom content is " + value + ", expected " + want);
            return String.valueOf(value);
        });
        robot("RTF", "Drops the RTF source, text/rtf, on the drop target", () -> {
            dropChip("RTF");
            String rtf = fx(() -> droppedRtf);
            check(RTF.equals(rtf), "the dropped RTF is " + rtf);
            return rtf.length() + " characters of RTF";
        });
        robot("Custom bytes", "Drops the Bytes source, a byte[] serialized by JavaFX", () -> {
            dropChip("Bytes");
            byte[] bytes = fx(() -> droppedBytes);
            check(Arrays.equals(bytes, new byte[] {1, 2, 3, 4}), "the dropped bytes are " + Arrays.toString(bytes));
            return Arrays.toString(bytes);
        });
        robot("Color copy", "Drags the red swatch to the first circle, a serialized double[] (DragDropColor)",
                () -> {
            fx(() -> colorCircles.forEach(c -> c.setFill(EMPTY)));
            Color red = fx(() -> (Color) swatches.getFirst().getFill());
            dragColor(swatches.getFirst(), colorCircles.getFirst());
            check(fx(() -> red.equals(colorCircles.getFirst().getFill())), "the circle is "
                    + fx(() -> colorCircles.getFirst().getFill()));
            check(fx(() -> colorDone) == TransferMode.COPY, "the drag ended with " + fx(() -> colorDone));
            return "the circle is " + red;
        });
        robot("Color move", "Drags the first circle to the second holding Shift: the color moves and the "
                + "first turns gray (DragDropColor)", () -> {
            Color green = fx(() -> (Color) swatches.get(1).getFill());
            fx(() -> {
                colorCircles.getFirst().setFill(green);
                colorCircles.get(1).setFill(EMPTY);
            });
            dragColor(colorCircles.getFirst(), colorCircles.get(1), KeyCode.SHIFT);
            await("the first circle gray", () -> EMPTY.equals(colorCircles.getFirst().getFill()));
            check(fx(() -> green.equals(colorCircles.get(1).getFill())), "the second circle is "
                    + fx(() -> colorCircles.get(1).getFill()));
            return "moved " + green;
        });
        robot("Color as text", "Drags the blue swatch to the text target, which takes the string",
                () -> {
            Color blue = fx(() -> (Color) swatches.get(2).getFill());
            dragColor(swatches.get(2), colorText);
            await("the text", () -> colorText.getText().equals(blue.toString()));
            return "\"" + blue + "\"";
        });
        robot("Rejected by the target", "Drops Text on the target that rejects everything", () -> {
            fx(() -> acceptBox.setValue(Accept.NONE));
            int before = fx(() -> drops);
            fx(() -> chipDone.remove("Text"));
            TestKit.drag(chips.get("Text"), received.getParent());
            await("the end of the drag", () -> chipDone.containsKey("Text"));
            fx(() -> acceptBox.setValue(Accept.ANY));
            check("not accepted".equals(fx(() -> chipDone.get("Text"))), "the drag ended with "
                    + fx(() -> chipDone.get("Text")));
            check(fx(() -> drops) == before, "the target received the drop");
            return "not accepted";
        });
        robot("Extreme image offsets", "Drags with the drag image at NaN and Double.MAX_VALUE offsets", () -> {
            try {
                for (String offset : List.of("NaN", "Double.MAX_VALUE")) {
                    fx(() -> offsetBox.setValue(offset));
                    dropChip("Text");
                }
            } finally {
                fx(() -> offsetBox.setValue("At the pointer"));
            }
            return "both drops arrived";
        });
        robot("Full drag", "A press-drag-release from the full drag source to the target Two, which ends with "
                + "one MOUSE_DRAG_DONE (JDK-8365635)", () -> {
            fx(() -> fullDragResult = null);
            int done = fx(() -> fullDone);
            TestKit.drag(fullSource, fullTargets.get(1));
            await("MOUSE_DRAG_RELEASED", () -> fullDragResult != null);
            String result = fx(() -> fullDragResult);
            check(result.equals("Two from the source"), "released on " + result);
            await("MOUSE_DRAG_DONE", 2000, () -> fullDone > done);
            TestKit.pause(200);
            check(fx(() -> fullDone) == done + 1, (fx(() -> fullDone) - done) + " MOUSE_DRAG_DONE");
            return "released on " + result;
        });
        robot("Menu after a drag", "Drops on the destination, the source shows a context menu on DRAG_DONE, "
                + "and a click picks its second item", () -> {
            fx(() -> {
                menuPicked = null;
                menuMouseEvents = 0;
                dragEndMenu.hide();
            });
            try {
                TestKit.drag(menuSource, menuTarget);
                await("the context menu", 5000, () -> dragEndMenu.isShowing());
                TestKit.click(awaitMenuItem(dragEndMenu, "#move-here"));
                try {
                    await("the item action", () -> menuPicked != null);
                } catch (AssertionError e) {
                    throw new AssertionError("JDK-8395594: the menu shown at DRAG_DONE did not take the click, with "
                            + fx(() -> menuMouseEvents) + " mouse events on it: the pointer stays with the drag");
                }
                check("Move here".equals(fx(() -> menuPicked)), "picked " + fx(() -> menuPicked));
                await("the menu hidden", () -> !dragEndMenu.isShowing());
            } catch (AssertionError e) {
                fx(() -> dragEndMenu.hide());
                throw e;
            }
            return "picked \"" + fx(() -> menuPicked) + "\"";
        });
        robot("Menu after a drop", "JDK-8395594: three times, with the FX thread busy, drops and shows a "
                + "context menu at the drop, whose item must highlight and take the click", () -> {
            int[] unexpected = {0};
            javafx.event.EventHandler<MouseEvent> click = e -> unexpected[0]++;
            fx(() -> stage.getScene().addEventFilter(MouseEvent.MOUSE_CLICKED, click));
            boolean[] busy = {true};
            Thread pulses = new Thread(() -> {
                while (busy[0]) {
                    Platform.runLater(() -> { });
                    TestKit.pause(100);
                }
            });
            pulses.setDaemon(true);
            pulses.start();
            try {
                for (int i = 1; i <= 3; i++) {
                    String id = List.of("#drop-alpha", "#drop-beta", "#drop-gamma").get(i - 1);
                    fx(() -> dropMenuPicked = null);
                    TestKit.drag(dropMenuSource, dropMenuTarget);
                    await("the context menu, try " + i, 5000, () -> dropMenu.isShowing());
                    Node item = awaitMenuItem(dropMenu, id);
                    TestKit.move(fx(() -> TestKit.center(item)));
                    await("the item highlighted under the pointer, try " + i, item::isHover);
                    TestKit.click(fx(() -> TestKit.center(item)));
                    int attempt = i;
                    await("the item action, try " + attempt, () -> dropMenuPicked != null);
                    await("the menu hidden, try " + attempt, () -> !dropMenu.isShowing());
                    check(unexpected[0] == 0, "the click reached the window below, try " + i);
                    TestKit.pause(200);
                }
            } finally {
                busy[0] = false;
                fx(() -> {
                    dropMenu.hide();
                    stage.getScene().removeEventFilter(MouseEvent.MOUSE_CLICKED, click);
                });
            }
            return "3 menus took the clicks";
        });
        robot("Dialog after a drag", "JDK-8375965: shows a dialog without a drag, as a control, and then after "
                + "a drag: its OK and Cancel buttons must take clicks", () -> {
            try {
                check(clickDialogButton(ButtonType.OK), "the OK button does not take clicks even without a drag, "
                        + "which is not JDK-8375965");
                for (ButtonType button : List.of(ButtonType.OK, ButtonType.CANCEL)) {
                    TestKit.drag(dialogSource, dialogTarget);
                    TestKit.pause(300);
                    check(clickDialogButton(button), "JDK-8375965: the " + button.getText()
                            + " button does not take clicks after a drag, but did without one");
                }
            } finally {
                fx(() -> {
                    if (dialogAfterDrag != null) {
                        dialogAfterDrag.close();
                    }
                });
            }
            return "OK and Cancel took the clicks";
        });
        robot("New stage during a drag", "JDK-8292922: a stage created in the drag handler follows the "
                + "pointer until the release, and the mouse works afterwards", () -> {
            fx(() -> {
                stageDragEvents = 0;
                stageDragReleased = false;
                stageDragEnd = null;
                stageDragClicks = 0;
            });
            try {
                TestKit.drag(stageDragSource, stageDragTarget);
                await("the release of the drag", () -> stageDragReleased);
                int events = fx(() -> stageDragEvents);
                check(events >= 5, "only " + events + " MOUSE_DRAGGED after the new stage");
                Point2D end = fx(() -> stageDragEnd);
                Point2D want = fx(() -> TestKit.center(stageDragTarget));
                check(end != null && end.distance(want) < 30, "the stage ended at " + TestKit.fmt(end)
                        + ", the pointer at " + TestKit.fmt(want));
                TestKit.click(stageDragTarget);
                await("the click after the drag", () -> stageDragClicks > 0);
                return events + " MOUSE_DRAGGED on the new stage, which ended under the pointer";
            } finally {
                fx(this::closeDraggedStage);
            }
        });
        robot("Pointer after a drop", "Drops on the destination; then the pointer must hover and click "
                + "without MOUSE_DRAGGED, and a second drag must work", () -> {
            for (int i = 1; i <= 2; i++) {
                String done = grabDrag(grabTarget, "drop " + i);
                check(done.equals("accepted COPY"), "drop " + i + " ended " + done);
                checkPointerAfterDrag("after drop " + i);
            }
            return "2 drops, the pointer worked after each";
        });
        robot("Drop not completed", "Drops on a destination that accepts the drag and then calls "
                + "setDropCompleted(false): the drag ends not accepted and the pointer works", () -> {
            String done = grabDrag(grabRefuser, "refused drop");
            check(done.equals("not accepted"), "the refused drop ended " + done);
            checkPointerAfterDrag("after a refused drop");
            return "not accepted, the pointer worked";
        });
        robot("Drop not handled", "Drops on a destination that accepts the drag but does not handle "
                + "DRAG_DROPPED: the drag ends not accepted and the pointer works", () -> {
            String done = grabDrag(grabIgnorer, "unhandled drop");
            check(done.equals("not accepted"), "the unhandled drop ended " + done);
            checkPointerAfterDrag("after an unhandled drop");
            return "not accepted, the pointer worked";
        });
        robot("Release over no destination", "Releases the drag on the tile that accepts nothing, in the "
                + "window: the drag ends not accepted, with no drop, and the pointer works", () -> {
            int drops = fx(() -> grabDrops(grabTarget) + grabDrops(grabRefuser) + grabDrops(grabIgnorer));
            fx(() -> grabDone = null);
            TestKit.drag(grabSource, grabClicker.node);
            await("DRAG_DONE", 5000, () -> grabDone != null);
            check("not accepted".equals(fx(() -> grabDone)), "the drag ended " + fx(() -> grabDone));
            check(fx(() -> grabDrops(grabTarget) + grabDrops(grabRefuser) + grabDrops(grabIgnorer)) == drops,
                    "a destination received a drop");
            checkPointerAfterDrag("after a release over no destination");
            return "not accepted, the pointer worked";
        });
        robot("Escape cancels a drag", "Holds the drag over the destination and presses Escape: the drag "
                + "ends without a drop, before the release, and the pointer works", () -> {
            int drops = fx(() -> grabDrops(grabTarget));
            Point2D from = fx(() -> TestKit.center(grabSource));
            Point2D to = fx(() -> TestKit.center(grabTarget));
            fx(() -> grabDone = null);
            try {
                TestKit.dragPress(from, to);
                TestKit.dragMove(from, to);
                TestKit.dragHover(to);
                TestKit.shortcut(KeyCode.ESCAPE);
                await("DRAG_DONE after Escape, with the button still down", 3000, () -> grabDone != null);
            } finally {
                TestKit.dragRelease(to);
            }
            TestKit.pause(300);
            check("not accepted".equals(fx(() -> grabDone)), "the drag ended " + fx(() -> grabDone));
            check(fx(() -> grabDrops(grabTarget)) == drops, "the destination received a drop after Escape");
            checkPointerAfterDrag("after Escape");
            return "cancelled, the pointer worked";
        });
        robot("Drag after a cancelled drag", "Cancels a drag with Escape, with the button still down, and then "
                + "drags again: the second drag must drop, and not hang in the first", () -> {
            Point2D from = fx(() -> TestKit.center(grabSource));
            Point2D to = fx(() -> TestKit.center(grabTarget));
            fx(() -> grabDone = null);
            try {
                TestKit.dragPress(from, to);
                TestKit.dragMove(from, to);
                TestKit.dragHover(to);
                TestKit.shortcut(KeyCode.ESCAPE);
                TestKit.pause(800);
            } finally {
                TestKit.dragRelease(to);
            }
            TestKit.pause(500);
            String first = fx(() -> grabDone);
            int drops = fx(() -> grabDrops(grabTarget));
            fx(() -> grabDone = null);
            TestKit.drag(grabSource, grabTarget);
            try {
                await("the drop of the drag after the cancelled one", 5000, () -> grabDrops(grabTarget) > drops);
                await("DRAG_DONE of the drag after the cancelled one", 5000, () -> grabDone != null);
            } catch (AssertionError e) {
                if (!fx(() -> grabStatus.getText().startsWith("Dragging"))) {
                    throw new AssertionError("the drag after a drag cancelled with Escape (which ended " + first
                            + ") never starts: no DRAG_DETECTED, as the Scene got no MOUSE_RELEASED after the first "
                            + "drag and takes the button for still down (primaryButtonDown), so that the next press "
                            + "starts no gesture");
                }
                throw new AssertionError("the drag after a drag cancelled with Escape (which ended " + first
                        + ") never ends: " + e.getMessage());
            }
            check("accepted COPY".equals(fx(() -> grabDone)), "the second drag ended " + fx(() -> grabDone));
            return "the cancelled drag ended " + first + ", the next one dropped";
        });
        robot("Release outside the window", "Releases the drag on the desktop beside the window: it ends "
                + "not accepted, and the pointer works when it comes back", () -> {
            Point2D outside = outsideWindow(grabSource);
            int drops = fx(() -> grabDrops(grabTarget));
            Point2D from = fx(() -> TestKit.center(grabSource));
            fx(() -> grabDone = null);
            TestKit.dragPress(from, outside);
            TestKit.dragMove(from, outside);
            TestKit.dragHover(outside);
            TestKit.dragRelease(outside);
            await("DRAG_DONE after the release outside", 5000, () -> grabDone != null);
            check("not accepted".equals(fx(() -> grabDone)), "the drag ended " + fx(() -> grabDone));
            check(fx(() -> grabDrops(grabTarget)) == drops, "the destination received a drop");
            checkPointerAfterDrag("after a release at " + TestKit.fmt(outside));
            return "not accepted at " + TestKit.fmt(outside) + ", the pointer worked";
        });
        robot("Out of the window and back", "Takes the drag out to the desktop and back to the destination, "
                + "which must see it enter again and take the drop", () -> {
            Point2D outside = outsideWindow(grabSource);
            int drops = fx(() -> grabDrops(grabTarget));
            Point2D from = fx(() -> TestKit.center(grabSource));
            Point2D to = fx(() -> TestKit.center(grabTarget));
            fx(() -> grabDone = null);
            TestKit.dragPress(from, outside);
            TestKit.dragMove(from, outside);
            TestKit.dragHover(outside);
            int entered = fx(() -> grabEntered);
            TestKit.dragMove(outside, to);
            TestKit.dragHover(to);
            TestKit.dragRelease(to);
            await("DRAG_DONE", 5000, () -> grabDone != null);
            check(fx(() -> grabEntered) > entered, "no DRAG_ENTERED on the destination after coming back");
            check(fx(() -> grabDrops(grabTarget)) == drops + 1, "the destination received no drop");
            check("accepted COPY".equals(fx(() -> grabDone)), "the drag ended " + fx(() -> grabDone));
            checkPointerAfterDrag("after coming back");
            return "out to " + TestKit.fmt(outside) + " and back, dropped";
        });
        robot("Drag from a popup", "Opens an auto-hiding popup, which holds a grab, and drags its source to "
                + "the destination in the window", () -> {
            int drops = fx(() -> grabDrops(grabTarget));
            try {
                fx(this::showGrabPopup);
                await("the popup", () -> grabPopup.isShowing() && grabPopupSource.localToScreen(0, 0) != null);
                TestKit.pause(400);
                fx(() -> grabDone = null);
                TestKit.drag(fx(() -> TestKit.center(grabPopupSource)), fx(() -> TestKit.center(grabTarget)));
                await("the drop from the popup", 5000, () -> grabDrops(grabTarget) > drops);
                await("DRAG_DONE on the source in the popup", 5000, () -> grabDone != null);
                check("accepted COPY".equals(fx(() -> grabDone)), "the drag ended " + fx(() -> grabDone));
            } finally {
                fx(() -> grabPopup.hide());
            }
            TestKit.pause(300);
            checkPointerAfterDrag("after the drag from the popup");
            return "dropped from the popup, the pointer worked";
        });
        robot("Move between lists", "Drags the first item of To do below the items of Done", () -> {
            String item = fx(() -> todoList.getItems().getFirst());
            Node from = fx(() -> todoList.lookupAll(".list-cell").stream()
                    .filter(n -> n instanceof ListCell<?> c && item.equals(c.getItem())).findFirst().orElseThrow());
            TestKit.reveal(doneList);
            javafx.geometry.Point2D to = fx(() -> {
                javafx.geometry.Bounds b = TestKit.screenBounds(doneList);
                return new javafx.geometry.Point2D(b.getCenterX(), b.getMaxY() - 12);
            });
            TestKit.reveal(from);
            TestKit.drag(fx(() -> TestKit.center(from)), to);
            await("the item in Done", () -> doneList.getItems().contains(item));
            await("the item out of To do", () -> !todoList.getItems().contains(item));
            fx(() -> {
                doneList.getItems().remove(item);
                todoList.getItems().addFirst(item);
            });
            return "\"" + item + "\" moved";
        });
        defineModeTests();
        defineFormatTests();
        defineEventTests();
        defineSituationTests();
        defineWindowTests();
    }

    private static boolean gtk4() {
        return "Gtk4".equalsIgnoreCase(System.getProperty("glass.platform"));
    }

    private void defineModeTests() {
        robot("Ctrl+Shift chooses LINK", "Drags ANY to ANY holding Ctrl and Shift", () -> {
            if (gtk4()) {
                TestKit.skip("Wayland has no link action for drag and drop");
            }
            return modes(0, 0, "LINK", KeyCode.CONTROL, KeyCode.SHIFT);
        });
        robot("Shift pressed during the drag", "JDK-8273379: drags ANY to ANY, and presses Shift while hovering "
                + "the destination: the action must turn to MOVE", () -> {
            Label done = sourceResults.getFirst();
            Label proposed = targetResults.getFirst();
            fx(() -> done.setText(""));
            TestKit.reveal(sourceTiles.getFirst());
            Point2D from = fx(() -> TestKit.center(sourceTiles.getFirst()));
            Point2D to = fx(() -> TestKit.center(targetTiles.getFirst()));
            String before;
            try {
                TestKit.dragPress(from, to);
                TestKit.dragMove(from, to);
                TestKit.dragHover(to);
                before = fx(() -> proposed.getText());
                fx(() -> TestKit.robot().keyPress(KeyCode.SHIFT));
                TestKit.dragHover(to);
                TestKit.dragRelease(to);
            } finally {
                fx(() -> TestKit.robot().keyRelease(KeyCode.SHIFT));
            }
            await("the end of the drag", 5000, () -> done.getText().startsWith("Done"));
            String doneText = fx(() -> done.getText());
            check(doneText.equals("Done, accepted MOVE"), "Shift pressed during the drag: the source shows \""
                    + doneText + "\", after \"" + before + "\" before Shift");
            return "\"" + before + "\", then MOVE with Shift";
        });
        robot("Ctrl released during the drag", "Drags ANY to ANY holding Ctrl, releases Ctrl and presses Shift "
                + "while hovering the destination: the action must follow, to MOVE", () -> {
            Label done = sourceResults.getFirst();
            fx(() -> done.setText(""));
            TestKit.reveal(sourceTiles.getFirst());
            Point2D from = fx(() -> TestKit.center(sourceTiles.getFirst()));
            Point2D to = fx(() -> TestKit.center(targetTiles.getFirst()));
            try {
                TestKit.dragPress(from, to, KeyCode.CONTROL);
                TestKit.dragMove(from, to);
                TestKit.dragHover(to);
                fx(() -> {
                    TestKit.robot().keyRelease(KeyCode.CONTROL);
                    TestKit.robot().keyPress(KeyCode.SHIFT);
                });
                TestKit.dragHover(to);
                TestKit.dragRelease(to);
            } finally {
                fx(() -> {
                    TestKit.robot().keyRelease(KeyCode.SHIFT);
                    TestKit.robot().keyRelease(KeyCode.CONTROL);
                });
            }
            await("the end of the drag", 5000, () -> done.getText().startsWith("Done"));
            String doneText = fx(() -> done.getText());
            check(doneText.equals("Done, accepted MOVE"), "the source shows \"" + doneText + "\"");
            return "COPY, then MOVE";
        });
        robot("Every pair of modes", "Drags each of the five sources to each of the five destinations: a drop "
                + "must happen exactly when they share a mode, with a mode of both", () -> {
            List<String> skipped = new ArrayList<>();
            StringBuilder defaults = new StringBuilder();
            int dropped = 0, rejected = 0;
            for (int s = 0; s < MODE_SETS.length; s++) {
                for (int t = 0; t < MODE_SETS.length; t++) {
                    Set<TransferMode> both = EnumSet.noneOf(TransferMode.class);
                    both.addAll(Set.of(MODE_SETS[s]));
                    both.retainAll(Set.of(MODE_SETS[t]));
                    String pair = modesText(MODE_SETS[s]) + " to " + modesText(MODE_SETS[t]);
                    if (gtk4() && both.equals(Set.of(TransferMode.LINK))) {
                        skipped.add(pair);
                        continue;
                    }
                    Label done = sourceResults.get(s);
                    Label proposed = targetResults.get(t);
                    fx(() -> done.setText(""));
                    TestKit.drag(sourceTiles.get(s), targetTiles.get(t));
                    await("the end of the drag " + pair, 5000, () -> done.getText().startsWith("Done"));
                    String doneText = fx(() -> done.getText());
                    String droppedText = fx(() -> proposed.getText());
                    if (both.isEmpty()) {
                        check(doneText.startsWith("Done, rejected"), pair + ": the source shows \"" + doneText + "\"");
                        check(!droppedText.startsWith("Dropped"), pair + ": the destination shows \"" + droppedText
                                + "\"");
                        rejected++;
                    } else {
                        String prefix = "Done, accepted ";
                        check(doneText.startsWith(prefix), pair + ": the source shows \"" + doneText + "\"");
                        TransferMode mode = TransferMode.valueOf(doneText.substring(prefix.length()));
                        check(both.contains(mode), pair + ": accepted " + mode + ", which is not "
                                + modesText(both));
                        check(droppedText.equals("Dropped here, proposed " + mode), pair
                                + ": the destination shows \"" + droppedText + "\"");
                        if (both.size() > 1) {
                            defaults.append(defaults.isEmpty() ? "" : ", ").append(pair).append(" ").append(mode);
                        }
                        dropped++;
                    }
                }
            }
            return dropped + " drops, " + rejected + " rejected" + (skipped.isEmpty() ? "" : ", " + skipped.size()
                    + " LINK pairs skipped") + "; without modifiers: " + defaults;
        });
    }

    private void defineFormatTests() {
        robot("HTML", "Drops the HTML source: text/html with its text/plain", () -> {
            dropChip("HTML");
            String html = fx(() -> droppedHtml);
            check(html != null && html.contains("<b>Bold</b>"), "the dropped HTML is " + html);
            check("Bold and italic from JavaFX".equals(fx(() -> droppedText)), "the dropped text is "
                    + fx(() -> droppedText));
            return html.length() + " characters of HTML and the text";
        });
        robot("URL", "JDK-8126966: drops the URL source, which must not show as files", () -> {
            dropChip("URL");
            String url = fx(() -> droppedUrl);
            check(URL.equals(url), "the dropped URL is " + url);
            List<File> files = fx(() -> droppedFileList);
            check(files == null || files.isEmpty(), "JDK-8126966: the URL arrived as files " + files);
            return url;
        });
        robot("Unicode text", "Drops text with accents, CJK, right-to-left script, emoji, a ZWJ sequence, a "
                + "combining mark, a tab and a new line", () -> {
            dropChip("Unicode");
            String text = fx(() -> droppedText);
            check(UNICODE.equals(text), "the dropped text is \"" + text + "\"");
            return text.codePointCount(0, text.length()) + " code points intact";
        });
        robot("Large text", "Drops 4 MiB of text, which X11 transfers in chunks (INCR)", () -> {
            dropChip("Large text", 20000);
            String text = fx(() -> droppedText);
            check(text != null, "no text was dropped");
            check(text.length() == LARGE_TEXT.length(), "the dropped text has " + text.length()
                    + " characters, expected " + LARGE_TEXT.length());
            check(text.equals(LARGE_TEXT), "the dropped text differs at character "
                    + Arrays.mismatch(text.toCharArray(), LARGE_TEXT.toCharArray()));
            return text.length() + " characters intact";
        });
        robot("Large image", "Drops a 1600 × 1200 image and compares its pixels", () -> {
            dropChip("Large image", 20000);
            Image image = fx(() -> droppedImage);
            check(image != null, "no image was dropped");
            int w = (int) largeImage.getWidth(), h = (int) largeImage.getHeight();
            check(image.getWidth() == w && image.getHeight() == h, "the image is " + image.getWidth() + " × "
                    + image.getHeight());
            for (int[] p : new int[][] {{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}, {w / 2, h / 3},
                    {1234, 987}}) {
                int want = gradientArgb(p[0], p[1], w, h);
                int got = image.getPixelReader().getArgb(p[0], p[1]);
                check(closeArgb(want, got), String.format(Locale.ROOT, "pixel %d, %d is %08x, expected %08x",
                        p[0], p[1], got, want));
            }
            return w + " × " + h + ", pixels match";
        });
        robot("Odd file names", "JDK-8131481, JDK-8124934, JDK-8231642: drops files whose names have spaces, "
                + "%, #, +, &, accents and an emoji, which must exist under their own names", () -> {
            dropChip("Odd file names");
            List<File> files = fx(() -> droppedFileList);
            check(files != null && files.size() == ODD_NAMES.size(), "the dropped files are " + files);
            for (File f : files) {
                check(ODD_NAMES.contains(f.getName()), "a dropped file is named \"" + f.getName()
                        + "\", which is none of " + ODD_NAMES);
                check(f.exists(), f.getAbsolutePath() + " does not exist");
            }
            String verdict = fx(() -> droppedFiles);
            check(("SUCCESS: " + ODD_NAMES.size() + " of " + ODD_NAMES.size() + " files verified").equals(verdict),
                    "the files verdict is " + verdict);
            return verdict;
        });
        robot("Every format at once", "Drops the Everything source: text, HTML, image, files and the custom "
                + "format in one drag", () -> {
            dropChip("Everything", 10000);
            Set<DataFormat> types = fx(() -> droppedTypes);
            for (DataFormat f : List.of(DataFormat.PLAIN_TEXT, DataFormat.HTML, DataFormat.IMAGE, DataFormat.FILES,
                    CUSTOM)) {
                check(types.contains(f), "the drop lacks " + f + ": " + formats(types));
            }
            check("Everything from JavaFX".equals(fx(() -> droppedText)), "the text is " + fx(() -> droppedText));
            check(fx(() -> droppedImage) != null, "the image could not be read");
            check(fx(() -> droppedHasImage), "hasImage() is false, although getImage() reads the image (see \"Image "
                    + "with each other format\")");
            String verdict = fx(() -> droppedFiles);
            check(verdict != null && verdict.startsWith("SUCCESS"), "the files verdict is " + verdict);
            return formats(types);
        });
        robot("Image with each other format", "Drops the image with text, HTML, files, a URL and a custom format, "
                + "one at a time and all together: the image must be read each time", () -> {
            List<List<String>> sets = List.of(List.of(), List.of("text"), List.of("HTML"), List.of("files"),
                    List.of("URL"), List.of("custom"), List.of("text", "HTML", "files", "URL", "custom"));
            List<String> broken = new ArrayList<>();
            List<String> notAnnounced = new ArrayList<>();
            for (List<String> set : sets) {
                fx(() -> imageWith = set);
                dropChip("Image and more", 10000);
                Image image = fx(() -> droppedImage);
                String name = set.isEmpty() ? "alone" : String.join(" + ", set);
                if (image == null || image.getWidth() != sampleImage.getWidth()) {
                    broken.add(name);
                } else if (!fx(() -> droppedHasImage)) {
                    notAnnounced.add(name);
                }
            }
            fx(() -> imageWith = List.of());
            check(broken.isEmpty(), "the image could not be read with: " + broken);
            check(notAnnounced.isEmpty(), "hasImage() is false, although getImage() reads the image, with: "
                    + notAnnounced + ": QuantumClipboard.hasContent(IMAGE) returns at text/html, whether its HTML has "
                    + "an <img>, before it looks at the raw image");
            return "read with each format and all of them";
        });
        robot("Full drag released outside the window", "JDK-8365635: a press-drag-release that ends on the "
                + "desktop must end with one MOUSE_DRAG_DONE on its source", () -> {
            Point2D outside = outsideWindow(fullSource);
            int done = fx(() -> fullDone);
            fx(() -> fullDragResult = null);
            Point2D from = fx(() -> TestKit.center(fullSource));
            TestKit.dragPress(from, outside);
            TestKit.dragMove(from, outside);
            TestKit.dragHover(outside);
            TestKit.dragRelease(outside);
            await("MOUSE_DRAG_DONE after a release outside the window", 3000, () -> fullDone > done);
            TestKit.pause(300);
            check(fx(() -> fullDone) == done + 1, (fx(() -> fullDone) - done) + " MOUSE_DRAG_DONE");
            check(fx(() -> fullDragResult) == null, "a target got MOUSE_DRAG_RELEASED: " + fx(() -> fullDragResult));
            return "one MOUSE_DRAG_DONE, released at " + TestKit.fmt(outside);
        });
        robot("Reorder within a list", "JDK-8089586: drags the first item of To do onto the fourth, which must "
                + "move it there without losing an item", () -> {
            List<String> before = fx(() -> List.copyOf(todoList.getItems()));
            String item = before.getFirst();
            String target = before.get(3);
            Node from = fx(() -> cellOf(todoList, item));
            Node to = fx(() -> cellOf(todoList, target));
            TestKit.reveal(from);
            try {
                TestKit.drag(fx(() -> TestKit.center(from)), fx(() -> TestKit.center(to)));
                List<String> want = new ArrayList<>(before);
                want.remove(item);
                want.add(want.indexOf(target), item);
                await("the item before \"" + target + "\"", () -> todoList.getItems().equals(want));
            } finally {
                fx(() -> todoList.getItems().setAll(before));
            }
            return "\"" + item + "\" moved before \"" + target + "\"";
        });
    }

    private static Node cellOf(ListView<String> list, String item) {
        return list.lookupAll(".list-cell").stream()
                .filter(n -> n instanceof ListCell<?> c && item.equals(c.getItem())).findFirst().orElseThrow();
    }

    private void resetEvents() {
        seqEvents.clear();
        seqDetected = false;
        seqDone = null;
        seqDoneString = null;
        seqDoneTarget = null;
        seqDropTarget = null;
        seqDropScreen = null;
    }

    private void defineEventTests() {
        robot("Event order", "Drops on the outer destination: DRAG_ENTERED, DRAG_OVER, DRAG_DROPPED, DRAG_EXITED and "
                + "then DRAG_DONE, with their targets, coordinates and data", () -> {
            fx(this::resetEvents);
            TestKit.reveal(seqSource);
            Point2D to = fx(() -> TestKit.center(seqOuterTitle));
            dragThrough(List.of(fx(() -> TestKit.center(seqSource)), intoOuter(), to));
            await("DRAG_DONE", 5000, () -> seqDone != null);
            List<String> events = fx(() -> List.copyOf(seqEvents));
            check(events.equals(List.of("outer ENTERED", "outer OVER", "outer DROPPED", "outer EXITED", "source DONE")),
                    "the events were " + events);
            check("accepted COPY".equals(fx(() -> seqDone)), "the drag ended " + fx(() -> seqDone));
            check(fx(() -> seqDropSource == seqSource), "the gesture source at the drop is " + fx(() -> seqDropSource));
            check(fx(() -> seqDropAccepting == seqOuter), "the gesture target at the drop is "
                    + fx(() -> seqDropAccepting));
            check(fx(() -> seqPayload.equals(seqDoneString)), "JDK-8124226: the source read \""
                    + fx(() -> seqDoneString) + "\" at DRAG_DONE");
            return checkDropPoint(to);
        });
        robot("Nested destinations", "Drags over the outer destination into the rotated inner one and drops there: "
                + "the outer one must not exit, and the inner one takes the drop", () -> {
            fx(this::resetEvents);
            TestKit.reveal(seqSource);
            Point2D from = fx(() -> TestKit.center(seqSource));
            Point2D outer = fx(() -> TestKit.center(seqOuterTitle));
            Point2D inner = fx(() -> TestKit.center(seqInner));
            dragThrough(List.of(from, intoOuter(), outer, inner));
            await("DRAG_DONE", 5000, () -> seqDone != null);
            List<String> events = fx(() -> List.copyOf(seqEvents));
            // JavaFX exits from the outermost destination in
            check(events.equals(List.of("outer ENTERED", "outer OVER", "inner ENTERED", "outer ENTERED_TARGET",
                    "inner OVER", "inner DROPPED", "outer EXITED", "inner EXITED", "source DONE")),
                    "the events were " + events);
            check(fx(() -> seqDropAccepting == seqInner), "the gesture target at the drop is "
                    + fx(() -> seqDropAccepting));
            return checkDropPoint(inner);
        });
        robot("Gesture target at DRAG_DONE", "DRAG_DONE must name the destination that took the drop as its "
                + "gesture target, also when the source is its own destination", () -> {
            fx(this::resetEvents);
            TestKit.reveal(seqSource);
            dragThrough(List.of(fx(() -> TestKit.center(seqSource)), intoOuter(),
                    fx(() -> TestKit.center(seqOuterTitle))));
            await("DRAG_DONE", 5000, () -> seqDone != null);
            check("accepted COPY".equals(fx(() -> seqDone)), "the drag ended " + fx(() -> seqDone));
            Object target = fx(() -> seqDoneTarget);
            check(target == fx(() -> seqOuter), "DRAG_DONE.getGestureTarget() is " + target + " instead of the "
                    + "destination: DnDGesture.processTargetDrop takes the accepting object of DRAG_DROPPED, which is "
                    + "the source of the event that Scene creates");
            return "the destination";
        });
        robot("Acceptance changes while hovering", "Hovers the half that rejects and then the half that accepts, "
                + "and drops; then the other way round, which must end not accepted", () -> {
            Point2D from = fx(() -> TestKit.center(seqSource));
            Bounds b = fx(() -> TestKit.screenBounds(halfTarget));
            Point2D left = new Point2D(b.getMinX() + b.getWidth() * 0.2, b.getCenterY());
            Point2D right = new Point2D(b.getMinX() + b.getWidth() * 0.8, b.getCenterY());
            int drops = fx(() -> halfDrops);
            fx(this::resetEvents);
            dragThrough(List.of(from, left, right));
            await("DRAG_DONE", 5000, () -> seqDone != null);
            check("accepted COPY".equals(fx(() -> seqDone)), "rejecting, then accepting: the drag ended "
                    + fx(() -> seqDone));
            check(fx(() -> halfDrops) == drops + 1, "rejecting, then accepting: no drop");
            fx(this::resetEvents);
            dragThrough(List.of(from, right, left));
            await("DRAG_DONE", 5000, () -> seqDone != null);
            check("not accepted".equals(fx(() -> seqDone)), "accepting, then rejecting: the drag ended "
                    + fx(() -> seqDone));
            check(fx(() -> halfDrops) == drops + 1, "accepting, then rejecting: the destination took the drop");
            return "the last answer of DRAG_OVER counted, both ways";
        });
        robot("Data read during DRAG_OVER", "JDK-8131121, JDK-8131193: the destination reads the text, the custom "
                + "format and formats that were not dragged before the drop", () -> {
            fx(() -> {
                resetEvents();
                readProblem = null;
                readOvers = 0;
            });
            TestKit.drag(seqSource, readTarget);
            await("DRAG_DONE", 5000, () -> seqDone != null);
            check(fx(() -> readProblem) == null, fx(() -> readProblem));
            check(fx(() -> readOvers) > 0, "no DRAG_OVER");
            check("accepted COPY".equals(fx(() -> seqDone)), "the drag ended " + fx(() -> seqDone));
            return "read in " + fx(() -> readOvers) + " DRAG_OVER and at the drop";
        });
        robot("Through a mouse-transparent node", "Drops on a destination under a mouse-transparent overlay, which "
                + "must get no drag event", () -> {
            int drops = fx(() -> underDrops);
            int hits = fx(() -> overlayHits);
            fx(this::resetEvents);
            TestKit.drag(seqSource, underTarget);
            await("DRAG_DONE", 5000, () -> seqDone != null);
            check(fx(() -> underDrops) == drops + 1, "the destination under the overlay got no drop");
            check(fx(() -> overlayHits) == hits, "the mouse-transparent overlay got DRAG_OVER");
            return "dropped under the overlay";
        });
        robot("Drop on its own source", "Drags a tile out and back onto itself, which accepts its own drag", () -> {
            TestKit.reveal(selfTile);
            Point2D at = fx(() -> TestKit.center(selfTile));
            Point2D out = at.add(0, fx(() -> selfTile.getHeight()) + 30);
            fx(() -> selfDone = null);
            dragThrough(List.of(at, out, at));
            await("DRAG_DONE", 5000, () -> selfDone != null);
            check("accepted COPY".equals(fx(() -> selfDone)), "the drag ended " + fx(() -> selfDone));
            return "accepted by itself";
        });
        robot("Source removed by the drop", "JDK-8205521: the destination removes the source from the scene at the "
                + "drop, and DRAG_DONE must still reach the source", () -> {
            try {
                fx(() -> removableDone = null);
                TestKit.drag(removable, removerTarget);
                await("DRAG_DONE on the removed source", 5000, () -> removableDone != null);
                check("accepted MOVE".equals(fx(() -> removableDone)), "the drag ended " + fx(() -> removableDone));
                check(fx(() -> removableDoneDetached), "the source was still in the scene at DRAG_DONE");
            } finally {
                fx(this::restoreRemovable);
            }
            return "DRAG_DONE out of the scene, accepted MOVE";
        });
        robot("Another button during a drag", "JDK-8165749: presses and releases the secondary button while "
                + "dragging over a destination, then releases: the drag ends and the pointer works", () -> {
            fx(this::resetEvents);
            TestKit.reveal(seqSource);
            Point2D from = fx(() -> TestKit.center(seqSource));
            Point2D to = fx(() -> TestKit.center(seqOuterTitle));
            try {
                TestKit.dragPress(from, to);
                TestKit.dragMove(from, to);
                TestKit.dragHover(to);
                fx(() -> TestKit.robot().mousePress(MouseButton.SECONDARY));
                TestKit.pause(150);
                fx(() -> TestKit.robot().mouseRelease(MouseButton.SECONDARY));
                TestKit.dragHover(to);
            } finally {
                TestKit.dragRelease(to);
            }
            awaitOrCancel("DRAG_DONE", () -> seqDone != null);
            checkPointerAfterDrag("after another button during a drag", seqOuter, eventsClicker);
            return "ended " + fx(() -> seqDone) + ", the pointer worked";
        });
        robot("Drag with the secondary button", "Starts the drag with the secondary button and drops it with "
                + "its release, which must end the drag", () -> {
            fx(this::resetEvents);
            TestKit.reveal(seqSource);
            Point2D from = fx(() -> TestKit.center(seqSource));
            Point2D to = fx(() -> TestKit.center(seqOuterTitle));
            dragWithButton(MouseButton.SECONDARY, from, to);
            if (!fx(() -> seqDetected)) {
                TestKit.skip("no DRAG_DETECTED for the secondary button");
            }
            awaitOrCancel("the end of the drag at the release of the secondary button", () -> seqDone != null);
            check("accepted COPY".equals(fx(() -> seqDone)), "the drag ended " + fx(() -> seqDone));
            check("outer".equals(fx(() -> seqDropTarget)), "dropped on " + fx(() -> seqDropTarget));
            checkPointerAfterDrag("after a drag with the secondary button", seqOuter, eventsClicker);
            return "dropped with the secondary button";
        });
    }

    /**
     * A point inside the left edge of the outer destination, at the height
     * of its title, so that a drag from the source enters it far from the
     * inner destination.
     */
    private Point2D intoOuter() {
        return fx(() -> new Point2D(TestKit.screenBounds(seqOuter).getMinX() + 6,
                TestKit.center(seqOuterTitle).getY()));
    }

    /**
     * Checks the coordinates of the last drop of the event card against
     * where the robot released.
     */
    private String checkDropPoint(Point2D released) {
        Point2D screen = fx(() -> seqDropScreen);
        Point2D local = fx(() -> seqDropLocal);
        Point2D expected = fx(() -> seqDropExpected);
        check(screen != null, "no drop");
        // The release motions wiggle by a pixel; fractional scales round by another
        check(screen.distance(released) < 4, "the drop is at " + TestKit.fmt(screen) + " on the screen, the pointer at "
                + TestKit.fmt(released));
        check(expected != null && local.distance(expected) < 1, "the local drop point is " + TestKit.fmt(local)
                + ", the screen point maps to " + TestKit.fmt(expected));
        return "dropped at " + TestKit.fmt(screen) + ", local " + TestKit.fmt(local);
    }

    private void defineSituationTests() {
        robot("Empty dragboard", "startDragAndDrop without content must not start a drag: the press-drag-release "
                + "goes on to the release, and no destination sees it", () -> {
            fx(emptyCounts::reset);
            int entered = fx(() -> sitDrops);
            TestKit.drag(emptySource, sitTarget);
            await("MOUSE_RELEASED on the source", 3000, () -> emptyCounts.released > 0);
            TestKit.pause(300);
            Counts c = fx(() -> emptyCounts);
            check(c.detected == 1, c.toString());
            check(fx(() -> emptyCounts.done) == 0, "DRAG_DONE of a drag that has no content: " + c);
            check(fx(() -> emptyCounts.dragged) >= 10, "the press-drag-release stopped: " + c);
            check(fx(() -> sitDrops) == entered, "the destination got a drop");
            checkPointerAfterDrag("after an empty drag", sitTarget, sitClicker);
            return fx(c::toString);
        });
        robot("Drag detected, no drag started", "A DRAG_DETECTED that starts nothing leaves a simple "
                + "press-drag-release, to the release", () -> {
            fx(noDndCounts::reset);
            TestKit.drag(noDndSource, sitTarget);
            await("MOUSE_RELEASED on the source", 3000, () -> noDndCounts.released > 0);
            String c = fx(noDndCounts::toString);
            check(fx(() -> noDndCounts.detected) == 1, c);
            check(fx(() -> noDndCounts.dragged) >= 10, "the press-drag-release stopped: " + c);
            checkPointerAfterDrag("after a drag that was not started", sitTarget, sitClicker);
            return c;
        });
        robot("Simple press-drag-release", "JDK-8350551: presses a node and drags over another and out of both: "
                + "every MOUSE_DRAGGED and the release go to the pressed node", () -> {
            fx(() -> {
                pdrCounts.reset();
                pdrStray = 0;
            });
            TestKit.reveal(pdrNode);
            Point2D from = fx(() -> TestKit.center(pdrNode));
            Point2D other = fx(() -> TestKit.center(pdrOther));
            Point2D beyond = other.add(0, 80);
            dragThrough(List.of(from, other, beyond));
            await("MOUSE_RELEASED on the pressed node", 3000, () -> pdrCounts.released > 0);
            check(fx(() -> pdrStray) == 0, fx(() -> pdrStray) + " events reached other nodes");
            check(fx(() -> pdrCounts.dragged) >= 20, "only " + fx(pdrCounts::toString));
            return fx(pdrCounts::toString);
        });
        robot("Middle and secondary button drags", "JDK-8131110: a press-drag-release with the middle and then the "
                + "secondary button: MOUSE_DRAGGED with that button down", () -> {
            StringBuilder result = new StringBuilder();
            for (MouseButton button : List.of(MouseButton.MIDDLE, MouseButton.SECONDARY)) {
                fx(pdrCounts::reset);
                TestKit.reveal(pdrNode);
                Point2D from = fx(() -> TestKit.center(pdrNode));
                dragWithButton(button, from, fx(() -> TestKit.center(pdrOther)));
                await("MOUSE_RELEASED of the " + button + " button", 3000, () -> pdrCounts.released > 0);
                int dragged = fx(() -> pdrCounts.dragged);
                int down = fx(() -> button == MouseButton.MIDDLE ? pdrCounts.middle : pdrCounts.secondary);
                check(dragged >= 10, "only " + dragged + " MOUSE_DRAGGED with the " + button + " button");
                check(down == dragged, down + " of " + dragged + " MOUSE_DRAGGED with the " + button + " button down");
                result.append(result.isEmpty() ? "" : ", ").append(button).append(" ").append(dragged);
                TestKit.pause(300);
            }
            return result + " MOUSE_DRAGGED";
        });
        robot("Child removed on press", "JDK-8087684: the pressed child removes itself, and its parent must still "
                + "detect the drag and get MOUSE_DRAGGED", () -> {
            fx(childCounts::reset);
            List<String> seen = new ArrayList<>();
            javafx.event.EventHandler<MouseEvent> filter = e -> {
                if (e.getEventType() != MouseEvent.MOUSE_MOVED && e.getEventType() != MouseEvent.MOUSE_DRAGGED) {
                    seen.add(e.getEventType() + " on " + e.getTarget());
                }
            };
            fx(() -> stage.getScene().addEventFilter(MouseEvent.ANY, filter));
            try {
                TestKit.reveal(childBox);
                Point2D at = fx(() -> TestKit.center(childRect));
                Bounds b = fx(() -> TestKit.screenBounds(childBox));
                TestKit.drag(at, new Point2D(b.getMaxX() - 10, b.getCenterY()));
                try {
                    await("MOUSE_RELEASED on the parent", 3000, () -> childCounts.released > 0);
                } catch (AssertionError e) {
                    throw new AssertionError("JDK-8087684: the parent got " + fx(childCounts::toString)
                            + ", as Scene sets pdrInProgress after it dispatched the press, so that "
                            + "handleNodeRemoval leaves the removed child the target of the gesture; the scene saw "
                            + fx(() -> List.copyOf(seen)));
                }
                check(fx(() -> childCounts.detected) == 1, "the parent: " + fx(childCounts::toString));
                check(fx(() -> childCounts.dragged) >= 10, "the parent: " + fx(childCounts::toString));
            } finally {
                fx(() -> {
                    stage.getScene().removeEventFilter(MouseEvent.ANY, filter);
                    restoreChild();
                });
            }
            return fx(childCounts::toString);
        });
        robot("Click with setDragDetect(true)", "JDK-8285918: clicks a button that calls setDragDetect(true) at the "
                + "press and starts a drag at DRAG_DETECTED: no drag may stay behind", () -> {
            int detected = fx(() -> pdDetected);
            int done = fx(() -> pdDone);
            TestKit.click(pdButton);
            TestKit.pause(500);
            if (fx(() -> pdDetected) > detected) {
                awaitOrCancel("JDK-8285918: the end of the drag that the click started", () -> pdDone > done);
            }
            checkPointerAfterDrag("after the click", pdButton, sitClicker);
            fx(() -> imgDone = null);
            TestKit.drag(imgSource, sitTarget);
            await("a drag after the click", 5000, () -> imgDone != null);
            check("accepted COPY".equals(fx(() -> imgDone)), "a drag after the click ended " + fx(() -> imgDone));
            return (fx(() -> pdDetected) > detected ? "a drag started and ended" : "no drag") + ", then a drag worked";
        });
        robot("Pressed state after a drag", "JDK-8307098: the source must not stay pressed after a drop, nor after a "
                + "release over no destination", () -> {
            fx(() -> imgDone = null);
            TestKit.drag(imgSource, sitTarget);
            await("DRAG_DONE", 5000, () -> imgDone != null);
            TestKit.pause(200);
            check(!fx(() -> imgSource.isPressed()), "JDK-8307098: the source stays pressed after a drop");
            fx(() -> imgDone = null);
            TestKit.drag(imgSource, sitClicker.node);
            await("DRAG_DONE", 5000, () -> imgDone != null);
            TestKit.pause(200);
            check(!fx(() -> imgSource.isPressed()), "JDK-8307098: the source stays pressed after a release over no "
                    + "destination");
            return "not pressed";
        });
        robot("Drag image colors", "JDK-8271398, JDK-8327469, JDK-8143599: captures the RGB stripes drag image under "
                + "the pointer, which must be red, green and blue from left to right, and gone after Escape", () -> {
            TestKit.reveal(imgSource);
            Point2D from = fx(() -> TestKit.center(imgSource));
            Point2D probe = fx(() -> new Point2D(stage.getX() + stage.getWidth() / 2,
                    stage.getY() + stage.getHeight() / 2));
            Color[] during;
            Color[] after;
            fx(() -> imgDone = null);
            try {
                TestKit.dragPress(from, probe);
                TestKit.dragMove(from, probe);
                TestKit.dragHover(probe);
                TestKit.move(probe);
                TestKit.pause(400);
                during = stripeColors(probe);
                TestKit.shortcut(KeyCode.ESCAPE);
                await("DRAG_DONE after Escape", 3000, () -> imgDone != null);
            } finally {
                TestKit.dragRelease(probe);
            }
            TestKit.move(probe);
            TestKit.pause(600);
            after = stripeColors(probe);
            String seen = describe(during);
            if (dominant(during[0], 2) && dominant(during[2], 0)) {
                throw new AssertionError("JDK-8271398: the drag image has red and blue swapped: " + seen);
            }
            check(dominant(during[0], 0) && dominant(during[1], 1) && dominant(during[2], 2),
                    "no RGB stripes under the pointer during the drag: " + seen);
            check(!(dominant(after[0], 0) && dominant(after[2], 2)), "JDK-8143599: the drag image stays on screen "
                    + "after Escape: " + describe(after));
            return seen + ", gone after Escape";
        });
        robot("Twenty drags in a row", "JDK-8280383, JDK-8227004: twenty drags with a drag image, one right after "
                + "the other, must all drop", () -> {
            int drops = fx(() -> sitDrops);
            for (int i = 1; i <= 20; i++) {
                fx(() -> imgDone = null);
                TestKit.drag(imgSource, sitTarget);
                int n = i;
                await("DRAG_DONE of drag " + n, 5000, () -> imgDone != null);
                check("accepted COPY".equals(fx(() -> imgDone)), "drag " + i + " ended " + fx(() -> imgDone));
                check(fx(() -> sitDrops) == drops + i, "drag " + i + " did not drop");
            }
            checkPointerAfterDrag("after twenty drags", sitTarget, sitClicker);
            return "20 drops";
        });
    }

    /**
     * The colors left of, under and right of the pointer, below it, where the
     * stripes of the drag image are whether the platform scales it or not.
     */
    private static Color[] stripeColors(Point2D probe) {
        WritableImage strip;
        try {
            strip = fx(() -> TestKit.robot().getScreenCapture(null, probe.getX() - 60, probe.getY() + 52, 120, 1,
                    true));
        } catch (RuntimeException e) {
            TestKit.skip("cannot capture the screen: " + e);
            return null;
        }
        return new Color[] {strip.getPixelReader().getColor(10, 0), strip.getPixelReader().getColor(52, 0),
                strip.getPixelReader().getColor(110, 0)};
    }

    private static boolean dominant(Color c, int channel) {
        double[] v = {c.getRed(), c.getGreen(), c.getBlue()};
        for (int i = 0; i < 3; i++) {
            if (i != channel && v[channel] - v[i] < 0.4) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Color[] colors) {
        return Arrays.stream(colors).map(Color::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    private void defineWindowTests() {
        robot("To another window", "JDK-8126843: drags from the main window to a second window of the application",
                () -> {
            try {
                openSecondWindowForTest();
                int drops = fx(() -> winDrops);
                fx(() -> winMainDone = null);
                TestKit.drag(fx(() -> TestKit.center(winMainSource)), fx(() -> TestKit.center(winTarget)));
                await("the drop in the second window", 5000, () -> winDrops > drops);
                await("DRAG_DONE", 5000, () -> winMainDone != null);
                check("accepted COPY".equals(fx(() -> winMainDone)), "the drag ended " + fx(() -> winMainDone));
                // Each scene has a gesture of its own: the other window sees no gesture source
                check(fx(() -> winDropSource == null || winDropSource == winMainSource), "the gesture source is "
                        + fx(() -> winDropSource));
            } finally {
                fx(this::closeSecondWindow);
            }
            return "dropped in the second window";
        });
        robot("From another window", "JDK-8126843: drags from a second window of the application to the main one",
                () -> {
            try {
                openSecondWindowForTest();
                int drops = fx(() -> winMainDrops);
                fx(() -> winSourceDone = null);
                TestKit.drag(fx(() -> TestKit.center(winSource)), fx(() -> TestKit.center(winMainTarget)));
                await("the drop in the main window", 5000, () -> winMainDrops > drops);
                await("DRAG_DONE", 5000, () -> winSourceDone != null);
                check("accepted COPY".equals(fx(() -> winSourceDone)), "the drag ended " + fx(() -> winSourceDone));
            } finally {
                fx(this::closeSecondWindow);
            }
            return "dropped in the main window";
        });
        robot("Drag from an inactive window", "JDK-8126395: with the second window focused, drags in the main window "
                + "without clicking it first", () -> {
            try {
                openSecondWindowForTest();
                fx(() -> secondStage.requestFocus());
                try {
                    await("the second window focused", 2000, () -> secondStage.isFocused() && !stage.isFocused());
                } catch (AssertionError e) {
                    TestKit.skip("the desktop did not focus the second window");
                }
                int drops = fx(() -> winMainDrops);
                TestKit.drag(fx(() -> TestKit.center(winMainSource)), fx(() -> TestKit.center(winMainTarget)));
                await("the drop", 5000, () -> winMainDrops > drops);
                check(fx(() -> winMainDropSource == winMainSource), "the gesture source is "
                        + fx(() -> winMainDropSource));
            } finally {
                fx(this::closeSecondWindow);
            }
            return "dropped from the inactive window";
        });
        robot("Extended stage", "JDK-8367557: drags in an extended stage, which must take the mouse right after the "
                + "drop, without the pointer leaving it", () -> {
            try {
                fx(this::openExtendedStage);
                await("the extended stage", 5000, () -> extendedStage != null && extendedStage.isShowing()
                        && extClicker.node.localToScreen(0, 0) != null);
                TestKit.pause(600);
                fx(() -> extDone = null);
                TestKit.drag(fx(() -> TestKit.center(extSource)), fx(() -> TestKit.center(extTarget)));
                await("DRAG_DONE", 5000, () -> extDone != null);
                check("accepted COPY".equals(fx(() -> extDone)), "the drag ended " + fx(() -> extDone));
                checkPointerAfterDrag("in the extended stage (JDK-8367557)", extTarget, extClicker);
            } finally {
                fx(this::closeExtendedStage);
            }
            return "dropped, and the stage took the mouse";
        });
        robot("No leak after a drag", "JDK-8279214: drags and does a full drag in a window, closes it, and checks "
                + "that its scene and nodes are collected", () -> {
            List<WeakReference<Object>> refs = new ArrayList<>();
            boolean[] done = {false, false};
            Stage[] holder = {null};
            fx(() -> holder[0] = leakStage(refs, done));
            Point2D[] points;
            try {
                await("the window", 5000, () -> holder[0].getScene().lookup("#leak-full-target")
                        .localToScreen(0, 0) != null);
                TestKit.pause(500);
                points = fx(() -> new Point2D[] {
                    TestKit.center(holder[0].getScene().lookup("#leak-source")),
                    TestKit.center(holder[0].getScene().lookup("#leak-target")),
                    TestKit.center(holder[0].getScene().lookup("#leak-full-source")),
                    TestKit.center(holder[0].getScene().lookup("#leak-full-target"))});
                TestKit.drag(points[0], points[1]);
                await("the drag in the window", 5000, () -> done[0]);
                TestKit.drag(points[2], points[3]);
                await("the full drag in the window", 5000, () -> done[1]);
            } finally {
                fx(() -> holder[0].close());
                holder[0] = null;
            }
            // Moves away and lets pulses run, so that nothing in flight holds the window
            TestKit.move(fx(() -> TestKit.center(winMainSource)));
            long end = System.nanoTime() + 8_000_000_000L;
            while (refs.stream().anyMatch(r -> r.get() != null) && System.nanoTime() < end) {
                System.gc();
                fx(() -> { });
                TestKit.pause(100);
            }
            List<String> kept = new ArrayList<>();
            for (WeakReference<Object> r : refs) {
                Object o = r.get();
                if (o != null) {
                    kept.add(o instanceof Node n && n.getId() != null ? "#" + n.getId() : o.getClass().getSimpleName());
                }
            }
            check(kept.isEmpty(), "still reachable after the window closed: " + kept);
            return refs.size() + " objects collected";
        });
    }

    /**
     * A window with a drag and drop source and destination and a full drag
     * source and target, which tell the test when each gesture ended.
     */
    private Stage leakStage(List<WeakReference<Object>> refs, boolean[] done) {
        Label source = new Label("Drag me");
        source.setId("leak-source");
        source.getStyleClass().addAll("tile", "source");
        source.setOnDragDetected(e -> {
            Dragboard db = source.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("leak");
            db.setContent(content);
            e.consume();
        });
        source.setOnDragDone(e -> done[0] = true);
        Label target = new Label("Drop here");
        target.setId("leak-target");
        target.getStyleClass().addAll("tile", "target");
        target.setOnDragOver(e -> {
            e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        target.setOnDragDropped(e -> {
            e.setDropCompleted(true);
            e.consume();
        });
        Label fullSrc = new Label("Full drag me");
        fullSrc.setId("leak-full-source");
        fullSrc.getStyleClass().addAll("tile", "source");
        fullSrc.setOnDragDetected(e -> {
            fullSrc.startFullDrag();
            e.consume();
        });
        Label fullDst = new Label("Full drag target");
        fullDst.setId("leak-full-target");
        fullDst.getStyleClass().add("full-target");
        fullDst.setOnMouseDragReleased(e -> done[1] = true);
        GridPane root = new GridPane(16, 16);
        root.addRow(0, source, target);
        root.addRow(1, fullSrc, fullDst);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8fafc;");
        Scene scene = new Scene(root, 360, 160);
        scene.getStylesheets().add(STYLESHEET);
        Stage s = new Stage();
        s.setTitle("Drag and drop: collected after closing");
        s.initOwner(stage);
        s.setScene(scene);
        s.setX(stage.getX() + 60);
        s.setY(stage.getY() + 160);
        s.show();
        for (Object o : List.of(s, scene, root, source, target, fullSrc, fullDst)) {
            refs.add(new WeakReference<>(o));
        }
        return s;
    }

    /**
     * Opens the second window and waits for it, or skips when it covers the
     * tiles of the main window, as on Wayland, which places windows itself.
     */
    private void openSecondWindowForTest() {
        TestKit.reveal(winMainSource);
        fx(this::openSecondWindow);
        await("the second window", 5000, () -> secondStage != null && secondStage.isShowing()
                && winTarget.localToScreen(0, 0) != null);
        TestKit.pause(600);
        boolean covers = fx(() -> {
            Rectangle2D w = new Rectangle2D(secondStage.getX(), secondStage.getY(), secondStage.getWidth(),
                    secondStage.getHeight());
            return w.contains(TestKit.center(winMainSource)) || w.contains(TestKit.center(winMainTarget));
        });
        if (covers) {
            TestKit.skip("the second window covers the tiles of the main window");
        }
    }

    /**
     * Waits for the end of a drag, and cancels it with Escape when it does not
     * come, so that the next tests do not start inside it.
     */
    private static void awaitOrCancel(String what, java.util.function.BooleanSupplier condition) {
        try {
            await(what, 5000, condition);
        } catch (AssertionError e) {
            TestKit.shortcut(KeyCode.ESCAPE);
            TestKit.pause(500);
            throw e;
        }
    }

    /**
     * Presses at the first point, moves through the others with the button
     * down, hovering at each, and releases at the last.
     */
    private static void dragThrough(List<Point2D> points, KeyCode... modifiers) {
        Point2D at = points.getFirst();
        TestKit.dragPress(at, points.get(1), modifiers);
        try {
            for (Point2D p : points.subList(1, points.size())) {
                TestKit.dragMove(at, p);
                TestKit.dragHover(p);
                at = p;
            }
        } finally {
            TestKit.dragRelease(at, modifiers);
        }
    }

    /**
     * A drag as TestKit.drag does, with another button than the primary.
     */
    private static void dragWithButton(MouseButton button, Point2D from, Point2D to) {
        TestKit.move(from.add(-2, 0));
        TestKit.pause(150);
        TestKit.move(from);
        TestKit.pause(250);
        fx(() -> TestKit.robot().mousePress(button));
        try {
            TestKit.pause(80);
            double dx = Math.signum(to.getX() - from.getX()), dy = Math.signum(to.getY() - from.getY());
            for (int i = 1; i <= 4; i++) {
                TestKit.move(from.add(dx * 3 * i, dy * 3 * i));
                TestKit.pause(120);
            }
            TestKit.dragMove(from, to);
            TestKit.dragHover(to);
        } finally {
            fx(() -> TestKit.robot().mouseRelease(button));
        }
        for (int i = 0; i < 4; i++) {
            TestKit.pause(80);
            TestKit.move(to.add((i + 1) % 2, 0));
        }
    }

    /**
     * Drags a mode source to a mode destination and checks the action of
     * both, or that the drop is rejected when the action is null.
     */
    private String modes(int source, int target, String action, KeyCode... modifiers) {
        Label done = sourceResults.get(source);
        Label dropped = targetResults.get(target);
        fx(() -> done.setText(""));
        TestKit.drag(sourceTiles.get(source), targetTiles.get(target), modifiers);
        await("the end of the drag", () -> done.getText().startsWith("Done"));
        String doneText = fx(() -> done.getText());
        String droppedText = fx(() -> dropped.getText());
        if (action == null) {
            check(doneText.startsWith("Done, rejected"), "the source shows \"" + doneText + "\"");
            check(!droppedText.startsWith("Dropped"), "the destination shows \"" + droppedText + "\"");
        } else {
            check(doneText.equals("Done, accepted " + action), "the source shows \"" + doneText + "\"");
            check(droppedText.equals("Dropped here, proposed " + action),
                    "the destination shows \"" + droppedText + "\"");
        }
        return doneText;
    }

    /**
     * Drags the grab source to a destination of the card, waits for its
     * DRAG_DROPPED and DRAG_DONE, and tells how the drag ended.
     */
    private String grabDrag(Label target, String what) {
        int drops = fx(() -> grabDrops(target));
        fx(() -> grabDone = null);
        TestKit.drag(grabSource, target);
        await("DRAG_DROPPED of the " + what, 5000, () -> grabDrops(target) > drops);
        await("DRAG_DONE of the " + what, 5000, () -> grabDone != null);
        return fx(() -> grabDone);
    }

    private void checkPointerAfterDrag(String when) {
        checkPointerAfterDrag(when, grabTarget, grabClicker);
    }

    /**
     * After a drag, moves from a node to a tile that accepts nothing, with no
     * button down: it must get MOUSE_MOVED and hover, no node of its scene may
     * get MOUSE_DRAGGED, and a click must reach it.
     */
    private void checkPointerAfterDrag(String when, Node fromNode, Clicker clicker) {
        int[] dragged = {0};
        javafx.event.EventHandler<MouseEvent> filter = e -> dragged[0]++;
        Scene scene = fx(() -> clicker.node.getScene());
        fx(() -> scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, filter));
        try {
            int moves = fx(() -> clicker.moves);
            int clicks = fx(() -> clicker.clicks);
            Point2D from = fx(() -> TestKit.center(fromNode));
            Point2D to = fx(() -> TestKit.center(clicker.node));
            for (int i = 1; i <= 12; i++) {
                TestKit.move(from.add(to.subtract(from).multiply(i / 12.0)));
                TestKit.pause(40);
            }
            TestKit.move(to.add(3, 0));
            TestKit.pause(150);
            check(fx(() -> dragged[0]) == 0, fx(() -> dragged[0]) + " MOUSE_DRAGGED with no button down " + when
                    + ": JavaFX or the platform still holds the button of the drag");
            await("MOUSE_MOVED on the tile " + when, 2000, () -> clicker.moves > moves);
            await("the tile hovered " + when, 2000, clicker.node::isHover);
            TestKit.click(to);
            await("the click on the tile " + when + " (JDK-8346932, JDK-8285918)", 2000,
                    () -> clicker.clicks > clicks);
        } finally {
            fx(() -> scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, filter));
        }
    }

    /**
     * A point of the desktop beside the window, on its screen, at the height
     * of a node, or skips when the window leaves no room.
     */
    private Point2D outsideWindow(Node from) {
        return fx(() -> {
            Point2D source = TestKit.center(from);
            Rectangle2D w = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            List<Screen> screens = Screen.getScreensForRectangle(w);
            Rectangle2D s = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
            double right = s.getMaxX() - w.getMaxX(), left = w.getMinX() - s.getMinX();
            double below = s.getMaxY() - w.getMaxY(), above = w.getMinY() - s.getMinY();
            double room = Math.max(Math.max(right, left), Math.max(below, above));
            if (room < 16) {
                TestKit.skip("the window leaves no room on its screen to drag outside it");
            }
            double gap = Math.min(40, room / 2);
            double y = Math.clamp(source.getY(), s.getMinY() + 8, s.getMaxY() - 8);
            double x = Math.clamp(source.getX(), s.getMinX() + 8, s.getMaxX() - 8);
            if (room == right) {
                return new Point2D(w.getMaxX() + gap, y);
            } else if (room == left) {
                return new Point2D(w.getMinX() - gap, y);
            } else if (room == below) {
                return new Point2D(x, w.getMaxY() + gap);
            }
            return new Point2D(x, w.getMinY() - gap);
        });
    }

    /**
     * Drags a format source to the drop target and waits for its drop.
     */
    private void dropChip(String title) {
        dropChip(title, 5000);
    }

    private void dropChip(String title, long timeoutMs) {
        int before = fx(() -> drops);
        fx(() -> chipDone.remove(title));
        TestKit.drag(chips.get(title), received.getParent());
        await("the drop of " + title, timeoutMs, () -> drops > before && chipDone.containsKey(title));
        String done = fx(() -> chipDone.get(title));
        check(!"not accepted".equals(done), "the drag of " + title + " was not accepted");
    }

    /**
     * Waits for a laid out node of a showing context menu.
     */
    /**
     * Shows the dialog and clicks a button, and tells whether that closed it
     * with the answer of the button.
     */
    private boolean clickDialogButton(ButtonType button) {
        Platform.runLater(this::showDialogAfterDrag);
        await("the dialog", 5000, () -> dialogAfterDrag != null && dialogAfterDrag.isShowing());
        TestKit.pause(400);
        Node node = fx(() -> dialogAfterDrag.getDialogPane().lookupButton(button));
        TestKit.click(fx(() -> TestKit.center(node)));
        long end = System.nanoTime() + 3_000_000_000L;
        while (fx(() -> dialogAfterDrag.isShowing()) && System.nanoTime() < end) {
            TestKit.pause(50);
        }
        boolean closed = fx(() -> !dialogAfterDrag.isShowing() && button.equals(dialogResult));
        fx(() -> dialogAfterDrag.close());
        return closed;
    }

    private static Node awaitMenuItem(ContextMenu menu, String selector) {
        Node[] found = {null};
        await("the node " + selector, () -> {
            Node n = menu.getScene() == null ? null : menu.getScene().getRoot().lookup(selector);
            found[0] = n != null && n.localToScreen(n.getLayoutBounds()) != null ? n : null;
            return found[0] != null;
        });
        TestKit.pause(150);
        return found[0];
    }

    /**
     * Drags a color source to a destination and waits for the end of the drag.
     */
    private void dragColor(Node from, Node to, KeyCode... modifiers) {
        fx(() -> colorDone = null);
        boolean[] done = {false};
        javafx.event.EventHandler<DragEvent> handler = e -> done[0] = true;
        fx(() -> from.addEventHandler(DragEvent.DRAG_DONE, handler));
        try {
            TestKit.drag(from, to, modifiers);
            await("the end of the drag", 5000, () -> done[0]);
        } finally {
            fx(() -> from.removeEventHandler(DragEvent.DRAG_DONE, handler));
        }
    }

    private static boolean closeArgb(int a, int b) {
        for (int shift = 0; shift < 32; shift += 8) {
            if (Math.abs(((a >> shift) & 0xff) - ((b >> shift) & 0xff)) > 3) {
                return false;
            }
        }
        return true;
    }

    private static void fx(Runnable r) {
        TestKit.fx(r);
    }

    private static <T> T fx(java.util.function.Supplier<T> s) {
        return TestKit.fx(s);
    }

    private static void await(String what, java.util.function.BooleanSupplier condition) {
        TestKit.await(what, condition);
    }

    private static void await(String what, long ms, java.util.function.BooleanSupplier condition) {
        TestKit.await(what, ms, condition);
    }

    private static void check(boolean condition, String message) {
        TestKit.check(condition, message);
    }

    // ---------------------------------------------------------------------
    // Log

    private Node logCard() {
        logView.setPrefHeight(220);
        logView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                setWrapText(true);
                setPrefWidth(0);
                getStyleClass().add("mono");
                setStyle("-fx-padding: 1 8 1 8; -fx-font-size: 11px;");
            }
        });
        VBox.setVgrow(logView, Priority.ALWAYS);
        Button clear = new Button("Clear");
        clear.setOnAction(e -> log.clear());
        HBox tools = new HBox(12, clear, new Label("Log"), logOver, logMouse);
        tools.setAlignment(Pos.CENTER_LEFT);
        VBox box = card("Event log", logView, tools);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private void logMouseEvents(Node node, String name) {
        node.addEventHandler(MouseEvent.ANY, e -> {
            if (logMouse.isSelected() && e.getEventType() != MouseEvent.MOUSE_MOVED) {
                log(String.format(Locale.ROOT, "%s on %s at %.1f, %.1f (screen %.1f, %.1f)%s", e.getEventType(),
                        name, e.getX(), e.getY(), e.getScreenX(), e.getScreenY(),
                        e.getEventType() == MouseEvent.MOUSE_CLICKED ? " clicks=" + e.getClickCount() : ""));
            }
        });
    }

    private void log(String text) {
        // The tests log from their own thread too: the list of the log is of the FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> log(text));
            return;
        }
        log.add(now() + "  " + text);
        if (log.size() > LOG_SIZE) {
            log.remove(0, log.size() - LOG_SIZE);
        }
        logView.scrollTo(log.size() - 1);
        System.out.println(text);
    }

    private static String now() {
        return LocalTime.now().format(CLOCK);
    }
}
