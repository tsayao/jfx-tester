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

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * The input of the robot through libei, with the foreign function API: a
 * remote desktop session of the XDG desktop portal, whose ConnectToEIS gives
 * the connection to the compositor, which libei then sends the events to. They
 * reach the application as those of a real device.
 * <p>
 * Everything runs on a thread of its own, with a GLib main context of its own
 * for the answers of the portal: the JavaFX application thread only waits, and
 * the dialog of the portal does not need it. The positions are global, in the
 * logical coordinates of the compositor. The screen comes from the Screenshot
 * interface of the portal: a pixel from its PickColor, as the eyedropper
 * apps do, which waits for a click that libei gives, and a region, or a pixel
 * when PickColor does not work, from a screenshot of all the screens.
 */
final class EiInput {

    private static final int EVENT_DISCONNECT = 2;
    private static final int EVENT_SEAT_ADDED = 3;
    private static final int EVENT_DEVICE_REMOVED = 6;
    private static final int EVENT_DEVICE_PAUSED = 7;
    private static final int EVENT_DEVICE_RESUMED = 8;
    private static final int EVENT_PONG = 90;

    private static final int CAP_POINTER_ABSOLUTE = 1 << 1;
    private static final int CAP_KEYBOARD = 1 << 2;
    private static final int CAP_SCROLL = 1 << 4;
    private static final int CAP_BUTTON = 1 << 5;

