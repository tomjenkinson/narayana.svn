package com.arjuna.ats.jta.distributed.server;

public interface LookupProvider {
	public RemoteServer lookup(Integer jndiName);
}
