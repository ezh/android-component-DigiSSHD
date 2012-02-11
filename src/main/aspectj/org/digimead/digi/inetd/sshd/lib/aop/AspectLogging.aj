/*
 * DigiSSHD - DigiINETD component for Android Platform
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
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.inetd.sshd.lib.aop;

import org.digimead.digi.inetd.lib.aop.Loggable;

privileged public final aspect AspectLogging extends
		org.digimead.digi.inetd.lib.aop.Logging {
	public pointcut loggingNonVoid(Loggable loggable) : execution(@Loggable !void *(..)) && @annotation(loggable);

	public pointcut loggingVoid(Loggable loggable) : execution(@Loggable void *(..)) && @annotation(loggable);

	public pointcut logging(Loggable loggable) : loggingVoid(loggable) || loggingNonVoid(loggable);

	before(final Loggable loggable) : logging(loggable) {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			enteringMethod(thisJoinPoint);
	}

	after(final Loggable loggable) returning(final Object result) : loggingNonVoid(loggable) {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			if (loggable.result())
				leavingMethod(thisJoinPoint, result);
			else
				leavingMethod(thisJoinPoint);
	}

	after(final Loggable loggable) returning() : loggingVoid(loggable) {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			leavingMethod(thisJoinPoint);
	}

	after(final Loggable loggable) throwing(final Exception ex) : logging(loggable) {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			leavingMethodException(thisJoinPoint, ex);
	}
}
