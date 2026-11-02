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

# The distributions that toolbox can create containers of, and the packages
# that building JavaFX and running the system tests in a nested GNOME, KDE or
# Cinnamon need in each. Sourced by jfx-tester.
#
# Linux Mint is a container of the Ubuntu it is based on, with the repository
# of Mint, as toolbox has no Mint: it takes the packages of Ubuntu, and those
# of Mint first, as Mint does.

DISTROS_SUPPORTED="fedora ubuntu arch rhel mint"

# The distribution whose packages a distribution takes
family() {
    case "$1" in
        mint) echo ubuntu ;;
        *) echo "$1" ;;
    esac
}

# The codename of a release of Mint, and the release of Ubuntu it is based on
mint_codename() {
    case "$1" in
        21) echo vanessa ;;
        21.1) echo vera ;;
        21.2) echo victoria ;;
        21.3) echo virginia ;;
        22) echo wilma ;;
        22.1) echo xia ;;
        22.2) echo zara ;;
        22.3) echo zena ;;
    esac
}

mint_ubuntu_release() {
    case "$1" in
        21|21.*) echo 22.04 ;;
        22|22.*) echo 24.04 ;;
    esac
}

# The arguments of toolbox create for a release of a distribution
toolbox_create_args() {
    case "$1:$2" in
        fedora:rawhide)
            # toolbox only takes release numbers
            echo "--image registry.fedoraproject.org/fedora-toolbox:rawhide" ;;
        mint:*) echo "--distro ubuntu --release $(mint_ubuntu_release "$2")" ;;
        *) echo "--distro $1 --release $2" ;;
    esac
}

# A script, run in the container with the codename of the release of Mint and
# the signing key of its repository (linuxmint.asc, from keyserver.ubuntu.com:
# 302F0738F465C1535761F965A6616109451BBBF2), that adds the repository, before
# Ubuntu as in Mint. The repository has no HTTPS, and is checked with the key
MINT_SETUP='list=/etc/apt/sources.list.d/linuxmint.list
grep -qs " $1 main " "$list" && exit 0
sudo install -D -m 644 "$2" /etc/apt/keyrings/linuxmint.asc &&
echo "deb [signed-by=/etc/apt/keyrings/linuxmint.asc] http://packages.linuxmint.com $1 main upstream import backport" |
    sudo tee "$list" > /dev/null &&
printf "Package: *\nPin: origin packages.linuxmint.com\nPin-Priority: 700\n" |
    sudo tee /etc/apt/preferences.d/linuxmint.pref > /dev/null &&
sudo env DEBIAN_FRONTEND=noninteractive apt-get update'

# The release of a distribution when none is given
default_release() {
    case "$1" in
        fedora)
            if [ "$(os_release ID)" = fedora ]; then
                os_release VERSION_ID
            else
                echo 44
            fi ;;
        ubuntu) echo 24.04 ;;
        arch) echo latest ;;
        rhel) echo 10.0 ;;
        mint) echo 22.3 ;;
    esac
}

os_release() {
    (. /etc/os-release 2>/dev/null && eval "printf '%s\n' \"\${$1}\"")
}

package_manager() {
    case "$(family "$1")" in
        fedora|rhel) echo dnf ;;
        ubuntu) echo apt ;;
        arch) echo pacman ;;
    esac
}

# What the JavaFX build needs: a C/C++ toolchain and the development files of
# GTK 3, XTest, Xxf86vm, OpenGL, Pango and FreeType, and the static C++
# library, which the natives link (see buildSrc/linux.gradle)
packages_build() {
    case "$(family "$1")" in
        fedora|rhel)
            echo "gcc gcc-c++ libstdc++-static make pkgconf-pkg-config gtk3-devel libXtst-devel libXxf86vm-devel" \
                "mesa-libGL-devel pango-devel freetype-devel" ;;
        ubuntu)
            echo "build-essential pkg-config libgtk-3-dev libxtst-dev libxxf86vm-dev libgl-dev" \
                "libpango1.0-dev libfreetype-dev" ;;
        arch)
            echo "gcc make pkgconf gtk3 libxtst libxxf86vm mesa pango freetype2" ;;
    esac
}

