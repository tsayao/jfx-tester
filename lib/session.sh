#
# Copyright (c) 2026, Thiago Sayão. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  The copyright holder designates
# this particular file as subject to the "Classpath" exception as provided
# in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

# Runs the system tests, or a manual test, in a nested GNOME Shell or KWin, or
# in Cinnamon on a nested X server.
# jfx-tester runs it in the toolbox container, on a private D-Bus session
# (dbus-run-session), with the Gradle arguments of the tests, or the arguments
# of the manual test, as its arguments and its settings in:
#
#   JT_DESKTOP     gnome, kde or cinnamon
#   JT_SESSION     the kind of session, wayland or x11
#   JT_XORG        when set, an X11 session on Xorg, also in a window
#   JT_XWAYLAND_SCALE  apps or system, how KWin scales the X11 windows
#   JT_WORKSPACE   the copy of the sources, built for the container
#   JT_LOGS        where the logs and the settings of the session go
#   JT_REPORT      where the HTML report of the tests goes
#   JT_ENV         the name of the environment, such as fedora-44-gnome
#   JT_MONITORS    the monitors, WxH@SCALE separated by commas, side by side
#   JT_HEADLESS    1 for a compositor without a window on the desktop
#   JT_HOME        user.home of the tests and XDG_DATA_HOME of the session,
#                  which keep the tokens and permissions of the portals
#   JT_OWNER       pid of jfx-tester, whose end ends the session
#   JT_MANUAL      the manual test, a .java source or a .jar, if any
#   JT_TIMEOUT     the seconds after which the manual test ends, if any
#   JT_SDK         the JavaFX SDK to run it with, instead of the one of the
#                  workspace
#   JT_JVM_ARGS    options of the JVMs of the tests
#   JT_MUTTER_DEBUG  debug topics of Mutter (MUTTER_DEBUG) for GNOME Shell
#   JT_COMPOSITOR_ENV  VAR=VALUE ..., the variables of the environment of the
#                  nested GNOME Shell or KWin
#
# Exits with the status of Gradle, or of the manual test.

mkdir -p "$JT_LOGS"
RUNTIME_DIR=${XDG_RUNTIME_DIR:-/run/user/$(id -u)}
SOCKET=wayland-jfx-tester-$JT_DESKTOP-$$
HOST_DISPLAY=$WAYLAND_DISPLAY
BUS=$DBUS_SESSION_BUS_ADDRESS
CHILDREN=
RESERVED=

# The settings of the nested desktop, which leave those of the host untouched:
# GSettings in a key file, as a write to the dconf database of the user also
# notifies the dconf clients of the host
export XDG_CONFIG_HOME="$JT_LOGS/config"
rm -rf "${XDG_CONFIG_HOME:?}"
mkdir -p "$XDG_CONFIG_HOME/glib-2.0/settings"
export GSETTINGS_BACKEND=keyfile
# The permission store of the portals, apart from the one of the host
export XDG_DATA_HOME="$JT_HOME/.local/share"
mkdir -p "$XDG_DATA_HOME"

