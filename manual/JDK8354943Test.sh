#!/bin/sh
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

# Runs the tests of JDK8354943Test.java, one ./jfx-tester for each bug in each
# of its environments, one after the other, and sums them up at the end. Each
# run exits when its tests end.
#
# Each bug runs as its report says: on Wayland, where the tests run on Xwayland,
# on Xorg, or on both when the report does not say which or names both.
#
# Usage: manual/JDK8354943Test.sh [--list] [--ubuntu22] [--session=S] [JDK-N ...] [options of jfx-tester]
#
#   --list       print the commands, without running them
#   --ubuntu22   also the environments of Ubuntu 22.04, which need its container
#                and a build of its workspace
#   --session=S  only the runs of this kind of session, wayland or xorg
#   JDK-N        only these bugs
#
# Any other option goes to jfx-tester, such as --headless or --sdk=DIR. It does not
# build: the runs take the builds that the workspaces of the distributions have, or
# the SDK of --sdk. --source=DIR only checks that the workspaces have those sources.
#
#   manual/JDK8354943Test.sh --headless
#   manual/JDK8354943Test.sh --source=$HOME/IdeaProjects/jfx2 --headless
#   manual/JDK8354943Test.sh --sdk=$HOME/jfx/build/sdk --headless JDK-8353556
#
# All the runs share a folder, RESULTS/JDK8354943Test_YYYY-MM-DD_HH-MM-SS (results
# without --results), with the output of each run as BUG-DISTRO-SESSION.log and its logs
# and reports in a folder beside it, so the results before them stay. The exit status is
# 1 when any of them fails, and 130 when Ctrl+C stops them, which leaves out the
# runs that follow.

cd "$(dirname "$0")/.." || exit 1

GNOME_WAYLAND="--distros=ubuntu:24.04 --desktops=gnome --session=wayland"
GNOME_XORG="--distros=ubuntu:24.04 --desktops=gnome --session=xorg"
CINNAMON_XORG="--distros=mint:22.3 --desktops=cinnamon --session=xorg"
UBUNTU_22_WAYLAND="--distros=ubuntu:22.04 --desktops=gnome --session=wayland"
UBUNTU_22_XORG="--distros=ubuntu:22.04 --desktops=gnome --session=xorg"
SMALLER_SECOND_MONITOR="--monitors=1920x1080,1280x720"

# Each bug in the environments of its report, or the nearest ones, and the
# repeats of the intermittent ones (0 for the default of the test)
tests() {
    echo "JDK-8365201 0 $CINNAMON_XORG $SMALLER_SECOND_MONITOR"
    echo "JDK-8366009 20 $GNOME_WAYLAND"
    echo "JDK-8366009 20 $GNOME_XORG"
    echo "JDK-8367893 0 $GNOME_WAYLAND"
    [ -n "$WITH_UBUNTU22" ] && echo "JDK-8367893 0 $UBUNTU_22_XORG"
    echo "JDK-8321624 20 $GNOME_WAYLAND"
    echo "JDK-8321624 20 $GNOME_XORG"
    [ -n "$WITH_UBUNTU22" ] && echo "JDK-8321624 20 $UBUNTU_22_WAYLAND"
    [ -n "$WITH_UBUNTU22" ] && echo "JDK-8321624 20 $UBUNTU_22_XORG"
    echo "JDK-8353556 0 $GNOME_WAYLAND"
    echo "JDK-8353556 0 $GNOME_XORG"
    echo "JDK-8355073 0 $GNOME_WAYLAND"
    echo "JDK-8355073 0 $GNOME_XORG"
    echo "JDK-8353612 0 $GNOME_WAYLAND"
    echo "JDK-8353612 0 $GNOME_XORG"
    echo "JDK-8337400 0 $GNOME_XORG"
    echo "JDK-8316891 20 $GNOME_WAYLAND"
    echo "JDK-8316891 20 $GNOME_XORG"
    echo "JDK-8316425 0 $GNOME_XORG"
    :
}

LIST=
WITH_UBUNTU22=
BUGS=
EXTRA=
RESULTS=results
SESSION=
SOURCE=
for a in "$@"; do
    case "$a" in
        --list) LIST=1 ;;
        --ubuntu22) WITH_UBUNTU22=1 ;;
        JDK-*) BUGS="$BUGS $a" ;;
        --source=*) SOURCE=${a#*=} ;;
        --build|--clean|--no-sync)
            echo "$a: this script does not build. Build each distribution with ./jfx-tester --source=DIR --build, then run it" >&2
            exit 2 ;;
        --results=*) RESULTS=${a#*=} ;;
        --work=*) WORK=${a#*=}; EXTRA="$EXTRA $a" ;;
        --session=wayland|--session=xorg) SESSION=${a#*=} ;;
        --session=*) echo "Unknown session: ${a#*=} (wayland or xorg)" >&2; exit 2 ;;
        *) EXTRA="$EXTRA $a" ;;
    esac
done

