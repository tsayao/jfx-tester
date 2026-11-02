# jfx-tester

A tool for testing JavaFX (OpenJFX) on many Linux environments from one machine.
It runs the system tests, or a manual test, on the distributions and desktops you
choose, using [Toolbx](https://containertoolbx.org). Your running desktop and your
own build stay untouched.

```sh
./jfx-tester --source=$HOME/jfx --distros=fedora:44,ubuntu:24.04,arch --desktops=gnome,kde --headless --parallel
```

## What is toolbox?

[Toolbx](https://containertoolbx.org) (the `toolbox` command) creates Linux
containers, based on Podman, that are integrated with your machine: a container has
your user and home directory, and can open windows on your desktop. A Fedora host
can run an Ubuntu or Arch userspace without a virtual machine.

That lets jfx-tester test JavaFX against the real GTK, compositor and X server of each
distribution. For each distribution, it:

1. creates a container named `jfx-tester-DISTRO-RELEASE`, if there is none, and
   installs the packages and the JDK it needs (it asks first, unless `--yes` is given);
2. with `--source`, copies the sources to a workspace under `~/.cache/jfx-tester` and
   builds them inside the container;
3. for each desktop, starts a nested session, runs the tests in it, and closes it.

Run jfx-tester on the host, not inside a toolbox.

## Requirements

On the host:

* Linux with `toolbox` and `podman`, which run the containers, and `rsync`, which copies
  the sources. jfx-tester stops when one of them is missing:

  ```sh
  sudo dnf install toolbox podman rsync      # Fedora
  sudo apt install podman-toolbox rsync      # Ubuntu and Debian
  sudo pacman -S toolbox rsync               # Arch
  ```

* A POSIX shell and the usual GNU tools (`awk`, `diff`, `sed`, `tail`), which most
  distributions have by default.
* The sources of OpenJFX for `--source`, or a JavaFX SDK for `--sdk` with `--manual`.
* A graphical session, unless you use `--headless`.
* Internet access for the first run of each distribution, and for `rhel`, a subscribed host.
* Disk space: about 1 GB for each workspace and 2.5 GB for the Gradle caches, besides the
  containers. Building WebKit (`-PCOMPILE_WEBKIT=true`) takes much more.

Nothing else is needed on the host: not Java, Gradle or Python. The packages of the build
and of the desktops, the OpenJDK, and the Python that `lib/approve.py` runs with are
installed in each container, which asks first, unless `--yes` is given.

The containers are regular toolbox containers, so you can also open one yourself:

```sh
./jfx-tester --list                    # the containers of jfx-tester
toolbox enter jfx-tester-ubuntu-24.04  # a shell in one of them
podman rm -f jfx-tester-ubuntu-24.04   # remove one (its workspaces stay in ~/.cache/jfx-tester)
```

## Examples

The first run of a distribution needs `--source`, to build its workspace. After that,
the tests use that build until you give `--source` again. Without `--distros`, the
distribution of the host is used; without `--desktops`, GNOME.

### System tests

```sh
# Build and run all the system tests, on the distribution of the host, with GNOME
./jfx-tester --source=$HOME/jfx

# Run again with the same build, with no window on your desktop
./jfx-tester --headless

# Several distributions and desktops, one after the other
./jfx-tester --distros=fedora:44,ubuntu:24.04 --desktops=gnome,kde --headless

# The same at the same time, with one workspace and build per environment
./jfx-tester --source=$HOME/jfx --distros=fedora:44,ubuntu:24.04 --desktops=gnome,kde --headless --parallel

# Linux Mint with Cinnamon, creating the container without asking
./jfx-tester --source=$HOME/jfx --distros=mint:22.3 --desktops=cinnamon --headless --yes

# Only some tests: a package, or a single test
./jfx-tester --distros=arch --desktops=kde --headless --tests='test.robot.javafx.stage.*'
./jfx-tester --tests=test.robot.javafx.scene.RobotTest.testMouseClickPrimary

# A build without WebKit, leaving out the tests that need it
./jfx-tester --source=$HOME/jfx --skip-webkit

# Arguments after -- go to Gradle
./jfx-tester --headless -- -PUNSTABLE_TEST=true
```

### Manual tests

A manual test is a `.java` source or a `.jar`, anywhere on the host. It ends when you
close its window, or after `--timeout` seconds (30 by default with `--headless`).

```sh
# A test of the OpenJFX sources, on KDE
./jfx-tester --desktops=kde --manual=$HOME/jfx/tests/manual/UI/WindowResizableTest.java

# Your own test, with JVM options and arguments for the test after --
./jfx-tester --distros=ubuntu:24.04 --manual=/path/to/MyTest.jar --jvm-args=-Dprism.order=sw -- arg1

# With a JavaFX SDK of the host: no sources and no build needed
./jfx-tester --distros=arch --desktops=kde --sdk=$HOME/javafx-sdk-25 --jdk-version=25 --manual=Hello.java

# With no window, for two minutes
./jfx-tester --desktops=gnome --headless --timeout=120 --manual=Hello.java
```

### Sessions and monitors

```sh
# GNOME on Wayland (the tests run on its Xwayland), then on Xorg
./jfx-tester --distros=ubuntu:24.04 --session=wayland --manual=Hello.java
./jfx-tester --distros=ubuntu:24.04 --session=xorg --manual=Hello.java

# KDE on X11
./jfx-tester --distros=fedora:44 --desktops=kde --session=x11 --manual=Hello.java

# One monitor of a given size and scale
./jfx-tester --headless --size=1080p --scale=1.5 --manual=Hello.java

# Two monitors, side by side, each with its scale
./jfx-tester --distros=fedora:44 --headless --monitors=4k@2,1080p@1.25 --manual=Hello.java

# How KWin scales X11 windows, such as JavaFX ones: apps (the default) or system
./jfx-tester --desktops=kde --headless --monitors=1440p@1.25,1440p@1 --xwayland-scale=system --manual=Hello.java
```

### JDKs and builds

```sh
# Another version of the OpenJDK of each distribution
./jfx-tester --source=$HOME/jfx --distros=fedora:44,arch --jdk-version=26

# A JDK of the host, for all the distributions
./jfx-tester --source=$HOME/jfx --jdk=$HOME/jdks/jdk-26

# Rebuild the workspace as it is, without copying the sources
./jfx-tester --build --no-sync

# Clean build
./jfx-tester --source=$HOME/jfx --clean
```

### Debugging

```sh
# The glass log: rebuilds the natives as DebugNative, and goes to gradle.log or manual.log
./jfx-tester --source=$HOME/jfx --log-categories=focus,state --manual=Hello.java

# What the robot does with the tokens of the portals
./jfx-tester --tests='test.robot.javafx.scene.*' --jvm-args=-Djavafx.robot.screenshotDebug=true
```

Log categories: `all`, `dialog`, `input`, `lifecycle`, `state`, `focus`, `position`
and `size`.

### Test lab

An interactive window with labs for the robot, windows, screens, dialogs, drag and
drop, clipboard, rendering, 3D and Swing. It needs JDK 22 and JavaFX 27 or later.

```sh
# Interactive
./jfx-tester --distros=fedora:44 --testlab

# Run the tests at startup and exit with their status
./jfx-tester --distros=fedora:44 --testlab --headless

# Only some labs
./jfx-tester --distros=fedora:44 --testlab --headless --jvm-args=-Dtests.labs=robot,windows

# Send the input through libei instead of the Robot of JavaFX (Wayland only)
./jfx-tester --distros=fedora:44 --testlab --robot=libei
```

### JDK-8354943

`manual/JDK8354943Test.sh` runs the tests of the bugs that JDK-8354943 should fix, each
on the environments of its report. Nothing is copied or built unless `--build` or
`--clean` is given. Without them, it uses `DIR/build/sdk` with `--source=DIR`, the SDK of
`--sdk`, or else the builds of the workspaces of `mint:22.3` and `ubuntu:24.04`. A build
made on a newer distribution may not load in those containers: it then stops and says so.

```sh
manual/JDK8354943Test.sh --list                                 # print the commands
manual/JDK8354943Test.sh --headless                             # run them all
manual/JDK8354943Test.sh --headless --session=xorg              # only on Xorg
manual/JDK8354943Test.sh --headless --source=$HOME/jfx           # run the build of these sources
manual/JDK8354943Test.sh --headless --source=$HOME/jfx --build   # copy and build them first
manual/JDK8354943Test.sh --sdk=DIR --headless JDK-8353556 JDK-8316425
```

## Distributions and desktops

| Distribution | `--distros`                     | GNOME | KDE | KDE on X11 | Cinnamon |
|--------------|---------------------------------|-------|-----|------------|----------|
| Fedora       | `fedora:N`, `fedora:rawhide`    | yes   | yes | yes        | yes      |
| Ubuntu       | `ubuntu:YY.MM`                  | yes   | yes | yes        | yes      |
| Arch         | `arch`                          | yes   | yes | no         | yes      |
| RHEL         | `rhel:X.Y` (subscribed host)    | yes   | no  | no         | no       |
| Linux Mint   | `mint:X.Y` (21–21.3, 22–22.3)   | yes   | yes | yes        | yes      |

Toolbox has no Mint image: `mint:X.Y` is the Ubuntu it is based on, plus the
repository of Mint. KDE on X11 is not available from Plasma 6.8 on.

`--session` chooses the kind of session. A desktop without that kind shows as `SKIP`.

| `--session` | The tests run on                                     | Desktops                       |
|-------------|------------------------------------------------------|--------------------------------|
| `wayland`   | Xwayland of a nested compositor (default for GNOME and KDE) | GNOME, KDE              |
| `xorg`      | Xorg, shown through VNC unless `--headless`          | GNOME before 49, KDE, Cinnamon |
| `x11`       | Xephyr in a window with one monitor, otherwise Xorg (default for Cinnamon) | GNOME before 49, KDE, Cinnamon |

Monitors have some limits. In a window, the nested desktop takes the scale of your
desktop, so exact sizes and scales need `--headless`.

* **GNOME**: GNOME 49 and 50 show only one monitor in a window, of the size of the viewer.
* **KDE on Wayland**: all the outputs get the size of the first monitor, each with its
  own scale.
* **X11**: one scale for all the monitors, the one of the first.

## Results

Each run gets its own folder, `results/YYYY-MM-DD_HH-MM-SS`, or the same under
`--results`. Old runs are never removed. An environment is a distribution and a desktop,
such as `fedora-44-gnome`.

| Path                     | What                                                 |
|--------------------------|------------------------------------------------------|
| `reports/index.html`     | the outcome of every environment, with links         |
| `reports/ENV/`           | the HTML report of the tests                         |
| `logs/ENV/`              | the logs of Gradle, the compositor and the manual test |
| `logs/ENV/tests/`        | a log per test class                                 |
| `logs/ENV/junit/`        | the JUnit reports                                    |
| `logs/DISTRO-RELEASE/build.log` | the build (`logs/ENV/build.log` with `--parallel`) |

While the system tests run, a terminal shows a progress bar for each environment, from
the copy and the build to the tests, with how many tests passed, failed and were
skipped, and a total. With `--parallel`, the output has only the bars:

```
⠹ ubuntu-26.04-gnome ████▏░░░░░░░░░░░░░░░  20% 61/302 ✓540 ✗2 ⊘11 41m10s left  …javafx.stage.SizeToSceneTest: SUCCESS
⠹ ubuntu-26.04-kde   ██▌░░░░░░░░░░░░░░░░░  12% 37/302 ✓301 ✗0 ⊘4 52m33s left  …javafx.scene.RobotTest: SUCCESS
  0/2 done  ✓841 ✗2 ⊘15  8m03s
```

An environment with no test for 3 minutes shows `⚠` and for how long. The bars use plain
characters outside UTF-8, and no colors with `NO_COLOR`. The total of classes and the time
left are estimates, from the sources. When the output is not a terminal, each test class
prints this as a `PROGRESS` line.

A summary is printed at the end, or when the run is interrupted. The exit status is 1
when any environment fails.

## Good to know

* All the system tests take about an hour per environment, plus the first build of
  each workspace. Use `--tests` to run fewer.
* Use `--headless` with `--parallel`, or each compositor opens a window. It also means
  that the focus on your desktop does not matter.
* After changing the sources, give `--source` again to copy and rebuild them.
* On Wayland, the robot goes through the portals of the compositor, and `lib/approve.py`
  answers their permission dialogs in the nested desktop.
* A test stuck in native code or in a deadlock fails at its JUnit timeout and the next
  class runs: the tests run in `SEPARATE_THREAD` timeout mode.
* Ctrl-C ends the nested sessions and the builds in the containers.

Run `./jfx-tester --help` for all the options.

## License

GPL version 2 with the Classpath exception, as OpenJFX: see [LICENSE](LICENSE).