# The robot tries the tokens that it saved, in order, before each action: one
# that no longer gives a stream makes it close the session of the next, which
# works, and that releases the buttons that the session holds, so a drag ends
# as it starts. A token is good for the monitors of the kind of session that
# got it, so a new kind grants them again, and so does more than one token
mkdir -p "$JT_HOME/.java/robot"
kind="${JT_HEADLESS:+headless }$JT_MONITORS"
[ "$(cat "$JT_HOME/.java/robot/session-kind" 2>/dev/null)" = "$kind" ] || rm -f "$JT_HOME"/.java/robot/*-tokens.properties
printf '%s\n' "$kind" > "$JT_HOME/.java/robot/session-kind"
for f in "$JT_HOME"/.java/robot/*-tokens.properties; do
    [ "$(grep -c = "$f" 2>/dev/null)" -gt 1 ] 2>/dev/null && rm -f "$f"
done

# The services that the bus activates, such as the portals, which the
# compositor may start before the tests, run in English, for the names of
# their buttons (approve.py), and Qt makes its accessibility available
# without a screen reader (QT_ACCESSIBILITY for Qt 5, as the portal of Plasma 5)
dbus-update-activation-environment XDG_DATA_HOME XDG_CONFIG_HOME GSETTINGS_BACKEND \
    LANGUAGE=C LC_ALL=C.UTF-8 QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1 QT_ACCESSIBILITY=1 > /dev/null 2>&1

# Everything that runs on the private bus but this shell and the one given:
# the compositor, Gradle, the test JVMs and the services the bus activated
session_pids() {
    # The subshell of the command substitution, which runs this
    self=$(exec sh -c 'echo $PPID')
    for p in /proc/[0-9]*; do
        pid=${p#/proc/}
        [ "$pid" = $$ ] || [ "$pid" = "$1" ] || [ "$pid" = "$self" ] && continue
        if grep -qzx -e "DBUS_SESSION_BUS_ADDRESS=$BUS" -F "$p/environ" 2>/dev/null; then
            echo "$pid"
        fi
    done
}

# Ends them, but the one given. A compositor whose Xwayland crashed does not
# end on TERM: what is left gets KILL
kill_session() {
    pids=$(session_pids "$1")
    [ -n "$pids" ] || return 0
    kill $pids 2>/dev/null
    for i in $(seq 1 15); do
        pids=$(session_pids "$1")
        [ -n "$pids" ] || return 0
        sleep 0.2
    done
    kill -KILL $pids 2>/dev/null
}

# Compositors and X servers take the first free X display, which is always the
# same one: they get a number that depends on the session, by holding the locks
# of the displays below it until they have started. Mutter gives up after 50
# locks. A lock whose process is gone would not hold a display: it is taken over
reserve_displays() {
    RESERVED=
    i=0
    while [ "$i" -lt $(($$ % 35 + 10)) ]; do
        lock=/tmp/.X$i-lock
        if [ -e "$lock" ] && ! kill -0 "$(tr -d ' ' < "$lock")" 2>/dev/null; then
            rm -f "$lock" 2>/dev/null
        fi
        if (set -C; printf '%10d\n' $$ > "$lock") 2>/dev/null; then
            RESERVED="$RESERVED $lock"
        fi
        i=$((i + 1))
    done
}

release_displays() {
    [ -n "$RESERVED" ] && rm -f $RESERVED
    RESERVED=
}

cleanup() {
    release_displays
    kill $CHILDREN 2>/dev/null
    rm -f "$RUNTIME_DIR/$SOCKET" "$RUNTIME_DIR/$SOCKET.lock" "$RUNTIME_DIR/jfx-tester-vnc-$$" \
        "$RUNTIME_DIR/at-spi/bus_$ATSPI_DISPLAY"
    kill_session
    kill -KILL $CHILDREN 2>/dev/null
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# A signal to jfx-tester does not reach the container: the session ends with
# it. A signal to this shell would wait for Gradle, so the processes of the
# session end first
if [ -n "$JT_OWNER" ]; then
    (while kill -0 "$JT_OWNER" 2>/dev/null; do sleep 1; done
     kill_session "$(exec sh -c 'echo $PPID')"
     kill -TERM $$) > /dev/null 2>&1 &
    CHILDREN="$CHILDREN $!"
fi

fail() {
    echo "$*" >&2
    exit 1
}

wait_for_socket() {
    for i in $(seq 1 150); do
        [ -S "$RUNTIME_DIR/$SOCKET" ] && return
        sleep 0.2
    done
    fail "The nested compositor did not start, see $JT_LOGS"
}

shell_eval() {
    gdbus call --session --dest org.gnome.Shell --object-path /org/gnome/Shell \
        --method org.gnome.Shell.Eval "$1" 2>/dev/null
}

# Whether a name has an owner on the bus
has_name() {
    gdbus call --session --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus \
        --method org.freedesktop.DBus.NameHasOwner "$1" 2>/dev/null | grep -q true
}

shell_getenv() {
    shell_eval "imports.gi.GLib.getenv('$1') ?? ''" | sed -n "s/^(true, '\"\(.*\)\"')$/\1/p"
}

# The monitors of JT_MONITORS, and their sizes and scales
monitors() {
    printf '%s' "$JT_MONITORS" | tr ',' ' '
}

monitor_count() {
    set -- $(monitors)
    echo $#
}

first_monitor() {
    set -- $(monitors)
    echo "$1"
}

# Whether all the monitors have the same size (%x) or scale (%@)
all_same() {
    first=
    for m in $(monitors); do
        case "$1" in
            size) v=${m%@*} ;;
            scale) v=${m#*@} ;;
        esac
        [ -z "$first" ] && first=$v
        [ "$v" = "$first" ] || return 1
    done
}

FIRST=$(first_monitor)
FIRST_SIZE=${FIRST%@*}
FIRST_SCALE=${FIRST#*@}

# Places the monitors of the nested shell side by side, at their scales, the
# nearest that their modes support, through the display configuration of
# Mutter
configure_gnome_monitors() {
    JT_MONITORS=$JT_MONITORS python3 - <<'PY' > "$JT_LOGS/monitors.log" 2>&1 \
        || echo "The monitors were not configured, see $JT_LOGS/monitors.log" >&2
import os
from gi.repository import Gio, GLib

specs = [m.split('@') for m in os.environ['JT_MONITORS'].split(',')]
wanted = [float(scale) for _, scale in specs]
sizes = [size for size, _ in specs]
bus = Gio.bus_get_sync(Gio.BusType.SESSION)
def call(method, args, reply):
    return bus.call_sync('org.gnome.Mutter.DisplayConfig', '/org/gnome/Mutter/DisplayConfig',
                         'org.gnome.Mutter.DisplayConfig', method, args, GLib.VariantType(reply),
                         Gio.DBusCallFlags.NONE, -1, None).unpack()
serial, monitors, logical, props = call('GetCurrentState', None,
                                        '(ua((ssss)a(siiddada{sv})a{sv})a(iiduba(ssss)a{sv})a{sv})')
# The order of the connectors is the order of the virtual monitors
monitors = sorted(monitors, key=lambda m: m[0][0])
config = []
x = 0
for i, (ids, modes, mprops) in enumerate(monitors):
    # The nested shell of GNOME before 49 offers all the sizes to all of them
    mode = next((m for m in modes if i < len(sizes) and f'{m[1]}x{m[2]}' == sizes[i]), None) \
        or next((m for m in modes if m[6].get('is-current')), modes[0])
    scale = wanted[i] if i < len(wanted) else 1.0
    scale = min(mode[5], key=lambda s: abs(s - scale)) if mode[5] else scale
    config.append((x, 0, scale, 0, i == 0, [(ids[0], mode[0], {})]))
    print(ids[0], mode[0], 'scale', scale, 'at', x)
    x += int(round(mode[1] / scale))
call('ApplyMonitorsConfig', GLib.Variant('(uua(iiduba(ssa{sv}))a{sv})', (serial, 1, config, {})), '()')
PY
}

# GNOME Shell on a nested X server, as the X11 session of GNOME before 49
start_gnome_x11() {
    x11_scale
    if [ -n "$X11_INT_SCALE" ]; then
        cat >> "$XDG_CONFIG_HOME/glib-2.0/settings/keyfile" <<EOF

[org/gnome/desktop/interface]
scaling-factor=uint32 $X11_INT_SCALE
EOF
    fi
    start_x11_server GNOME
    (exec gnome-shell --x11 --replace --sm-disable --unsafe-mode) > "$JT_LOGS/shell.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    for i in $(seq 1 150); do
        shell_eval "Main.layoutManager._startingUp" | grep -q "'false'" && break
        sleep 0.2
    done
    has_name org.gnome.Shell || fail "GNOME Shell did not start, see $JT_LOGS/shell.log"
    shell_eval "Main.overview.hide()" > /dev/null
    [ -n "$XRANDR_ARGS" ] && configure_xrandr
    sleep 1
}

# The variables of --compositor-env, for the compositor and what it starts
compositor_env() {
    for v in $JT_COMPOSITOR_ENV; do
        export "$v"
    done
}

start_gnome() {
    # The debug topics of Mutter, which it logs to shell.log
    if [ -n "$JT_MUTTER_DEBUG" ]; then
        export MUTTER_DEBUG="$JT_MUTTER_DEBUG"
    fi
    compositor_env
    # GNOME Shell before 49 starts the services of X11 through systemd when the
    # process is in a unit, as it is on the host, and gives up on X11 without
    # them: on this bus, nothing answers but this
    if [ "$(gnome-shell --version | sed 's/[^0-9]*\([0-9]*\).*/\1/')" -lt 49 ]; then
        (exec python3 -u "$(dirname "$0")/fake-systemd.py") > "$JT_LOGS/fake-systemd.log" 2>&1 &
        CHILDREN="$CHILDREN $!"
        sleep 0.5
    fi
    cat > "$XDG_CONFIG_HOME/glib-2.0/settings/keyfile" <<EOF
