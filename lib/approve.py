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

# Grants the screencast and remote desktop permissions that the robot asks the
# portal of the nested desktop for, through the accessibility of its dialogs:
# it turns on their switches and check boxes (the remote interaction, the
# remembering of the selection), selects the first screen when none is, and
# presses the button that shares. The dialog of KDE only takes the selection from a
# key, sent through the fake input of KWin. The portal runs in English (session.sh),
# for the names of the buttons. Runs until killed.

import os
import socket
import struct
import sys
import time

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi

ACCEPT = {"share", "allow", "ok", "continue", "select"}
# Of the dialog of KDE: they ask for a new virtual screen or a region, not for
# what is shared, and leave its button off
OPTIONS = {"share virtual screen", "share region"}
# How long the dialog of KDE has to turn on its button after the key, in seconds
KEY_WAIT = 3
KEY_SPACE = 57
# AT-SPI before 2.50 has no role for a switch, which it shows as a toggle button
CHECKABLE = {getattr(Atspi.Role, "SWITCH", Atspi.Role.TOGGLE_BUTTON), Atspi.Role.CHECK_BOX}
LISTS = {Atspi.Role.LIST, Atspi.Role.LIST_BOX, Atspi.Role.TABLE, Atspi.Role.TREE_TABLE}
ITEMS = {Atspi.Role.LIST_ITEM, Atspi.Role.TABLE_CELL, Atspi.Role.TABLE_ROW}


def log(*args):
    print(time.strftime("%H:%M:%S"), *args, flush=True)


class FakeKeyboard:
    """A client of org_kde_kwin_fake_input of KWin, which allows it as session.sh
    starts it with KWIN_WAYLAND_NO_PERMISSION_CHECKS. It stays connected, as KWin
    drops the keys of a client that is gone"""

    REGISTRY, SYNC, FAKE_INPUT = 2, 3, 4

    def __init__(self):
        path = os.environ["WAYLAND_DISPLAY"]
        if not path.startswith("/"):
            path = os.path.join(os.environ["XDG_RUNTIME_DIR"], path)
        self.socket = socket.socket(socket.AF_UNIX)
        self.socket.settimeout(5)
        self.socket.connect(path)
        self.request(1, 1, struct.pack("I", self.REGISTRY))
        self.request(1, 0, struct.pack("I", self.SYNC))
        global_name, version = self.find_global(b"org_kde_kwin_fake_input")
        if global_name is None or version < 4:
            raise RuntimeError("no keyboard in the fake input of KWin")
        self.request(self.REGISTRY, 0, struct.pack("I", global_name) + self.string("org_kde_kwin_fake_input")
                     + struct.pack("II", 4, self.FAKE_INPUT))
        self.request(self.FAKE_INPUT, 0, self.string("jfx-tester") + self.string("portal dialogs"))

    def find_global(self, interface):
        found, version, data = None, 0, b""
        while True:
            data += self.socket.recv(65536)
            while len(data) >= 8:
                target, header = struct.unpack("II", data[:8])
                size = header >> 16
                if len(data) < size:
                    break
                body, data = data[8:size], data[size:]
                if target == self.SYNC:
                    return found, version
                if target == self.REGISTRY and header & 0xffff == 0:
                    length = struct.unpack("I", body[4:8])[0]
                    if body[8:7 + length] == interface:
                        found = struct.unpack("I", body[:4])[0]
                        version = struct.unpack("I", body[8 + ((length + 3) & ~3):][:4])[0]

    def string(self, text):
        data = text.encode() + b"\0"
        return struct.pack("I", len(data)) + data + b"\0" * (-len(data) % 4)

    def request(self, target, opcode, body):
        self.socket.sendall(struct.pack("II", target, (8 + len(body)) << 16 | opcode) + body)

    def press(self, key):
        for state in (1, 0):
            self.request(self.FAKE_INPUT, 10, struct.pack("II", key, state))
            time.sleep(0.05)


def children(node):
    try:
        return [c for c in (node.get_child_at_index(i) for i in range(node.get_child_count())) if c]
    except Exception:
        return []


def walk(node, depth=0):
    if depth > 40:
        return
    yield node
    for c in children(node):
        yield from walk(c, depth + 1)


def states(node):
    try:
        return node.get_state_set()
    except Exception:
        return None


def has(node, state):
    s = states(node)
    return s is not None and s.contains(state)


def actionable(node):
    try:
        action = node.get_action_iface()
        return action is not None and action.get_n_actions() > 0
    except Exception:
        return False


def act(node):
    try:
        action = node.get_action_iface()
        if action and action.get_n_actions() > 0:
            return action.do_action(0)
    except Exception as e:
        log("action failed:", e)
    return False


def name(node):
    try:
        return (node.get_name() or "").strip()
    except Exception:
        return ""


def label(node):
    """The name of a node as a button shows it: without the underscores of the
    mnemonics, in lower case. GTK 4 before 4.10 names a button by its mnemonic
    and its label, as '_Share Share': a name of the same word twice is that word"""
    words = name(node).replace("_", "").lower().rstrip(".").split()
    if len(set(words)) == 1:
        return words[0]
    return " ".join(words)


