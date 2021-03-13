#!/bin/sh
#
# Copyright (c) 2001-2020 Mathew A. Nelson and Robocode contributors
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# https://robocode.sourceforge.io/license/epl-v10.html
#

pwd=`pwd`
cd "${0%/*}"
java -DALLOWLOCALHOST=true -Ddebug=true -Xmx512M -cp libs/robocode.jar:libs/gson-2.8.6.jar -XX:+IgnoreUnrecognizedVMOptions "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED" "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED" "--add-opens=java.desktop/sun.awt=ALL-UNNAMED" robocode.Robocode $*
cd "${pwd}"