[org/gnome/shell]
welcome-dialog-last-shown-version='999'

[org/gnome/desktop/interface]
enable-animations=false
enable-hot-corners=false

[org/gnome/desktop/session]
idle-delay=uint32 0

[org/gnome/desktop/notifications]
show-banners=false

[org/gnome/desktop/screensaver]
idle-activation-enabled=false
lock-enabled=false

[org/gnome/mutter]
edge-tiling=false
experimental-features=['scale-monitor-framebuffer']
EOF
    if [ "$JT_SESSION" = x11 ]; then
        start_gnome_x11
        return
    fi
    help=$(gnome-shell --help-all 2>&1)
    has() {
        printf '%s\n' "$help" | grep -q -e "--$1 "
    }
    devkit=
    for d in /usr/libexec/mutter-devkit /usr/lib/mutter-devkit /usr/lib/mutter/mutter-devkit; do
        [ -x "$d" ] && devkit=$d
    done
    unset_display=1
    configure=1
    if [ -z "$JT_HEADLESS" ] && has devkit && [ -z "$devkit" ]; then
        echo "The viewer of the nested shell (mutter-devkit) is not installed: running headless" >&2
        JT_HEADLESS=1
    fi
    headless() {
        set -- --headless
        for m in $(monitors); do
            set -- "$@" --virtual-monitor "${m%@*}"
        done
        HEADLESS_ARGS="$*"
    }
    if [ -n "$JT_HEADLESS" ] || { ! has devkit && ! has nested; }; then
        # No window on the desktop, whose focus would deactivate the nested
        # windows: a virtual monitor for each
        headless
        set -- $HEADLESS_ARGS
    elif has devkit; then
        # GNOME 49 and later: a headless shell, shown by the devkit viewer. The
        # viewer takes the monitors from GNOME 51 on; before, it has one of a
        # size of its own, and a --virtual-monitor would be a second monitor,
        # which it does not show
        if has devkit-args; then
            args=
            for m in $(monitors); do
                if [ -z "$args" ]; then
                    args="--monitor-size=${m%@*}"
                else
                    args="$args --add-monitor=${m%@*}"
                fi
            done
            set -- --devkit --devkit-args="$args"
        else
            [ "$(monitor_count)" -gt 1 ] && echo "The viewer of GNOME 49 and 50 shows one monitor, of its" \
                "own size: use --headless for $JT_MONITORS" >&2
            set -- --devkit
        fi
    else
        # Before GNOME 49: a window of the host, the client of its compositor,
        # for each monitor. All of them get all the sizes, as modes, and then
        # the size that they should have, unless they all have the same
        scales=
        sizes=
        for m in $(monitors); do
            scales="$scales${scales:+,}${m#*@}"
            case ":$sizes:" in
                *":${m%@*}:"*) ;;
                *) sizes="$sizes${sizes:+:}${m%@*}" ;;
            esac
        done
        export MUTTER_DEBUG_DUMMY_MODE_SPECS=$sizes MUTTER_DEBUG_NUM_DUMMY_MONITORS=$(monitor_count) \
            MUTTER_DEBUG_DUMMY_MONITOR_SCALES=$scales
        set -- --nested
        unset_display=
        all_same size && configure=
    fi
    (if [ -n "$unset_display" ]; then unset WAYLAND_DISPLAY DISPLAY; fi
     exec gnome-shell "$@" --unsafe-mode --wayland --wayland-display="$SOCKET") > "$JT_LOGS/shell.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    wait_for_socket
    for i in $(seq 1 150); do
        shell_eval "Main.layoutManager._startingUp" | grep -q "'false'" && break
        sleep 0.2
    done
    shell_eval "Main.overview.hide()" > /dev/null
    # A keyboard and a pointer on the seat, which has none, before Xwayland starts:
    # without them, the first key of the robot of each test is lost
    (exec python3 -u "$(dirname "$0")/seat-devices.py") > "$JT_LOGS/seat-devices.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    [ -n "$configure" ] && configure_gnome_monitors
    sleep 1

    export XDG_CURRENT_DESKTOP=GNOME
    # The Xwayland of the shell, started when the first X client connects
    DISPLAY=$(shell_getenv DISPLAY)
    XAUTHORITY=$(shell_getenv XAUTHORITY)
}

