# jfx-tester

Runs the OpenJFX system tests (`tests/system`, with the robot), or a manual
test, in a nested GNOME Shell or Plasma desktop, or in Cinnamon on a nested X server,
inside a
[toolbox](https://containertoolbx.org) container of each distribution given.
The tests run against the GTK, the compositor and the Xwayland of that
distribution, and the running desktop is left untouched.

```sh
./jfx-tester --source=$HOME/jfx --distros=fedora:44,ubuntu:24.04,arch --desktops=gnome,kde --headless --parallel
```

For each distribution, jfx-tester:

1. creates the toolbox container `jfx-tester-DISTRO-RELEASE` if it does not
   exist (it asks first, unless `--yes` is given);
2. installs what the build, the runtime and the desktops need
   (`lib/distros.sh`), and installs again whatever is missing on later runs;
3. installs the OpenJDK of the distribution, of the feature version given by
   `--jdk-version` (by default `jfx.build.jdk.version.min` of the
   `build.properties` of the sources, or of the workspace), or uses the JDK
   of the host given by `--jdk`;
4. with `--source`, copies the sources given to a workspace under
   `~/.cache/jfx-tester/workspace`, so that the natives are built against the
   libraries of the container and the build of the host is left alone, and
   builds the SDK and the system tests in the container;
5. for each desktop, starts the nested compositor on a private D-Bus session
   with its own settings, runs `:systemTests:test` in it, and closes it.

Without `--source`, the tests run with the build that the workspace has: the
first run of a workspace needs `--source`, and so does a change of the sources.
A change of the configuration (such as the one `--log-categories` asks for)
needs a build too, with `--source` or `--build --no-sync`, which builds the
workspace as it is, without copying the sources. `--clean` cleans it first.

There is no default path for the sources: `--build` needs `--source`, unless
it is given `--no-sync`. Without `--tests`, all the system tests run.

## Results

Each run gets a folder of its own, `results/YYYY-MM-DD_HH-MM-SS` (`RUN` below),
or the same under `--results`, so the results of the runs before it stay. Nothing
removes the old ones. Each environment, a distribution and a desktop such as
`fedora-44-gnome`, gets:

| Path                     | What                                                        |
|--------------------------|-------------------------------------------------------------|
| `RUN/logs/ENV/`          | logs of Gradle, of the compositor and of `approve.py`       |
| `RUN/logs/ENV/tests/`    | a log per test class: the output and outcome of each test   |
| `RUN/logs/ENV/junit/`    | the JUnit reports                                           |
| `RUN/reports/ENV/`       | the HTML report of the tests                                |
| `RUN/reports/index.html` | the outcome of every environment, with links to the reports |

The build log is `RUN/logs/DISTRO-RELEASE/build.log`, or
`RUN/logs/ENV/build.log` with `--parallel`. When all the environments end,
or the run is interrupted, a summary is printed, with or without `--parallel`:
the outcome of each environment, the tests that failed, its report and the
totals. An environment that did not end shows as `STOPPED`. The exit status is 1
when any environment fails.

## Parallel

`--parallel` runs every environment at the same time, once the containers
are set up (one after the other, as that may ask questions). Each environment
then has a workspace and a build of its own (built with `--source` the first
time), as two Gradle builds in the same workspace run the same tasks at the
same time and fail. The output of each
distribution goes to `RUN/logs/DISTRO-RELEASE/console.log`. Use it with
`--headless`, or each compositor opens a window.

## Distributions

As toolbox supports them (see `man toolbox`):

| Distribution | Release                         | GNOME | KDE | KDE on X11 |
|--------------|---------------------------------|-------|-----|------------|
| `fedora`     | `N`, `fN` or `rawhide`          | yes   | yes | yes        |
| `ubuntu`     | `YY.MM`                         | yes   | yes | yes        |
| `arch`       | `latest` (the default)          | yes   | yes | no (6.8)   |
| `rhel`       | `X.Y` (needs a subscribed host) | yes   | no  | no         |
| `mint`       | `21`–`21.3`, `22`–`22.3` (22.3) | yes   | yes | yes        |

KDE on X11 needs `kwin-x11`, which Plasma 6.8 and later do not have.

All of them also have `cinnamon`, but RHEL. Without `--distros`, the
distribution of the host is used.

### Linux Mint

toolbox has no Mint: `mint:X.Y` is a container of the Ubuntu that release is
based on (24.04 for 22.x, 22.04 for 21.x), with the repository of Mint
(`main upstream import backport`) pinned before Ubuntu at priority 700, as
Mint does, so that Cinnamon and what it needs come from Mint. The repository
has no HTTPS: apt checks it with the signing key of Mint in `lib/linuxmint.asc`
(`302F0738F465C1535761F965A6616109451BBBF2`, from keyserver.ubuntu.com).

## Desktops

* **gnome**: `gnome-shell --devkit` (GNOME 49 and later, in a window of the
  devkit viewer), `gnome-shell --nested` (earlier), or
  `gnome-shell --headless`. Animations, the welcome dialog, the screen lock
  and the hot corner are turned off.
* **kde**: the whole Plasma desktop (`plasma-workspace` and `plasma-desktop`:
  the panel, `plasmashell`, `ksmserver`, `kded`), started by `plasma_session`
  as `startplasma-wayland` does, without systemd. With `--session=wayland`
  (the default), on a nested `kwin_wayland_wrapper --xwayland`, in a window of
  the host or with `--virtual`; with `--session=x11`, on a nested X server, as
  Cinnamon, where `plasma_session` starts `kwin_x11`. The environments of
  X11 are named `DISTRO-RELEASE-kde-x11`. Animations, the focus stealing
  prevention, the screen lock and the indexing of the files are turned off.

  `--xwayland-scale` is the setting *Legacy Applications (X11)* of the
  displays (`XwaylandClientsScale` of `kdeglobals`), for the X11 windows,
  such as the ones of JavaFX: `apps` (the default of Plasma), where they scale
  themselves to the largest scale of the monitors (`Xft.dpi`), or `system`,
  where they draw at the scale 1 and KWin scales them. Xwayland has one
  scale: with monitors of different scales, KWin scales the windows on the
  others either way, down with `apps` and up with `system`, which blurs them:

  ```sh
  ./jfx-tester --desktops=kde --headless --monitors=2560x1600@1.25,2560x1080@1 --xwayland-scale=system --manual=manual/ScaleInfo.java
  ```

* **cinnamon**: Cinnamon on a nested X server: `Xephyr` in a window, or
  Xorg with the dummy driver with more than one monitor or with `--headless`.
  In a window, Xorg shows through `x0vncserver` and the TigerVNC viewer,
  which takes the mouse and the keyboard; closing it ends the session, as
  closing Xephyr does. The tests see an X11 session, as on Xorg: the
  robot uses XTest and the capture of the X server, with no portals.
  Animations, the screen lock and the notifications are turned off.

`--headless` runs the compositor without a window: nothing shows on the
desktop, and the focus on the host does not matter. A manual test then ends
after `--timeout=SECONDS` (30 by default), as there is no window to close; in a
window, it ends when closed, or after `--timeout` when given.

In a window, the nested compositor takes the scale of the desktop of the host:
KWin gives its outputs that scale, and `--monitors` gets its sizes and scales
exactly with `--headless` only.

`manual/ScaleInfo.java` prints what JavaFX sees of the monitors and of the
scale of a window on each, and of the settings of Xwayland (`xrdb`). It moves
its window to the second monitor after 3 seconds, and prints again whenever the
window changes monitor or scale.

`manual/MenuTest.java` has a menu bar with items of each kind (an
accelerator, disabled, check, radio, a submenu, separators) and a context
menu; it opens the File menu by itself, and prints the items chosen and the
menus shown and hidden.

The container gets a private `/tmp/.X11-unix` (a tmpfs mounted with
`podman exec --user root`), as the one of the host shows there as owned by
`nobody`, where neither Mutter nor KWin create the socket of their Xwayland.

## Monitors

`--monitors=4k@2,1080p@1.25` gives the nested desktop monitors side by side,
each a resolution, `WxH` or a name (`720p`, `1080p`/`fhd`, `wuxga`,
`1440p`/`qhd`/`2k`, `4k`/`uhd`/`2160p`, `5k`, `8k`), with a scale. `--size`
and `--scale` are one monitor (`wuxga@1` by default).

* **gnome**: all of them, with their scales (through the display
  configuration of Mutter; fractional scales with `scale-monitor-framebuffer`).
  With a window, GNOME 51 or later, or before 49; GNOME 49 and 50 show one
  monitor of the size of the viewer, so use `--headless`.
* **kde**: as many outputs, all of the size of the first, each with its scale
  and side by side, through `kscreen-doctor`. The Xwayland of KWin takes the
  coordinates of the largest scale. On X11, as Cinnamon.
* **cinnamon**: X11 has one scale, the one of the first monitor (`GDK_SCALE`,
  or `-Dglass.gtk.uiScale` when it is fractional). With `--headless`, Xorg
  with the dummy driver has an output of RandR for each monitor, also in a
  window (through the VNC viewer), as Xephyr has one:

  ```sh
  ./jfx-tester --distros=mint --desktops=cinnamon --monitors=1280x800,1024x768 --manual=manual/MenuTest.java
  ```

## Sessions

`--session` sets the kind of session of every desktop:

| Value     | The tests run on                                   | Desktops                |
|-----------|----------------------------------------------------|-------------------------|
| `wayland` | Xwayland of a nested compositor                    | GNOME, KDE              |
| `xorg`    | Xorg, shown through VNC unless `--headless`        | GNOME before 49, KDE, Cinnamon |
| `x11`     | Xephyr in a window with one monitor, otherwise Xorg | GNOME before 49, KDE, Cinnamon |

Without it, GNOME and KDE are Wayland and Cinnamon `x11`. A desktop without that
kind of session shows as `SKIP`. The environments of GNOME and KDE on X11 are named
`DISTRO-RELEASE-DESKTOP-x11`.

## Robot

In an X11 session, the tests run on Xorg or Xephyr, as said above. On Wayland, the GTK 3
glass, an X11 client, runs on the Xwayland of the nested compositor, but they see a Wayland session (`Util.isOnWayland()`): the
robot goes through the screencast and remote desktop portals of the
compositor. XTest and a capture of the root window do not work there, as the
compositor redirects the X windows and owns the pointer.

The portals ask for permission in a dialog of the nested desktop.
`lib/approve.py` grants it through the accessibility (AT-SPI) of the dialog:
it turns on "Allow Remote Interaction", and presses "Share". The portals run
in English for that. What it did is in `logs/ENV/approve.log`. The robot keeps
its token in `~/.cache/jfx-tester/home/CONTAINER-DESKTOP` (the `user.home` of
the tests), and the portals keep their permissions in the `.local/share`
there, apart from the host.

`--jvm-args="-Djavafx.robot.screenshotDebug=true"` shows what the robot does
with the tokens, in the logs of the tests.

## Glass log

The tests and the manual tests always run with `-Djdk.gtk.verbose=true`, but
the test lab, whose screen queries would fill the log: `--jvm-args` can ask for it.
`--log-categories=focus,state` turns on categories of the log of the glass of
GTK (`-Dglass.gtk.logCategories`): `all`, `dialog`, `input`, `lifecycle`,
`state`, `focus`, `position` and `size`. That log is compiled in the natives
of `-PCONF=DebugNative` only, which the build then gets, unless a `-PCONF` is
passed after `--`. Switching it needs `--source` or `--build --no-sync`, which
then builds the natives again.

The natives write it to the output of the process, not through `System.out`:
it is in `logs/ENV/gradle.log` (or `manual.log`), not in `logs/ENV/tests/`.

## Manual tests

```sh
./jfx-tester --desktops=kde --manual=$HOME/jfx/tests/manual/UI/WindowResizableTest.java
./jfx-tester --distros=ubuntu:24.04 --manual=/path/to/MyTest.jar --jvm-args=-Dprism.order=sw -- arg1
```

`--manual` runs a `.java` source (with the source launcher) or a `.jar` with
the SDK built in the container (`java @build/run.args`), from its directory,
in a nested desktop with a window; the session ends when it is closed. The
file can be anywhere on the host (a relative path is from the current
directory), and runs from where it is. The arguments after `--` go to the
test, and `--jvm-args` to its JVM.

`--sdk=DIR` runs it with a JavaFX SDK of the host instead (a directory with
`lib/javafx.*.jar` and the natives, such as one downloaded or built
elsewhere), which needs no sources, no workspace and no build, so it takes
neither `--source` nor `--build`. The JDK is the one of `--jdk-version` or
`--jdk`, or else the version of the workspace of the container, if any:

```sh
./jfx-tester --distros=arch --desktops=kde --sdk=$HOME/javafx-sdk-25 --jdk-version=25 --manual=Hello.java
```

## JDK-8354943

`manual/JDK8354943Test.java` has the tests of the bugs that JDK-8354943 should
fix, each with its link, where it was reported and the commands below. With
`--source=DIR`, they first build a branch; without it, the build of the
workspace runs. `-Dtests.repeat` is for the intermittent bugs.

`manual/JDK8354943Test.sh` runs them all, one after the other, and sums them up:

```sh
manual/JDK8354943Test.sh --list
manual/JDK8354943Test.sh --source=$HOME/IdeaProjects/jfx --build --headless
manual/JDK8354943Test.sh JDK-8353556 JDK-8316425
```

`--source` and `--build` go only to the first run of each distribution, and the
runs share a folder, `results/JDK8354943Test_YYYY-MM-DD_HH-MM-SS`, with the output
of each one as `BUG-DISTRO-SESSION.log` and its logs and reports in a folder beside it.

| Bug | Command |
|-----|---------|
| [JDK-8365201](https://bugs.openjdk.org/browse/JDK-8365201) | `./jfx-tester --distros=mint:22.3 --desktops=cinnamon --session=xorg --monitors=1920x1080,1280x720 --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8365201"` |
| [JDK-8366009](https://bugs.openjdk.org/browse/JDK-8366009) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8366009 -Dtests.repeat=20"` |
| [JDK-8366009](https://bugs.openjdk.org/browse/JDK-8366009) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8366009 -Dtests.repeat=20"` |
| [JDK-8367893](https://bugs.openjdk.org/browse/JDK-8367893) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8367893"` |
| [JDK-8367893](https://bugs.openjdk.org/browse/JDK-8367893) | `./jfx-tester --distros=ubuntu:22.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8367893"` |
| [JDK-8321624](https://bugs.openjdk.org/browse/JDK-8321624) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8321624 -Dtests.repeat=20"` |
| [JDK-8321624](https://bugs.openjdk.org/browse/JDK-8321624) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8321624 -Dtests.repeat=20"` |
| [JDK-8321624](https://bugs.openjdk.org/browse/JDK-8321624) | `./jfx-tester --distros=ubuntu:22.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8321624 -Dtests.repeat=20"` |
| [JDK-8321624](https://bugs.openjdk.org/browse/JDK-8321624) | `./jfx-tester --distros=ubuntu:22.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8321624 -Dtests.repeat=20"` |
| [JDK-8353556](https://bugs.openjdk.org/browse/JDK-8353556) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8353556"` |
| [JDK-8353556](https://bugs.openjdk.org/browse/JDK-8353556) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8353556"` |
| [JDK-8355073](https://bugs.openjdk.org/browse/JDK-8355073) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8355073"` |
| [JDK-8355073](https://bugs.openjdk.org/browse/JDK-8355073) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8355073"` |
| [JDK-8353612](https://bugs.openjdk.org/browse/JDK-8353612) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8353612"` |
| [JDK-8353612](https://bugs.openjdk.org/browse/JDK-8353612) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8353612"` |
| [JDK-8337400](https://bugs.openjdk.org/browse/JDK-8337400) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8337400"` |
| [JDK-8316891](https://bugs.openjdk.org/browse/JDK-8316891) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=wayland --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8316891 -Dtests.repeat=20"` |
| [JDK-8316891](https://bugs.openjdk.org/browse/JDK-8316891) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8316891 -Dtests.repeat=20"` |
| [JDK-8316425](https://bugs.openjdk.org/browse/JDK-8316425) | `./jfx-tester --distros=ubuntu:24.04 --desktops=gnome --session=xorg --manual=manual/JDK8354943Test.java --jvm-args="-Dtests.run=true -Dtests.bugs=JDK-8316425"` |

Each bug runs as its report says, on Ubuntu 24.04 GNOME on Wayland, where the
tests run on Xwayland, on Xorg, or on both when the report names both or does not
say. JDK-8365201 was reported on Cinnamon, so it runs on the Xorg of Mint 22.3,
with a second monitor smaller than the first. JDK-8316425 was reported on Xorg on
Fedora 38 and Ubuntu 23.04, whose nearest environment here is Ubuntu 24.04. The
runs of `ubuntu:22.04` need `--ubuntu22`, its container and a build of its
workspace (`--yes --source=DIR --build`). `--session=wayland` or `--session=xorg`
keeps only the runs of that kind of session.

## Test lab

```sh
./jfx-tester --distros=fedora:44 --testlab
./jfx-tester --distros=fedora:44 --testlab --headless --jvm-args=-Dtests.labs=robot,windows
```

`--testlab` runs `manual/testlab/TestLab.java` as the manual test, based on the
tests app of jfx-wayland: a window with a tab per lab (robot, windows, screens,
stage, dialogs, drag and drop, clipboard, render, 3D and Swing), some with sub-tabs by subject, beside its tests, which show OK,
FAIL or SKIP and run all or those of the sub-tab. A test shows the sub-tab that
it needs while it runs.
It needs a JDK of 22 or later and JavaFX 27 or later. With `--headless`, the tests run at startup and it exits
with their status, in 3600 seconds at most (`--timeout`); the results are the
`OK`, `FAIL` and `SKIP` lines of `RUN/logs/ENV/manual.log`.
`-Dtests.labs=robot,windows` keeps only those labs, and `-Dtests.robot=false`
skips the tests of the robot.

`--robot=libei` sends the input of its tests through libei instead of the Robot
of JavaFX, with the foreign function API (`manual/testlab/EiInput.java`), over a
session of the remote desktop portal: it needs Wayland, and a portal with
`ConnectToEIS`, or the robot tests are skipped. Captures and colors come from the Screenshot
portal, which saves each one in a file, deleted right after it is read, and
`--robot=libei` does not reach the system tests, which use that Robot directly.

The robot keeps the tokens of the portals in the home of the container and the
desktop. A token works for the kind of session that got it, in a window or
`--headless`, with the same `--monitors`: a new kind, or more than one token,
drops them, and the next session asks the portal again (`lib/approve.py` answers
it). With a stale token first in the list, the robot closes its session before
each action, which releases the buttons: clicks fail, and drags end as they start.

Each session gets an X display of its own, from `:10` to `:44`, instead of the
first free one.

## Time

All the system tests take about an hour per environment, plus the first build
of each workspace. `--tests` runs only some of them, such as
`--tests=test.robot.javafx.scene.RobotTest.testMouseClickPrimary`.
`--skip-webkit` leaves out the tests that need the natives of WebKit, for a build
without `-PCOMPILE_WEBKIT=true`: the classes of the WebKit packages or that import
them, and the two tests of `NullCCLTest` that use it.

## Stopping

Ctrl-C, or a signal to jfx-tester, ends the nested sessions and the builds in
the containers: a watcher there ends them when jfx-tester ends.

## Options

Run `./jfx-tester --help` for all of them. Arguments after `--` are passed to
Gradle, for both the build and the tests (or to the manual test):

```sh
./jfx-tester --tests='test.robot.javafx.stage.*' --headless -- -PUNSTABLE_TEST=true
./jfx-tester --distros=fedora:44,arch --jdk-version=26
./jfx-tester --list    # the containers of jfx-tester
```

To remove a container: `podman rm -f jfx-tester-ubuntu-24.04`, and its
workspaces and Gradle home under `~/.cache/jfx-tester`.
