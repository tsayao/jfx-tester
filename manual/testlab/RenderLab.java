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

import javafx.animation.AnimationTimer;
import javafx.application.ColorScheme;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.AmbientLight;
import javafx.scene.CacheHint;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.LightBase;
import javafx.scene.Node;
import javafx.scene.ParallelCamera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SpotLight;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.DisplacementMap;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.FloatMap;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Glow;
import javafx.scene.effect.ImageInput;
import javafx.scene.effect.InnerShadow;
import javafx.scene.effect.Light;
import javafx.scene.effect.Lighting;
import javafx.scene.effect.MotionBlur;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.effect.Reflection;
import javafx.scene.effect.SepiaTone;
import javafx.scene.effect.Shadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Box;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Rotate;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * A laboratory for the Prism renderer: tabs of shapes, paints, text, images,
 * effects, blending and clips, canvases, 3D, CSS and controls, and a stress
 * test, all animated, and tests after the rendering system tests of OpenJFX:
 * colors read back from the screen, PixelBuffer updates, LCD text, snapshots,
 * and every tab rendered.
 */
public class RenderLab {

    /**
     * Runs this lab as an application of its own, with its tests:
     * <pre>java RenderLab.java</pre>
     * or {@code ./jfx-tester --tests=render}.
     */
    public static void main(String[] args) {
        TestLab.launchLab("render", args);
    }

    private static final String CSS = TestKit.BASE_CSS + """
        .card {
            -fx-background: #20242e;
            -fx-base: #3a3f4b;
            -fx-control-inner-background: #20242e;
            -fx-background-color: -fx-background;
            -fx-padding: 10;
        }
        .card-title { -fx-font-size: 12px; -fx-font-weight: bold; }
        .card-note { -fx-font-size: 10px; }
        .css-a {
            -fx-background-color: linear-gradient(to bottom right, #f472b6, #8b5cf6), rgba(0,0,0,0.25);
            -fx-background-insets: 0, 10;
            -fx-background-radius: 24, 14;
            -fx-border-color: #fde68a;
            -fx-border-width: 3;
            -fx-border-style: segments(10, 6) line-cap round;
            -fx-border-radius: 24;
        }
        .css-b {
            -fx-background-color: radial-gradient(center 30% 30%, radius 80%, #38bdf8, #0f172a);
            -fx-background-radius: 50% 10% 50% 10%;
            -fx-border-color: #22d3ee #a3e635 #f472b6 #fbbf24;
            -fx-border-width: 4 8 4 8;
            -fx-border-radius: 50% 10% 50% 10%;
            -fx-effect: dropshadow(gaussian, #22d3ee, 18, 0.2, 0, 0);
        }
        .css-c {
            -fx-background-color: repeating-image-pattern("@TEXTURE@"), rgba(15,23,42,0.6);
            -fx-background-radius: 16;
            -fx-border-color: white;
            -fx-border-style: dotted;
            -fx-border-width: 2;
            -fx-border-radius: 16;
        }
        """;

    private static final double CARD_W = 250;
    private static final double CARD_H = 176;

    private final Map<Tab, List<DoubleConsumer>> ticks = new HashMap<>();
    private final TabPane tabs = new TabPane();
    private final CheckBox animate = new CheckBox("Animate");
    private final CheckBox cycle = new CheckBox("Cycle tabs");
    private final Label fpsLabel = new Label("— fps");
    private final Region fill = new Region();
    private final Random random = new Random(7);
    private Tab building;
    private boolean backdrop;
    private Stage commandStage;
    private Image texture;
    private Image photo;
    private double time;
    private double lastCycle;