# The outputs of KWin, in the order of their names (Virtual-0, Virtual-1, or
# WL-1, WL-2 in a window), get the scales of the monitors, side by side
configure_kde_outputs() {
    doctor() {
        WAYLAND_DISPLAY=$SOCKET QT_QPA_PLATFORM=wayland kscreen-doctor "$@"
    }
    names=$(doctor -o 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | awk '/^Output:/ { print $3 }' | sort -V)
    set --
    x=0
    for m in $(monitors); do
        name=$(printf '%s\n' $names | head -n 1)
        names=$(printf '%s\n' $names | tail -n +2)
        [ -n "$name" ] || break
        scale=${m#*@}
        set -- "$@" "output.$name.scale.$scale" "output.$name.position.$x,0"
        x=$(awk -v w="${FIRST_SIZE%x*}" -v s="$scale" -v x="$x" 'BEGIN { printf "%d", x + w / s }')
    done
    echo "kscreen-doctor $*" > "$JT_LOGS/monitors.log"
    doctor "$@" >> "$JT_LOGS/monitors.log" 2>&1
    doctor -o 2>&1 | sed 's/\x1b\[[0-9;]*m//g' >> "$JT_LOGS/monitors.log"
}

# The settings of Plasma, and the session of Plasma (plasma_session), as
# startplasma-wayland and startplasma-x11 start it, without systemd
kde_settings() {
    # No animations, which the tests would wait for
    cat > "$XDG_CONFIG_HOME/kdeglobals" <<EOF
[KDE]
AnimationDurationFactor=0
EOF
    # The scaling of the X11 windows, of Legacy Applications (X11) in the
    # settings of the displays: the X11 windows scale themselves to the largest
    # scale of the outputs (Xft.dpi), or draw at 1 and KWin scales them
    xwayland_clients_scale=true
    [ "$JT_XWAYLAND_SCALE" = system ] && xwayland_clients_scale=false
    cat >> "$XDG_CONFIG_HOME/kdeglobals" <<EOF

[KScreen]
XwaylandClientsScale=$xwayland_clients_scale
EOF
    # The windows of the tests take the focus when they show
    cat > "$XDG_CONFIG_HOME/kwinrc" <<EOF
[Windows]
FocusStealingPreventionLevel=0
EOF
    # No splash, which stays over the desktop of a nested session, no screen
    # lock, no indexing of the files, and no systemd, which the container does
    # not have
    cat > "$XDG_CONFIG_HOME/ksplashrc" <<EOF
[KSplash]
Engine=none
Theme=None
EOF
    cat > "$XDG_CONFIG_HOME/kscreenlockerrc" <<EOF
[Daemon]
Autolock=false
LockOnResume=false
EOF
    cat > "$XDG_CONFIG_HOME/baloofilerc" <<EOF
[Basic Settings]
Indexing-Enabled=false
EOF
    cat > "$XDG_CONFIG_HOME/startkderc" <<EOF
[General]
systemdBoot=false
EOF
    plasma_session=
    for p in /usr/bin/plasma_session /usr/lib/*/libexec/plasma_session /usr/libexec/plasma_session; do
        [ -x "$p" ] && plasma_session=$p && break
    done
    [ -n "$plasma_session" ] || fail "plasma_session is not installed: no Plasma desktop"
    version=$(plasmashell --version 2>/dev/null | sed -n 's/^plasmashell \([0-9]*\).*/\1/p')
    export JT_PLASMA_SESSION="$plasma_session" JT_PLASMA_VERSION="${version:-6}"
}

# The shell of Plasma, whose panel takes room of the desktop, before the tests
wait_for_plasma() {
    for i in $(seq 1 150); do
        has_name org.kde.plasmashell && break
        sleep 0.2
    done
    has_name org.kde.plasmashell || fail "Plasma did not start, see $JT_LOGS/plasma.log"
}

# The whole Plasma desktop, on a nested KWin, or on a nested X server
start_kde() {
    compositor_env
    # KWin activates the portals as it starts: with the desktop unknown, they
    # pick the backend of GNOME where both are installed, which then refuses
    # the sessions of the remote desktop
    dbus-update-activation-environment XDG_CURRENT_DESKTOP=KDE > /dev/null 2>&1
    kde_settings
    if [ "$JT_SESSION" = x11 ]; then
        start_kde_x11
    else
        start_kde_wayland
    fi
    sleep 2
}

start_kde_wayland() {
    if [ -n "$JT_HEADLESS" ]; then
        set -- --virtual
    elif [ -n "$HOST_DISPLAY" ]; then
        set -- --wayland-display "$HOST_DISPLAY"
    else
        set -- --x11-display "$DISPLAY"
    fi
    # KWin starts its Xwayland before the session, which hands the displays of
    # KWin and of Xwayland back, and then starts Plasma. KDE_FULL_SESSION and
    # KDE_SESSION_VERSION are for Plasma only: with them, Qt loads the platform
    # theme of Plasma, and KWin crashes on start in the container
    export JT_KWIN_ENV="$JT_LOGS/kwin.env"
    rm -f "$JT_KWIN_ENV"
    cat > "$JT_LOGS/kwin-session" <<'EOF'
#!/bin/sh
printf 'WAYLAND_DISPLAY=%s\nDISPLAY=%s\nXAUTHORITY=%s\n' "$WAYLAND_DISPLAY" "$DISPLAY" "$XAUTHORITY" \
    > "$JT_KWIN_ENV.tmp"
mv "$JT_KWIN_ENV.tmp" "$JT_KWIN_ENV"
export KDE_FULL_SESSION=true KDE_SESSION_VERSION="$JT_PLASMA_VERSION" XDG_SESSION_TYPE=wayland \
    XDG_CURRENT_DESKTOP=KDE DESKTOP_SESSION=plasma
"$JT_PLASMA_SESSION" > "$(dirname "$JT_KWIN_ENV")/plasma.log" 2>&1
exec sleep infinity
EOF
    chmod +x "$JT_LOGS/kwin-session"
    # Through kwin_wayland_wrapper, as plasma_session starts it otherwise, a
    # second KWin. The wrapper names the socket itself, which the session
    # hands back
    # KWin gives all its outputs the size of the first monitor, and takes it
    # in logical pixels, of --scale: the outputs start at the scale 1, and
    # kscreen-doctor sets the scale and the position of each then
    all_same size || echo "KWin gives its outputs the size of the first monitor, $FIRST_SIZE" >&2
    [ "$(monitor_count)" -gt 1 ] && set -- "$@" --output-count "$(monitor_count)"
    # Xwayland 23 crashes in glamor under the KWin of Plasma 5, in a container
    if [ "$(Xwayland -version 2>&1 | sed -n 's/.*Xwayland Version \([0-9]*\)\..*/\1/p')" -lt 24 ] 2>/dev/null; then
        export XWAYLAND_NO_GLAMOR=1
    fi
    # KWin gives its screen cast and screenshots to the executables of the desktop
    # files that list them, which it does not recognize in the container
    (exec env XDG_CURRENT_DESKTOP=KDE QT_FORCE_STDERR_LOGGING=1 KWIN_WAYLAND_NO_PERMISSION_CHECKS=1 \
        KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1 kwin_wayland_wrapper "$@" --xwayland \
        --width "${FIRST_SIZE%x*}" --height "${FIRST_SIZE#*x}" \
        --exit-with-session "$JT_LOGS/kwin-session") > "$JT_LOGS/kwin.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    for i in $(seq 1 150); do
        [ -f "$JT_KWIN_ENV" ] && break
        sleep 0.2
    done
    [ -f "$JT_KWIN_ENV" ] || fail "KWin and its Xwayland did not start, see $JT_LOGS/kwin.log"
    SOCKET=$(sed -n 's/^WAYLAND_DISPLAY=//p' "$JT_KWIN_ENV")
    # KWin activates the portal of KDE as it starts, which then does not find it:
    # the portal starts again, on the socket of KWin, at the next call
    dbus-update-activation-environment WAYLAND_DISPLAY="$SOCKET" > /dev/null 2>&1
    for pid in $(session_pids); do
        [ "$(cat "/proc/$pid/comm" 2>/dev/null)" = xdg-desktop-por ] \
            && grep -qz '^xdg-desktop-portal-kde\|/xdg-desktop-portal-kde$' "/proc/$pid/cmdline" 2>/dev/null \
            && kill "$pid"
    done
    if ! all_same scale || [ "$FIRST_SCALE" != 1 ]; then
        if command -v kscreen-doctor > /dev/null; then
            configure_kde_outputs
        else
            echo "kscreen-doctor is not installed: the outputs have the scale 1" >&2
        fi
    fi
    wait_for_plasma

    export XDG_CURRENT_DESKTOP=KDE
    DISPLAY=$(sed -n 's/^DISPLAY=//p' "$JT_KWIN_ENV")
    XAUTHORITY=$(sed -n 's/^XAUTHORITY=//p' "$JT_KWIN_ENV")
}

# Plasma on a nested X server, whose session starts the X11 KWin
start_kde_x11() {
    x11_scale
    if [ -n "$X11_INT_SCALE" ]; then
        cat >> "$XDG_CONFIG_HOME/kdeglobals" <<EOF

[KScreen]
ScaleFactor=$X11_INT_SCALE
EOF
    fi
    start_x11_server KDE
    (exec env KDE_FULL_SESSION=true KDE_SESSION_VERSION="$JT_PLASMA_VERSION" DESKTOP_SESSION=plasma \
        "$JT_PLASMA_SESSION") > "$JT_LOGS/plasma.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    wait_for_plasma
    [ -n "$XRANDR_ARGS" ] && configure_xrandr
}

# The bus of AT-SPI of the session, before anything activates it. Its launcher
# names its socket in XDG_RUNTIME_DIR, which the host and all the containers
# share, after the X display, which the sessions of GNOME inherit from the host
# (bus_0): the socket of the last session to start would take the place of
# the others, whose clients would then join its bus, or none. With a display of
# its own, which it only takes the name from, each session has a socket of its own
ATSPI_DISPLAY=jfx-tester-$$
start_atspi_bus() {
    for l in /usr/libexec/at-spi-bus-launcher /usr/lib/at-spi-bus-launcher /usr/lib/at-spi2-core/at-spi-bus-launcher; do
        if [ -x "$l" ]; then
            (DISPLAY=:$ATSPI_DISPLAY exec "$l" --launch-immediately) > "$JT_LOGS/atspi-bus.log" 2>&1 &
            CHILDREN="$CHILDREN $!"
            break
        fi
    done
    for i in $(seq 1 50); do
        gdbus call --session --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus \
            --method org.freedesktop.DBus.NameHasOwner org.a11y.Bus 2>/dev/null | grep -q true && break
        sleep 0.1
    done
}

# The bus of AT-SPI activates its registry through systemd, which the private
# session does not have
start_atspi() {
    for r in /usr/libexec/at-spi2-registryd /usr/lib/at-spi2-registryd /usr/lib/at-spi2-core/at-spi2-registryd; do
        if [ -x "$r" ]; then
            ("$r") > "$JT_LOGS/atspi.log" 2>&1 &
            CHILDREN="$CHILDREN $!"
            return
        fi
    done
}

# A mode line for xrandr --newmode, of 60 Hz
mode_line() {
    w=${1%x*}
    h=${1#*x}
    awk -v w="$w" -v h="$h" 'BEGIN {
        ht = w + 160; vt = h + 30
        printf "%.2f %d %d %d %d %d %d %d %d", ht * vt * 60 / 1000000, w, w + 48, w + 80, ht, h, h + 3, h + 8, vt
    }'
}

# Xorg with the dummy driver, without a window: an output of RandR for each
# monitor, side by side
# The size of the screen of an X server whose monitors are side by side
x11_size() {
    width=0
    height=0
    for m in $(monitors); do
        w=${m%@*}
        width=$((width + ${w%x*}))
        [ "${w#*x}" -gt "$height" ] && height=${w#*x}
    done
}

start_xorg() {
    x11_size
    # The framebuffer, in kB, with room to spare
    ram=$((width * height * 4 / 1024 + 65536))
    mkdir -p "$JT_LOGS/xorg.conf.d"
    cat > "$JT_LOGS/xorg.conf" <<EOF
Section "ServerFlags"
    Option "AutoAddDevices" "false"
    Option "AutoEnableDevices" "false"
    Option "DontVTSwitch" "true"
EndSection

Section "Device"
    Identifier "dummy"
    Driver "dummy"
    VideoRam $ram
EndSection

Section "Screen"
    Identifier "screen"
    Device "dummy"
    DefaultDepth 24
    SubSection "Display"
        Depth 24
        Virtual $width $height
    EndSubSection
EndSection
EOF
    xorg=
    for x in /usr/lib/xorg/Xorg /usr/libexec/Xorg /usr/lib/Xorg; do
        [ -x "$x" ] && xorg=$x && break
    done
    [ -n "$xorg" ] || fail "Xorg is not installed"
    (exec "$xorg" -displayfd 5 -noreset -nolisten tcp -config "$JT_LOGS/xorg.conf" \
        -configdir "$JT_LOGS/xorg.conf.d" -logfile "$JT_LOGS/xorg.log" 5> "$JT_LOGS/display") \
        > "$JT_LOGS/xserver.log" 2>&1 &
    XORG_PID=$!
    CHILDREN="$CHILDREN $!"
    wait_for_display
    set --
    i=0
    x=0
    for m in $(monitors); do
        w=${m%@*}
        DISPLAY=$X11_DISPLAY xrandr --newmode "$w" $(mode_line "$w") > /dev/null 2>&1
        DISPLAY=$X11_DISPLAY xrandr --addmode "DUMMY$i" "$w" >> "$JT_LOGS/monitors.log" 2>&1
        set -- "$@" --output "DUMMY$i" --mode "$w" --pos "${x}x0"
        [ $i = 0 ] && set -- "$@" --primary
        x=$((x + ${w%x*}))
        i=$((i + 1))
    done
    XRANDR_ARGS="--fb ${width}x${height} $*"
    configure_xrandr
}

# The outputs of RandR, which the window manager sets to their preferred
# modes when it starts, and then keeps as they are set
configure_xrandr() {
    DISPLAY=$X11_DISPLAY xrandr $XRANDR_ARGS >> "$JT_LOGS/monitors.log" 2>&1 \
        || fail "The monitors were not configured, see $JT_LOGS/monitors.log"
    DISPLAY=$X11_DISPLAY xrandr --listmonitors >> "$JT_LOGS/monitors.log" 2>&1
}

# The screen of Xorg in a window of the desktop of the host: x0vncserver
# shares it on a socket, and the VNC viewer shows it, and takes the mouse and
# the keyboard. As with Xephyr, closing the window ends the session
start_vnc_viewer() {
    VNC_SOCKET=$RUNTIME_DIR/jfx-tester-vnc-$$
    rm -f "$VNC_SOCKET"
    (exec x0vncserver -display "$X11_DISPLAY" -rfbport -1 -rfbunixpath "$VNC_SOCKET" -SecurityTypes None \
        -desktop "jfx-tester: $JT_DESKTOP") > "$JT_LOGS/vnc.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    for i in $(seq 1 50); do
        [ -S "$VNC_SOCKET" ] && break
        sleep 0.2
    done
    [ -S "$VNC_SOCKET" ] || fail "x0vncserver did not start, see $JT_LOGS/vnc.log"
    (vncviewer -RemoteResize=0 -AlertOnFatalError=0 -ReconnectOnError=0 "$VNC_SOCKET" \
        >> "$JT_LOGS/vnc.log" 2>&1
     kill "$XORG_PID" 2>/dev/null) &
    CHILDREN="$CHILDREN $!"
}

# The display that the X server writes to the descriptor of -displayfd, once
# it takes clients
wait_for_display() {
    for i in $(seq 1 150); do
        [ -s "$JT_LOGS/display" ] && break
        sleep 0.2
    done
    [ -s "$JT_LOGS/display" ] || fail "The nested X server did not start, see $JT_LOGS/xserver.log"
    X11_DISPLAY=:$(head -n 1 "$JT_LOGS/display")
}

# The scale of an X11 session, which has one for all the monitors: the one of
# the first. Sets X11_INT_SCALE when it is an integer scale above 1
x11_scale() {
    X11_INT_SCALE=
    all_same scale || echo "X11 has one scale for all the monitors: $FIRST_SCALE, the one of the first" >&2
    case "$FIRST_SCALE" in
        1|1.0) ;;
        *.*)
            # A fractional scale, which GDK has not: the one of the glass
            JT_JVM_ARGS="$JT_JVM_ARGS -Dglass.gtk.uiScale=$FIRST_SCALE"
            export JT_JVM_ARGS ;;
        *)
            export GDK_SCALE=$FIRST_SCALE
            X11_INT_SCALE=$FIRST_SCALE ;;
    esac
}

