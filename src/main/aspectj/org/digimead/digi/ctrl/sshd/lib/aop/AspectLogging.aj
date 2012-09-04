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
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd.lib.aop;

import org.aspectj.lang.reflect.SourceLocation;
import org.digimead.digi.lib.log.Logging;
import org.digimead.digi.lib.aop.Loggable;

privileged public final aspect AspectLogging {
	public pointcut loggingNonVoid(Logging obj, Loggable loggable) : target(obj) && execution(@Loggable !void *(..)) && @annotation(loggable);

	public pointcut loggingVoid(Logging obj, Loggable loggable) : target(obj) && execution(@Loggable void *(..)) && @annotation(loggable);

	public pointcut logging(Logging obj, Loggable loggable) : loggingVoid(obj, loggable) || loggingNonVoid(obj, loggable);

	before(final Object obj, final Loggable loggable) : logging(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.enteringMethod(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Object obj, final Loggable loggable) returning(final Object result) : loggingNonVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (loggable.result())
			org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
					location.getFileName(), location.getLine(),
					thisJoinPointStaticPart.getSignature(), obj, result);
		else
			org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
					location.getFileName(), location.getLine(),
					thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Object obj, final Loggable loggable) returning() : loggingVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Object obj, final Loggable loggable) throwing(final Exception ex) : logging(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethodException(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj, ex);
	}
}