    /**
     * The lab, for its tab in the test lab.
     */
    Region content(Stage stage) {
        commandStage = stage;
        backdrop = Boolean.getBoolean("glass.gtk4.backdrop.active");
        texture = checker(64, Color.web("#f472b6"), Color.web("#1e293b"));
        photo = synthetic(256, 256);

        animate.setSelected(true);
        cycle.setSelected(Boolean.getBoolean("render.cycle"));

        addTab("Shapes", this::shapes);
        addTab("Paints", this::paints);
        addTab("Text", this::text);
        addTab("Images", this::images);
        addTab("Effects", this::effects);
        addTab("Blend and clip", this::blending);
        addTab("Canvas", this::canvas);
        addTab("3D", this::scene3d);
        addTab("CSS and controls", this::controls);
        addTab("Stress", this::stress);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(Integer.getInteger("render.tab", 0));
        defineTests();
        suite.setBeforeRun(() -> {
            stage.toFront();
            stage.requestFocus();
        });
        suite.setAfterEach(this::closeTestStage);

        fpsLabel.getStyleClass().add("mono");
        HBox fps = new HBox(fpsLabel);
        fps.getStyleClass().add("pill");
        HBox pipeline = new HBox(new Label(pipelineName() + " · 3D "
                + (Platform.isSupported(ConditionalFeature.SCENE3D) ? "yes" : "no")));
        pipeline.getStyleClass().add("pill");
        HBox bar = new HBox(10, fps, pipeline, animate, cycle);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox showcase = new VBox(10, bar, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        showcase.setMinSize(0, 0);
        HBox columns = new HBox(14, showcase);
        HBox.setHgrow(showcase, Priority.ALWAYS);
        columns.setPadding(new Insets(4, 18, 18, 18));
        // Short of room, the cards are cut at the bottom, the header stays
        columns.setMinHeight(0);
        columns.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.RIGHT && e.isControlDown()) {
                tabs.getSelectionModel().selectNext();
            } else if (e.getCode() == KeyCode.LEFT && e.isControlDown()) {
                tabs.getSelectionModel().selectPrevious();
            }
        });
        loop();
        return columns;
    }

    /**
     * The stylesheet of the lab, with its texture, once the content is made.
     */
    String stylesheet() {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(
                CSS.replace("@TEXTURE@", dataUrl(texture)).getBytes(StandardCharsets.UTF_8));
    }

    private void addTab(String name, Runnable content) {
        building = new Tab(name);
        ticks.put(building, new ArrayList<>());
        FlowPane flow = new FlowPane(14, 14);
        flow.setPadding(new Insets(14));
        building.setUserData(flow);
        content.run();
        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        building.setContent(scroll);
        tabs.getTabs().add(building);
    }

    private void card(String title, String note, Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label n = new Label(note);
        n.getStyleClass().add("card-note");
        n.setWrapText(true);
        StackPane stage = new StackPane(content);
        stage.setMinSize(CARD_W, CARD_H);
        stage.setPrefSize(CARD_W, CARD_H);
        stage.setMaxSize(CARD_W, CARD_H);
        stage.setClip(new Rectangle(CARD_W, CARD_H));
        VBox box = new VBox(t, stage, n);
        box.getStyleClass().add("card");
        box.setPrefWidth(CARD_W + 20);
        ((FlowPane) building.getUserData()).getChildren().add(box);
    }

    private void wide(String title, String note, Node content, double w, double h) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label n = new Label(note);
        n.getStyleClass().add("card-note");
        StackPane stage = new StackPane(content);
        stage.setMinSize(w, h);
        stage.setPrefSize(w, h);
        stage.setMaxSize(w, h);
        stage.setClip(new Rectangle(w, h));
        VBox box = new VBox(t, stage, n);
        box.getStyleClass().add("card");
        ((FlowPane) building.getUserData()).getChildren().add(box);
    }

    private void tick(DoubleConsumer consumer) {
        ticks.get(building).add(consumer);
    }

    private void loop() {
        new AnimationTimer() {
            private long last;
            private long fpsStart;
            private int frames;

            @Override
            public void handle(long now) {
                if (last == 0) {
                    last = now;
                    fpsStart = now;
                }
                double dt = (now - last) / 1e9;
                last = now;
                frames++;
                if (now - fpsStart > 500_000_000L) {
                    fpsLabel.setText(String.format("%.0f fps", frames * 1e9 / (now - fpsStart)));
                    frames = 0;
                    fpsStart = now;
                }
                if (animate.isSelected()) {
                    time += dt;
                }
                if (cycle.isSelected() && time - lastCycle > 5) {
                    lastCycle = time;
                    int next = (tabs.getSelectionModel().getSelectedIndex() + 1) % tabs.getTabs().size();
                    tabs.getSelectionModel().select(next);
                }
                Tab selected = tabs.getSelectionModel().getSelectedItem();
                for (DoubleConsumer c : ticks.getOrDefault(selected, List.of())) {
                    c.accept(time);
                }
            }
        }.start();
    }

    private static String pipelineName() {
        String name;
        try {
            // Without exporting com.sun.prism, which a manual test of jfx-tester does not get
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            name = pipeline.getClass().getSimpleName();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return System.getProperty("prism.order", "default");
        }
        return switch (name) {
            case "VKPipeline" -> "Vulkan";
            case "ES2Pipeline" -> "OpenGL ES";
            case "SWPipeline" -> "Software";
            default -> name;
        };
    }

    private void shapes() {
        Rectangle rect = new Rectangle(150, 100, Color.web("#38bdf8", 0.35));
        rect.setArcWidth(36);
        rect.setArcHeight(36);
        rect.setStroke(Color.web("#38bdf8"));
        rect.setStrokeWidth(4);
        rect.getStrokeDashArray().addAll(18.0, 8.0, 2.0, 8.0);
        rect.setStrokeLineCap(StrokeLineCap.ROUND);
        tick(t -> rect.setStrokeDashOffset(-t * 40));
        card("Rounded rectangle", "Animated dashes with round caps", rect);

        HBox types = new HBox(18);
        types.setAlignment(Pos.CENTER);
        for (StrokeType type : StrokeType.values()) {
            Circle c = new Circle(30, Color.web("#a78bfa", 0.5));
            c.setStroke(Color.web("#f472b6", 0.8));
            c.setStrokeWidth(12);
            c.setStrokeType(type);
            Circle guide = new Circle(30, Color.TRANSPARENT);
            guide.setStroke(Color.WHITE);
            types.getChildren().add(new StackPane(c, guide));
        }
        card("StrokeType", "INSIDE · CENTERED · OUTSIDE, a white line marks the outline", types);

        Group arcs = new Group();
        ArcType[] arcTypes = ArcType.values();
        for (int i = 0; i < arcTypes.length; i++) {
            Arc arc = new Arc(45 + i * 80, 70, 34, 34, 30, 250);
            arc.setType(arcTypes[i]);
            arc.setFill(Color.hsb(40 + i * 60, 0.7, 0.9, 0.6));
            arc.setStroke(Color.WHITE);
            arc.setStrokeWidth(2);
            int k = i;
            tick(t -> arc.setLength(200 + 100 * Math.sin(t * 1.5 + k)));
            arcs.getChildren().add(arc);
        }
        Ellipse ellipse = new Ellipse(120, 145, 100, 18);
        ellipse.setFill(Color.web("#22d3ee", 0.25));
        ellipse.setStroke(Color.web("#22d3ee"));
        arcs.getChildren().add(ellipse);
        card("Arcs and ellipse", "OPEN · CHORD · ROUND with animated length", arcs);

        Polygon star = new Polygon();
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 4 * Math.PI / 5;
            star.getPoints().addAll(Math.cos(a) * 70, Math.sin(a) * 70);
        }
        star.setFill(Color.web("#fbbf24", 0.75));
        star.setStroke(Color.web("#fde68a"));
        star.setStrokeWidth(2);
        Path evenOdd = new Path(new MoveTo(0, 0), new LineTo(120, 0), new LineTo(120, 120), new LineTo(0, 120), new ClosePath(),
                new MoveTo(30, 30), new LineTo(90, 30), new LineTo(90, 90), new LineTo(30, 90), new ClosePath());
        evenOdd.setFillRule(FillRule.EVEN_ODD);
        evenOdd.setFill(Color.web("#34d399", 0.7));
        evenOdd.setScaleX(0.6);
        evenOdd.setScaleY(0.6);
        HBox fillRules = new HBox(10, star, evenOdd);
        fillRules.setAlignment(Pos.CENTER);
        tick(t -> star.setRotate(t * 30));
        card("Fill rules", "Star polygon (NON_ZERO) and EVEN_ODD path", fillRules);

        Group joins = new Group();
        StrokeLineJoin[] js = StrokeLineJoin.values();
        StrokeLineCap[] caps = StrokeLineCap.values();
        for (int i = 0; i < 3; i++) {
            Polyline p = new Polyline(10 + i * 80, 140, 40 + i * 80, 40, 70 + i * 80, 140);
            p.setStroke(Color.hsb(190 + i * 50, 0.7, 1));
            p.setStrokeWidth(16);
            p.setStrokeLineJoin(js[i]);
            p.setStrokeLineCap(caps[i]);
            p.setFill(null);
            joins.getChildren().add(p);
        }
        card("Joins and caps", "MITER/SQUARE · BEVEL/BUTT · ROUND/ROUND", joins);

        Path path = new Path(new MoveTo(10, 120), new CubicCurveTo(40, 0, 120, 200, 160, 60),
                new QuadCurveTo(190, 10, 230, 80), new ArcTo(40, 30, 0, 150, 160, true, false), new ClosePath());
        path.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f472b6", 0.6)), new Stop(1, Color.web("#38bdf8", 0.6))));
        path.setStroke(Color.WHITE);
        path.setStrokeWidth(1.5);
        card("Path", "Cubic, quadratic and ArcTo", new Group(path));

        SVGPath heart = new SVGPath();
        heart.setContent("M 60 108 C 20 80 0 55 0 32 C 0 12 16 0 32 0 C 44 0 54 6 60 16 C 66 6 76 0 88 0 "
                + "C 104 0 120 12 120 32 C 120 55 100 80 60 108 Z");
        heart.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fecdd3")), new Stop(1, Color.web("#e11d48"))));
        tick(t -> {
            double s = 1 + 0.08 * Math.pow(Math.max(0, Math.sin(t * 6)), 8);
            heart.setScaleX(s);
            heart.setScaleY(s);
        });
        card("SVGPath", "Heart with radial gradient and scale", heart);

        QuadCurve quad = new QuadCurve(10, 140, 120, -20, 230, 140);
        quad.setFill(null);
        quad.setStroke(Color.web("#a3e635"));
        quad.setStrokeWidth(3);
        CubicCurve cubic = new CubicCurve(10, 80, 80, 0, 160, 170, 230, 80);
        cubic.setFill(Color.web("#a3e635", 0.15));
        cubic.setStroke(Color.web("#facc15"));
        cubic.setStrokeWidth(3);
        tick(t -> {
            cubic.setControlY1(80 - 80 * Math.sin(t));
            cubic.setControlY2(80 + 80 * Math.sin(t));
        });
        card("Curves", "QuadCurve and CubicCurve with animated controls", new Group(quad, cubic));

        Shape a = new Circle(55, 80, 50);
        Shape b = new Rectangle(55, 40, 100, 80);
        Shape union = Shape.union(a, b);
        Shape sub = Shape.subtract(new Circle(55, 80, 50), new Rectangle(55, 40, 100, 80));
        Shape inter = Shape.intersect(new Circle(55, 80, 50), new Rectangle(55, 40, 100, 80));
        union.setFill(Color.web("#38bdf8", 0.6));
        sub.setFill(Color.web("#f472b6", 0.8));
        inter.setFill(Color.web("#fbbf24", 0.8));
        union.setScaleX(0.6);
        union.setScaleY(0.6);
        sub.setScaleX(0.6);
        sub.setScaleY(0.6);
        inter.setScaleX(0.6);
        inter.setScaleY(0.6);
        HBox ops = new HBox(-30, new Group(union), new Group(sub), new Group(inter));
        ops.setAlignment(Pos.CENTER);
        card("Shape operations", "union · subtract · intersect", ops);

        Group hair = new Group();
        double[] widths = {0.25, 0.5, 1, 1.5, 2, 3};
        for (int i = 0; i < widths.length; i++) {
            Line l = new Line(10, 15 + i * 26, 230, 25 + i * 26);
            l.setStroke(Color.WHITE);
            l.setStrokeWidth(widths[i]);
            Line raw = new Line(10, 22 + i * 26, 230, 32 + i * 26);
            raw.setStroke(Color.web("#f472b6"));
            raw.setStrokeWidth(widths[i]);
            raw.setSmooth(false);
            hair.getChildren().addAll(l, raw);
        }
        card("Hairlines", "0.25 to 3 px, white with antialiasing, pink without", hair);
    }

    private void paints() {
        CycleMethod[] cycles = CycleMethod.values();
        VBox linear = new VBox(8);
        for (CycleMethod c : cycles) {
            Rectangle r = new Rectangle(230, 46);
            r.setArcWidth(12);
            r.setArcHeight(12);
            r.setFill(new LinearGradient(0.35, 0, 0.65, 0, true, c,
                    new Stop(0, Color.web("#f472b6")), new Stop(0.5, Color.web("#fde68a")), new Stop(1, Color.web("#38bdf8"))));
            linear.getChildren().add(r);
        }
        linear.setAlignment(Pos.CENTER);
        card("LinearGradient", "NO_CYCLE · REFLECT · REPEAT", linear);

        Rectangle radial = new Rectangle(230, 160);
        tick(t -> radial.setFill(new RadialGradient(Math.toDegrees(t) % 360, 0.6, 0.5, 0.5, 0.25, true, CycleMethod.REFLECT,
                new Stop(0, Color.web("#0ea5e9")), new Stop(0.5, Color.web("#f0abfc")), new Stop(1, Color.web("#1e1b4b")))));
        card("RadialGradient", "Animated off-center focus with REFLECT", radial);

        Rectangle pattern = new Rectangle(230, 160);
        tick(t -> pattern.setFill(new ImagePattern(texture, t * 20 % 64, t * 10 % 64, 64, 64, false)));
        card("ImagePattern", "Scrolling 64 px tile", pattern);

        Group alpha = new Group();
        Color[] colors = {Color.web("#ef4444"), Color.web("#22c55e"), Color.web("#3b82f6")};
        for (int i = 0; i < 3; i++) {
            Circle c = new Circle(60, colors[i].deriveColor(0, 1, 1, 0.55));
            int k = i;
            tick(t -> {
                c.setCenterX(120 + 40 * Math.cos(t + k * 2.09));
                c.setCenterY(85 + 40 * Math.sin(t + k * 2.09));
            });
            alpha.getChildren().add(c);
        }
        card("Transparency", "Three overlapping colors at 55%", alpha);

        HBox swatches = new HBox();
        for (int i = 0; i < 24; i++) {
            Rectangle r = new Rectangle(10, 160);
            int k = i;
            tick(t -> r.setFill(Color.hsb((k * 15 + t * 60) % 360, 0.85, 0.95)));
            swatches.getChildren().add(r);
        }
        card("Solid colors", "Rotating HSB bands", swatches);

        Text grad = new Text("GRADIENT");
        grad.setFont(Font.font("System", FontWeight.BLACK, 36));
        grad.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#facc15")), new Stop(1, Color.web("#ec4899"))));
        grad.setStroke(new LinearGradient(0, 0, 1, 0, true, CycleMethod.REPEAT,
                new Stop(0, Color.WHITE), new Stop(0.1, Color.web("#38bdf8"))));
        grad.setStrokeWidth(1.5);
        card("Text paint", "Gradient fill and stroke", grad);
    }

    private void text() {
        VBox sizes = new VBox(0);
        for (int s : new int[] {8, 10, 12, 16, 22, 30, 40}) {
            Text t = new Text(s + "px Café naïve Ångström");
            t.setFont(Font.font(s));
            t.setFill(Color.WHITE);
            sizes.getChildren().add(t);
        }
        card("Sizes", "8 to 40 px with accents", sizes);

        VBox smoothing = new VBox(6);
        for (FontSmoothingType type : FontSmoothingType.values()) {
            Text t = new Text(type + ": The quick brown fox jumps over the lazy dog");
            t.setFontSmoothingType(type);
            t.setFont(Font.font(12));
            t.setFill(Color.WHITE);
            t.setWrappingWidth(230);
            smoothing.getChildren().add(t);
            Text dark = new Text(type + ": dark text on a light background");
            dark.setFontSmoothingType(type);
            dark.setFill(Color.BLACK);
            StackPane light = new StackPane(dark);
            light.setBackground(new Background(new BackgroundFill(Color.web("#f1f5f9"), null, null)));
            smoothing.getChildren().add(light);
        }
        card("Smoothing", "GRAY and LCD on dark and light backgrounds", smoothing);

        VBox styles = new VBox(4);
        Text bold = new Text("Bold");
        bold.setFont(Font.font("System", FontWeight.BOLD, 20));
        Text italic = new Text("Italic");
        italic.setFont(Font.font("System", FontPosture.ITALIC, 20));
        Text under = new Text("Underline");
        under.setUnderline(true);
        under.setFont(Font.font(20));
        Text strike = new Text("Strikethrough");
        strike.setStrikethrough(true);
        strike.setFont(Font.font(20));
        Text mono = new Text("Monospace 0O1lI");
        mono.setFont(Font.font("Monospace", 18));
        for (Text t : List.of(bold, italic, under, strike, mono)) {
            t.setFill(Color.WHITE);
        }
        styles.getChildren().addAll(bold, italic, under, strike, mono);
        card("Styles", "Weight, posture, decorations and monospace", styles);

        Text spin = new Text("Rotation ↻");
        spin.setFont(Font.font("System", FontWeight.BOLD, 28));
        spin.setFill(Color.web("#fde68a"));
        tick(t -> {
            spin.setRotate(t * 45);
            double s = 0.6 + 0.5 * (1 + Math.sin(t * 1.3)) / 2;
            spin.setScaleX(s);
            spin.setScaleY(s);
        });
        card("Transforms", "Text rotating and scaling", spin);

        TextFlow flow = new TextFlow();
        Text t1 = new Text("TextFlow ");
        t1.setFill(Color.web("#38bdf8"));
        t1.setFont(Font.font("System", FontWeight.BOLD, 16));
        Text t2 = new Text("mixes styles, emoji 🎨🚀✨, ");
        t2.setFill(Color.WHITE);
        Text t3 = new Text("עברית ");
        t3.setFill(Color.web("#f472b6"));
        Text t4 = new Text("العربية ");
        t4.setFill(Color.web("#a3e635"));
        Text t5 = new Text("日本語 中文 한국어 ");
        t5.setFill(Color.web("#fbbf24"));
        Text t6 = new Text("and wraps lines automatically within the width of the card.");
        t6.setFill(Color.web("#cbd5e1"));
        flow.getChildren().addAll(t1, t2, t3, t4, t5, t6);
        flow.setPrefWidth(230);
        flow.setTextAlignment(TextAlignment.JUSTIFY);
        card("TextFlow", "Color emoji, bidi, CJK and justified", flow);

        Text many = new Text();
        many.setFill(Color.web("#e2e8f0"));
        many.setFont(Font.font("Monospace", 9));
        StringBuilder sb = new StringBuilder();
        tick(t -> {
            sb.setLength(0);
            for (int i = 0; i < 14; i++) {
                for (int j = 0; j < 38; j++) {
                    sb.append((char) ('!' + (int) ((t * 7 + i * 13 + j * 7) % 90)));
                }
                sb.append('\n');
            }
            many.setText(sb.toString());
        });
        card("Dynamic glyphs", "Text changed every frame (glyph cache)", many);
    }

    private void images() {
        ImageView nearest = new ImageView(checker(8, Color.WHITE, Color.web("#0ea5e9")));
        nearest.setSmooth(false);
        nearest.setFitWidth(110);
        nearest.setFitHeight(110);
        ImageView smooth = new ImageView(checker(8, Color.WHITE, Color.web("#0ea5e9")));
        smooth.setSmooth(true);
        smooth.setFitWidth(110);
        smooth.setFitHeight(110);
        HBox filters = new HBox(10, nearest, smooth);
        filters.setAlignment(Pos.CENTER);
        card("Filtering", "A 16×16 image scaled up: nearest neighbor and linear", filters);

        ImageView viewport = new ImageView(photo);
        viewport.setFitWidth(230);
        viewport.setFitHeight(160);
        tick(t -> viewport.setViewport(new Rectangle2D(64 + 60 * Math.cos(t * 0.7), 64 + 60 * Math.sin(t * 0.9), 128, 89)));
        card("Viewport", "Animated viewport of a 256×256 image", viewport);

        ImageView rotated = new ImageView(photo);
        rotated.setFitWidth(120);
        rotated.setPreserveRatio(true);
        rotated.setRotationAxis(new javafx.geometry.Point3D(1, 1, 0));
        tick(t -> rotated.setRotate(t * 40));
        card("Transformed image", "Rotation around a diagonal axis", rotated);

        int w = 230;
        int h = 160;
        WritableImage plasma = new WritableImage(w, h);
        int[] pixels = new int[w * h];
        tick(t -> {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double v = Math.sin(x * 0.05 + t) + Math.sin(y * 0.07 - t * 1.3) + Math.sin((x + y) * 0.04 + t * 0.7);
                    int r = (int) (128 + 127 * Math.sin(v * Math.PI));
                    int g = (int) (128 + 127 * Math.sin(v * Math.PI + 2));
                    int b = (int) (128 + 127 * Math.sin(v * Math.PI + 4));
                    pixels[y * w + x] = 0xff000000 | r << 16 | g << 8 | b;
                }
            }
            plasma.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        });
        card("WritableImage", "Texture upload every frame with PixelWriter", new ImageView(plasma));

        IntBuffer buffer = IntBuffer.allocate(w * h);
        PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(w, h, buffer, PixelFormat.getIntArgbPreInstance());
        WritableImage bufferImage = new WritableImage(pixelBuffer);
        tick(t -> pixelBuffer.updateBuffer(pb -> {
            int[] data = buffer.array();
            int cx = (int) (w / 2 + 70 * Math.cos(t));
            int cy = (int) (h / 2 + 45 * Math.sin(t * 1.4));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                    int a = Math.max(0, 255 - d / 12);
                    int c = (a * 0xf4 / 255) << 16 | (a * 0x72 / 255) << 8 | (a * 0xb6 / 255);
                    data[y * w + x] = a << 24 | c;
                }
            }
            return null;
        }));
        card("PixelBuffer", "Premultiplied NIO buffer with alpha", new ImageView(bufferImage));

        Group source = new Group();
        for (int i = 0; i < 6; i++) {
            Rectangle r = new Rectangle(40, 40, Color.hsb(i * 60, 0.8, 1));
            r.setTranslateX(Math.cos(i) * 40);
            r.setTranslateY(Math.sin(i) * 40);
            source.getChildren().add(r);
        }
        WritableImage snap = new WritableImage(120, 120);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setViewport(new Rectangle2D(-60, -60, 120, 120));
        ImageView snapView = new ImageView(snap);
        snapView.setFitWidth(160);
        snapView.setFitHeight(160);
        tick(t -> {
            source.setRotate(t * 50);
            source.snapshot(params, snap);
        });
        card("Snapshot", "Render to texture every frame", snapView);

        WritableImage big = new WritableImage(4096, 4096);
        PixelWriter pw = big.getPixelWriter();
        int[] row = new int[4096];
        for (int y = 0; y < 4096; y++) {
            for (int x = 0; x < 4096; x++) {
                row[x] = ((x / 256 + y / 256) % 2 == 0 ? 0xff1e293b : 0xff334155) | ((x ^ y) & 0xff);
            }
            pw.setPixels(0, y, 4096, 1, PixelFormat.getIntArgbInstance(), row, 0, 4096);
        }
        ImageView bigView = new ImageView(big);
        bigView.setFitWidth(230);
        bigView.setFitHeight(160);
        tick(t -> {
            double z = 256 + 3800 * (1 + Math.sin(t * 0.4)) / 2;
            bigView.setViewport(new Rectangle2D((4096 - z) / 2, (4096 - z) / 2, z, z * 160 / 230));
        });
        card("Large texture", "4096×4096 with continuous zoom", bigView);
    }

    private Node sample() {
        Rectangle back = new Rectangle(150, 90, Color.web("#1e293b"));
        back.setArcWidth(18);
        back.setArcHeight(18);
        Circle c = new Circle(28, Color.web("#f472b6"));
        c.setTranslateX(-35);
        Text t = new Text("FX");
        t.setFont(Font.font("System", FontWeight.BLACK, 40));
        t.setFill(Color.web("#38bdf8"));
        t.setTranslateX(30);
        return new StackPane(back, c, t);
    }

    private void effect(String title, Effect effect, DoubleConsumer animation) {
        Node node = sample();
        node.setEffect(effect);
        if (animation != null) {
            tick(animation);
        }
        card(title, effect.getClass().getSimpleName(), node);
    }

    private void effects() {
        DropShadow drop = new DropShadow(20, Color.web("#38bdf8"));
        effect("Drop shadow", drop, t -> {
            drop.setOffsetX(10 * Math.cos(t));
            drop.setOffsetY(10 * Math.sin(t));
        });
        InnerShadow inner = new InnerShadow(16, Color.BLACK);
        effect("Inner shadow", inner, t -> inner.setRadius(4 + 16 * (1 + Math.sin(t * 2)) / 2));
        GaussianBlur gauss = new GaussianBlur();
        effect("Gaussian blur", gauss, t -> gauss.setRadius(30 * (1 + Math.sin(t)) / 2));
        BoxBlur box = new BoxBlur(10, 10, 3);
        effect("Box blur", box, t -> box.setWidth(1 + 20 * (1 + Math.sin(t * 1.3)) / 2));
        MotionBlur motion = new MotionBlur(0, 25);
        effect("Motion blur", motion, t -> motion.setAngle(t * 60 % 360));
        Bloom bloom = new Bloom(0.3);
        effect("Bloom", bloom, t -> bloom.setThreshold(0.5 + 0.5 * Math.sin(t)));
        Glow glow = new Glow();
        effect("Glow", glow, t -> glow.setLevel((1 + Math.sin(t * 2)) / 2));
        effect("Reflection", new Reflection(4, 0.7, 0.6, 0), null);
        ColorAdjust adjust = new ColorAdjust();
        effect("Color adjust", adjust, t -> adjust.setHue(Math.sin(t * 0.8)));
        effect("Sepia", new SepiaTone(0.9), null);
        effect("Shadow", new Shadow(12, Color.web("#a78bfa")), null);

        Lighting distant = new Lighting(new Light.Distant(45, 45, Color.WHITE));
        distant.setSurfaceScale(4);
        effect("Distant light", distant, t -> ((Light.Distant) distant.getLight()).setAzimuth(t * 60 % 360));
        Light.Point point = new Light.Point(0, 0, 60, Color.web("#fde68a"));
        effect("Point light", new Lighting(point), t -> {
            point.setX(75 + 70 * Math.cos(t));
            point.setY(45 + 40 * Math.sin(t));
        });
        Light.Spot spot = new Light.Spot(75, 45, 120, 2, Color.web("#f472b6"));
        effect("Spot light", new Lighting(spot), t -> {
            spot.setPointsAtX(75 + 60 * Math.cos(t * 1.4));
            spot.setPointsAtY(45 + 40 * Math.sin(t * 1.4));
        });

        int mw = 160;
        int mh = 100;
        FloatMap map = new FloatMap(mw, mh);
        DisplacementMap displacement = new DisplacementMap(map);
        effect("Displacement", displacement, t -> {
            for (int x = 0; x < mw; x++) {
                float v = (float) (Math.sin(x / 8.0 + t * 3) * 0.04);
                for (int y = 0; y < mh; y++) {
                    map.setSamples(x, y, 0, v);
                }
            }
        });
        PerspectiveTransform perspective = new PerspectiveTransform();
        effect("Perspective", perspective, t -> {
            double d = 30 * Math.sin(t);
            perspective.setUlx(0);
            perspective.setUly(d);
            perspective.setUrx(150);
            perspective.setUry(-d);
            perspective.setLrx(150);
            perspective.setLry(90 + d);
            perspective.setLlx(0);
            perspective.setLly(90 - d);
        });
        ColorInput tint = new ColorInput(0, 0, 150, 90, Color.web("#22d3ee"));
        Blend blend = new Blend(BlendMode.MULTIPLY);
        blend.setTopInput(tint);
        effect("Blend with ColorInput", blend, t -> blend.setOpacity((1 + Math.sin(t)) / 2));
        Blend image = new Blend(BlendMode.SCREEN, null, new ImageInput(texture, 0, 0));
        effect("ImageInput", image, null);
        DropShadow chained = new DropShadow(18, Color.web("#f472b6"));
        GaussianBlur chainBlur = new GaussianBlur(3);
        Glow chainGlow = new Glow(0.8);
        chainGlow.setInput(chainBlur);
        chained.setInput(chainGlow);
        effect("Effect chain", chained, null);
    }

    private void blending() {
        FlowPane modes = new FlowPane(4, 4);
        modes.setPrefWrapLength(240);
        for (BlendMode mode : BlendMode.values()) {
            Rectangle base = new Rectangle(52, 34, new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#f472b6")), new Stop(1, Color.web("#38bdf8"))));
            Circle top = new Circle(14, Color.web("#fde68a", 0.9));
            top.setBlendMode(mode);
            StackPane cell = new StackPane(base, top);
            Label l = new Label(mode.name().toLowerCase().replace('_', ' '));
            l.setStyle("-fx-font-size: 7px;");
            modes.getChildren().add(new VBox(l, cell));
        }
        wide("Blend modes", "Every BlendMode over a gradient", modes, 250, 250);

        Group clipped = new Group();
        for (int i = 0; i < 12; i++) {
            Rectangle stripe = new Rectangle(i * 20, 0, 20, 176);
            stripe.setFill(Color.hsb(i * 30, 0.7, 0.95));
            clipped.getChildren().add(stripe);
        }
        Circle clip = new Circle(50);
        clipped.setClip(clip);
        tick(t -> {
            clip.setCenterX(125 + 80 * Math.cos(t));
            clip.setCenterY(88 + 40 * Math.sin(t * 2));
            clip.setRadius(40 + 20 * Math.sin(t * 3));
        });
        card("Clip", "Animated circular clip", clipped);

        Text clipText = new Text("CLIP");
        clipText.setFont(Font.font("System", FontWeight.BLACK, 80));
        ImageView behind = new ImageView(photo);
        behind.setFitWidth(250);
        behind.setFitHeight(176);
        behind.setClip(clipText);
        clipText.setX(20);
        clipText.setY(120);
        tick(t -> clipText.setX(20 + 20 * Math.sin(t)));
        card("Text clip", "An image clipped by glyphs", behind);

        Group groupAlpha = new Group(new Circle(60, 80, 50, Color.web("#ef4444")), new Circle(110, 80, 50, Color.web("#3b82f6")));
        groupAlpha.setOpacity(0.5);
        Group nodeAlpha = new Group(new Circle(60, 80, 50, Color.web("#ef4444", 0.5)), new Circle(110, 80, 50, Color.web("#3b82f6", 0.5)));
        nodeAlpha.setScaleX(0.6);
        nodeAlpha.setScaleY(0.6);
        groupAlpha.setScaleX(0.6);
        groupAlpha.setScaleY(0.6);
        HBox opacity = new HBox(0, groupAlpha, nodeAlpha);
        opacity.setAlignment(Pos.CENTER);
        card("Group opacity", "Group at 50% (no overlap) and nodes at 50%", opacity);

        Group cached = new Group();
        for (int i = 0; i < 40; i++) {
            Circle c = new Circle(random.nextDouble() * 220, random.nextDouble() * 150, 6 + random.nextDouble() * 14,
                    Color.hsb(random.nextDouble() * 360, 0.7, 1, 0.7));
            c.setEffect(new DropShadow(6, Color.BLACK));
            cached.getChildren().add(c);
        }
        cached.setCache(true);
        cached.setCacheHint(CacheHint.SPEED);
        tick(t -> {
            cached.setRotate(t * 20);
            cached.setScaleX(0.8 + 0.2 * Math.sin(t));
            cached.setScaleY(0.8 + 0.2 * Math.sin(t));
        });
        card("Cache", "Group with cached effects and CacheHint.SPEED", cached);

        StackPane cube = new StackPane();
        Rectangle face = new Rectangle(120, 120);
        face.setFill(new ImagePattern(photo));
        face.setStroke(Color.WHITE);
        cube.getChildren().add(face);
        face.setRotationAxis(Rotate.Y_AXIS);
        tick(t -> face.setRotate(t * 50));
        SubScene perspective = new SubScene(cube, CARD_W, CARD_H, true, SceneAntialiasing.DISABLED);
        perspective.setFill(Color.TRANSPARENT);
        perspective.setCamera(new PerspectiveCamera());
        cube.setPrefSize(CARD_W, CARD_H);
        card("2D node in perspective", "Rotation in Y with a perspective camera", perspective);
    }

    private void canvas() {
        Canvas canvas = new Canvas(520, 340);
        GraphicsContext g = canvas.getGraphicsContext2D();
        tick(t -> {
            g.setGlobalBlendMode(BlendMode.SRC_OVER);
            g.setGlobalAlpha(1);
            g.setEffect(null);
            g.clearRect(0, 0, 520, 340);
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#0f172a")), new Stop(1, Color.web("#312e81"))));
            g.fillRoundRect(0, 0, 520, 340, 24, 24);

            g.save();
            g.translate(130, 120);
            g.rotate(Math.toDegrees(t * 0.5));
            for (int i = 0; i < 12; i++) {
                g.setFill(Color.hsb(i * 30, 0.8, 1, 0.6));
                g.fillArc(-80, -80, 160, 160, i * 30, 25, ArcType.ROUND);
            }
            g.restore();

            g.setStroke(Color.web("#fde68a"));
            g.setLineWidth(4);
            g.setLineDashes(14, 6);
            g.setLineDashOffset(-t * 30);
            g.beginPath();
            g.moveTo(260, 40);
            g.bezierCurveTo(320, -20 + 60 * Math.sin(t), 420, 140, 500, 40);
            g.quadraticCurveTo(470, 160, 300, 150);
            g.arcTo(250, 160, 270, 60, 30);
            g.closePath();
            g.stroke();
            g.setLineDashes();

            g.setEffect(new DropShadow(14, Color.web("#38bdf8")));
            g.setFill(Color.web("#38bdf8"));
            g.setFont(Font.font("System", FontWeight.BOLD, 30));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText("Canvas", 390, 210);
            g.setEffect(null);

            g.drawImage(photo, 20, 220, 110, 110);
            g.drawImage(photo, 64, 64, 128, 128, 140, 220, 110, 110);

            g.save();
            g.beginPath();
            g.arc(330, 285, 45, 45, 0, 360);
            g.closePath();
            g.clip();
            g.setFill(new ImagePattern(texture, t * 30, 0, 32, 32, false));
            g.fillRect(280, 235, 100, 100);
            g.restore();

            g.setGlobalBlendMode(BlendMode.ADD);
            g.setGlobalAlpha(0.7);
            for (int i = 0; i < 3; i++) {
                g.setFill(Color.hsb(i * 120, 1, 1));
                g.fillOval(400 + 30 * Math.cos(t + i * 2.09), 250 + 20 * Math.sin(t + i * 2.09), 70, 70);
            }
            g.setGlobalBlendMode(BlendMode.SRC_OVER);
            g.setGlobalAlpha(1);

            PixelWriter writer = g.getPixelWriter();
            for (int i = 0; i < 400; i++) {
                int x = 20 + random.nextInt(90);
                int y = 20 + random.nextInt(50);
                writer.setArgb(x, y, 0xff000000 | random.nextInt(0xffffff));
            }
        });
        wide("GraphicsContext", "Transforms, arcs, dashes, curves, shadow, text, images, clip, ADD and PixelWriter",
                canvas, 520, 340);

        Canvas effectCanvas = new Canvas(250, 176);
        GraphicsContext e = effectCanvas.getGraphicsContext2D();
        tick(t -> {
            e.clearRect(0, 0, 250, 176);
            for (int i = 0; i < 20; i++) {
                e.setFill(Color.hsb((i * 18 + t * 40) % 360, 0.8, 1));
                e.fillOval(10 + i * 11, 70 + 50 * Math.sin(t * 2 + i * 0.4), 24, 24);
            }
            e.applyEffect(new BoxBlur(3 + 3 * Math.sin(t), 3, 2));
        });
        card("applyEffect", "Effect applied to the content of the canvas", effectCanvas);
    }

    private void scene3d() {
        PhongMaterial boxMaterial = new PhongMaterial(Color.WHITE);
        boxMaterial.setDiffuseMap(photo);
        boxMaterial.setSpecularColor(Color.WHITE);
        boxMaterial.setSpecularPower(32);
        Box box = new Box(140, 140, 140);
        box.setMaterial(boxMaterial);
        box.setTranslateX(-220);

        PhongMaterial sphereMaterial = new PhongMaterial(Color.web("#38bdf8"));
        sphereMaterial.setSpecularColor(Color.WHITE);
        sphereMaterial.setSpecularPower(64);
        sphereMaterial.setBumpMap(bumps(256));
        Sphere sphere = new Sphere(80, 96);
        sphere.setMaterial(sphereMaterial);

        PhongMaterial glowMaterial = new PhongMaterial(Color.web("#1e1b4b"));
        glowMaterial.setSelfIlluminationMap(checker(16, Color.web("#f472b6"), Color.BLACK));
        Cylinder cylinder = new Cylinder(60, 160);
        cylinder.setMaterial(glowMaterial);
        cylinder.setTranslateX(220);

        MeshView torus = new MeshView(torus(70, 24, 64, 24));
        PhongMaterial torusMaterial = new PhongMaterial(Color.WHITE);
        torusMaterial.setDiffuseMap(texture);
        torusMaterial.setSpecularColor(Color.web("#fde68a"));
        torus.setMaterial(torusMaterial);
        torus.setTranslateY(170);
        torus.setTranslateX(-110);

        Sphere wire = new Sphere(60, 24);
        wire.setDrawMode(DrawMode.LINE);
        wire.setMaterial(new PhongMaterial(Color.web("#a3e635")));
        wire.setTranslateY(170);
        wire.setTranslateX(110);

        Box plane = new Box(700, 2, 500);
        plane.setTranslateY(260);
        plane.setCullFace(CullFace.NONE);
        PhongMaterial planeMaterial = new PhongMaterial(Color.web("#334155"));
        planeMaterial.setDiffuseMap(checker(32, Color.web("#475569"), Color.web("#1e293b")));
        plane.setMaterial(planeMaterial);

        Group objects = new Group(box, sphere, cylinder, torus, wire, plane);
        AmbientLight ambient = new AmbientLight(Color.color(0.18, 0.18, 0.22));
        PointLight point = new PointLight(Color.web("#fde68a"));
        point.setConstantAttenuation(0.6);
        point.setLinearAttenuation(0.0008);
        Sphere bulb = new Sphere(8);
        PhongMaterial bulbMaterial = new PhongMaterial(Color.BLACK);
        bulbMaterial.setSelfIlluminationMap(solid(Color.web("#fde68a")));
        bulb.setMaterial(bulbMaterial);
        SpotLight spot = new SpotLight(Color.web("#f472b6"));
        spot.setTranslateY(-400);
        spot.setDirection(new javafx.geometry.Point3D(0, 1, 0));
        spot.setInnerAngle(10);
        spot.setOuterAngle(35);
        DirectionalLight directional = new DirectionalLight(Color.color(0.25, 0.3, 0.45));
        directional.setDirection(new javafx.geometry.Point3D(-1, 1, 1));
        Group world = new Group(objects, ambient, point, bulb, spot, directional);
        tick(t -> {
            box.setRotationAxis(new javafx.geometry.Point3D(1, 1, 0));
            box.setRotate(t * 40);
            sphere.setRotationAxis(Rotate.Y_AXIS);
            sphere.setRotate(t * 30);
            cylinder.setRotationAxis(Rotate.Z_AXIS);
            cylinder.setRotate(t * 25);
            torus.setRotationAxis(new javafx.geometry.Point3D(1, 0, 0.5));
            torus.setRotate(t * 60);
            wire.setRotationAxis(Rotate.Y_AXIS);
            wire.setRotate(-t * 40);
            point.setTranslateX(300 * Math.cos(t * 0.8));
            point.setTranslateZ(-200 + 250 * Math.sin(t * 0.8));
            point.setTranslateY(-120);
            bulb.setTranslateX(point.getTranslateX());
            bulb.setTranslateY(point.getTranslateY());
            bulb.setTranslateZ(point.getTranslateZ());
            spot.setTranslateX(250 * Math.sin(t * 0.6));
        });

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(1);
        camera.setFarClip(5000);
        camera.setFieldOfView(40);
        camera.setTranslateZ(-1100);
        camera.setTranslateY(-120);
        camera.setRotationAxis(Rotate.X_AXIS);
        camera.setRotate(-8);
        SubScene sub = new SubScene(world, 760, 470, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#020617"));
        sub.setCamera(camera);

        HBox lights = new HBox(10);
        lights.setAlignment(Pos.CENTER_LEFT);
        for (LightBase light : List.of(ambient, point, spot, directional)) {
            ToggleButton b = new ToggleButton(light.getClass().getSimpleName());
            b.setSelected(true);
            light.lightOnProperty().bind(b.selectedProperty());
            lights.getChildren().add(b);
        }
        CheckBox wireframe = new CheckBox("Wireframe");
        wireframe.selectedProperty().addListener((o, a, b) -> {
            DrawMode mode = b ? DrawMode.LINE : DrawMode.FILL;
            box.setDrawMode(mode);
            sphere.setDrawMode(mode);
            cylinder.setDrawMode(mode);
            torus.setDrawMode(mode);
        });
        lights.getChildren().add(wireframe);
        VBox view = new VBox(8, sub, lights);
        wide("3D scene", "Textured box, bump-mapped sphere, self-illuminated cylinder, TriangleMesh torus, "
                + "wireframe, MSAA and the four lights", view, 760, 510);

        Group ortho = new Group();
        for (int i = 0; i < 6; i++) {
            Box b = new Box(30, 30, 30);
            b.setMaterial(new PhongMaterial(Color.hsb(i * 60, 0.7, 1)));
            b.setTranslateX(30 + (i % 3) * 80);
            b.setTranslateY(40 + (i / 3) * 80);
            int k = i;
            tick(t -> {
                b.setRotationAxis(new javafx.geometry.Point3D(1, 1, 1));
                b.setRotate(t * 50 + k * 30);
            });
            ortho.getChildren().add(b);
        }
        SubScene orthoScene = new SubScene(ortho, CARD_W, CARD_H, true, SceneAntialiasing.DISABLED);
        orthoScene.setCamera(new ParallelCamera());
        orthoScene.setFill(Color.web("#0f172a"));
        card("Parallel camera", "Orthographic projection with a depth buffer", orthoScene);
    }

    private void controls() {
        Region a = new Region();
        a.getStyleClass().add("css-a");
        a.setPrefSize(220, 150);
        card("CSS backgrounds and borders", "Gradient, multiple insets, radii and segmented border", a);
        Region b = new Region();
        b.getStyleClass().add("css-b");
        b.setPrefSize(200, 140);
        card("Percentage radii", "Four-color borders and CSS dropshadow", b);
        Region c = new Region();
        c.getStyleClass().add("css-c");
        c.setPrefSize(220, 150);
        card("CSS image pattern", "repeating-image-pattern with a dotted border", c);

        ProgressIndicator spinner = new ProgressIndicator();
        ProgressIndicator determinate = new ProgressIndicator();
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(220);
        tick(t -> {
            determinate.setProgress((t * 0.2) % 1);
            bar.setProgress((t * 0.15) % 1);
        });
        HBox indicators = new HBox(16, spinner, determinate);
        indicators.setAlignment(Pos.CENTER);
        VBox progress = new VBox(14, indicators, bar);
        progress.setAlignment(Pos.CENTER);
        card("Progress", "Indeterminate and determinate indicators and a bar", progress);

        Slider slider = new Slider(0, 100, 40);
        TextField field = new TextField("Editable text");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("Vulkan", "OpenGL ES", "Software");
        combo.getSelectionModel().select(0);
        ColorPicker picker = new ColorPicker(Color.web("#f472b6"));
        ChoiceBox<String> choice = new ChoiceBox<>();
        choice.getItems().addAll("One", "Two", "Three");
        choice.getSelectionModel().select(1);
        Button button = new Button("Default button");
        button.setDefaultButton(true);
        VBox form = new VBox(6, slider, field, new HBox(6, combo, choice), new HBox(6, picker, button));
        form.setAlignment(Pos.CENTER_LEFT);
        card("Controls", "Popups open as child windows", form);
    }

    private void stress() {
        Pane arena = new Pane();
        arena.setPrefSize(980, 520);
        Slider count = new Slider(100, 20000, 2000);
        Label countLabel = new Label();
        ChoiceBox<String> kind = new ChoiceBox<>();
        kind.getItems().addAll("Gradient circles", "Images", "Text", "Shadowed rectangles");
        kind.getSelectionModel().select(0);
        List<double[]> state = new ArrayList<>();
        Runnable rebuild = () -> {
            arena.getChildren().clear();
            state.clear();
            int n = (int) count.getValue();
            countLabel.setText(n + " nodes");
            String mode = kind.getValue();
            for (int i = 0; i < n; i++) {
                Node node = switch (mode) {
                    case "Images" -> {
                        ImageView v = new ImageView(photo);
                        v.setFitWidth(18);
                        v.setFitHeight(18);
                        yield v;
                    }
                    case "Text" -> {
                        Text t = new Text("✦" + i);
                        t.setFill(Color.hsb(i % 360, 0.7, 1));
                        yield t;
                    }
                    case "Shadowed rectangles" -> {
                        Rectangle r = new Rectangle(14, 14, Color.hsb(i % 360, 0.7, 1));
                        r.setEffect(new DropShadow(4, Color.BLACK));
                        yield r;
                    }
                    default -> {
                        Circle c = new Circle(7, new RadialGradient(0, 0, 0.3, 0.3, 0.8, true, CycleMethod.NO_CYCLE,
                                new Stop(0, Color.WHITE), new Stop(1, Color.hsb(i % 360, 0.8, 0.9))));
                        yield c;
                    }
                };
                state.add(new double[] {random.nextDouble() * 960, random.nextDouble() * 500,
                        (random.nextDouble() - 0.5) * 160, (random.nextDouble() - 0.5) * 160});
                arena.getChildren().add(node);
            }
        };
        count.valueChangingProperty().addListener((o, was, is) -> {
            if (!is) {
                rebuild.run();
            }
        });
        count.valueProperty().addListener((o, a, b) -> {
            if (!count.isValueChanging()) {
                rebuild.run();
            }
        });
        kind.setOnAction(e -> rebuild.run());
        rebuild.run();
        double[] last = {0};
        tick(t -> {
            double dt = Math.min(0.05, t - last[0]);
            last[0] = t;
            List<Node> children = arena.getChildren();
            for (int i = 0; i < children.size(); i++) {
                double[] s = state.get(i);
                s[0] += s[2] * dt;
                s[1] += s[3] * dt;
                if (s[0] < 0 || s[0] > 960) {
                    s[2] = -s[2];
                }
                if (s[1] < 0 || s[1] > 500) {
                    s[3] = -s[3];
                }
                children.get(i).relocate(s[0], s[1]);
            }
        });
        HBox options = new HBox(12, new Label("Count"), count, countLabel, kind);
        options.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(8, options, arena);
        wide("Load", "Many animated nodes to measure frames per second", content, 980, 560);
    }

    private static TriangleMesh torus(float radius, float tube, int segments, int sides) {
        TriangleMesh mesh = new TriangleMesh();
        for (int i = 0; i <= segments; i++) {
            double u = 2 * Math.PI * i / segments;
            for (int j = 0; j <= sides; j++) {
                double v = 2 * Math.PI * j / sides;
                float x = (float) ((radius + tube * Math.cos(v)) * Math.cos(u));
                float y = (float) (tube * Math.sin(v));
                float z = (float) ((radius + tube * Math.cos(v)) * Math.sin(u));
                mesh.getPoints().addAll(x, y, z);
                mesh.getTexCoords().addAll((float) i / segments * 4, (float) j / sides);
            }
        }
        int stride = sides + 1;
        for (int i = 0; i < segments; i++) {
            for (int j = 0; j < sides; j++) {
                int p0 = i * stride + j;
                int p1 = (i + 1) * stride + j;
                int p2 = (i + 1) * stride + j + 1;
                int p3 = i * stride + j + 1;
                mesh.getFaces().addAll(p0, p0, p2, p2, p1, p1);
                mesh.getFaces().addAll(p0, p0, p3, p3, p2, p2);
            }
        }
        return mesh;
    }

    private static Image checker(int cell, Color a, Color b) {
        int size = cell * 2;
        WritableImage image = new WritableImage(size, size);
        PixelWriter w = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                w.setColor(x, y, (x / cell + y / cell) % 2 == 0 ? a : b);
            }
        }
        return image;
    }

    private static Image solid(Color color) {
        WritableImage image = new WritableImage(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.getPixelWriter().setColor(x, y, color);
            }
        }
        return image;
    }

    private static Image bumps(int size) {
        WritableImage image = new WritableImage(size, size);
        PixelWriter w = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double nx = Math.sin(x * 0.4) * 0.5;
                double ny = Math.sin(y * 0.4) * 0.5;
                w.setColor(x, y, Color.color(0.5 + nx * 0.5, 0.5 + ny * 0.5, 1));
            }
        }
        return image;
    }

    private static Image synthetic(int w, int h) {
        WritableImage image = new WritableImage(w, h);
        PixelWriter pw = image.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - w * 0.5;
                double dy = y - h * 0.55;
                double r = Math.hypot(dx, dy);
                Color sky = Color.web("#0f172a").interpolate(Color.web("#7c3aed"), y / (double) h);
                Color sun = r < 48 ? Color.web("#fde68a").interpolate(Color.web("#f472b6"), r / 48) : null;
                boolean grid = y > h * 0.6 && ((x - w / 2) * (y - h * 0.6) / 40 % 16 < 1.2 || y % 16 < 1);
                Color c = sun != null ? sun : grid ? Color.web("#22d3ee") : sky;
                pw.setColor(x, y, c);
            }
        }
        return image;
    }

    private static String dataUrl(Image image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bi.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(bi, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------------
    // Tests, after the rendering system tests of OpenJFX (tests/system)

    final TestKit.Suite suite = new TestKit.Suite("Render");
    private Stage testStage;
    // One bit of rounding writing a color out and one reading it back
    private static final double COMPONENT_TOLERANCE = 2.0001 / 255.0;
    private static final double[][] SRGB_COLORS = {
            {0.25, 0.5, 0.75}, {0.25, 0.75, 0.5}, {0.5, 0.25, 0.75},
            {0.5, 0.75, 0.25}, {0.75, 0.25, 0.5}, {0.75, 0.5, 0.25}};
    private static final Color PB_INIT = new Color(0.92, 0.56, 0.1, 1);
    private static final Color PB_TEST = new Color(0.2, 0.6, 0.8, 1);
    private static final int PB_SIZE = 24;
    private static final Color LCD_TRANSPARENT = Color.color(0.5, 0.5, 0.5, 0.6);

    private void defineTests() {
        suite.auto("sRGB pixels", "Six colors read back with getPixelColor, within 2/255 (SRGBTest)",
                () -> srgbTest(false));
        suite.auto("sRGB screen capture", "Six colors read back with getScreenCapture, within 2/255 (SRGBTest)",
                () -> srgbTest(true));
        suite.auto("PixelBuffer IntArgbPre, direct", "Four images of one direct IntBuffer redraw after "
                + "updateBuffer (PixelBufferDrawTest)", () -> pixelBufferTest(true, true));
        suite.auto("PixelBuffer IntArgbPre, heap", "Four images of one heap IntBuffer redraw after updateBuffer "
                + "(PixelBufferDrawTest)", () -> pixelBufferTest(true, false));
        suite.auto("PixelBuffer ByteBgraPre, direct", "Four images of one direct ByteBuffer redraw after "
                + "updateBuffer (PixelBufferDrawTest)", () -> pixelBufferTest(false, true));
        suite.auto("PixelBuffer ByteBgraPre, heap", "Four images of one heap ByteBuffer redraw after updateBuffer "
                + "(PixelBufferDrawTest)", () -> pixelBufferTest(false, false));
        suite.auto("PixelBuffer off the FX thread", "updateBuffer on another thread throws IllegalStateException "
                + "(PixelBufferDrawTest)", this::pixelBufferThreadTest);
        suite.auto("LCD text, opaque", "Opaque LCD text keeps its color and has color fringes (TransparentLCDTest)",
                () -> lcdTest(true));
        suite.auto("LCD text, transparent", "Translucent LCD text falls back to gray antialiasing "
                + "(TransparentLCDTest)", () -> lcdTest(false));
        suite.auto("Snapshot matches the screen", "A snapshot of shapes, gradients and text matches the frame on "
                + "the screen (Snapshot1Test)", this::snapshotTest);
        suite.auto("Every tab renders", "Each tab of the showcase draws something, without an exception on the FX "
                + "thread", this::tabsTest);
        defineRegressionTests();
    }

    // ---------------------------------------------------------------------
    // The rendering bugs of the JBS, mostly on Linux, so that they do not come back

    private void defineRegressionTests() {
        suite.auto("Arcs of every size", "JDK-8393157, JDK-8393053, JDK-8332950: 400 ArcTo of radius 1 to every "
                + "point up to 20, 20, an SVG arc and an arc without rotation have their shapes", this::arcsTest);
        suite.auto("Covered node keeps the scene updating", "JDK-8322619: changes a node that another one covers, "
                + "and then the visible ones, which must still be drawn", this::coveredNodeTest);
        suite.auto("Snapshot of a node in a SubScene", "JDK-8260013: the snapshot of a node in a SubScene with a "
                + "perspective camera shows the node", this::subSceneSnapshotTest);
        suite.auto("New scene renders", "JDK-8306121: a scene set on a showing stage is drawn, three times",
                this::newSceneTest);
        suite.auto("Zero-size SubScene", "JDK-8372761: the transforms of a 0 × 0 SubScene give finite points",
                this::zeroSubSceneTest);
        suite.auto("Color emoji", "JDK-8337816, JDK-8234473: an emoji is drawn in color, where a color emoji font "
                + "is installed", this::colorEmojiTest);
        suite.auto("Emoji in the title", "JDK-8382165: the window gets a title with an emoji and accents, as "
                + "_NET_WM_NAME in UTF-8 (X11)", this::emojiTitleTest);
        suite.auto("Centered text with trailing spaces", "JDK-8314215: the space where a centered text wraps does not "
                + "move its line", this::trailingSpaceTest);
        suite.auto("Hit test in a TextFlow", "JDK-8306083: hitTest on the second Text of a TextFlow gives the "
                + "index of its own character", this::hitTestTest);
        suite.auto("Huge glyphs", "JDK-8237833: text of 500 px, larger than the glyph cache, is drawn",
                this::hugeGlyphsTest);
        suite.auto("Context menu renders", "JDK-8242362: a context menu shown 15 times is drawn each time, as its "
                + "snapshot", this::contextMenuTest);
        suite.auto("Transparent stage", "JDK-8171330, JDK-8253853: a TRANSPARENT stage shows what is behind it, "
                + "where its scene has no content", this::transparentStageTest);
    }

    private String arcsTest() {
        return TestKit.fx(() -> {
            List<String> empty = new ArrayList<>();
            for (int x = 1; x <= 20; x++) {
                for (int y = 1; y <= 20; y++) {
                    javafx.scene.shape.Path p = new javafx.scene.shape.Path(new javafx.scene.shape.MoveTo(0, 0),
                            new javafx.scene.shape.ArcTo(1, 1, 0, x, y, false, true));
                    Bounds b = p.getLayoutBounds();
                    if (Math.max(b.getWidth(), b.getHeight()) < Math.max(x, y) * 0.9) {
                        empty.add(x + "," + y);
                    }
                }
            }
            TestKit.check(empty.isEmpty(), "JDK-8393157: ArcTo to these points has no shape: " + empty);
            javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
            svg.setContent("M11.65,6.47A1.69,1.69 0 0,1 13.34,4.78");
            Bounds s = svg.getLayoutBounds();
            TestKit.check(s.getWidth() > 1 && s.getHeight() > 1, "JDK-8393053: the SVG arc has no shape: " + s);
            javafx.scene.shape.ArcTo a = new javafx.scene.shape.ArcTo();
            a.setX(113.8233880241887);
            a.setY(316.3783247347362);
            a.setRadiusX(9.5);
            a.setRadiusY(9.5);
            javafx.scene.shape.Path half = new javafx.scene.shape.Path(
                    new javafx.scene.shape.MoveTo(119.83171557850895, 334.4033073976959), a,
                    new javafx.scene.shape.LineTo(63.99583622283988, 332.98750866852015),
                    new javafx.scene.shape.LineTo(70.00416377716013, 351.01249133147985),
                    new javafx.scene.shape.ClosePath());
            half.setFill(Color.BLACK);
            half.setStroke(null);
            Bounds h = half.getLayoutBounds();
            TestKit.check(h.getMinY() <= 316.5, "JDK-8332950: the arc without rotation was skipped: the shape starts "
                    + "at y " + h.getMinY() + ", not 316.4");
            return "400 arcs, the SVG arc and the arc without rotation";
        });
    }

    private String coveredNodeTest() {
        int lines = 10;
        Color on = Color.LIMEGREEN, off = Color.DARKGREEN;
        List<javafx.scene.shape.Line[]> allLines = new ArrayList<>();
        List<Circle> circles = new ArrayList<>();
        VBox[] side = new VBox[1];
        StackPane root = TestKit.fx(() -> {
            HBox contents = new HBox(10);
            contents.setPadding(new Insets(10));
            for (int k = 0; k < 2; k++) {
                Group group = new Group();
                group.setManaged(false);
                javafx.scene.shape.Line[] ls = new javafx.scene.shape.Line[lines];
                for (int i = 0; i < lines; i++) {
                    ls[i] = new javafx.scene.shape.Line(20, 50 + i * 10, 220, 50 + i * 10);
                    ls[i].setStrokeWidth(4);
                    ls[i].setStroke(off);
                    group.getChildren().add(ls[i]);
                }
                Circle c = new Circle(220, 50, 5, on);
                group.getChildren().add(c);
                StackPane element = new StackPane(group);
                element.setStyle("-fx-background-color: lightgrey; -fx-border-color: black;");
                element.setMinSize(240, 200);
                contents.getChildren().add(element);
                allLines.add(ls);
                circles.add(c);
            }
            side[0] = new VBox(new Label("Side"));
            side[0].setMaxWidth(150);
            side[0].setStyle("-fx-background-color: lightblue;");
            StackPane.setAlignment(side[0], Pos.CENTER_RIGHT);
            return new StackPane(contents, side[0]);
        });
        show(root, 520, 240);
        int last = 0;
        for (int step = 0; step < 14; step++) {
            int index = step % lines;
            last = index;
            TestKit.fx(() -> {
                for (javafx.scene.shape.Line[] ls : allLines) {
                    for (int i = 0; i < lines; i++) {
                        ls[i].setStroke(i == index ? on : off);
                    }
                }
            });
            TestKit.pause(120);
            TestKit.fx(() -> circles.forEach(c -> c.setCenterY(50 + index * 10)));
            TestKit.pause(120);
            if (step % 3 == 0) {
                TestKit.fx(() -> side[0].setVisible(!side[0].isVisible()));
                TestKit.pause(100);
            }
        }
        TestKit.fx(() -> side[0].setVisible(false));
        TestKit.pause(500);
        int lastIndex = last;
        List<String> stale = new ArrayList<>();
        for (int k = 0; k < 2; k++) {
            int e = k;
            Color seen = TestKit.fx(() -> {
                Circle c = circles.get(e);
                Point2D p = c.localToScreen(c.getCenterX(), c.getCenterY());
                return TestKit.robot().getPixelColor(p);
            });
            Color line = TestKit.fx(() -> {
                javafx.scene.shape.Line l = allLines.get(e)[lastIndex];
                return TestKit.robot().getPixelColor(l.localToScreen(120, l.getStartY()));
            });
            if (!near(seen, on, 0.12)) {
                stale.add("the circle of element " + (e + 1) + " is " + rgb(seen));
            }
            if (!near(line, on, 0.12)) {
                stale.add("the line " + (lastIndex + 1) + " of element " + (e + 1) + " is " + rgb(line));
            }
        }
        TestKit.fx(this::closeTestStage);
        TestKit.check(stale.isEmpty(), "JDK-8322619: not drawn any more: " + String.join("; ", stale));
        return "both elements up to date";
    }

    private String subSceneSnapshotTest() {
        Rectangle[] rect = new Rectangle[1];
        StackPane root = TestKit.fx(() -> {
            rect[0] = new Rectangle(60, 60, 80, 80);
            rect[0].setFill(Color.RED);
            SubScene sub = new SubScene(new Group(rect[0]), 200, 200, true, SceneAntialiasing.DISABLED);
            sub.setFill(Color.WHITE);
            sub.setCamera(new PerspectiveCamera());
            return new StackPane(sub);
        });
        show(root, 240, 240);
        Color center = TestKit.fx(() -> {
            WritableImage snap = rect[0].snapshot(null, null);
            return snap.getPixelReader().getColor((int) snap.getWidth() / 2, (int) snap.getHeight() / 2);
        });
        TestKit.fx(this::closeTestStage);
        TestKit.check(near(center, Color.RED, 0.1), "JDK-8260013: the snapshot of the node in the SubScene is "
                + rgb(center) + " at its center, not red");
        return "red";
    }

    private String newSceneTest() {
        Rectangle first = TestKit.fx(() -> new Rectangle(200, 200, Color.web("#dc2626")));
        StackPane root = TestKit.fx(() -> new StackPane(first));
        show(root, 200, 200);
        Color[] colors = {Color.web("#2563eb"), Color.web("#16a34a"), Color.web("#9333ea")};
        for (Color c : colors) {
            Rectangle r = TestKit.fx(() -> new Rectangle(200, 200, c));
            TestKit.fx(() -> testStage.setScene(new Scene(new StackPane(r), 200, 200)));
            try {
                TestKit.await("the new scene drawn", 3000, () -> near(TestKit.robot().getPixelColor(TestKit.center(r)),
                        c, 0.05));
            } catch (AssertionError e) {
                Color seen = TestKit.fx(() -> TestKit.robot().getPixelColor(TestKit.center(r)));
                TestKit.fx(this::closeTestStage);
                throw new AssertionError("JDK-8306121: the new scene is not drawn: " + rgb(seen) + ", not " + rgb(c));
            }
        }
        TestKit.fx(this::closeTestStage);
        return "3 scenes drawn";
    }

    private String zeroSubSceneTest() {
        return TestKit.fx(() -> {
            Rectangle r = new Rectangle(10, 10, 20, 20);
            SubScene sub = new SubScene(new Group(r), 0, 0);
            Scene scene = new Scene(new Group(sub), 100, 100);
            List<String> bad = new ArrayList<>();
            for (javafx.scene.Camera camera : List.of(new ParallelCamera(), new PerspectiveCamera())) {
                sub.setCamera(camera);
                Point2D p = r.localToScene(15, 15);
                Point2D q = r.sceneToLocal(5, 5);
                if (p == null || !Double.isFinite(p.getX()) || !Double.isFinite(p.getY())
                        || (q != null && (!Double.isFinite(q.getX()) || !Double.isFinite(q.getY())))) {
                    bad.add(camera.getClass().getSimpleName() + ": " + p + ", " + q);
                }
            }
            TestKit.check(scene != null && bad.isEmpty(), "JDK-8372761: points that are not finite: " + bad);
            return "finite with both cameras";
        });
    }

    private static String fcList(String pattern) {
        try {
            Process p = new ProcessBuilder("fc-list", pattern, "family").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return out.strip();
        } catch (java.io.IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String colorEmojiTest() {
        String fonts = fcList(":charset=1f600:color=true");
        if (fonts == null || fonts.isEmpty()) {
            TestKit.skip("no color emoji font (fc-list :charset=1f600:color=true)");
        }
        Text emoji = TestKit.fx(() -> {
            Text t = new Text(new StringBuilder().appendCodePoint(0x1F600).appendCodePoint(0x1F680).toString());
            t.setFont(Font.font(72));
            return t;
        });
        StackPane root = TestKit.fx(() -> {
            StackPane p = new StackPane(emoji);
            p.setStyle("-fx-background-color: white;");
            return p;
        });
        show(root, 260, 140);
        int[] counts = TestKit.fx(() -> {
            Bounds b = emoji.localToScreen(emoji.getLayoutBounds());
            WritableImage image = TestKit.robot().getScreenCapture(null, b.getMinX(), b.getMinY(), b.getWidth(),
                    b.getHeight());
            int colored = 0, drawn = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color c = image.getPixelReader().getColor(x, y);
                    if (c.getBrightness() < 0.95 || c.getSaturation() > 0.1) {
                        drawn++;
                    }
                    if (c.getSaturation() > 0.4 && c.getBrightness() > 0.3) {
                        colored++;
                    }
                }
            }
            return new int[] {drawn, colored};
        });
        TestKit.fx(this::closeTestStage);
        TestKit.check(counts[0] > 100, "the emoji is not drawn (" + counts[0] + " pixels)");
        TestKit.check(counts[1] > 100, "JDK-8337816: the emoji is drawn in gray: " + counts[1] + " colored pixels of "
                + counts[0] + ", with " + fonts.lines().findFirst().orElse(""));
        return counts[1] + " colored pixels, " + fonts.lines().findFirst().orElse("");
    }

    private String emojiTitleTest() {
        if ("Gtk4".equalsIgnoreCase(System.getProperty("glass.platform")) || System.getenv("DISPLAY") == null) {
            TestKit.skip("the title is read with xprop, from X11");
        }
        String token = String.valueOf(System.nanoTime());
        String title = "Title 👍 ação " + token;
        StackPane root = TestKit.fx(() -> new StackPane(new Label("Title")));
        show(root, 200, 100);
        TestKit.fx(() -> testStage.setTitle(title));
        TestKit.pause(500);
        String found = null;
        try {
            String list = run("xprop", "-root", "_NET_CLIENT_LIST");
            if (list == null) {
                TestKit.skip("xprop is not installed");
            }
            for (String id : list.replaceAll(".*#", "").split(",")) {
                String name = run("xprop", "-id", id.strip(), "-notype", "_NET_WM_NAME");
                if (name != null && name.contains(token)) {
                    found = name.replaceAll("^[^=]*= ?\"?", "").replaceAll("\"\\s*$", "");
                    break;
                }
            }
        } finally {
            TestKit.fx(this::closeTestStage);
        }
        TestKit.check(found != null, "JDK-8382165: no window has the title in _NET_WM_NAME, as \"" + title + "\"");
        TestKit.check(found.equals(title), "JDK-8382165: the title is \"" + found + "\", expected \"" + title + "\"");
        return "\"" + found + "\"";
    }

    private static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0 ? out.strip() : null;
        } catch (java.io.IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String trailingSpaceTest() {
        return TestKit.fx(() -> {
            // Three words that wrap one per line: the first two lines end with the space where they wrap
            Text t = new Text("AAA AAA AAA");
            t.setFont(Font.font("Monospaced", 40));
            t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            Text word = new Text("AAA ");
            word.setFont(t.getFont());
            new Scene(new Group(word));
            double wrap = Math.ceil(word.getLayoutBounds().getWidth()) + 10;
            t.setWrappingWidth(wrap);
            new Scene(new Group(t));
            double[] x = new double[3];
            for (int i = 0; i < 3; i++) {
                x[i] = new javafx.scene.shape.Path(t.rangeShape(i * 4, i * 4 + 3)).getLayoutBounds().getMinX();
            }
            TestKit.check(Math.abs(x[0] - x[2]) < 1 && Math.abs(x[1] - x[2]) < 1, String.format(Locale.ROOT,
                    "JDK-8314215: the wrapped lines start at x %.1f, %.1f and %.1f: the trailing spaces move them",
                    x[0], x[1], x[2]));
            return String.format(Locale.ROOT, "the three lines at x %.1f", x[2]);
        });
    }

    private String hitTestTest() {
        return TestKit.fx(() -> {
            Text first = new Text("Normal ");
            Text bold = new Text("BOLD");
            Text last = new Text(" text");
            for (Text t : List.of(first, bold, last)) {
                t.setFont(Font.font("System", t == bold ? FontWeight.BOLD : FontWeight.NORMAL, 48));
            }
            javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow(first, bold, last);
            new Scene(new Group(flow));
            flow.applyCss();
            flow.layout();
            List<String> wrong = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Bounds b = new javafx.scene.shape.Path(bold.rangeShape(i, i + 1)).getLayoutBounds();
                // A third of the glyph in, on its leading side
                Point2D p = new Point2D(b.getMinX() + b.getWidth() / 3, b.getCenterY());
                int index = bold.hitTest(p).getCharIndex();
                if (index != i) {
                    wrong.add("'" + "BOLD".charAt(i) + "' gave " + index);
                }
            }
            TestKit.check(wrong.isEmpty(), "JDK-8306083: wrong character indexes: " + wrong);
            return "B O L D at 0 1 2 3";
        });
    }

    private String hugeGlyphsTest() {
        Text text = TestKit.fx(() -> {
            Text t = new Text("Wg");
            t.setFont(Font.font(500));
            t.setFill(Color.BLACK);
            return t;
        });
        StackPane root = TestKit.fx(() -> {
            StackPane p = new StackPane(text);
            p.setStyle("-fx-background-color: white;");
            return p;
        });
        Rectangle2D screen = TestKit.fx(() -> javafx.stage.Screen.getPrimary().getVisualBounds());
        show(root, Math.min(1000, screen.getWidth() - 40), Math.min(700, screen.getHeight() - 40));
        double dark = TestKit.fx(() -> {
            Bounds b = root.localToScreen(root.getLayoutBounds());
            WritableImage image = TestKit.robot().getScreenCapture(null, b.getMinX(), b.getMinY(), b.getWidth(),
                    b.getHeight());
            int n = 0, total = 0;
            for (int y = 0; y < image.getHeight(); y += 4) {
                for (int x = 0; x < image.getWidth(); x += 4) {
                    total++;
                    if (image.getPixelReader().getColor(x, y).getBrightness() < 0.3) {
                        n++;
                    }
                }
            }
            return (double) n / total;
        });
        TestKit.fx(this::closeTestStage);
        TestKit.check(dark > 0.05, String.format(Locale.ROOT, "JDK-8237833: the huge glyphs are not drawn: %.1f%% "
                + "of the window is dark", dark * 100));
        return String.format(Locale.ROOT, "%.0f%% of the window is glyph", dark * 100);
    }

    private String contextMenuTest() {
        Button button = TestKit.fx(() -> new Button("Menu"));
        show(TestKit.fx(() -> new StackPane(button)), 300, 200);
        javafx.scene.control.ContextMenu menu = TestKit.fx(() -> new javafx.scene.control.ContextMenu(
                new javafx.scene.control.MenuItem("First item"), new javafx.scene.control.MenuItem("Second item"),
                new javafx.scene.control.MenuItem("Third item")));
        List<Integer> blank = new ArrayList<>();
        String[] detail = {""};
        try {
            for (int i = 1; i <= 15; i++) {
                TestKit.fx(() -> menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0));
                TestKit.await("the menu", 3000, () -> menu.isShowing() && menu.getScene() != null
                        && menu.getScene().getRoot().getLayoutBounds().getWidth() > 0);
                TestKit.pause(250);
                double diff = TestKit.fx(() -> {
                    Node r = menu.getScene().getRoot();
                    WritableImage snap = r.snapshot(null, null);
                    Bounds b = r.localToScreen(r.getLayoutBounds());
                    WritableImage screen = TestKit.robot().getScreenCapture(null, b.getMinX(), b.getMinY(),
                            b.getWidth(), b.getHeight(), false);
                    // The snapshot covers the shadow of the menu too: its layout bounds start further in
                    Bounds layout = r.getLayoutBounds(), all = r.getBoundsInLocal();
                    int dx = (int) Math.round(layout.getMinX() - all.getMinX());
                    int dy = (int) Math.round(layout.getMinY() - all.getMinY());
                    int w = (int) Math.min(snap.getWidth() - dx, screen.getWidth());
                    int h = (int) Math.min(snap.getHeight() - dy, screen.getHeight());
                    double sum = 0;
                    int n = 0;
                    for (int y = 3; y < h - 3; y += 3) {
                        for (int x = 3; x < w - 3; x += 3) {
                            Color a = snap.getPixelReader().getColor(x + dx, y + dy);
                            Color c = screen.getPixelReader().getColor(x, y);
                            // Only the opaque body of the menu: its shadow is translucent over the window behind
                            if (a.getOpacity() < 0.99) {
                                continue;
                            }
                            sum += (Math.abs(a.getRed() - c.getRed()) + Math.abs(a.getGreen() - c.getGreen())
                                    + Math.abs(a.getBlue() - c.getBlue())) / 3;
                            n++;
                        }
                    }
                    Color a = snap.getPixelReader().getColor(w / 2 + dx, h / 2 + dy);
                    Color c = screen.getPixelReader().getColor(w / 2, h / 2);
                    detail[0] = String.format(Locale.ROOT, "mean difference %.3f over %d pixels; snapshot %.0f × %.0f, "
                            + "screen %.0f × %.0f at %.0f, %.0f; center %s on the screen, %s in the snapshot",
                            n == 0 ? 1 : sum / n, n, snap.getWidth(), snap.getHeight(), screen.getWidth(),
                            screen.getHeight(), b.getMinX(), b.getMinY(), rgb(c), rgb(a));
                    return n == 0 ? 1 : sum / n;
                });
                if (diff > 0.08) {
                    blank.add(i);
                }
                TestKit.fx(menu::hide);
                TestKit.pause(150);
            }
        } finally {
            TestKit.fx(() -> {
                menu.hide();
                closeTestStage();
            });
        }
        TestKit.check(blank.isEmpty(), "JDK-8242362: the menu was not drawn as its snapshot the times " + blank
                + " of 15: " + detail[0]);
        return "drawn 15 times";
    }

    private String transparentStageTest() {
        Rectangle back = TestKit.fx(() -> new Rectangle(300, 300, Color.MAGENTA));
        show(TestKit.fx(() -> new StackPane(back)), 300, 300);
        Stage[] transparent = new Stage[1];
        Circle circle = TestKit.fx(() -> new Circle(40, Color.web("#0ea5e9")));
        try {
            TestKit.fx(() -> {
                Stage s = new Stage(StageStyle.TRANSPARENT);
                s.initOwner(testStage);
                StackPane p = new StackPane(circle);
                p.setStyle("-fx-background-color: transparent;");
                Scene scene = new Scene(p, 200, 200);
                scene.setFill(Color.TRANSPARENT);
                s.setScene(scene);
                s.setX(testStage.getX() + 50);
                s.setY(testStage.getY() + 50);
                s.show();
                transparent[0] = s;
            });
            TestKit.await("the transparent stage", 5000, () -> circle.localToScreen(0, 0) != null);
            TestKit.pause(600);
            Color through = TestKit.fx(() -> {
                Bounds b = circle.getScene().getRoot().localToScreen(circle.getScene().getRoot().getLayoutBounds());
                return TestKit.robot().getPixelColor(b.getMinX() + 10, b.getMinY() + 10);
            });
            Color center = TestKit.fx(() -> TestKit.robot().getPixelColor(TestKit.center(circle)));
            TestKit.check(near(center, Color.web("#0ea5e9"), 0.08), "the circle of the transparent stage is "
                    + rgb(center));
            TestKit.check(near(through, Color.MAGENTA, 0.08), "the transparent area shows " + rgb(through)
                    + ", not the magenta window behind");
            return "magenta through it, the circle drawn";
        } finally {
            TestKit.fx(() -> {
                if (transparent[0] != null) {
                    transparent[0].hide();
                }
                closeTestStage();
            });
        }
    }

    private void closeTestStage() {
        if (testStage != null) {
            testStage.hide();
            testStage = null;
        }
    }

    /** Shows a root in an undecorated stage of the tests, and waits for it. */
    private void show(Region root, double width, double height) {
        TestKit.fx(() -> {
            closeTestStage();
            testStage = new Stage(StageStyle.UNDECORATED);
            testStage.setTitle("Render test");
            testStage.setScene(new Scene(root, width, height));
            testStage.show();
        });
        TestKit.await("the test stage shown", 5000, () -> testStage != null && testStage.isShowing()
                && root.getWidth() > 0);
        TestKit.pause(400);
    }

    private static boolean near(Color a, Color b, double tolerance) {
        return Math.abs(a.getRed() - b.getRed()) <= tolerance && Math.abs(a.getGreen() - b.getGreen()) <= tolerance
                && Math.abs(a.getBlue() - b.getBlue()) <= tolerance;
    }

    private static String rgb(Color c) {
        return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String srgbTest(boolean capture) {
        Rectangle swatch = TestKit.fx(() -> new Rectangle(200, 200));
        show(TestKit.fx(() -> new StackPane(swatch)), 200, 200);
        for (double[] c : SRGB_COLORS) {
            Color expected = Color.color(c[0], c[1], c[2]);
            TestKit.fx(() -> swatch.setFill(expected));
            Color[] seen = {null};
            try {
                TestKit.await("the color " + rgb(expected), 3000, () -> {
                    Point2D center = TestKit.center(swatch);
                    seen[0] = capture
                            ? TestKit.robot().getScreenCapture(null, center.getX(), center.getY(), 5, 5)
                                    .getPixelReader().getColor(3, 3)
                            : TestKit.robot().getPixelColor(center);
                    return near(expected, seen[0], COMPONENT_TOLERANCE);
                });
            } catch (AssertionError e) {
                throw new AssertionError("expected " + rgb(expected) + ", read " + rgb(seen[0]));
            }
        }
        return SRGB_COLORS.length + " colors within 2/255";
    }

    private String pixelBufferTest(boolean ints, boolean direct) {
        HBox row = TestKit.fx(() -> new HBox(1));
        Runnable[] update = new Runnable[1];
        TestKit.fx(() -> {
            if (ints) {
                IntBuffer shared = intBuffer(PB_SIZE, PB_SIZE, direct, PB_INIT);
                IntBuffer source = intBuffer(PB_SIZE, PB_SIZE / 2, direct, PB_TEST);
                PixelBuffer<IntBuffer> pb = new PixelBuffer<>(PB_SIZE, PB_SIZE, shared,
                        PixelFormat.getIntArgbPreInstance());
                for (int i = 0; i < 4; i++) {
                    row.getChildren().add(new ImageView(new WritableImage(pb)));
                }
                update[0] = () -> pb.updateBuffer(b -> {
                    IntBuffer dst = b.getBuffer();
                    dst.put(source);
                    source.rewind();
                    dst.rewind();
                    return new Rectangle2D(0, 0, PB_SIZE, PB_SIZE / 2);
                });
            } else {
                java.nio.ByteBuffer shared = byteBuffer(PB_SIZE, PB_SIZE, direct, PB_INIT);
                java.nio.ByteBuffer source = byteBuffer(PB_SIZE, PB_SIZE / 2, direct, PB_TEST);
                PixelBuffer<java.nio.ByteBuffer> pb = new PixelBuffer<>(PB_SIZE, PB_SIZE, shared,
                        PixelFormat.getByteBgraPreInstance());
                for (int i = 0; i < 4; i++) {
                    row.getChildren().add(new ImageView(new WritableImage(pb)));
                }
                update[0] = () -> pb.updateBuffer(b -> {
                    java.nio.ByteBuffer dst = b.getBuffer();
                    dst.put(source);
                    source.rewind();
                    dst.rewind();
                    return new Rectangle2D(0, 0, PB_SIZE, PB_SIZE / 2);
                });
            }
        });
        show(TestKit.fx(() -> new StackPane(row)), PB_SIZE * 4 + 3 + 40, PB_SIZE + 40);
        TestKit.await("the initial color in the four images", 3000, () -> imagesShow(row, PB_INIT, PB_INIT));
        TestKit.fx(update[0]);
        TestKit.await("the top halves updated in the four images", 3000, () -> imagesShow(row, PB_TEST, PB_INIT));
        return "4 images updated together";
    }

    /** Whether each image shows the colors above and below its middle. */
    private static boolean imagesShow(HBox row, Color top, Color bottom) {
        for (Node n : row.getChildren()) {
            Bounds b = n.localToScreen(n.getLayoutBounds());
            Color a = TestKit.robot().getPixelColor(b.getCenterX(), b.getMinY() + PB_SIZE * 0.45 - 2);
            Color c = TestKit.robot().getPixelColor(b.getCenterX(), b.getMinY() + PB_SIZE * 0.55 + 2);
            if (!near(a, top, 0.01) || !near(c, bottom, 0.01)) {
                return false;
            }
        }
        return true;
    }

    private static IntBuffer intBuffer(int w, int h, boolean direct, Color c) {
        int color = (int) Math.round(c.getOpacity() * 255) << 24 | (int) Math.round(c.getRed() * 255) << 16
                | (int) Math.round(c.getGreen() * 255) << 8 | (int) Math.round(c.getBlue() * 255);
        IntBuffer b = direct ? java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
                .asIntBuffer() : IntBuffer.allocate(w * h);
        for (int i = 0; i < w * h; i++) {
            b.put(color);
        }
        return b.rewind();
    }

    private static java.nio.ByteBuffer byteBuffer(int w, int h, boolean direct, Color c) {
        java.nio.ByteBuffer b = direct ? java.nio.ByteBuffer.allocateDirect(w * h * 4)
                : java.nio.ByteBuffer.allocate(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            b.put((byte) Math.round(c.getBlue() * 255)).put((byte) Math.round(c.getGreen() * 255))
                    .put((byte) Math.round(c.getRed() * 255)).put((byte) Math.round(c.getOpacity() * 255));
        }
        return b.rewind();
    }

    private String pixelBufferThreadTest() {
        PixelBuffer<IntBuffer> pb = TestKit.fx(() -> new PixelBuffer<>(PB_SIZE, PB_SIZE,
                intBuffer(PB_SIZE, PB_SIZE, true, PB_INIT), PixelFormat.getIntArgbPreInstance()));
        try {
            pb.updateBuffer(b -> null);
        } catch (IllegalStateException e) {
            return "IllegalStateException: " + e.getMessage();
        }
        throw new AssertionError("updateBuffer ran on the test thread");
    }

    private String lcdTest(boolean opaque) {
        if (!Platform.isSupported(ConditionalFeature.SCENE3D)) {
            TestKit.skip("The software pipeline draws no LCD text");
        }
        double scale = TestKit.fx(() -> commandStage.getOutputScaleX());
        if (scale != 1) {
            TestKit.skip("The samples need an output scale of 1, not " + scale);
        }
        double a = LCD_TRANSPARENT.getOpacity();
        Color opaqueColor = Color.color(LCD_TRANSPARENT.getRed() * a + 1 - a, LCD_TRANSPARENT.getGreen() * a + 1 - a,
                LCD_TRANSPARENT.getBlue() * a + 1 - a);
        Text text = TestKit.fx(() -> {
            Text t = new Text("V");
            t.setFont(Font.font("System", FontWeight.BOLD, 36));
            t.setFill(opaque ? opaqueColor : LCD_TRANSPARENT);
            t.setFontSmoothingType(FontSmoothingType.LCD);
            t.setLayoutX(5);
            t.setLayoutY(30);
            return t;
        });
        javafx.scene.layout.Pane root = TestKit.fx(() -> {
            javafx.scene.layout.Pane p = new javafx.scene.layout.Pane(text);
            p.setStyle("-fx-background-color: white;");
            return p;
        });
        show(root, 400, 300);
        List<Color> colors = TestKit.fx(() -> {
            Point2D origin = root.localToScreen(5, 11);
            Image image = TestKit.robot().getScreenCapture(null, origin.getX(), origin.getY(), 12, 1);
            List<Color> list = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                list.add(image.getPixelReader().getColor(i, 0));
            }
            return list;
        });
        double tolerance = 2.5 / 255;
        TestKit.check(near(colors.get(0), Color.WHITE, tolerance) && near(colors.get(11), Color.WHITE, tolerance),
                "the background around the text is " + rgb(colors.get(0)) + " and " + rgb(colors.get(11)));
        TestKit.check(near(colors.get(6), opaqueColor, tolerance), "the middle of the stroke is " + rgb(colors.get(6))
                + ", not " + rgb(opaqueColor));
        long colored = colors.stream().filter(c -> c.getRed() != c.getGreen() || c.getRed() != c.getBlue()).count();
        if (opaque) {
            TestKit.check(colored > 0, "the opaque text has gray antialiasing, not LCD");
            return colored + " colored edge pixels: LCD";
        }
        TestKit.check(colored == 0, "the translucent text has " + colored + " colored pixels, not gray antialiasing");
        return "gray antialiasing";
    }

    private String snapshotTest() {
        StackPane root = TestKit.fx(() -> {
            Rectangle back = new Rectangle(300, 200, new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#1e3a8a")), new Stop(1, Color.web("#db2777"))));
            Circle circle = new Circle(60, Color.web("#facc15"));
            Text label = new Text("Snapshot");
            label.setFont(Font.font("System", FontWeight.BOLD, 28));
            label.setFill(Color.WHITE);
            StackPane p = new StackPane(back, circle, label);
            p.setStyle("-fx-background-color: black;");
            return p;
        });
        show(root, 300, 200);
        double[] diff = TestKit.fx(() -> {
            WritableImage snap = root.snapshot(null, null);
            Bounds b = root.localToScreen(root.getLayoutBounds());
            WritableImage screen = TestKit.robot().getScreenCapture(null, b.getMinX(), b.getMinY(), b.getWidth(),
                    b.getHeight(), false);
            int w = (int) Math.min(snap.getWidth(), screen.getWidth());
            int h = (int) Math.min(snap.getHeight(), screen.getHeight());
            double sum = 0, worst = 0;
            int n = 0;
            for (int y = 2; y < h - 2; y += 2) {
                for (int x = 2; x < w - 2; x += 2) {
                    Color c1 = snap.getPixelReader().getColor(x, y), c2 = screen.getPixelReader().getColor(x, y);
                    double d = (Math.abs(c1.getRed() - c2.getRed()) + Math.abs(c1.getGreen() - c2.getGreen())
                            + Math.abs(c1.getBlue() - c2.getBlue())) / 3;
                    sum += d;
                    worst = Math.max(worst, d);
                    n++;
                }
            }
            return new double[] {sum / n, worst};
        });
        TestKit.check(diff[0] < 0.02, String.format(Locale.ROOT, "the snapshot differs from the screen by %.3f "
                + "on average", diff[0]));
        return String.format(Locale.ROOT, "mean difference %.4f, worst %.3f", diff[0], diff[1]);
    }

    private String tabsTest() {
        Throwable[] failure = {null};
        Thread.UncaughtExceptionHandler old = TestKit.fx(() -> Thread.currentThread().getUncaughtExceptionHandler());
        TestKit.fx(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> failure[0] = e));
        int selected = TestKit.fx(() -> tabs.getSelectionModel().getSelectedIndex());
        List<String> blank = new ArrayList<>();
        try {
            int count = TestKit.fx(() -> tabs.getTabs().size());
            for (int i = 0; i < count; i++) {
                int index = i;
                TestKit.fx(() -> tabs.getSelectionModel().select(index));
                TestKit.pause(700);
                double spread = TestKit.fx(() -> {
                    Node content = tabs.getSelectionModel().getSelectedItem().getContent();
                    Bounds b = content.localToScreen(content.getLayoutBounds());
                    WritableImage image = TestKit.robot().getScreenCapture(null, b.getMinX(), b.getMinY(),
                            Math.min(b.getWidth(), 600), Math.min(b.getHeight(), 400));
                    double min = 1, max = 0;
                    for (int y = 0; y < image.getHeight(); y += 7) {
                        for (int x = 0; x < image.getWidth(); x += 7) {
                            double v = image.getPixelReader().getColor(x, y).getBrightness();
                            min = Math.min(min, v);
                            max = Math.max(max, v);
                        }
                    }
                    return max - min;
                });
                if (spread < 0.1) {
                    blank.add(TestKit.fx(() -> tabs.getTabs().get(index).getText()));
                }
                TestKit.check(failure[0] == null, "the FX thread threw " + failure[0]);
            }
            TestKit.check(blank.isEmpty(), "blank tabs: " + blank);
            return count + " tabs drawn";
        } finally {
            TestKit.fx(() -> {
                Thread.currentThread().setUncaughtExceptionHandler(old);
                tabs.getSelectionModel().select(selected);
            });
        }
    }
}