# A nested X server for an X11 session: Xephyr, a window on the desktop, or
# Xorg without one, or in a window with --session=xorg. The tests see an X11 session, where XTest and a capture of
# the root window work, as on Xorg
start_x11_server() {
    SESSION_TYPE=x11
    rm -f "$JT_LOGS/display"
    XRANDR_ARGS=
    if [ -n "$JT_HEADLESS" ]; then
        start_xorg
    elif [ -n "$JT_XORG" ] || [ "$(monitor_count)" -gt 1 ]; then
        # Xephyr has one output, which the window manager and GDK take as
        # one monitor: Xorg, with an output for each, shown by a VNC viewer
        start_xorg
        start_vnc_viewer
    else
        (exec Xephyr -displayfd 5 -screen "$FIRST_SIZE" -title "jfx-tester: $JT_DESKTOP" -no-host-grab \
            -nolisten tcp 5> "$JT_LOGS/display") > "$JT_LOGS/xserver.log" 2>&1 &
        CHILDREN="$CHILDREN $!"
        wait_for_display
    fi
    DISPLAY=$X11_DISPLAY
    XAUTHORITY=
    export DISPLAY XDG_CURRENT_DESKTOP="$1" XDG_SESSION_TYPE=x11
    unset WAYLAND_DISPLAY XAUTHORITY
    dbus-update-activation-environment DISPLAY XDG_CURRENT_DESKTOP XDG_SESSION_TYPE > /dev/null 2>&1
}