# The sources given, which the workspaces have to be built from: a build of the host
# in DIR/build/sdk does not load in the containers, whose C library may be older
check_workspace() {
    workspace=${WORK:-$HOME/.cache/jfx-tester}/workspace/$1
    glass=modules/javafx.graphics/src/main
    if ! [ -f "$workspace/build/sdk/lib/libglassgtk3.so" ]; then
        echo "$workspace has no build: build it with ./jfx-tester --source=$SOURCE --build --distros=$2" >&2
        exit 2
    fi
    if ! diff -rq "$SOURCE/$glass" "$workspace/$glass" > /dev/null 2>&1; then
        echo "$workspace was not built from $SOURCE: build it with ./jfx-tester --source=$SOURCE --build --distros=$2" >&2
        exit 2
    fi
}

if [ -n "$SOURCE" ]; then
    case " $EXTRA " in
        *" --sdk="*) echo "Give either --sdk or --source, not both" >&2; exit 2 ;;
    esac
    given=$SOURCE
    SOURCE=$(cd "$OLDPWD" && cd "$given" 2>/dev/null && pwd) || { echo "No sources in $given" >&2; exit 2; }
fi
if [ -n "$SOURCE" ] && [ -z "$LIST" ]; then
    checked=
    while read -r bug repeat env; do
        if [ -n "$BUGS" ]; then
            case " $BUGS " in
                *" $bug "*) ;;
                *) continue ;;
            esac
        fi
        distro=${env%% *}
        distro=${distro#--distros=}
        case " $checked " in
            *" $distro "*) continue ;;
        esac
        checked="$checked $distro"
        check_workspace "$(echo "$distro" | tr ':' '-')" "$distro"
    done <<EOF
$(tests)
EOF
    echo "Using the builds of the workspaces, of the sources of $SOURCE"
fi

if [ -z "$LIST" ]; then
    mkdir -p "$RESULTS" || exit 1
    RESULTS=$(cd "$RESULTS" && pwd)
    LOGS_BASE=$RESULTS/JDK8354943Test_$(date +%F_%H-%M-%S)
    LOGS=$LOGS_BASE
    c=1
    until mkdir "$LOGS" 2>/dev/null; do
        [ -e "$LOGS" ] || { echo "Cannot create $LOGS" >&2; exit 1; }
        c=$((c + 1))
        LOGS=$LOGS_BASE-$c
    done
fi
SUMMARY=
FAILED=
INTERRUPTED=
trap 'INTERRUPTED=1' INT
n=0
while read -r bug repeat env; do
    if [ -n "$BUGS" ]; then
        case " $BUGS " in
            *" $bug "*) ;;
            *) continue ;;
        esac
    fi
    case "$env" in
        *--session=xorg*) session=xorg ;;
        *) session=wayland ;;
    esac
    [ -z "$SESSION" ] || [ "$SESSION" = "$session" ] || continue
    distro=${env%% *}
    props="-Dtests.run=true -Dtests.exit=true -Dtests.bugs=$bug"
    [ "$repeat" = 0 ] || props="$props -Dtests.repeat=$repeat"
    if [ -n "$LIST" ]; then
        echo "./jfx-tester $env$EXTRA --manual=manual/JDK8354943Test.java --timeout=1800 --jvm-args=\"$props\""
        continue
    fi
    n=$((n + 1))
    name=$bug-$(echo "${distro#--distros=}" | tr ':' '-')-$session
    log=$LOGS/$name.log
    echo "== $n: $bug, $env$EXTRA"
    # shellcheck disable=SC2086
    { ./jfx-tester $env $EXTRA --results="$LOGS" --manual=manual/JDK8354943Test.java --timeout=1800 \
        --jvm-args="$props" < /dev/null; echo $? > "$log.status"; } 2>&1 | tee "$log" | grep --line-buffered -E '^(OK|FAIL|SKIP) +JDK|^RUN +JDK|^TESTS'
    status=$(cat "$log.status")
    rm -f "$log.status"
    result=$(grep -E '^TESTS' "$log" | tail -1 | sed 's/^TESTS //')
    [ "$status" = 130 ] && INTERRUPTED=1
    if [ -n "$INTERRUPTED" ]; then
        SUMMARY="$SUMMARY$(printf '  %-12s %-22s %-5s %s' "$bug" "${distro#--distros=} $session" STOP "stopped by Ctrl+C, the ones after it did not run")
"
        break
    fi
    if [ "$status" = 0 ]; then
        outcome=OK
    else
        outcome=FAIL
        FAILED=1
    fi
    SUMMARY="$SUMMARY$(printf '  %-12s %-22s %-5s %s' "$bug" "${distro#--distros=} $session" "$outcome" "${result:-it did not end, see $log}")
"
done <<EOF
$(tests)
EOF

[ -n "$LIST" ] && exit 0
echo
echo "Results (the output of each run is in $LOGS):"
printf '%s' "$SUMMARY"
[ -z "$INTERRUPTED" ] || exit 130
[ -z "$FAILED" ]
