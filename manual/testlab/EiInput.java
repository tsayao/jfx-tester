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
 * interface of the portal.
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
        private static final String SCREENSHOT = "org.freedesktop.portal.Screenshot";
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

        private static void init(Arena arena) {
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

        static int connectToEis() throws InterruptedException {
            try (Arena arena = Arena.ofConfined()) {
                init(arena);
                MemorySegment error;
                if (portalVersion(arena) < 2) {
                    throw new UnsupportedOperationException("The RemoteDesktop portal has no ConnectToEIS (version 2)");
                }
                String session = start(arena);
                MemorySegment fds = arena.allocate(ValueLayout.ADDRESS);
                error = error(arena);
                MemorySegment reply = (MemorySegment) Ei.invoke(CALL_FD_SYNC, bus, arena.allocateFrom(PORTAL),
                        arena.allocateFrom(PORTAL_PATH), arena.allocateFrom(REMOTE_DESKTOP),
                        arena.allocateFrom("ConnectToEIS"),
                        parse(arena, "(objectpath '" + session + "', @a{sv} {})"), MemorySegment.NULL, 0, -1,
                        MemorySegment.NULL, fds, MemorySegment.NULL, error);
                check(error, "ConnectToEIS");
                int index = (int) Ei.invoke(GET_HANDLE, child(reply, 0));
                error = error(arena);
                int fd = (int) Ei.invoke(FD_LIST_GET, fds.get(ValueLayout.ADDRESS, 0), index, error);
                check(error, "the file descriptor of ConnectToEIS");
                return fd;
            }
        }

        private static int portalVersion(Arena arena) {
            MemorySegment error = error(arena);
            MemorySegment reply = (MemorySegment) Ei.invoke(CALL_SYNC, bus, arena.allocateFrom(PORTAL),
                    arena.allocateFrom(PORTAL_PATH), arena.allocateFrom("org.freedesktop.DBus.Properties"),
                    arena.allocateFrom("Get"), parse(arena, "('" + REMOTE_DESKTOP + "', 'version')"),
                    MemorySegment.NULL, 0, -1, MemorySegment.NULL, error);
            check(error, "the version of the RemoteDesktop portal");
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

        // Calls a method that answers with a Request, and waits for its Response
        private static MemorySegment request(Arena arena, String iface, String method, String format, String options)
                throws InterruptedException {
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
                long deadline = System.nanoTime() + RESPONSE_TIMEOUT;
                while (!responded) {
                    Ei.invoke(CONTEXT_ITERATION, context, 0);
                    if (System.nanoTime() > deadline) {
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
