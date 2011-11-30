package com.arjuna.ats.jta.distributed.server;


public class LookupProviderImpl implements LookupProvider {
	static {
		System.out.println("Loaded the provider");
	}

	private static LookupProviderImpl instance;

	private RemoteServer[] remoteServers = new RemoteServer[3];

	public static LookupProvider getLookupProvider() {
		if (instance == null) {
			instance = new LookupProviderImpl();
		}
		return instance;
	}

	public LookupProviderImpl() {
		System.out.println("Created the provider");
	}

	@Override
	public RemoteServer lookup(String jndiName) {
		int index = (Integer.valueOf(jndiName) / 1000) - 1;
		return remoteServers[index];
	}

	@Override
	public void clear() {
		for (int i = 0; i < remoteServers.length; i++) {
			// Disconnect
			remoteServers[i] = null;
		}
	}

	@Override
	public void bind(int index, RemoteServer connectTo) {
		remoteServers[index] = connectTo;
	}
}