    private static final long CONNECT_TIMEOUT = 5_000_000_000L;
    private static final long SYNC_TIMEOUT = 2_000_000_000L;
    private static final int SCROLL_DETENT = 120;
    // The compositor finds the surface under the pointer at its next frame:
    // what follows a motion must go to that surface
    private static final long POINTER_FOCUS_DELAY = 40;
    private static final int BTN_LEFT = 0x110;
    // PickColor shows its picker a moment after the call: the click waits for
    // it, and comes again when the picker missed it
    private static final long PICK_FIRST_CLICK_MS = 80;
    private static final long PICK_CLICK_EVERY_MS = 400;
    private static final int PICK_CLICKS = 8;
    // Why PickColor does not work, once it failed: the screenshot does then
    private static volatile String pickFailure;
    // The last position given, and the buttons held, which a pick must not break
    private double lastX = Double.NaN, lastY = Double.NaN;
    private int buttonsHeld;

    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "libei");
        t.setDaemon(true);
        return t;
    });

    private MemorySegment ei;
    private MemorySegment seat;
    private MemorySegment absolute;
    private MemorySegment buttons;
    private MemorySegment scroll;
    private MemorySegment keyboard;
    private boolean disconnected;
    private MemorySegment pendingPing;
    // How long a frame is given to reach the compositor without a ping to wait for
    private static final long UNSYNCED_FRAME_MS = 30;
    private int sequence;

    /**
     * Connects to the compositor, which may show the dialog of the portal.
     *
     * @throws UnsupportedOperationException when the system has no libei, or
     *         the desktop no EIS, or the permission was refused
     */
    EiInput() {
        call(() -> {
            if (Ei.LIB == null) {
                throw new UnsupportedOperationException("libei is not installed");
            }
            int fd;
            try {
                fd = Portal.connectToEis();
            } catch (UnsatisfiedLinkError | IllegalStateException e) {
                throw new UnsupportedOperationException("The remote desktop portal: " + e.getMessage(), e);
            }
            ei = (MemorySegment) Ei.invoke(Ei.NEW_SENDER, MemorySegment.NULL);
            try (Arena arena = Arena.ofConfined()) {
                Ei.invoke(Ei.CONFIGURE_NAME, ei, arena.allocateFrom("JavaFX test lab"));
            }
            int status = (int) Ei.invoke(Ei.SETUP_BACKEND_FD, ei, fd);
            if (status != 0) {
                throw new UnsupportedOperationException("ei_setup_backend_fd failed: " + status);
            }
            awaitDevices();
            return null;
        });
    }

    void moveTo(double x, double y) {
        call(() -> {
            dispatch();
            MemorySegment device = require(() -> absolute, "absolute pointer");
            Ei.invoke(Ei.POINTER_MOTION_ABSOLUTE, device, x, y);
            frame(device);
            lastX = x;
            lastY = y;
            Thread.sleep(POINTER_FOCUS_DELAY);
            return null;
        });
    }

    void button(int evdevButton, boolean press) {
        call(() -> {
            dispatch();
            MemorySegment device = require(() -> buttons, "button");
            Ei.invoke(Ei.BUTTON_BUTTON, device, evdevButton, press);
            frame(device);
            buttonsHeld = Math.max(0, buttonsHeld + (press ? 1 : -1));
            return null;
        });
    }

    void scroll(int detents) {
        call(() -> {
            dispatch();
            MemorySegment device = require(() -> scroll, "scroll");
            Ei.invoke(Ei.SCROLL_DISCRETE, device, 0, detents * SCROLL_DETENT);
            frame(device);
            return null;
        });
    }

    void key(int evdevKeycode, boolean press) {
        call(() -> {
            dispatch();
            MemorySegment device = require(() -> keyboard, "keyboard");
            Ei.invoke(Ei.KEYBOARD_KEY, device, evdevKeycode, press);
            frame(device);
            return null;
        });
    }

    /**
     * The color of a pixel, in sRGB, through the PickColor of the Screenshot
     * portal: it shows a picker, which the pointer of libei clicks at the
     * point, and answers the color there, with no screenshot. The pointer then
     * goes back. Null when PickColor does not work, or a button is held, which
     * the click would release: a screenshot does then.
     */
    double[] pickColor(double x, double y) {
        if (pickFailure != null) {
            return null;
        }
        return call(() -> {
            if (buttonsHeld > 0) {
                return null;
            }
            double backX = lastX, backY = lastY;
            try (Arena arena = Arena.ofConfined()) {
                Portal.init(arena);
                if (Portal.version(arena, Portal.SCREENSHOT) < 2) {
                    throw new UnsupportedOperationException("The Screenshot portal has no PickColor (version 2)");
                }
                int[] clicks = {0};
                MemorySegment results = Portal.request(arena, Portal.SCREENSHOT, "PickColor", "('', %s)", null,
                        PICK_FIRST_CLICK_MS + PICK_CLICK_EVERY_MS * PICK_CLICKS, elapsed -> {
                            dispatch();
                            if (clicks[0] < PICK_CLICKS
                                    && elapsed >= PICK_FIRST_CLICK_MS + PICK_CLICK_EVERY_MS * clicks[0]) {
                                clicks[0]++;
                                clickAt(x, y);
                            }
                        });
                if (Boolean.getBoolean("tests.pickTrace")) {
                    System.out.println("PickColor answered after " + clicks[0] + " clicks");
                }
                double[] rgb = Portal.doubles(arena, results, "color", 3);
                if (rgb == null) {
                    throw new IllegalStateException("PickColor returned no color");
                }
                return rgb;
            } catch (RuntimeException e) {
                pickFailure = e.getMessage();
                System.out.println("PickColor does not work, the pixels come from screenshots: " + e.getMessage());
                return null;
            } finally {
                if (!Double.isNaN(backX)) {
                    MemorySegment device = absolute;
                    if (device != null) {
                        Ei.invoke(Ei.POINTER_MOTION_ABSOLUTE, device, backX, backY);
                        frame(device);
                        lastX = backX;
                        lastY = backY;
                    }
                }
            }
        });
    }

    // On the thread of libei, inside a call
    private void clickAt(double x, double y) throws InterruptedException {
        MemorySegment pointer = require(() -> absolute, "absolute pointer");
        Ei.invoke(Ei.POINTER_MOTION_ABSOLUTE, pointer, x, y);
        frame(pointer);
        Thread.sleep(POINTER_FOCUS_DELAY);
        MemorySegment button = require(() -> buttons, "button");
        Ei.invoke(Ei.BUTTON_BUTTON, button, BTN_LEFT, true);
        frame(button);
        Ei.invoke(Ei.BUTTON_BUTTON, button, BTN_LEFT, false);
        frame(button);
    }

    /**
     * A screenshot of all the screens, through the portal, as libei has no way
     * to read the screen. The portal saves it in a file, which goes away here.
     */
    static BufferedImage screenshot() {
        return call(() -> {
            Path file = Portal.screenshot();
            try {
                BufferedImage image = ImageIO.read(file.toFile());
                if (image == null) {
                    throw new IllegalStateException("Cannot read the screenshot " + file);
                }
                return image;
            } finally {
                Files.deleteIfExists(file);
            }
        });
    }

    private static <T> T call(Callable<T> action) {
        Future<T> result = THREAD.submit(action);
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    // A device that the compositor paused for a moment comes back: it is awaited
    private MemorySegment require(Supplier<MemorySegment> device, String name) throws InterruptedException {
        long deadline = System.nanoTime() + SYNC_TIMEOUT;
        while (device.get() == null) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("The compositor offers no " + name + " to libei");
            }
            Thread.sleep(5);
            dispatch();
        }
        return device.get();
    }

    // Sends the frame, and waits for the compositor to have processed it
    private void frame(MemorySegment device) throws InterruptedException {
        Ei.invoke(Ei.DEVICE_FRAME, device, (long) Ei.invoke(Ei.NOW, ei));
        if (Ei.NEW_PING == null || Ei.PING == null || Ei.PING_UNREF == null || Ei.EVENT_PONG_GET_PING == null) {
            // libei before 1.3, as in Ubuntu 24.04, has no ping: the frame is
            // flushed by a dispatch, and given time to arrive
            dispatch();
            Thread.sleep(UNSYNCED_FRAME_MS);
            dispatch();
            return;
        }
        MemorySegment ping = (MemorySegment) Ei.invoke(Ei.NEW_PING, ei);
        pendingPing = ping;
        try {
            Ei.invoke(Ei.PING, ping);
            long deadline = System.nanoTime() + SYNC_TIMEOUT;
            while (pendingPing != null) {
                dispatch();
                if (pendingPing != null && System.nanoTime() > deadline) {
                    throw new IllegalStateException("The compositor did not process the input of libei");
                }
                Thread.sleep(1);
            }
        } finally {
            pendingPing = null;
            Ei.invoke(Ei.PING_UNREF, ping);
        }
    }

    private void awaitDevices() throws InterruptedException {
        long deadline = System.nanoTime() + CONNECT_TIMEOUT;
        while (absolute == null || buttons == null || scroll == null || keyboard == null) {
            dispatch();
            if (System.nanoTime() > deadline) {
                throw new UnsupportedOperationException("The compositor did not offer the devices to libei: absolute="
                        + (absolute != null) + " buttons=" + (buttons != null) + " scroll=" + (scroll != null)
                        + " keyboard=" + (keyboard != null));
            }
            Thread.sleep(5);
        }
    }

    private void dispatch() {
        if (disconnected) {
            throw new IllegalStateException("The compositor disconnected libei");
        }
        Ei.invoke(Ei.DISPATCH, ei);
        for (MemorySegment event = (MemorySegment) Ei.invoke(Ei.GET_EVENT, ei);
                !event.equals(MemorySegment.NULL); event = (MemorySegment) Ei.invoke(Ei.GET_EVENT, ei)) {
            try {
                handle(event);
            } finally {
                Ei.invoke(Ei.EVENT_UNREF, event);
            }
        }
    }

    private void handle(MemorySegment event) {
        switch ((int) Ei.invoke(Ei.EVENT_GET_TYPE, event)) {
            case EVENT_SEAT_ADDED -> {
                seat = (MemorySegment) Ei.invoke(Ei.EVENT_GET_SEAT, event);
                bindCapabilities(CAP_POINTER_ABSOLUTE, CAP_BUTTON, CAP_SCROLL, CAP_KEYBOARD);
            }
            case EVENT_DEVICE_RESUMED -> resumed((MemorySegment) Ei.invoke(Ei.EVENT_GET_DEVICE, event));
            case EVENT_DEVICE_PAUSED, EVENT_DEVICE_REMOVED ->
                removed((MemorySegment) Ei.invoke(Ei.EVENT_GET_DEVICE, event));
            case EVENT_DISCONNECT -> disconnected = true;
            case EVENT_PONG -> {
                if (((MemorySegment) Ei.invoke(Ei.EVENT_PONG_GET_PING, event)).equals(pendingPing)) {
                    pendingPing = null;
                }
            }
            default -> { }
        }
    }

    private void bindCapabilities(int... capabilities) {
        for (int capability : capabilities) {
            Ei.invoke(Ei.SEAT_BIND_CAPABILITIES, seat, capability, MemorySegment.NULL);
        }
    }

    private static boolean has(MemorySegment device, int capability) {
        return (boolean) Ei.invoke(Ei.DEVICE_HAS_CAPABILITY, device, capability);
    }

    private void resumed(MemorySegment device) {
        boolean used = false;
        if (absolute == null && has(device, CAP_POINTER_ABSOLUTE)) {
            absolute = device;
            used = true;
        }
        if (buttons == null && has(device, CAP_BUTTON)) {
            buttons = device;
            used = true;
        }
        if (scroll == null && has(device, CAP_SCROLL)) {
            scroll = device;
            used = true;
        }
        if (keyboard == null && has(device, CAP_KEYBOARD)) {
            keyboard = device;
            used = true;
        }
        if (used) {
            Ei.invoke(Ei.DEVICE_REF, device);
            Ei.invoke(Ei.DEVICE_START_EMULATING, device, ++sequence);
        }
    }

    private void removed(MemorySegment device) {
        boolean used = false;
        if (device.equals(absolute)) {
            absolute = null;
            used = true;
        }
        if (device.equals(buttons)) {
            buttons = null;
            used = true;
        }
        if (device.equals(scroll)) {
            scroll = null;
            used = true;
        }
        if (device.equals(keyboard)) {
            keyboard = null;
            used = true;
        }
        if (used) {
            Ei.invoke(Ei.DEVICE_UNREF, device);
        }
    }

    private static MethodHandle handle(SymbolLookup lib, String name, FunctionDescriptor descriptor,
                                       Linker.Option... options) {
        return lib == null ? null : lib.find(name)
                .map(symbol -> Linker.nativeLinker().downcallHandle(symbol, descriptor, options))
                .orElse(null);
    }

    private static SymbolLookup library(String name) {
        try {
            return SymbolLookup.libraryLookup(name, Arena.global());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The sender side of libei.
     */
    private static final class Ei {
        private static final ValueLayout P = ValueLayout.ADDRESS;
        private static final ValueLayout I = ValueLayout.JAVA_INT;
        private static final ValueLayout Z = ValueLayout.JAVA_BOOLEAN;
        private static final ValueLayout J = ValueLayout.JAVA_LONG;
        private static final ValueLayout D = ValueLayout.JAVA_DOUBLE;

        static final SymbolLookup LIB = library("libei.so.1");
        static final MethodHandle NEW_SENDER = ei("ei_new_sender", FunctionDescriptor.of(P, P));
        static final MethodHandle CONFIGURE_NAME = ei("ei_configure_name", FunctionDescriptor.ofVoid(P, P));
        static final MethodHandle SETUP_BACKEND_FD = ei("ei_setup_backend_fd", FunctionDescriptor.of(I, P, I));
        static final MethodHandle DISPATCH = ei("ei_dispatch", FunctionDescriptor.ofVoid(P));
        static final MethodHandle GET_EVENT = ei("ei_get_event", FunctionDescriptor.of(P, P));
        static final MethodHandle NOW = ei("ei_now", FunctionDescriptor.of(J, P));
        static final MethodHandle EVENT_GET_TYPE = ei("ei_event_get_type", FunctionDescriptor.of(I, P));
        static final MethodHandle EVENT_GET_SEAT = ei("ei_event_get_seat", FunctionDescriptor.of(P, P));
        static final MethodHandle EVENT_GET_DEVICE = ei("ei_event_get_device", FunctionDescriptor.of(P, P));
        static final MethodHandle EVENT_UNREF = ei("ei_event_unref", FunctionDescriptor.of(P, P));
        static final MethodHandle NEW_PING = ei("ei_new_ping", FunctionDescriptor.of(P, P));
        static final MethodHandle PING = ei("ei_ping", FunctionDescriptor.ofVoid(P));
        static final MethodHandle PING_UNREF = ei("ei_ping_unref", FunctionDescriptor.of(P, P));
        static final MethodHandle EVENT_PONG_GET_PING = ei("ei_event_pong_get_ping", FunctionDescriptor.of(P, P));
        static final MethodHandle SEAT_BIND_CAPABILITIES = ei("ei_seat_bind_capabilities",
                FunctionDescriptor.ofVoid(P, I, P), Linker.Option.firstVariadicArg(1));
        static final MethodHandle DEVICE_REF = ei("ei_device_ref", FunctionDescriptor.of(P, P));
        static final MethodHandle DEVICE_UNREF = ei("ei_device_unref", FunctionDescriptor.of(P, P));
        static final MethodHandle DEVICE_HAS_CAPABILITY = ei("ei_device_has_capability",
                FunctionDescriptor.of(Z, P, I));
        static final MethodHandle DEVICE_START_EMULATING = ei("ei_device_start_emulating",
                FunctionDescriptor.ofVoid(P, I));
        static final MethodHandle DEVICE_FRAME = ei("ei_device_frame", FunctionDescriptor.ofVoid(P, J));
        static final MethodHandle POINTER_MOTION_ABSOLUTE = ei("ei_device_pointer_motion_absolute",
                FunctionDescriptor.ofVoid(P, D, D));
        static final MethodHandle BUTTON_BUTTON = ei("ei_device_button_button", FunctionDescriptor.ofVoid(P, I, Z));
        static final MethodHandle SCROLL_DISCRETE = ei("ei_device_scroll_discrete", FunctionDescriptor.ofVoid(P, I, I));
        static final MethodHandle KEYBOARD_KEY = ei("ei_device_keyboard_key", FunctionDescriptor.ofVoid(P, I, Z));

        private static MethodHandle ei(String name, FunctionDescriptor descriptor, Linker.Option... options) {
            return handle(LIB, name, descriptor, options);
        }

        static Object invoke(MethodHandle handle, Object... args) {
            try {
                return handle.invokeWithArguments(args);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }

    /**
     * The RemoteDesktop interface of the portal, through GIO: a persistent
     * session with a keyboard and a pointer, whose restore
     * token asks for the permission once. Its file goes with the tokens of the
     * robot, which jfx-tester drops when the kind of session changes.
     */
    private static final class Portal {
        private static final ValueLayout P = ValueLayout.ADDRESS;
        private static final ValueLayout I = ValueLayout.JAVA_INT;
        private static final SymbolLookup GIO = library("libgio-2.0.so.0");
        private static final SymbolLookup GLIB = library("libglib-2.0.so.0");

        private static final MethodHandle BUS_GET_SYNC = handle(GIO, "g_bus_get_sync", FunctionDescriptor.of(P, I, P, P));
        private static final MethodHandle CALL_SYNC = handle(GIO, "g_dbus_connection_call_sync",
                FunctionDescriptor.of(P, P, P, P, P, P, P, P, I, I, P, P));
        private static final MethodHandle CALL_FD_SYNC = handle(GIO, "g_dbus_connection_call_with_unix_fd_list_sync",
                FunctionDescriptor.of(P, P, P, P, P, P, P, P, I, I, P, P, P, P));
        private static final MethodHandle UNIQUE_NAME = handle(GIO, "g_dbus_connection_get_unique_name",
                FunctionDescriptor.of(P, P));
        private static final MethodHandle SUBSCRIBE = handle(GIO, "g_dbus_connection_signal_subscribe",
                FunctionDescriptor.of(I, P, P, P, P, P, P, I, P, P, P));
        private static final MethodHandle UNSUBSCRIBE = handle(GIO, "g_dbus_connection_signal_unsubscribe",
                FunctionDescriptor.ofVoid(P, I));
        private static final MethodHandle FD_LIST_GET = handle(GIO, "g_unix_fd_list_get", FunctionDescriptor.of(I, P, I, P));
        private static final MethodHandle PARSE = handle(GLIB, "g_variant_parse", FunctionDescriptor.of(P, P, P, P, P, P));
        private static final MethodHandle CHILD = handle(GLIB, "g_variant_get_child_value", FunctionDescriptor.of(P, P, ValueLayout.JAVA_LONG));
        private static final MethodHandle GET_UINT32 = handle(GLIB, "g_variant_get_uint32", FunctionDescriptor.of(I, P));
        private static final MethodHandle GET_BOOLEAN = handle(GLIB, "g_variant_get_boolean", FunctionDescriptor.of(I, P));
        private static final MethodHandle GET_DOUBLE = handle(GLIB, "g_variant_get_double",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, P));
        private static final MethodHandle N_CHILDREN = handle(GLIB, "g_variant_n_children",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, P));
        private static final MethodHandle GET_HANDLE = handle(GLIB, "g_variant_get_handle", FunctionDescriptor.of(I, P));
        private static final MethodHandle GET_VARIANT = handle(GLIB, "g_variant_get_variant", FunctionDescriptor.of(P, P));
        private static final MethodHandle GET_STRING = handle(GLIB, "g_variant_get_string", FunctionDescriptor.of(P, P, P));
        private static final MethodHandle LOOKUP = handle(GLIB, "g_variant_lookup_value", FunctionDescriptor.of(P, P, P, P));
        private static final MethodHandle CONTEXT_NEW = handle(GLIB, "g_main_context_new", FunctionDescriptor.of(P));
        private static final MethodHandle CONTEXT_PUSH = handle(GLIB, "g_main_context_push_thread_default", FunctionDescriptor.ofVoid(P));
        private static final MethodHandle CONTEXT_ITERATION = handle(GLIB, "g_main_context_iteration", FunctionDescriptor.of(I, P, I));

        private static final String PORTAL = "org.freedesktop.portal.Desktop";
        private static final String PORTAL_PATH = "/org/freedesktop/portal/desktop";
        private static final String REMOTE_DESKTOP = "org.freedesktop.portal.RemoteDesktop";
        static final String SCREENSHOT = "org.freedesktop.portal.Screenshot";
        private static final String MUTTER_RD = "org.gnome.Mutter.RemoteDesktop";
        private static final String KWIN = "org.kde.KWin";
        private static final int SESSION_BUS = 2;
        private static final int RESPONSE_SUCCESS = 0;
        // A keyboard and a pointer, kept until they are revoked
        private static final int DEVICES = 1 | 2;
        private static final int PERSIST_PERMANENT = 2;
        private static final long RESPONSE_TIMEOUT = 60_000_000_000L;

        private static MemorySegment context;
        private static MemorySegment bus;
        private static int requests;
        // Called from the native code of GIO, on the thread that iterates the context
        private static boolean responded;
        private static int responseCode;
        private static MemorySegment responseResults;

        static void init(Arena arena) {
            if (GIO == null || GLIB == null) {
                throw new IllegalStateException("GIO is not installed");
            }
            if (bus == null) {
                context = (MemorySegment) Ei.invoke(CONTEXT_NEW);
                Ei.invoke(CONTEXT_PUSH, context);
                MemorySegment error = error(arena);
                bus = (MemorySegment) Ei.invoke(BUS_GET_SYNC, SESSION_BUS, MemorySegment.NULL, error);
                check(error, "the session bus");
            }
        }

        static Path screenshot() throws InterruptedException {
            try (Arena arena = Arena.ofConfined()) {
                init(arena);
                MemorySegment results = request(arena, SCREENSHOT, "Screenshot", "('', %s)",
                        "'interactive': <false>, 'modal': <false>");
                String uri = string(arena, results, "uri");
                if (uri == null) {
                    throw new IllegalStateException("The Screenshot portal returned no file");
                }
                return Path.of(URI.create(uri));
            }
        }

        /**
         * The connection to the EIS of the compositor: straight from Mutter or
         * KWin, whose remote desktop D-Bus interfaces create a keyboard and a
         * pointer for any client of the session bus, the private one of the
         * nested desktop, without a dialog; from the portal otherwise, or with
         * -Dtests.eisPortal=true.
         */
        static int connectToEis() throws InterruptedException {
            try (Arena arena = Arena.ofConfined()) {
                init(arena);
                if (!Boolean.getBoolean("tests.eisPortal")) {
                    if (hasOwner(arena, MUTTER_RD)) {
                        return mutterEis(arena);
                    }
                    if (hasOwner(arena, KWIN)) {
                        try {
                            return kwinEis(arena);
                        } catch (IllegalStateException e) {
                            // KWin before 6.1 has no EIS: the portal has none either
                        }
                    }
                }
                if (version(arena, REMOTE_DESKTOP) < 2) {
                    throw new UnsupportedOperationException("The RemoteDesktop portal has no ConnectToEIS (version 2)");
                }
                String session = start(arena);
                return callForFd(arena, PORTAL, PORTAL_PATH, REMOTE_DESKTOP, "ConnectToEIS",
                        "(objectpath '" + session + "', @a{sv} {})");
            }
        }

        // A remote desktop session of Mutter, which lasts as long as the bus
        // connection that created it, and takes its calls from that one only
        private static int mutterEis(Arena arena) {
            MemorySegment created = call(arena, MUTTER_RD, "/org/gnome/Mutter/RemoteDesktop", MUTTER_RD,
                    "CreateSession", null);
            String session = ((MemorySegment) Ei.invoke(GET_STRING, child(created, 0), MemorySegment.NULL))
                    .reinterpret(Long.MAX_VALUE).getString(0);
            call(arena, MUTTER_RD, session, MUTTER_RD + ".Session", "Start", null);
            return callForFd(arena, MUTTER_RD, session, MUTTER_RD + ".Session", "ConnectToEIS",
                    "({'device-types': <uint32 " + DEVICES + ">},)");
        }

        // The EIS of KWin for its portal, whose context it closes when the bus
        // connection that asked for it goes; the cookie that comes with the
        // descriptor only names the context to close
        private static int kwinEis(Arena arena) {
            return callForFd(arena, KWIN, "/org/kde/KWin/EIS/RemoteDesktop", "org.kde.KWin.EIS.RemoteDesktop",
                    "connectToEIS", "(" + DEVICES + ",)");
        }

        private static boolean hasOwner(Arena arena, String name) {
            MemorySegment reply = call(arena, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                    "NameHasOwner", "('" + name + "',)");
            return (int) Ei.invoke(GET_BOOLEAN, child(reply, 0)) != 0;
        }

        private static MemorySegment call(Arena arena, String dest, String path, String iface, String method,
                                          String args) {
            MemorySegment error = error(arena);
            MemorySegment reply = (MemorySegment) Ei.invoke(CALL_SYNC, bus, arena.allocateFrom(dest),
                    arena.allocateFrom(path), arena.allocateFrom(iface), arena.allocateFrom(method),
                    args == null ? MemorySegment.NULL : parse(arena, args), MemorySegment.NULL, 0, -1,
                    MemorySegment.NULL, error);
            check(error, method);
            return reply;
        }

        // Calls a method whose first answer is a file descriptor
        private static int callForFd(Arena arena, String dest, String path, String iface, String method, String args) {
            MemorySegment fds = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment error = error(arena);
            MemorySegment reply = (MemorySegment) Ei.invoke(CALL_FD_SYNC, bus, arena.allocateFrom(dest),
                    arena.allocateFrom(path), arena.allocateFrom(iface), arena.allocateFrom(method),
                    parse(arena, args), MemorySegment.NULL, 0, -1, MemorySegment.NULL, fds, MemorySegment.NULL, error);
            check(error, method);
            int index = (int) Ei.invoke(GET_HANDLE, child(reply, 0));
            error = error(arena);
            int fd = (int) Ei.invoke(FD_LIST_GET, fds.get(ValueLayout.ADDRESS, 0), index, error);
            check(error, "the file descriptor of " + method);
            return fd;
        }

        static int version(Arena arena, String iface) {
            MemorySegment error = error(arena);
            MemorySegment reply = (MemorySegment) Ei.invoke(CALL_SYNC, bus, arena.allocateFrom(PORTAL),
                    arena.allocateFrom(PORTAL_PATH), arena.allocateFrom("org.freedesktop.DBus.Properties"),
                    arena.allocateFrom("Get"), parse(arena, "('" + iface + "', 'version')"),
                    MemorySegment.NULL, 0, -1, MemorySegment.NULL, error);
            check(error, "the version of " + iface);
            return (int) Ei.invoke(GET_UINT32, Ei.invoke(GET_VARIANT, child(reply, 0)));
        }

        private static String start(Arena arena) throws InterruptedException {
            long pid = ProcessHandle.current().pid();
            MemorySegment created = request(arena, REMOTE_DESKTOP, "CreateSession", "(%s,)", "'session_handle_token': <'jfx_lab_" + pid + "'>");
            String session = string(arena, created, "session_handle");
            if (session == null) {
                throw new IllegalStateException("CreateSession returned no session");
            }
            String restore = readToken();
            request(arena, REMOTE_DESKTOP, "SelectDevices", "(objectpath '" + session + "', %s)", "'types': <uint32 " + DEVICES
                    + ">, 'persist_mode': <uint32 " + PERSIST_PERMANENT + ">"
                    + (restore == null ? "" : ", 'restore_token': <'" + restore + "'>"));
            MemorySegment started = request(arena, REMOTE_DESKTOP, "Start", "(objectpath '" + session + "', '', %s)", null);
            String token = string(arena, started, "restore_token");
            if (token != null) {
                writeToken(token);
            }
            return session;
        }

        // What a request does while it waits, after the call, with the milliseconds since
        interface Waiting {
            void tick(long elapsedMs) throws InterruptedException;
        }

        // Calls a method that answers with a Request, and waits for its Response
        private static MemorySegment request(Arena arena, String iface, String method, String format, String options)
                throws InterruptedException {
            return request(arena, iface, method, format, options, RESPONSE_TIMEOUT / 1_000_000, null);
        }

        static MemorySegment request(Arena arena, String iface, String method, String format, String options,
                                     long timeoutMs, Waiting waiting) throws InterruptedException {
            String token = "jfx_lab_" + ProcessHandle.current().pid() + "_" + ++requests;
            String sender = ((MemorySegment) Ei.invoke(UNIQUE_NAME, bus)).reinterpret(Long.MAX_VALUE).getString(0)
                    .substring(1).replace('.', '_');
            String path = PORTAL_PATH + "/request/" + sender + "/" + token;
            String dict = "{'handle_token': <'" + token + "'>" + (options == null ? "" : ", " + options) + "}";
            responded = false;
            MemorySegment callback;
            try {
                callback = Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(Portal.class, "response",
                        MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)),
                        FunctionDescriptor.ofVoid(P, P, P, P, P, P, P), Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            int subscription = (int) Ei.invoke(SUBSCRIBE, bus, arena.allocateFrom(PORTAL),
                    arena.allocateFrom("org.freedesktop.portal.Request"), arena.allocateFrom("Response"),
                    arena.allocateFrom(path), MemorySegment.NULL, 0, callback, MemorySegment.NULL, MemorySegment.NULL);
            try {
                MemorySegment error = error(arena);
                Ei.invoke(CALL_SYNC, bus, arena.allocateFrom(PORTAL), arena.allocateFrom(PORTAL_PATH),
                        arena.allocateFrom(iface), arena.allocateFrom(method),
                        parse(arena, format.formatted(dict)), MemorySegment.NULL, 0, -1, MemorySegment.NULL, error);
                check(error, method);
                long called = System.nanoTime();
                long deadline = called + timeoutMs * 1_000_000;
                while (!responded) {
                    Ei.invoke(CONTEXT_ITERATION, context, 0);
                    if (waiting != null && !responded) {
                        waiting.tick((System.nanoTime() - called) / 1_000_000);
                    }
                    if (!responded && System.nanoTime() > deadline) {
                        throw new UnsupportedOperationException("The portal did not answer " + method);
                    }
                    Thread.sleep(5);
                }
            } finally {
                Ei.invoke(UNSUBSCRIBE, bus, subscription);
            }
            if (responseCode != RESPONSE_SUCCESS) {
                throw new UnsupportedOperationException("The portal refused " + method + " (response " + responseCode + ")");
            }
            return responseResults;
        }

        @SuppressWarnings("unused")
        private static void response(MemorySegment connection, MemorySegment sender, MemorySegment path,
                                     MemorySegment iface, MemorySegment signal, MemorySegment parameters,
                                     MemorySegment data) {
            responseCode = (int) Ei.invoke(GET_UINT32, child(parameters, 0));
            responseResults = child(parameters, 1);
            responded = true;
        }

        private static MemorySegment child(MemorySegment variant, long index) {
            return (MemorySegment) Ei.invoke(CHILD, variant, index);
        }

        // A tuple of doubles in a dictionary, such as the (ddd) of a color
        static double[] doubles(Arena arena, MemorySegment dict, String key, int count) {
            MemorySegment value = (MemorySegment) Ei.invoke(LOOKUP, dict, arena.allocateFrom(key), MemorySegment.NULL);
            if (value.equals(MemorySegment.NULL) || (long) Ei.invoke(N_CHILDREN, value) < count) {
                return null;
            }
            double[] result = new double[count];
            for (int i = 0; i < count; i++) {
                result[i] = (double) Ei.invoke(GET_DOUBLE, child(value, i));
            }
            return result;
        }

        private static String string(Arena arena, MemorySegment dict, String key) {
            MemorySegment value = (MemorySegment) Ei.invoke(LOOKUP, dict, arena.allocateFrom(key), MemorySegment.NULL);
            if (value.equals(MemorySegment.NULL)) {
                return null;
            }
            return ((MemorySegment) Ei.invoke(GET_STRING, value, MemorySegment.NULL)).reinterpret(Long.MAX_VALUE).getString(0);
        }

        private static MemorySegment parse(Arena arena, String text) {
            MemorySegment error = error(arena);
            MemorySegment variant = (MemorySegment) Ei.invoke(PARSE, MemorySegment.NULL, arena.allocateFrom(text),
                    MemorySegment.NULL, MemorySegment.NULL, error);
            check(error, "the arguments " + text);
            return variant;
        }

        private static MemorySegment error(Arena arena) {
            return arena.allocate(ValueLayout.ADDRESS);
        }

        // A GError: its domain, its code, and then its message
        private static void check(MemorySegment error, String what) {
            MemorySegment e = error.get(ValueLayout.ADDRESS, 0);
            if (!e.equals(MemorySegment.NULL)) {
                String message = e.reinterpret(16).get(ValueLayout.ADDRESS, 8).reinterpret(Long.MAX_VALUE).getString(0);
                throw new IllegalStateException(what + ": " + message);
            }
        }

        private static Path tokenFile() {
            return Path.of(System.getProperty("user.home"), ".java", "robot", "libei-tokens.properties");
        }

        private static String readToken() {
            try {
                String token = Files.readString(tokenFile()).trim();
                return token.isEmpty() ? null : token;
            } catch (IOException e) {
                return null;
            }
        }

        private static void writeToken(String token) {
            try {
                Files.createDirectories(tokenFile().getParent());
                Files.writeString(tokenFile(), token);
            } catch (IOException e) {
                System.err.println("Cannot keep the restore token of libei: " + e);
            }
        }
    }
}
