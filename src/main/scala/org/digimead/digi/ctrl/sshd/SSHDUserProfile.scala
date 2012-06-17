/*
 * DigiSSHD - DigiControl component for Android Platform
 * Copyright (c) 2012, Alexey Aksenov ezh@ezh.msk.ru. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 or any later
 * version, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd

object SSHDUserProfile {
  val content = """
#!/bin/sh

[[ -r $HOME/.sh_aliases   ]] && . $HOME/.sh_aliases
[[ -r $HOME/.sh_functions ]] && . $HOME/.sh_functions

[[ -r $HOME/.gpg-agent-info ]] && . $HOME/.gpg-agent-info
[[ -r $HOME/.ssh-agent-info ]] && . $HOME/.ssh-agent-info

set -o | grep "stdin " > /dev/null && SHELL=MKSH

which aee > /dev/null && EDITORS=${EDITORS}" aee"
which vi > /dev/null && EDITORS=${EDITORS}" vi"

if [ "${SHELL}" == "MKSH" ]
then
  SHELL_WELCOME="Android MirBSD Korn"
  SHELL_DESCRIPTION="You may find more detailed information about shell at https://www.mirbsd.org/mksh.htm"
fi

if [ -z ${TERM} ]
then
 export TERM=xterm-utf8
fi

echo
echo Welcome to ${SHELL_WELCOME}!
echo DigiSSHD v${DIGISSHD_V} build ${DIGISSHD_B}
echo DigiControl v${DIGICONTROL_V} build ${DIGICONTROL_B}
echo
echo ${SHELL_DESCRIPTION}
echo
echo Profile: ${HOME}/.profile
echo Available console editors: ${EDITORS}
echo Your current terminal: ${TERM}
echo Terminal descriptors located at ${TERMINFO}
echo Ncurses `/data/data/org.digimead.digi.ctrl/files/armeabi/bin/ncursesw6-config --version`"; possible terminals are vt100, linux, xterm (search for more at TERMINFO, for example xterm-utf8 ;-) )" 
echo
echo Your current state is `id`
echo

if [ "${SHELL}" == "MKSH" ]
then
  alias l='ls'
  alias la='l -a'
  alias ll='l -l'
  alias lo='l -a -l'
  	
  echo Aliases
  alias
fi

echo
  """
}
