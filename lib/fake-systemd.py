#!/usr/bin/python3
"""Answers for systemd on the private bus of a session, for what GNOME Shell asks
of it: the units start at once, as nothing in the container runs them."""

import gi

gi.require_version("Gio", "2.0")
from gi.repository import Gio, GLib

XML = """<node><interface name="org.freedesktop.systemd1.Manager">
  <method name="StartUnit"><arg type="s"/><arg type="s"/><arg type="o" direction="out"/></method>
  <method name="StopUnit"><arg type="s"/><arg type="s"/><arg type="o" direction="out"/></method>
  <method name="GetUnit"><arg type="s"/><arg type="o" direction="out"/></method>
  <method name="Subscribe"/>
  <method name="Unsubscribe"/>
  <signal name="JobRemoved"><arg type="u"/><arg type="o"/><arg type="s"/><arg type="s"/></signal>
</interface></node>"""


JOB = "/org/freedesktop/systemd1/job/1"


def call(connection, _sender, path, interface, method, parameters, invocation):
    if method == "GetUnit":
        invocation.return_dbus_error("org.freedesktop.systemd1.NoSuchUnit", "No such unit")
    elif method in ("Subscribe", "Unsubscribe"):
        invocation.return_value(None)
    else:
        invocation.return_value(GLib.Variant("(o)", (JOB,)))
        done = GLib.Variant("(uoss)", (1, JOB, parameters[0], "done"))
        GLib.timeout_add(100, lambda: connection.emit_signal(None, path, interface, "JobRemoved", done) and False)


def acquired(connection, _name):
    connection.register_object("/org/freedesktop/systemd1", Gio.DBusNodeInfo.new_for_xml(XML).interfaces[0],
                               call, None, None)


Gio.bus_own_name(Gio.BusType.SESSION, "org.freedesktop.systemd1", Gio.BusNameOwnerFlags.NONE, acquired, None, None)
GLib.MainLoop().run()