def shows(window):
    """Whether a node of the window is on the screen. The dialogs of GTK 4 on a
    headless GNOME Shell have the state showing on the window only: there, all
    the nodes are taken as showing"""
    reported = any(has(n, Atspi.StateType.SHOWING) for n in walk(window) if n is not window)
    return lambda node: has(node, Atspi.StateType.SHOWING) or not reported


def accept_buttons(nodes, shown):
    return [n for n in nodes if n.get_role() == Atspi.Role.PUSH_BUTTON and shown(n)
            and label(n) in ACCEPT]


def select_kde_screen(nodes, title, keyboard):
    """Focuses the first screen of the dialog of KDE and presses space. The actions of
    AT-SPI check its box without selecting it, and then the key would turn it off,
    so they are left alone. The other check box of a screen is in the list of those
    selected, and has no component"""
    for n in nodes:
        if n.get_role() == Atspi.Role.CHECK_BOX and n.get_component_iface() and name(n) \
                and name(n).lower() not in OPTIONS and not name(n).lower().startswith("allow"):
            action = n.get_action_iface()
            for i in range(action.get_n_actions()):
                if action.get_action_name(i) == "SetFocus":
                    action.do_action(i)
            time.sleep(0.3)
            log("selecting", repr(name(n)), "in", repr(title))
            keyboard.press(KEY_SPACE)
            return


def prepare(window, title, kde, shown):
    """Turns on the switches and check boxes of a dialog, and selects the first
    item of its lists, once. A switch of GTK 4 shows twice in the tree, as
    the row, without actions, and the switch. Of KDE, only what allows something:
    the others ask for what to share"""
    nodes = list(walk(window))
    for n in nodes:
        if n.get_role() in CHECKABLE and actionable(n) and shown(n) \
                and has(n, Atspi.StateType.SENSITIVE) and not has(n, Atspi.StateType.CHECKED) \
                and (name(n).lower().startswith("allow") if kde else True):
            log("turning on", repr(name(n)), "in", repr(title))
            act(n)
    for n in nodes:
        if n.get_role() in LISTS and shown(n):
            items = [c for c in walk(n) if c.get_role() in ITEMS]
            if items and not any(has(i, Atspi.StateType.SELECTED) for i in items):
                log("selecting", repr(name(items[0])), "in", repr(title))
                try:
                    n.get_selection_iface().select_child(0)
                except Exception:
                    act(items[0])


def main():
    log("approving the portal dialogs")
    # The dialogs on the screen, by application and object path: prepared,
    # selected or accepted. The portal can ask again as soon as a dialog
    # closes, with the same title
    dialogs = {}
    keyboard = None
    key_sent = {}
    while True:
        seen = set()
        try:
            for app in children(Atspi.get_desktop(0)):
                if "portal" not in name(app).lower():
                    continue
                for window in children(app):
                    title = name(window)
                    key = (app.get_process_id(), getattr(window, "path", None) or title)
                    nodes = list(walk(window))
                    shown = shows(window)
                    buttons = accept_buttons(nodes, shown)
                    if not buttons:
                        continue
                    seen.add(key)
                    state = dialogs.get(key)
                    if state is None:
                        prepare(window, title, "kde" in name(app).lower(), shown)
                        dialogs[key] = "prepared"
                    elif state in ("prepared", "selected"):
                        sensitive = [b for b in buttons if has(b, Atspi.StateType.SENSITIVE)]
                        if sensitive:
                            log("pressing", repr(name(sensitive[0])), "in", repr(title))
                            act(sensitive[0])
                            dialogs[key] = "accepted"
                        elif "kde" in name(app).lower():
                            # Refused when the key does not select it, or the
                            # robot would wait for it forever
                            if state == "prepared":
                                dialogs[key] = "selected"
                                key_sent[key] = time.time()
                                try:
                                    keyboard = keyboard or FakeKeyboard()
                                    select_kde_screen(nodes, title, keyboard)
                                except (OSError, RuntimeError, KeyError) as e:
                                    log("no key for", repr(title) + ":", e)
                            elif time.time() - key_sent[key] > KEY_WAIT:
                                log("cannot select a screen, canceling", repr(title))
                                for n in nodes:
                                    if n.get_role() == Atspi.Role.PUSH_BUTTON and label(n) == "cancel":
                                        act(n)
                                dialogs[key] = "accepted"
                        elif state == "prepared":
                            # No screen is selected: the buttons of the
                            # screens do not tell whether they are, so the
                            # first one is clicked only then
                            dialogs[key] = "selected"
                            for n in nodes:
                                if n.get_role() == Atspi.Role.TOGGLE_BUTTON and actionable(n) and shown(n):
                                    log("selecting", repr(name(n)), "in", repr(title))
                                    act(n)
                                    break
        except Exception as e:
            log("error:", e)
        for key in list(dialogs):
            if key not in seen:
                del dialogs[key]
        time.sleep(0.5)


if __name__ == "__main__":
    sys.exit(main())
