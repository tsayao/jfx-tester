#!/usr/bin/python3
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

"""A keyboard and a pointer for the seat of a nested GNOME Shell on Wayland, for as
long as the session lasts. Its seat has no input devices: the remote desktop
session of the robot of each test creates a virtual keyboard as it emits its first
key, and Mutter emits that key before the clients, Xwayland too, take the keyboard
the seat then has, which loses it. A remote desktop session of Mutter, which asks
no permission, creates the devices here with a key of Shift and a motion of
nothing, and keeps them while this runs."""

import sys

import gi

gi.require_version("Gio", "2.0")
from gi.repository import Gio, GLib

KEY_RIGHTSHIFT = 54


def call(bus, path, interface, method, args=None, reply=None):
    return bus.call_sync("org.gnome.Mutter.RemoteDesktop", path, interface, method, args,
                         GLib.VariantType.new(reply) if reply else None,
                         Gio.DBusCallFlags.NONE, -1, None)


def main():
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    session = call(bus, "/org/gnome/Mutter/RemoteDesktop", "org.gnome.Mutter.RemoteDesktop",
                   "CreateSession", None, "(o)").unpack()[0]
    iface = "org.gnome.Mutter.RemoteDesktop.Session"
    call(bus, session, iface, "Start")
    call(bus, session, iface, "NotifyKeyboardKeycode", GLib.Variant("(ub)", (KEY_RIGHTSHIFT, True)))
    call(bus, session, iface, "NotifyKeyboardKeycode", GLib.Variant("(ub)", (KEY_RIGHTSHIFT, False)))
    call(bus, session, iface, "NotifyPointerMotionRelative", GLib.Variant("(dd)", (0.0, 0.0)))
    print("seat devices created by", session, flush=True)
    loop = GLib.MainLoop()
    bus.signal_subscribe("org.gnome.Mutter.RemoteDesktop", iface, "Closed", session, None,
                         Gio.DBusSignalFlags.NONE, lambda *a: (print("session closed", flush=True), loop.quit()))
    loop.run()


if __name__ == "__main__":
    try:
        main()
    except GLib.Error as e:
        print("no seat devices:", e.message, file=sys.stderr)
        sys.exit(1)
