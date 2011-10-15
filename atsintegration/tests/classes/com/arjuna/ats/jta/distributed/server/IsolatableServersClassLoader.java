package com.arjuna.ats.jta.distributed.server;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import sun.misc.Resource;
import sun.misc.URLClassPath;

public class IsolatableServersClassLoader extends ClassLoader {

	private Map<String, Class<?>> clazzMap = new HashMap<String, Class<?>>();
	private URLClassPath ucp;
	private String ignoredPackage;

	public IsolatableServersClassLoader(String ignoredPackage, ClassLoader parent) throws SecurityException, NoSuchMethodException, MalformedURLException {
		super(parent);
		this.ignoredPackage = ignoredPackage;

		String property = System.getProperty("java.class.path");
		String[] split = property.split(":");
		URL[] urls = new URL[1];
		for (int i = 0; i < urls.length; i++) {
			String url = split[0];
			if (url.endsWith(".jar")) {
				urls[0] = new URL("jar:file:" + url + "/");
			} else {
				urls[0] = new URL("file:" + url + "/");
			}
		}
		this.ucp = new URLClassPath(urls);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (clazzMap.containsKey(name)) {
			return clazzMap.get(name);
		}
		return super.findClass(name);
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		Class<?> clazz = null;
		if (clazzMap.containsKey(name)) {
			clazz = clazzMap.get(name);
		}

		if (!name.startsWith("com.arjuna") || (ignoredPackage != null && name.matches(ignoredPackage + ".[A-Za-z0-9]*"))) {
			clazz = super.loadClass(name);
		} else {

			String path = name.replace('.', '/').concat(".class");
			Resource res = ucp.getResource(path, false);
			if (res == null) {
				throw new ClassNotFoundException(name);
			}
			try {
				byte[] classData = res.getBytes();
				clazz = defineClass(name, classData, 0, classData.length);
				clazzMap.put(name, clazz);
			} catch (IOException e) {
				throw new ClassNotFoundException(name, e);
			}
		}

		return clazz;
	}
}