# Cinnamon, on a nested X server
start_cinnamon() {
    cat > "$XDG_CONFIG_HOME/glib-2.0/settings/keyfile" <<EOF
[org/cinnamon]
desktop-effects-workspace=false
startup-animation=false

[org/cinnamon/desktop/interface]
enable-animations=false

[org/cinnamon/desktop/session]
idle-delay=uint32 0

[org/cinnamon/desktop/screensaver]
lock-enabled=false
idle-activation-enabled=false

[org/cinnamon/desktop/notifications]
display-notifications=false
EOF
    x11_scale
    if [ -n "$X11_INT_SCALE" ]; then
        cat >> "$XDG_CONFIG_HOME/glib-2.0/settings/keyfile" <<EOF

[org/cinnamon/desktop/interface]
scaling-factor=uint32 $X11_INT_SCALE

[org/gnome/desktop/interface]
scaling-factor=uint32 $X11_INT_SCALE
EOF
    fi
    start_x11_server X-Cinnamon

    # Cinnamon 6 also runs on Wayland, and takes the kind of the session of
    # logind, which is the one of the host
    x11=
    cinnamon --help-all 2>&1 | grep -q -e '--x11 ' && x11=--x11
    (exec cinnamon $x11 --replace) > "$JT_LOGS/cinnamon.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
    for i in $(seq 1 150); do
        has_name org.Cinnamon && break
        sleep 0.2
    done
    has_name org.Cinnamon || fail "Cinnamon did not start, see $JT_LOGS/cinnamon.log"
    [ -n "$XRANDR_ARGS" ] && configure_xrandr
    sleep 2
}

SESSION_TYPE=wayland
start_atspi_bus
reserve_displays
start_$JT_DESKTOP
release_displays

[ -n "$DISPLAY" ] || fail "The nested compositor has no Xwayland, see $JT_LOGS"
export DISPLAY
if [ -n "$XAUTHORITY" ]; then
    export XAUTHORITY
else
    unset XAUTHORITY
