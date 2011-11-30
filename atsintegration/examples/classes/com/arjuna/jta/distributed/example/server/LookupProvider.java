/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.arjuna.jta.distributed.example.server;

public class LookupProvider {
	private static LookupProvider instance;

	private RemoteServer[] remoteServers = new RemoteServer[3];

	public static LookupProvider getInstance() {
		if (instance == null) {
			instance = new LookupProvider();
		}
		return instance;
	}

	protected LookupProvider() {
	}

	public RemoteServer lookup(String jndiName) {
		int index = (Integer.valueOf(jndiName) / 1000) - 1;
		return remoteServers[index];
	}

	public void clear() {
		for (int i = 0; i < remoteServers.length; i++) {
			// Disconnect
			remoteServers[i] = null;
		}
	}

	public void bind(int index, RemoteServer connectTo) {
		remoteServers[index] = connectTo;
	}
}
