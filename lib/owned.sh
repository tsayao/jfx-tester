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

# Runs a command in its own process group, which ends when the process given
# ends: jfx-tester runs the build in the container with it, as a signal to
# jfx-tester does not reach the container.
#
# Usage: sh owned.sh OWNER COMMAND [ARGUMENTS...]

owner=$1
shift
setsid "$@" &
child=$!
(while kill -0 "$owner" 2>/dev/null && kill -0 "$child" 2>/dev/null; do sleep 1; done
 kill -TERM -- "-$child") > /dev/null 2>&1 &
wait "$child"
