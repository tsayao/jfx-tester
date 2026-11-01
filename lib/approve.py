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
# presses the button that shares. The portal runs in English (session.sh), for
# the names of the buttons. Runs until killed.

import sys
import time

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi

ACCEPT = {"share", "allow", "ok", "continue", "select"}
# Of the dialog of KDE: they ask for a new virtual screen or a region, not for
# what is shared, and leave its button off
OPTIONS = {"share virtual screen", "share region"}
# AT-SPI before 2.50 has no role for a switch, which it shows as a toggle button
CHECKABLE = {getattr(Atspi.Role, "SWITCH", Atspi.Role.TOGGLE_BUTTON), Atspi.Role.CHECK_BOX}
LISTS = {Atspi.Role.LIST, Atspi.Role.LIST_BOX, Atspi.Role.TABLE, Atspi.Role.TREE_TABLE}
ITEMS = {Atspi.Role.LIST_ITEM, Atspi.Role.TABLE_CELL, Atspi.Role.TABLE_ROW}


def log(*args):
    print(time.strftime("%H:%M:%S"), *args, flush=True)


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


def accept_buttons(nodes):
    return [n for n in nodes if n.get_role() == Atspi.Role.PUSH_BUTTON and has(n, Atspi.StateType.SHOWING)
            and name(n).lower().rstrip(".") in ACCEPT]


def prepare(window, title, kde):
    """Turns on the switches and check boxes of a dialog, and selects the first
    item of its lists, once. A switch of GTK 4 shows twice in the tree, as
    the row, without actions, and the switch. KDE can show its options without
    a name, while what it shares is always named"""
    nodes = list(walk(window))
    for n in nodes:
        if n.get_role() in CHECKABLE and actionable(n) and has(n, Atspi.StateType.SHOWING) \
                and has(n, Atspi.StateType.SENSITIVE) and not has(n, Atspi.StateType.CHECKED) \
                and name(n).lower() not in OPTIONS and (name(n) or not kde):
            log("turning on", repr(name(n)), "in", repr(title))
            act(n)
    for n in nodes:
        if n.get_role() in LISTS and has(n, Atspi.StateType.SHOWING):
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
                    buttons = accept_buttons(nodes)
                    if not buttons:
                        continue
                    seen.add(key)
                    state = dialogs.get(key)
                    if state is None:
                        prepare(window, title, "kde" in name(app).lower())
                        dialogs[key] = "prepared"
                    elif state in ("prepared", "selected"):
                        sensitive = [b for b in buttons if has(b, Atspi.StateType.SENSITIVE)]
                        if sensitive:
                            log("pressing", repr(name(sensitive[0])), "in", repr(title))
                            act(sensitive[0])
                            dialogs[key] = "accepted"
                        elif "kde" in name(app).lower():
                            # Its button only follows a click: the actions of
                            # AT-SPI check a screen without selecting it. It is
                            # refused, or the robot would wait for it forever
                            log("cannot select a screen, canceling", repr(title))
                            for n in nodes:
                                if n.get_role() == Atspi.Role.PUSH_BUTTON and name(n).lower() == "cancel":
                                    act(n)
                            dialogs[key] = "accepted"
                        elif state == "prepared":
                            # No screen is selected: the buttons of the
                            # screens do not tell whether they are, so the
                            # first one is clicked only then
                            dialogs[key] = "selected"
                            for n in nodes:
                                if n.get_role() == Atspi.Role.TOGGLE_BUTTON and actionable(n) \
                                        and has(n, Atspi.StateType.SHOWING):
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