# What the tests need to run: the private session bus, Xwayland (the glass of
# GTK 3 is an X11 client), the GL drivers, fonts and gdbus
packages_runtime() {
    case "$(family "$1")" in
        fedora|rhel)
            echo "dbus-daemon dbus-tools glib2 xorg-x11-server-Xwayland mesa-dri-drivers" \
                "dejavu-sans-fonts dejavu-serif-fonts dejavu-sans-mono-fonts at-spi2-core" ;;
        ubuntu)
            echo "dbus libglib2.0-bin xwayland libgl1-mesa-dri fonts-dejavu-core at-spi2-core" ;;
        arch)
            echo "dbus glib2 xorg-xwayland ttf-dejavu at-spi2-core" ;;
    esac
}

packages_desktop() {
    case "$1:$2" in
        mint:cinnamon) printf '%s ' mint-themes mint-y-icons ;;
    esac
    case "$(family "$1"):$2" in
        # The settings of GNOME, which Arch does not install with the shell
        fedora:gnome|rhel:gnome|ubuntu:gnome|arch:gnome) echo gnome-shell gnome-control-center ;;
        # The whole Plasma desktop, of which plasma-workspace has the session
        # and plasmashell, and plasma-desktop the desktop and its panel
        fedora:kde|arch:kde) echo kwin plasma-workspace plasma-desktop ;;
        ubuntu:kde) echo kwin-wayland plasma-workspace plasma-desktop ;;
        fedora:cinnamon|arch:cinnamon) echo cinnamon ;;
        # The icons of Cinnamon are SVG, which need the loader of librsvg
        ubuntu:cinnamon) echo cinnamon librsvg2-common ;;
    esac
}

# The kind of session of a desktop, wayland or x11, of the one asked for: a
# nested compositor or a nested X server. Without one, Cinnamon is X11 and the
# others Wayland
session_type() {
    case "$2" in
        wayland) echo wayland ;;
        x11|xorg) echo x11 ;;
        *) [ "$1" = cinnamon ] && echo x11 || echo wayland ;;
    esac
}

# Installed when the distribution has them: the viewer of the nested GNOME
# Shell 49 and later (gnome-shell --devkit), which some releases ship apart,
# kscreen-doctor, for the scale of each output of KWin, and the Wayland plugin
# of the Qt of Plasma, without which it does not start on KWin, and the backends of
# KWin, which Plasma 5.24 ships apart
packages_optional() {
    case "$(family "$1"):$2" in
        fedora:gnome) echo mutter-devkit ;;
        ubuntu:gnome) echo mutter-dev-bin ;;
        arch:gnome) echo mutter-devkit ;;
        fedora:kde|arch:kde) echo libkscreen ;;
        ubuntu:kde) echo libkscreen-bin libkf5screen-bin qtwayland5 qt6-wayland \
            kwin-wayland-backend-virtual kwin-wayland-backend-wayland ;;
    esac
}

# What the session of a desktop needs, of a kind of session. Wayland: the
# portals of the robot,
# through the screencast and remote desktop of the compositor, and the Python
# and AT-SPI of approve.py, which grants their permissions, and of the
# configuration of the monitors of GNOME. X11: the nested X server, Xephyr in
# a window, or Xorg with the dummy driver, xrandr for its monitors, the VNC
# server and viewer that show Xorg in a window, with more than one monitor,
# and the X11 KWin of KDE. GNOME before 49 needs Python on X11 too, for the
# stand-in of systemd
packages_session() {
    if [ "$(session_type "$2" "$3")" = x11 ]; then
        case "$2:$(family "$1")" in
            kde:*) printf '%s ' kwin-x11 ;;
            gnome:arch) printf '%s ' python ;;
            gnome:*) printf '%s ' python3 ;;
        esac
        case "$(family "$1")" in
            fedora|rhel) echo xorg-x11-server-Xephyr xorg-x11-server-Xorg xorg-x11-drv-dummy xrandr \
                tigervnc tigervnc-x11-server ;;
            ubuntu) echo xserver-xephyr xserver-xorg-core xserver-xorg-video-dummy x11-xserver-utils \
                tigervnc-viewer tigervnc-scraping-server ;;
            arch) echo xorg-server-xephyr xorg-server xf86-video-dummy xorg-xrandr tigervnc ;;
        esac
        return
    fi
    case "$2" in
        gnome) printf '%s ' xdg-desktop-portal xdg-desktop-portal-gnome ;;
        kde) printf '%s ' xdg-desktop-portal xdg-desktop-portal-kde ;;
    esac
    case "$(family "$1")" in
        fedora|rhel) echo python3-gobject ;;
        ubuntu) echo python3-gi gir1.2-atspi-2.0 ;;
        arch) echo python-gobject ;;
    esac
}