fi
if [ "$SESSION_TYPE" = wayland ]; then
    # The tests see a Wayland session, and the robot goes through the
    # screencast and remote desktop portals of the compositor: on Xwayland,
    # the compositor redirects the X windows, which then do not show in a
    # capture of the root window, and XTest does not move the pointer of the
    # compositor
    export WAYLAND_DISPLAY=$SOCKET XDG_SESSION_TYPE=wayland
    # The robot of JavaFX takes the remote desktop portal on GNOME 47 and later
    # only: elsewhere the screencast portal, for the captures alone, and XTest
    # for the mouse and the keyboard. On KDE, the remote desktop portal, whose
    # permission is granted below, unless the options of the JVM choose
    if [ "$JT_DESKTOP" = kde ]; then
        case "$JT_JVM_ARGS" in
            *-Djavafx.robot.screenshotMethod=*) ;;
            *) JT_JVM_ARGS="$JT_JVM_ARGS -Djavafx.robot.screenshotMethod=dbusRemoteDesktop"
               export JT_JVM_ARGS ;;
        esac
    fi
fi
dbus-update-activation-environment DISPLAY XAUTHORITY WAYLAND_DISPLAY XDG_CURRENT_DESKTOP \
    > /dev/null 2>&1
start_atspi

# The portals, before the tests. xdg-desktop-portal before 1.16 starts the
# secret service as it starts, which does not answer in the container: it
# answers the bus only after that times out, after 25 s, when the robot of the
# first test has given up on it for good, and captures nothing but black
if [ "$SESSION_TYPE" = wayland ]; then
    gdbus call --session --timeout 90 --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus \
        --method org.freedesktop.DBus.StartServiceByName org.freedesktop.portal.Desktop 0 > /dev/null 2>&1 \
        || echo "The portals did not start: the tests of the robot will fail" >&2
fi

# Nobody is there to grant the permissions that the robot asks the portals for
# in dialogs: the dialogs of the permissions that the portals keep do not show,
# as they are granted here, in the store of the session (XDG_DATA_HOME)
grant_permission() {
    gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore \
        --object-path /org/freedesktop/impl/portal/PermissionStore \
        --method org.freedesktop.impl.portal.PermissionStore.SetPermission \
        "$1" true "$2" '' "['yes']" > /dev/null 2>&1
}
if [ "$SESSION_TYPE" = wayland ]; then
    grant_permission screenshot screenshot
    grant_permission kde-authorized remote-desktop
    # It ends when the bus of AT-SPI is not there yet, which takes longer with other
    # sessions starting at the same time, and starts again until the session ends.
    # Without it, the tests of the robot wait for the dialogs forever
    (while :; do
         python3 -u "$(dirname "$0")/approve.py" && break
         sleep 2
     done) > "$JT_LOGS/approve.log" 2>&1 &
    CHILDREN="$CHILDREN $!"
fi

# What another shell needs to join the session, such as for a screenshot
cat > "$JT_LOGS/session.env" <<EOF
DBUS_SESSION_BUS_ADDRESS=$BUS
WAYLAND_DISPLAY=$WAYLAND_DISPLAY
DISPLAY=$DISPLAY
XDG_CURRENT_DESKTOP=$XDG_CURRENT_DESKTOP
EOF

# The tests keep the tokens of the portals in JT_HOME, as the robot keeps them
# in user.home, and get the options of JT_JVM_ARGS. Their results go to
# directories of the environment, as the environments of a distribution share
# the workspace and can run at the same time: the JUnit reports and a log of
# each test class in JT_LOGS, and the HTML report in JT_REPORT. A PROGRESS line
# follows each test class of the system tests: their total is an estimate, the
# classes with tests in the sources that the filters of the run take
cat > "$JT_LOGS/jfx-tester.init.gradle" <<'EOF'
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult

allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'user.home', System.getenv('JT_HOME')
        // A test stuck where an interrupt does not reach, as in the wait of the robot of
        // AWT for a portal or in a deadlock of class initializers, fails at its timeout
        // and leaves its thread, instead of keeping the run there forever
        systemProperty 'junit.jupiter.execution.timeout.thread.mode.default', 'SEPARATE_THREAD'
        def args = System.getenv('JT_JVM_ARGS')
        if (args) {
            jvmArgs args.trim().split(/\s+/)
        }
        def env = System.getenv('JT_ENV')
        def logs = new File(System.getenv('JT_LOGS'))
        binaryResultsDirectory.set(project.layout.buildDirectory.dir("jfx-tester/$env/binary"))
        reports.junitXml.outputLocation.set(new File(logs, 'junit'))
        reports.html.outputLocation.set(new File(System.getenv('JT_REPORT')))

        // --skip-webkit: the classes that use WebKit, by their package or
        // imports. Most of NullCCLTest does not
        if (System.getenv('JT_SKIP_WEBKIT') && project.path == ':systemTests') {
            def uses = ~/(?m)^\s*import\s+(static\s+)?(javafx\.scene\.web|com\.sun\.webkit|com\.sun\.javafx\.webkit|test\.javafx\.scene\.web|test\.com\.sun\.webkit)\b/
            def root = project.file('src/test/java')
            project.fileTree(root) { include '**/*.java' }.each { File f ->
                def path = root.toPath().relativize(f.toPath()).toString()
                def name = path.replace('.java', '').replace('/', '.')
                if (name == 'test.com.sun.javafx.application.NullCCLTest') {
                    filter.excludeTestsMatching("${name}.testHTMLEditor")
                    filter.excludeTestsMatching("${name}.testWebView")
                } else if (name == 'test.launchertest.ModuleLauncherTest') {
                    filter.excludeTestsMatching("${name}.testModuleJSCallback*")
                } else if (path.startsWith('test/javafx/scene/web/') || path.startsWith('test/com/sun/webkit/')
                        || f.text =~ uses) {
                    filter.excludeTestsMatching(name)
                }
            }
        }

        def testTask = it
        def systemTests = project.path == ':systemTests'
        def classes = [] as Set
        def shown = 0L
        def total = 0, passed = 0, failed = 0, skipped = 0, started = 0L
        def progress = { String what ->
            def done = classes.size()
            def width = 20
            def full = total > 0 ? Math.min(width, (int) (done * width / total)) : 0
            def percent = total > 0 ? Math.min(100, (int) (done * 100 / total)) : 0
            def elapsed = (System.currentTimeMillis() - started) / 1000
            def left = done > 0 && total > done ? (int) (elapsed * (total - done) / done) : 0
            printf('PROGRESS %s [%s%s] %3d%% %d/%d classes, %d passed, %d failed, %d skipped, %dm%02ds left  %s%n',
                    env, '#' * full, '.' * (width - full), percent, done, total, passed, failed, skipped,
                    (int) (left / 60), (int) (left % 60), what)
        }
        doFirst {
            started = System.currentTimeMillis()
            def regex = { String p -> ~('^' + p.split(/\*/, -1).collect { java.util.regex.Pattern.quote(it) }.join('.*') + '$') }
            def includes = (filter.includePatterns + filter.commandLineIncludePatterns).collect { regex(it) }
            def includePrefixes = filter.includePatterns + filter.commandLineIncludePatterns
            def excludes = filter.excludePatterns.collect { regex(it) }
            def root = project.file('src/test/java')
            // The patterns of the task too, which take the paths of the classes, as the
            // exclude of the monocle tests of JDK-8196607
            def toJava = { String p -> p.replaceAll(/\.class$/, '.java') }
            def taskIncludes = testTask.includes.collect(toJava)
            def taskExcludes = testTask.excludes.collect(toJava)
            project.fileTree(root) {
                include(taskIncludes ?: ['**/*.java'])
                exclude taskExcludes
            }.matching { include '**/*.java' }.each { File f ->
                def name = root.toPath().relativize(f.toPath()).toString().replace('.java', '').replace('/', '.')
                if (excludes.any { name ==~ it } || !(f.text =~ /@(Test|ParameterizedTest|RepeatedTest)\b/)) {
                    return
                }
                if (includes.isEmpty() || includes.any { name ==~ it } || includePrefixes.any { it.startsWith(name + '.') }) {
                    total++
                }
            }
            if (systemTests) {
                progress('starting')
            }
        }

        def tests = new File(logs, 'tests')
        def logOf = { TestDescriptor d ->
            tests.mkdirs()
            new File(tests, "${d.className ?: d.name}.log")
        }
        addTestOutputListener(new TestOutputListener() {
            void onOutput(TestDescriptor d, TestOutputEvent event) {
                logOf(d).append(event.message)
            }
        })
        addTestListener(new TestListener() {
            void beforeSuite(TestDescriptor d) {}
            void afterSuite(TestDescriptor d, TestResult r) {
                // A class, and not the suites of its parameterized tests
                if (systemTests && d.className != null && d.name == d.className) {
                    classes << d.name
                    progress("${d.name}: ${r.resultType}")
                }
            }
            void beforeTest(TestDescriptor d) {
                logOf(d).append("==== ${d.name}\n")
            }
            void afterTest(TestDescriptor d, TestResult r) {
                switch (r.resultType) {
                    case TestResult.ResultType.SUCCESS: passed++; break
                    case TestResult.ResultType.FAILURE: failed++; break
                    default: skipped++
                }
                // Between the classes too, which may take minutes
                if (systemTests && System.currentTimeMillis() - shown >= 2000) {
                    shown = System.currentTimeMillis()
                    progress("${d.className}: ${d.name}")
                }
                def text = "==== ${d.name}: ${r.resultType} (${r.endTime - r.startTime} ms)\n"
                r.exceptions.each { e ->
                    def trace = new StringWriter()
                    e.printStackTrace(new PrintWriter(trace))
                    text += trace.toString()
                }
                logOf(d).append(text)
            }
        })
    }
}
EOF

if [ -n "$JT_MANUAL" ]; then
    echo "Running $JT_MANUAL on $JT_DESKTOP (DISPLAY=$DISPLAY): close it to end the session${JT_TIMEOUT:+, or it ends after $JT_TIMEOUT s}"
    # From its directory, as the manual tests read their resources from there
    cd "$(dirname "$JT_MANUAL")" || exit 1
    case "$JT_MANUAL" in
        *.jar) set -- -jar "$JT_MANUAL" "$@" ;;
        *) set -- "$JT_MANUAL" "$@" ;;
    esac
    # The options of the SDK, as build/run.args has them for the one built
    args=$JT_WORKSPACE/build/run.args
    if [ -n "$JT_SDK" ]; then
        args=$JT_LOGS/run.args
        modules=
        for jar in "$JT_SDK"/lib/*.jar; do
            m=$(basename "$jar" .jar)
            # javafx-swt.jar is not a module
            case "$m" in *-*) continue ;; esac
            modules="$modules${modules:+,}$m"
        done
        access=
        for m in javafx.graphics javafx.media javafx.web; do
            [ -f "$JT_SDK/lib/$m.jar" ] && access="$access${access:+,}$m"
        done
        printf -- '--upgrade-module-path="%s"\n--add-modules=%s\n--enable-native-access=%s\n' \
            "$JT_SDK/lib" "$modules" "$access" > "$args"
    fi
    # Ended after JT_TIMEOUT, which is no failure of the test. Its user.home is
    # JT_HOME, as for the system tests: the robot keeps its tokens there, for
    # the portals of this distribution and desktop, and not in the home of the
    # host, which all of them would share. JT_JVM_ARGS can give another
    set -- "$JAVA_HOME/bin/java" @"$args" -Duser.home="$JT_HOME" $JT_JVM_ARGS "$@"
    [ -n "$JT_TIMEOUT" ] && set -- timeout "$JT_TIMEOUT" "$@"
    { "$@"; status=$?
      if [ -n "$JT_TIMEOUT" ] && [ "$status" = 124 ]; then
          echo "Ended the manual test after $JT_TIMEOUT s (--timeout)"
          status=0
      fi
      echo "$status" > "$JT_LOGS/manual.status"; } 2>&1 | tee "$JT_LOGS/manual.log"
    exit "$(cat "$JT_LOGS/manual.status" 2>/dev/null || echo 1)"
fi

echo "Running the system tests on $JT_DESKTOP (DISPLAY=$DISPLAY), logs in $JT_LOGS"
cd "$JT_WORKSPACE" || exit 1
{ sh ./gradlew --init-script "$JT_LOGS/jfx-tester.init.gradle" "$@"; echo $? > "$JT_LOGS/gradle.status"; } 2>&1 | tee "$JT_LOGS/gradle.log"
exit "$(cat "$JT_LOGS/gradle.status" 2>/dev/null || echo 1)"