# The library of --robot=libei
packages_libei() {
    case "$(family "$1")" in
        fedora|rhel|arch) echo libei ;;
        ubuntu) echo libei1 ;;
    esac
}

# Why a desktop, in a kind of session, is not available on a distribution,
# if it is not
desktop_unsupported() {
    case "$1:$2" in
        rhel:kde) echo "RHEL does not ship KDE Plasma (use fedora, ubuntu or arch)"; return ;;
        rhel:cinnamon) echo "RHEL does not ship Cinnamon (use mint, fedora, ubuntu or arch)"; return ;;
    esac
    case "$1:$2:$(session_type "$2" "$3")" in
        *:cinnamon:wayland) echo "Cinnamon runs on X11 here (use gnome or kde)" ;;
        arch:kde:x11) echo "The Plasma of Arch (6.8 and later) has no X11 session (use fedora or ubuntu)" ;;
    esac
}

# A script, run in the container with a feature version, that prints the
# package of the OpenJDK of that version that the distribution has, if any: a
# package of its own, or the one of the newest OpenJDK, when that is the
# version
jdk_package_script() {
    case "$(package_manager "$1")" in
        dnf) printf '%s' 'for p in "java-$1-openjdk-devel" java-latest-openjdk-devel; do
                v=$(dnf -q repoquery --latest-limit=1 --qf "%{version}\n" "$p" 2>/dev/null | head -n 1)
                case "$v" in "$1"|"$1".*) echo "$p"; exit 0 ;; esac
            done' ;;
        apt) printf '%s' 'sudo env DEBIAN_FRONTEND=noninteractive apt-get update > /dev/null 2>&1
            apt-cache show "openjdk-$1-jdk" > /dev/null 2>&1 && echo "openjdk-$1-jdk"' ;;
        pacman) printf '%s' 'sudo pacman -Sy > /dev/null 2>&1
            for p in "jdk$1-openjdk" jdk-openjdk; do
                v=$(pacman -Si "$p" 2>/dev/null | sed -n "s/^Version *: *//p")
                case "$v" in "$1"|"$1".*|"$1"u*) echo "$p"; exit 0 ;; esac
            done' ;;
    esac
}

# A script, run in the container with the packages as arguments, that prints
# the ones not installed
missing_script() {
    case "$(package_manager "$1")" in
        dnf) printf '%s' 'for p in "$@"; do rpm -q "$p" > /dev/null 2>&1 || printf "%s " "$p"; done' ;;
        apt) printf '%s' 'for p in "$@"; do dpkg-query -W -f="\${Status}" "$p" 2>/dev/null | grep -q "ok installed" || printf "%s " "$p"; done' ;;
        pacman) printf '%s' 'for p in "$@"; do pacman -Q "$p" > /dev/null 2>&1 || printf "%s " "$p"; done' ;;
    esac
}

# A script, run in the container with the packages as arguments, that
# installs them
install_script() {
    case "$(package_manager "$1")" in
        dnf) printf '%s' 'sudo dnf install -y "$@"' ;;
        apt) printf '%s' 'sudo env DEBIAN_FRONTEND=noninteractive apt-get update &&
            sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@"' ;;
        pacman) printf '%s' 'sudo pacman -Syu --noconfirm --needed "$@"' ;;
    esac
}
